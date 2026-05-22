package dev.hytalemodding.impulse.core.internal.resources;

import com.hypixel.hytale.component.Resource;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.core.ImpulsePlugin;
import dev.hytalemodding.impulse.core.internal.worker.PhysicsWorkerCommand;
import dev.hytalemodding.impulse.core.internal.worker.PhysicsWorkerResult;
import dev.hytalemodding.impulse.core.internal.worker.PhysicsWorkerRunner;
import dev.hytalemodding.impulse.core.internal.worker.PhysicsWorkerStepCommand;
import dev.hytalemodding.impulse.core.internal.worker.PhysicsWorkerStepCompletion;
import lombok.Getter;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Runtime-only worker owner for one world EntityStore.
 */
public final class PhysicsWorldWorkerResource implements Resource<EntityStore>, AutoCloseable {

    public static final int DEFAULT_QUEUE_CAPACITY = 2;
    public static final Duration DEFAULT_CLOSE_TIMEOUT = Duration.ofSeconds(5L);

    private final Object lifecycleLock = new Object();
    private final int queueCapacity;
    @Nonnull
    private final Duration closeTimeout;
    @Nullable
    private PhysicsWorkerRunner runner;
    @Nullable
    private PendingStep pendingStep;
    @Getter
    private volatile boolean closed;

    public PhysicsWorldWorkerResource() {
        this(DEFAULT_QUEUE_CAPACITY, DEFAULT_CLOSE_TIMEOUT);
    }

    PhysicsWorldWorkerResource(int queueCapacity, @Nonnull Duration closeTimeout) {
        if (queueCapacity < 1) {
            throw new IllegalArgumentException("queueCapacity must be positive");
        }
        this.queueCapacity = queueCapacity;
        this.closeTimeout = Objects.requireNonNull(closeTimeout, "closeTimeout");
    }

    public void start(@Nonnull String worldName) {
        Objects.requireNonNull(worldName, "worldName");
        synchronized (lifecycleLock) {
            if (closed || runner != null) {
                return;
            }
            runner = new PhysicsWorkerRunner(threadName(worldName), queueCapacity);
        }
    }

    @Nonnull
    public PhysicsWorkerResult submitAndDrain(@Nonnull PhysicsWorkerCommand command)
        throws InterruptedException, ExecutionException {
        Objects.requireNonNull(command, "command");
        PhysicsWorkerRunner current = currentRunner();
        return current.submit(command).get();
    }

    public boolean submitStepIfIdle(@Nonnull PhysicsWorkerStepCommand command) {
        Objects.requireNonNull(command, "command");
        synchronized (lifecycleLock) {
            if (pendingStep != null) {
                return false;
            }
            PhysicsWorkerRunner current = currentRunner();
            CompletableFuture<PhysicsWorkerResult> future = current.submit(command);
            pendingStep = new PendingStep(command, future);
            return true;
        }
    }

    @Nullable
    public PhysicsWorkerStepCompletion pollCompletedStep() {
        PendingStep completed;
        synchronized (lifecycleLock) {
            PendingStep current = pendingStep;
            if (current == null || !current.future().isDone()) {
                return null;
            }
            pendingStep = null;
            completed = current;
        }

        try {
            PhysicsWorkerResult result = completed.future().get();
            return new PhysicsWorkerStepCompletion(result,
                completed.command().publishedFrame(),
                completed.command().failure(),
                null);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return new PhysicsWorkerStepCompletion(null, null, null, exception);
        } catch (ExecutionException exception) {
            return new PhysicsWorkerStepCompletion(null, null, null, exception.getCause());
        }
    }

    public boolean hasPendingStep() {
        synchronized (lifecycleLock) {
            return pendingStep != null;
        }
    }

    public boolean isStarted() {
        PhysicsWorkerRunner current = runner;
        return current != null && current.isAccepting();
    }

    public int pendingCommands() {
        PhysicsWorkerRunner current = runner;
        return current == null ? 0 : current.pendingCommands();
    }

    public boolean isWorkerThread() {
        PhysicsWorkerRunner current = runner;
        return current != null && current.isWorkerThread();
    }

    @Override
    public void close() {
        PhysicsWorkerRunner current;
        synchronized (lifecycleLock) {
            closed = true;
            current = runner;
            runner = null;
            pendingStep = null;
        }
        if (current != null && !current.shutdown(closeTimeout)) {
            throw new IllegalStateException("Physics worker runner did not stop within "
                + closeTimeout);
        }
    }

    @Nonnull
    @Override
    public PhysicsWorldWorkerResource clone() {
        return new PhysicsWorldWorkerResource(queueCapacity, closeTimeout);
    }

    @Nonnull
    private PhysicsWorkerRunner currentRunner() {
        PhysicsWorkerRunner current = runner;
        if (current == null) {
            throw new RejectedExecutionException(closed
                ? "physics worker resource is closed"
                : "physics worker runner is not started");
        }
        return current;
    }

    @Nonnull
    private static String threadName(@Nonnull String worldName) {
        String trimmed = worldName.trim();
        String suffix = trimmed.isEmpty() ? "<unknown>" : trimmed;
        return "Impulse physics worker [" + suffix + "]";
    }

    public static ResourceType<EntityStore, PhysicsWorldWorkerResource> getResourceType() {
        return ImpulsePlugin.get().getPhysicsWorldWorkerResourceType();
    }

    private record PendingStep(@Nonnull PhysicsWorkerStepCommand command,
                               @Nonnull CompletableFuture<PhysicsWorkerResult> future) {
    }
}
