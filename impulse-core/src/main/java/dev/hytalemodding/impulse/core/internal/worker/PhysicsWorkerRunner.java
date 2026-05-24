package dev.hytalemodding.impulse.core.internal.worker;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * FIFO single-thread runner for physics worker execution.
 *
 * <p>Step commands are still published through the tick barrier, while queued
 * mutation commands can complete in the background and be observed later by the
 * publication system.</p>
 */
public final class PhysicsWorkerRunner implements AutoCloseable {

    private static final Duration DEFAULT_CLOSE_TIMEOUT = Duration.ofSeconds(5L);
    private static final long POLL_MILLIS = 100L;

    private final BlockingQueue<QueuedCommand> commands;
    private final AtomicBoolean accepting = new AtomicBoolean(true);
    private final AtomicLong nextSequence = new AtomicLong(1L);
    private final AtomicInteger pendingCommands = new AtomicInteger();
    private final Object lifecycleLock = new Object();
    private final Thread thread;
    @Nullable
    private volatile Thread workerThread;

    public PhysicsWorkerRunner(@Nonnull String threadName, int queueCapacity) {
        if (queueCapacity < 1) {
            throw new IllegalArgumentException("queueCapacity must be positive");
        }
        commands = new ArrayBlockingQueue<>(queueCapacity);
        thread = new Thread(this::runLoop, Objects.requireNonNull(threadName, "threadName"));
        thread.setDaemon(true);
        thread.start();
    }

    @Nonnull
    public CompletableFuture<PhysicsWorkerResult> submit(@Nonnull PhysicsWorkerCommand command) {
        try {
            return submitOrThrow(command);
        } catch (RejectedExecutionException exception) {
            return rejected(exception.getMessage());
        }
    }

    @Nonnull
    public CompletableFuture<PhysicsWorkerResult> submitOrThrow(
        @Nonnull PhysicsWorkerCommand command) {
        Objects.requireNonNull(command, "command");
        synchronized (lifecycleLock) {
            if (!accepting.get()) {
                throw new RejectedExecutionException("physics worker runner is closed");
            }

            long sequence = nextSequence.getAndIncrement();
            QueuedCommand queuedCommand = new QueuedCommand(sequence,
                command,
                System.nanoTime(),
                new CompletableFuture<>());
            pendingCommands.incrementAndGet();
            if (!commands.offer(queuedCommand)) {
                pendingCommands.decrementAndGet();
                throw new RejectedExecutionException("physics worker command queue is full");
            }
            return queuedCommand.future();
        }
    }

    public boolean isAccepting() {
        return accepting.get();
    }

    public boolean isWorkerThread() {
        return Thread.currentThread() == workerThread;
    }

    public int pendingCommands() {
        return pendingCommands.get();
    }

    public boolean shutdown(@Nonnull Duration timeout) {
        synchronized (lifecycleLock) {
            accepting.set(false);
        }
        long timeoutNanos = Math.max(0L, timeout.toNanos());
        long deadline = timeoutNanos == 0L
            ? Long.MAX_VALUE
            : System.nanoTime() + timeoutNanos;
        boolean interrupted = false;
        try {
            while (thread.isAlive()) {
                long remainingNanos = deadline - System.nanoTime();
                if (remainingNanos <= 0L) {
                    break;
                }
                try {
                    long millis = TimeUnit.NANOSECONDS.toMillis(remainingNanos);
                    int nanos = (int) (remainingNanos - TimeUnit.MILLISECONDS.toNanos(millis));
                    thread.join(millis, nanos);
                } catch (InterruptedException e) {
                    interrupted = true;
                }
            }
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
        return !thread.isAlive();
    }

    @Override
    public void close() {
        shutdown(DEFAULT_CLOSE_TIMEOUT);
    }

    private void runLoop() {
        workerThread = Thread.currentThread();
        try {
            while (accepting.get() || !commands.isEmpty()) {
                QueuedCommand command;
                try {
                    command = commands.poll(POLL_MILLIS, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    if (!accepting.get() && commands.isEmpty()) {
                        break;
                    }
                    continue;
                }
                if (command == null) {
                    continue;
                }
                run(command);
            }
        } finally {
            rejectRemaining();
            workerThread = null;
        }
    }

    private void run(@Nonnull QueuedCommand command) {
        long startNanos = System.nanoTime();
        PhysicsWorkerResult result = null;
        Throwable failure = null;
        try {
            PhysicsWorkerSnapshot snapshot = command.command().run();
            long completedNanos = System.nanoTime();
            result = new PhysicsWorkerResult(command.sequence(),
                snapshot,
                startNanos - command.submittedNanos(),
                completedNanos - startNanos,
                completedNanos);
        } catch (Throwable throwable) {
            failure = throwable;
        } finally {
            pendingCommands.decrementAndGet();
        }
        if (failure != null) {
            command.future().completeExceptionally(failure);
        } else {
            command.future().complete(result);
        }
    }

    private void rejectRemaining() {
        QueuedCommand queuedCommand;
        while ((queuedCommand = commands.poll()) != null) {
            queuedCommand.future().completeExceptionally(new RejectedExecutionException(
                "physics worker runner closed before command executed"));
            pendingCommands.decrementAndGet();
        }
    }

    @Nonnull
    private static CompletableFuture<PhysicsWorkerResult> rejected(@Nonnull String message) {
        CompletableFuture<PhysicsWorkerResult> future = new CompletableFuture<>();
        future.completeExceptionally(new RejectedExecutionException(message));
        return future;
    }

    private record QueuedCommand(long sequence,
                                 @Nonnull PhysicsWorkerCommand command,
                                 long submittedNanos,
                                 @Nonnull CompletableFuture<PhysicsWorkerResult> future) {
    }
}
