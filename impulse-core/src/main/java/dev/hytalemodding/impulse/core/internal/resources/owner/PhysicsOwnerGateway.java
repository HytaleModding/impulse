package dev.hytalemodding.impulse.core.internal.resources.owner;

import dev.hytalemodding.impulse.api.PhysicsBody;
import dev.hytalemodding.impulse.api.PhysicsJoint;
import dev.hytalemodding.impulse.api.PhysicsSpace;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsWorldWorkerResource;
import dev.hytalemodding.impulse.core.internal.worker.PhysicsWorkerAccess;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsMutationHandle;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Internal owner-thread gateway for one world physics resource.
 *
 * <p>This class centralizes worker routing while {@code PhysicsWorldResource} remains the
 * plugin-facing facade. Callbacks routed through this gateway may touch live {@link PhysicsSpace},
 * {@link PhysicsBody}, and {@link PhysicsJoint} instances; ordinary world-thread reads should use
 * published snapshots instead.</p>
 */
public final class PhysicsOwnerGateway {

    private final AtomicReference<PhysicsWorldWorkerResource> workerResource = new AtomicReference<>();

    public void attachWorkerResource(@Nonnull PhysicsOwnerHandle workerResource) {
        this.workerResource.set(requireWorkerResource(workerResource));
    }

    public void detachWorkerResource(@Nonnull PhysicsOwnerHandle workerResource) {
        PhysicsWorldWorkerResource worker = requireWorkerResource(workerResource);
        this.workerResource.compareAndSet(worker, null);
    }

    /**
     * Returns whether the current thread may touch live backend objects without routing.
     */
    public boolean canAccessLiveBackendDirectly() {
        PhysicsWorldWorkerResource worker = workerResource.get();
        return worker == null || worker.isWorkerThread();
    }

    public boolean hasWorkerResource() {
        return workerResource.get() != null;
    }

    public void assertCanAccessLiveBackendDirectly(@Nonnull String operation) {
        Objects.requireNonNull(operation, "operation");
        if (!canAccessLiveBackendDirectly()) {
            throw new IllegalStateException("Impulse live backend operation " + operation
                + " must run on the physics owner thread. Use copied simulation commands, "
                + "copied queries, or an internal owner-routed resource method.");
        }
    }

    public void run(@Nonnull String operation,
        @Nonnull PhysicsOwnerMutation mutation) {
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(mutation, "mutation");
        PhysicsWorldWorkerResource worker = workerResource.get();
        if (worker == null || worker.isWorkerThread()) {
            runDirect(operation, mutation);
            return;
        }
        PhysicsWorkerAccess.run(worker, operation, mutation::run);
    }

    @Nonnull
    public PhysicsMutationHandle<Void> enqueue(@Nonnull String operation,
        @Nonnull PhysicsOwnerMutation mutation) {
        return enqueue(operation, null, mutation);
    }

    @Nonnull
    public <T> PhysicsMutationHandle<T> enqueue(@Nonnull String operation,
        @Nullable T value,
        @Nonnull PhysicsOwnerMutation mutation) {
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(mutation, "mutation");
        PhysicsWorldWorkerResource worker = workerResource.get();
        if (worker == null || worker.isWorkerThread()) {
            return runDirectAsync(operation, value, mutation);
        }
        return PhysicsWorkerAccess.runAsync(worker, operation, value, mutation::run);
    }

    @Nonnull
    public <T> CompletableFuture<T> enqueueCall(@Nonnull String operation,
        @Nonnull PhysicsOwnerCallable<T> callable) {
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(callable, "callable");
        PhysicsWorldWorkerResource worker = workerResource.get();
        if (worker == null || worker.isWorkerThread()) {
            return callDirectAsync(operation, callable);
        }
        return PhysicsWorkerAccess.callAsync(worker, operation, callable::call);
    }

    @Nonnull
    public <T> T call(@Nonnull String operation,
        @Nonnull PhysicsOwnerCallable<T> callable) {
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(callable, "callable");
        PhysicsWorldWorkerResource worker = workerResource.get();
        if (worker == null || worker.isWorkerThread()) {
            try {
                return callable.call();
            } catch (RuntimeException exception) {
                throw exception;
            } catch (Exception exception) {
                throw new IllegalStateException("Physics operation " + operation + " failed",
                    exception);
            }
        }
        return PhysicsWorkerAccess.call(worker, operation, callable::call);
    }

    @Nonnull
    private static PhysicsWorldWorkerResource requireWorkerResource(
        @Nonnull PhysicsOwnerHandle workerResource) {
        Objects.requireNonNull(workerResource, "workerResource");
        if (workerResource instanceof PhysicsWorldWorkerResource worker) {
            return worker;
        }
        throw new IllegalArgumentException("Unsupported physics owner handle "
            + workerResource.getClass().getName());
    }

    private static void runDirect(@Nonnull String operation,
        @Nonnull PhysicsOwnerMutation mutation) {
        try {
            mutation.run();
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Physics operation " + operation + " failed",
                exception);
        }
    }

    @Nonnull
    private static <T> PhysicsMutationHandle<T> runDirectAsync(@Nonnull String operation,
        @Nullable T value,
        @Nonnull PhysicsOwnerMutation mutation) {
        try {
            mutation.run();
            return PhysicsMutationHandle.completed(operation, value);
        } catch (Throwable throwable) {
            return PhysicsMutationHandle.failed(operation, value, throwable);
        }
    }

    @Nonnull
    private static <T> CompletableFuture<T> callDirectAsync(@Nonnull String operation,
        @Nonnull PhysicsOwnerCallable<T> callable) {
        try {
            return CompletableFuture.completedFuture(callable.call());
        } catch (Throwable throwable) {
            CompletableFuture<T> completion = new CompletableFuture<>();
            completion.completeExceptionally(throwable);
            return completion;
        }
    }
}
