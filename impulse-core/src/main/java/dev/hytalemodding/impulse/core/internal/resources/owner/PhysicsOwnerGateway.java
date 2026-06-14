package dev.hytalemodding.impulse.core.internal.resources.owner;

import dev.hytalemodding.impulse.core.plugin.resources.PhysicsMutationHandle;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Internal owner-lane gateway for one world physics resource.
 *
 * <p>This class centralizes owner-context routing while {@code PhysicsWorldResource} remains the
 * plugin-facing facade. Callbacks routed through this gateway may touch backend runtime state;
 * ordinary world-thread reads should use published snapshots instead.</p>
 */
public final class PhysicsOwnerGateway {

    private final AtomicReference<PhysicsOwnerExecutor> ownerExecutor = new AtomicReference<>();

    public void attachOwnerExecutor(@Nonnull PhysicsOwnerExecutor ownerExecutor) {
        this.ownerExecutor.set(Objects.requireNonNull(ownerExecutor, "ownerExecutor"));
    }

    public void detachOwnerExecutor(@Nonnull PhysicsOwnerExecutor ownerExecutor) {
        this.ownerExecutor.compareAndSet(Objects.requireNonNull(ownerExecutor, "ownerExecutor"), null);
    }

    /**
     * Returns whether the current thread may touch live backend objects without routing.
     */
    public boolean canAccessLiveBackendDirectly() {
        PhysicsOwnerExecutor executor = ownerExecutor.get();
        return executor == null || executor.isOwnerContext();
    }

    public boolean hasOwnerExecutor() {
        return ownerExecutor.get() != null;
    }

    public void assertCanAccessLiveBackendDirectly(@Nonnull String operation) {
        Objects.requireNonNull(operation, "operation");
        if (!canAccessLiveBackendDirectly()) {
            throw new IllegalStateException("Impulse live backend operation " + operation
                + " must run in the physics owner lane. Use PhysicsStore row mutation, "
                + "queued reads, or an internal owner-routed resource method.");
        }
    }

    public void rejectSynchronousCompletionCallbackWait(@Nonnull String operation) {
        Objects.requireNonNull(operation, "operation");
        PhysicsOwnerExecutor executor = ownerExecutor.get();
        if (executor != null && executor.isCompletionCallbackContext()) {
            throw new RejectedExecutionException(
                "cannot synchronously wait for physics owner operation " + operation
                    + " from a completion callback");
        }
    }

    public void run(@Nonnull String operation,
        @Nonnull PhysicsOwnerMutation mutation) {
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(mutation, "mutation");
        PhysicsOwnerExecutor executor = ownerExecutor.get();
        if (executor == null || executor.isOwnerContext()) {
            runDirect(operation, mutation);
            return;
        }
        executor.run(operation, mutation);
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
        PhysicsOwnerExecutor executor = ownerExecutor.get();
        if (executor == null || executor.isOwnerContext()) {
            return runDirectAsync(operation, value, mutation);
        }
        try {
            return executor.enqueue(operation, value, mutation);
        } catch (RejectedExecutionException exception) {
            return PhysicsMutationHandle.failed(operation, value, exception);
        }
    }

    @Nonnull
    public <T> CompletableFuture<T> enqueueCall(@Nonnull String operation,
        @Nonnull PhysicsOwnerCallable<T> callable) {
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(callable, "callable");
        PhysicsOwnerExecutor executor = ownerExecutor.get();
        if (executor == null || executor.isOwnerContext()) {
            return callDirectAsync(callable);
        }
        try {
            return executor.enqueueCall(operation, callable);
        } catch (RejectedExecutionException exception) {
            CompletableFuture<T> completion = new CompletableFuture<>();
            completion.completeExceptionally(exception);
            return completion;
        }
    }

    @Nonnull
    public <T> T call(@Nonnull String operation,
        @Nonnull PhysicsOwnerCallable<T> callable) {
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(callable, "callable");
        PhysicsOwnerExecutor executor = ownerExecutor.get();
        if (executor == null || executor.isOwnerContext()) {
            try {
                return callable.call();
            } catch (RuntimeException exception) {
                throw exception;
            } catch (Exception exception) {
                throw new IllegalStateException("Physics operation " + operation + " failed",
                    exception);
            }
        }
        return executor.call(operation, callable);
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
    private static <T> CompletableFuture<T> callDirectAsync(@Nonnull PhysicsOwnerCallable<T> callable) {
        try {
            return CompletableFuture.completedFuture(callable.call());
        } catch (Throwable throwable) {
            CompletableFuture<T> completion = new CompletableFuture<>();
            completion.completeExceptionally(throwable);
            return completion;
        }
    }
}
