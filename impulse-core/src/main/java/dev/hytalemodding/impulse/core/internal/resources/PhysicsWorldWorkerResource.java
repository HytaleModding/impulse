package dev.hytalemodding.impulse.core.internal.resources;

import com.hypixel.hytale.component.Resource;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.core.ImpulsePlugin;
import dev.hytalemodding.impulse.core.internal.worker.PhysicsWorkerCommand;
import dev.hytalemodding.impulse.core.internal.worker.PhysicsWorkerMutationCompletion;
import dev.hytalemodding.impulse.core.internal.worker.PhysicsWorkerResult;
import dev.hytalemodding.impulse.core.internal.worker.PhysicsWorkerRunner;
import dev.hytalemodding.impulse.core.internal.worker.PhysicsWorkerStepCommand;
import dev.hytalemodding.impulse.core.internal.worker.PhysicsWorkerStepCompletion;
import dev.hytalemodding.impulse.core.internal.resources.owner.PhysicsOwnerHandle;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsMutationHandle;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import lombok.Getter;

/**
 * Runtime-only worker owner for one world EntityStore.
 *
 * <p>Submission completion means the owner thread ran the command. It does not mean a published
 * snapshot or reader-side registration view has caught up. Tick systems should poll this resource
 * and let publication apply completed frames instead of blocking on worker futures.</p>
 */
public final class PhysicsWorldWorkerResource implements Resource<EntityStore>, AutoCloseable, PhysicsOwnerHandle {

    public static final int DEFAULT_QUEUE_CAPACITY = 128;
    public static final Duration DEFAULT_CLOSE_TIMEOUT = Duration.ofSeconds(5L);

    private final Object lifecycleLock = new Object();
    private final Queue<PendingMutation> pendingMutations = new ConcurrentLinkedQueue<>();
    private final int queueCapacity;
    @Nonnull
    private final Duration closeTimeout;
    @Nullable
    private OwnedPhysicsWorkerRunner runner;
    @Nullable
    private PendingStep pendingStep;
    @Getter
    private volatile boolean closed;

    public PhysicsWorldWorkerResource() {
        this(DEFAULT_QUEUE_CAPACITY, DEFAULT_CLOSE_TIMEOUT);
    }

    public PhysicsWorldWorkerResource(int queueCapacity, @Nonnull Duration closeTimeout) {
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
            runner = createOwnedRunner(threadName(worldName), queueCapacity);
        }
    }

    @Nonnull
    public PhysicsWorkerResult submitAndDrain(@Nonnull PhysicsWorkerCommand command)
        throws InterruptedException, ExecutionException {
        Objects.requireNonNull(command, "command");
        return borrowRunner().submit(command).get();
    }

    public boolean submitStepIfIdle(@Nonnull PhysicsWorkerStepCommand command) {
        Objects.requireNonNull(command, "command");
        synchronized (lifecycleLock) {
            if (pendingStep != null) {
                return false;
            }
            CompletableFuture<PhysicsWorkerResult> future = borrowRunnerLocked().submitStepOrThrow(command);
            pendingStep = new PendingStep(command, future, System.nanoTime());
            return true;
        }
    }

    @Nonnull
    public PhysicsMutationHandle<Void> submitMutation(@Nonnull String operation,
        @Nonnull PhysicsWorkerCommand command) {
        return submitMutation(operation, null, command);
    }

    @Nonnull
    public <T> PhysicsMutationHandle<T> submitMutation(@Nonnull String operation,
        @Nullable T value,
        @Nonnull PhysicsWorkerCommand command) {
        CompletableFuture<PhysicsWorkerResult> future = submitMutationFuture(operation, command);
        return PhysicsMutationHandle.fromCompletion(operation, value, future);
    }

    @Nonnull
    public CompletableFuture<PhysicsWorkerResult> submitMutationFuture(@Nonnull String operation,
        @Nonnull PhysicsWorkerCommand command) {
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(command, "command");
        synchronized (lifecycleLock) {
            CompletableFuture<PhysicsWorkerResult> future = borrowRunnerLocked().submitOrThrow(command);
            pendingMutations.add(new PendingMutation(operation, future));
            return future;
        }
    }

    @Nonnull
    public List<PhysicsWorkerMutationCompletion> pollCompletedMutations(int maxCompletions) {
        // Mutation publication is intentionally nonblocking: only completed futures are drained.
        int limit = Math.max(0, maxCompletions);
        if (limit == 0) {
            return Collections.emptyList();
        }
        PendingMutation first = pendingMutations.peek();
        if (first == null || !first.future().isDone()) {
            return Collections.emptyList();
        }
        List<PhysicsWorkerMutationCompletion> completions = new ArrayList<>(limit);
        while (completions.size() < limit) {
            PendingMutation current = pendingMutations.peek();
            if (current == null || !current.future().isDone()) {
                break;
            }
            pendingMutations.poll();
            completions.add(toMutationCompletion(current));
        }
        return completions;
    }

    @Nullable
    public PhysicsWorkerStepCompletion pollCompletedStep() {
        // The isDone guard keeps the world tick from waiting on the physics step.
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

    public long pendingStepAgeNanos() {
        synchronized (lifecycleLock) {
            PendingStep current = pendingStep;
            return current == null ? 0L : Math.max(0L, System.nanoTime() - current.submittedNanos());
        }
    }

    public int pendingMutations() {
        return pendingMutations.size();
    }

    public boolean isStarted() {
        synchronized (lifecycleLock) {
            OwnedPhysicsWorkerRunner current = runner;
            return current != null && current.isAccepting();
        }
    }

    public int pendingCommands() {
        synchronized (lifecycleLock) {
            OwnedPhysicsWorkerRunner current = runner;
            return current == null ? 0 : current.pendingCommands();
        }
    }

    public boolean isWorkerThread() {
        synchronized (lifecycleLock) {
            OwnedPhysicsWorkerRunner current = runner;
            return current != null && current.isWorkerThread();
        }
    }

    @Override
    public boolean isPhysicsOwnerThread() {
        return isWorkerThread();
    }

    @Override
    public void close() {
        shutdownOwnedRunner(detachOwnedRunnerForClose());
    }

    private void shutdownOwnedRunner(@Nullable OwnedPhysicsWorkerRunner ownedRunner) {
        // ownedRunner has been detached from the field; this method releases that ownership.
        if (ownedRunner != null && !ownedRunner.shutdown(closeTimeout)) {
            throw new IllegalStateException("Physics worker runner did not stop within "
                + closeTimeout);
        }
    }

    @Nullable
    private OwnedPhysicsWorkerRunner detachOwnedRunnerForClose() {
        synchronized (lifecycleLock) {
            closed = true;
            OwnedPhysicsWorkerRunner ownedRunner = runner;
            runner = null;
            pendingStep = null;
            pendingMutations.clear();
            return ownedRunner;
        }
    }

    @Nonnull
    @Override
    public PhysicsWorldWorkerResource clone() {
        return new PhysicsWorldWorkerResource(queueCapacity, closeTimeout);
    }

    @Nonnull
    private OwnedPhysicsWorkerRunner borrowRunner() {
        synchronized (lifecycleLock) {
            return borrowRunnerLocked();
        }
    }

    @Nonnull
    private OwnedPhysicsWorkerRunner borrowRunnerLocked() {
        // Borrowed reference only. PhysicsWorldWorkerResource owns and closes the runner.
        OwnedPhysicsWorkerRunner borrowedRunner = runner;
        if (closed || borrowedRunner == null) {
            throw new RejectedExecutionException(closed
                ? "physics worker resource is closed"
                : "physics worker runner is not started");
        }
        return borrowedRunner;
    }

    @Nonnull
    private static OwnedPhysicsWorkerRunner createOwnedRunner(@Nonnull String threadName, int queueCapacity) {
        return new OwnedPhysicsWorkerRunner(threadName, queueCapacity);
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

    @Nonnull
    private static PhysicsWorkerMutationCompletion toMutationCompletion(
        @Nonnull PendingMutation mutation) {
        try {
            return new PhysicsWorkerMutationCompletion(mutation.operation(),
                mutation.future().get(),
                null);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return new PhysicsWorkerMutationCompletion(mutation.operation(), null, exception);
        } catch (ExecutionException exception) {
            return new PhysicsWorkerMutationCompletion(mutation.operation(),
                null,
                exception.getCause());
        }
    }

    private record PendingStep(@Nonnull PhysicsWorkerStepCommand command,
                               @Nonnull CompletableFuture<PhysicsWorkerResult> future,
                               long submittedNanos) {
    }

    private record PendingMutation(@Nonnull String operation,
                                   @Nonnull CompletableFuture<PhysicsWorkerResult> future) {
    }

    private static final class OwnedPhysicsWorkerRunner {

        private final PhysicsWorkerRunner runner;

        private OwnedPhysicsWorkerRunner(@Nonnull String threadName, int queueCapacity) {
            runner = new PhysicsWorkerRunner(threadName, queueCapacity);
        }

        @Nonnull
        private CompletableFuture<PhysicsWorkerResult> submit(@Nonnull PhysicsWorkerCommand command) {
            return runner.submit(command);
        }

        @Nonnull
        private CompletableFuture<PhysicsWorkerResult> submitOrThrow(
            @Nonnull PhysicsWorkerCommand command) {
            return runner.submitOrThrow(command);
        }

        @Nonnull
        private CompletableFuture<PhysicsWorkerResult> submitStepOrThrow(
            @Nonnull PhysicsWorkerCommand command) {
            return runner.submitStepOrThrow(command);
        }

        private boolean isAccepting() {
            return runner.isAccepting();
        }

        private boolean isWorkerThread() {
            return runner.isWorkerThread();
        }

        private int pendingCommands() {
            return runner.pendingCommands();
        }

        private boolean shutdown(@Nonnull Duration timeout) {
            return runner.shutdown(timeout);
        }
    }
}
