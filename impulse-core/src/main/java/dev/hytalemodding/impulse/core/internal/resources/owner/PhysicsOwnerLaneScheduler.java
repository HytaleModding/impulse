package dev.hytalemodding.impulse.core.internal.resources.owner;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Shared execution pool for serialized per-world physics owner lanes.
 */
public final class PhysicsOwnerLaneScheduler implements AutoCloseable {

    public static final int DEFAULT_POOL_SIZE = 1;
    public static final int DEFAULT_QUEUE_CAPACITY = 128;
    public static final Duration DEFAULT_CLOSE_TIMEOUT = Duration.ofSeconds(5L);

    private static final ThreadLocal<PhysicsOwnerLaneResource> CURRENT_LANE =
        new ThreadLocal<>();
    private static final ThreadLocal<PhysicsOwnerLaneResource> COMPLETING_LANE =
        new ThreadLocal<>();

    private final Object lifecycleLock = new Object();
    private final Set<PhysicsOwnerLaneResource> lanes = ConcurrentHashMap.newKeySet();
    private final ExecutorService executor;
    private final ExecutorService completionExecutor;
    private final int queueCapacity;
    @Nonnull
    private final Duration closeTimeout;
    private boolean closing;
    private boolean closed;

    public PhysicsOwnerLaneScheduler(int poolSize,
        int queueCapacity,
        @Nonnull Duration closeTimeout) {
        if (poolSize < 1) {
            throw new IllegalArgumentException("poolSize must be positive");
        }
        if (queueCapacity < 1) {
            throw new IllegalArgumentException("queueCapacity must be positive");
        }
        this.queueCapacity = queueCapacity;
        this.closeTimeout = Objects.requireNonNull(closeTimeout, "closeTimeout");
        executor = Executors.newFixedThreadPool(poolSize, new OwnerLaneThreadFactory());
        completionExecutor = Executors.newThreadPerTaskExecutor(
            Thread.ofVirtual().name("Impulse physics owner completion ", 1).factory());
    }

    @Nonnull
    public PhysicsOwnerLaneResource createLane() {
        synchronized (lifecycleLock) {
            if (closing || closed) {
                throw new RejectedExecutionException("physics owner lane scheduler is closed");
            }
            PhysicsOwnerLaneResource lane = new PhysicsOwnerLaneResource(this,
                queueCapacity,
                closeTimeout);
            lanes.add(lane);
            return lane;
        }
    }

    boolean isCurrentLane(@Nonnull PhysicsOwnerLaneResource lane) {
        return CURRENT_LANE.get() == lane;
    }

    boolean isCompletingAnyLane() {
        return COMPLETING_LANE.get() != null;
    }

    @Nullable
    PhysicsOwnerLaneResource currentLane() {
        return CURRENT_LANE.get();
    }

    void execute(@Nonnull PhysicsOwnerLaneResource lane,
        @Nonnull Runnable command) {
        Objects.requireNonNull(lane, "lane");
        Objects.requireNonNull(command, "command");
        synchronized (lifecycleLock) {
            if (closed || !lanes.contains(lane)) {
                throw new RejectedExecutionException("physics owner lane is not registered");
            }
            executor.execute(() -> runInLane(lane, command));
        }
    }

    void completeOutsideOwnerContext(@Nonnull PhysicsOwnerLaneResource lane,
        @Nonnull Runnable completion) {
        Objects.requireNonNull(lane, "lane");
        Objects.requireNonNull(completion, "completion");
        try {
            completionExecutor.execute(() -> runCompletion(lane, completion));
        } catch (RejectedExecutionException exception) {
            try {
                runCompletion(lane, completion);
            } catch (RuntimeException | Error completionFailure) {
                exception.addSuppressed(completionFailure);
            }
        }
    }

    void unregister(@Nonnull PhysicsOwnerLaneResource lane) {
        lanes.remove(Objects.requireNonNull(lane, "lane"));
    }

    @Override
    public void close() {
        if (CURRENT_LANE.get() != null || COMPLETING_LANE.get() != null) {
            throw new RejectedExecutionException(
                "cannot synchronously close the physics owner lane scheduler from owner context");
        }

        ArrayList<PhysicsOwnerLaneResource> lanesToClose;
        synchronized (lifecycleLock) {
            if (closed) {
                return;
            }
            closing = true;
            lanesToClose = new ArrayList<>(lanes);
        }

        RuntimeException closeFailure = null;
        for (PhysicsOwnerLaneResource lane : lanesToClose) {
            try {
                lane.close();
            } catch (RuntimeException exception) {
                if (closeFailure == null) {
                    closeFailure = exception;
                } else {
                    closeFailure.addSuppressed(exception);
                }
            }
        }

        executor.shutdown();
        boolean ownerStopped = awaitExecutorStop(executor);
        completionExecutor.shutdown();
        boolean completionStopped = awaitExecutorStop(completionExecutor);
        synchronized (lifecycleLock) {
            closed = true;
        }
        if (!ownerStopped || !completionStopped) {
            IllegalStateException timeout = new IllegalStateException(
                "Physics owner lane scheduler did not stop within " + closeTimeout);
            if (closeFailure != null) {
                timeout.addSuppressed(closeFailure);
            }
            throw timeout;
        }
        if (closeFailure != null) {
            throw closeFailure;
        }
    }

    private void runInLane(@Nonnull PhysicsOwnerLaneResource lane,
        @Nonnull Runnable command) {
        PhysicsOwnerLaneResource previous = CURRENT_LANE.get();
        CURRENT_LANE.set(lane);
        try {
            command.run();
        } finally {
            if (previous == null) {
                CURRENT_LANE.remove();
            } else {
                CURRENT_LANE.set(previous);
            }
        }
    }

    private void runCompletion(@Nonnull PhysicsOwnerLaneResource lane,
        @Nonnull Runnable completion) {
        PhysicsOwnerLaneResource previousCurrent = CURRENT_LANE.get();
        PhysicsOwnerLaneResource previousCompleting = COMPLETING_LANE.get();
        CURRENT_LANE.remove();
        COMPLETING_LANE.set(lane);
        try {
            completion.run();
        } finally {
            if (previousCompleting == null) {
                COMPLETING_LANE.remove();
            } else {
                COMPLETING_LANE.set(previousCompleting);
            }
            if (previousCurrent == null) {
                CURRENT_LANE.remove();
            } else {
                CURRENT_LANE.set(previousCurrent);
            }
        }
    }

    private boolean awaitExecutorStop(@Nonnull ExecutorService target) {
        long timeoutNanos = Math.max(0L, closeTimeout.toNanos());
        long deadline = timeoutNanos == 0L
            ? Long.MAX_VALUE
            : System.nanoTime() + timeoutNanos;
        boolean interrupted = false;
        try {
            while (!target.isTerminated()) {
                long remainingNanos = deadline - System.nanoTime();
                if (remainingNanos <= 0L) {
                    return false;
                }
                try {
                    if (target.awaitTermination(remainingNanos, TimeUnit.NANOSECONDS)) {
                        return true;
                    }
                } catch (InterruptedException exception) {
                    interrupted = true;
                }
            }
            return true;
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static final class OwnerLaneThreadFactory implements ThreadFactory {

        private final AtomicInteger nextThread = new AtomicInteger(1);

        @Override
        public Thread newThread(@Nonnull Runnable runnable) {
            Thread thread = new Thread(runnable,
                "Impulse physics owner lane executor " + nextThread.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        }
    }

}
