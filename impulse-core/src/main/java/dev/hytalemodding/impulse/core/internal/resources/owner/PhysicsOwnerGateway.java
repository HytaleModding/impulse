package dev.hytalemodding.impulse.core.internal.resources.owner;

import dev.hytalemodding.impulse.core.plugin.resources.PhysicsMutationHandle;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Direct compatibility gateway for legacy world-resource operations.
 *
 * <p>Authoritative PhysicsStore paths do not use this gateway. Remaining legacy
 * {@code PhysicsWorldResource} methods run directly when the early PhysicsStore is not active.</p>
 */
public final class PhysicsOwnerGateway {

    /**
     * Returns whether the current thread may touch live backend objects without routing.
     */
    public boolean canAccessLiveBackendDirectly() {
        return true;
    }

    public void assertCanAccessLiveBackendDirectly(@Nonnull String operation) {
        Objects.requireNonNull(operation, "operation");
    }

    public void rejectSynchronousCompletionCallbackWait(@Nonnull String operation) {
        Objects.requireNonNull(operation, "operation");
    }

    public void run(@Nonnull String operation,
        @Nonnull PhysicsOwnerMutation mutation) {
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(mutation, "mutation");
        runDirect(operation, mutation);
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
        return runDirectAsync(operation, value, mutation);
    }

    @Nonnull
    public <T> CompletableFuture<T> enqueueCall(@Nonnull String operation,
        @Nonnull PhysicsOwnerCallable<T> callable) {
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(callable, "callable");
        return callDirectAsync(callable);
    }

    @Nonnull
    public <T> T call(@Nonnull String operation,
        @Nonnull PhysicsOwnerCallable<T> callable) {
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(callable, "callable");
        try {
            return callable.call();
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Physics operation " + operation + " failed",
                exception);
        }
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
