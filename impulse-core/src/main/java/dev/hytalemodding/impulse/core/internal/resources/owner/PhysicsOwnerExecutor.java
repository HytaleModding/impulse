package dev.hytalemodding.impulse.core.internal.resources.owner;

import dev.hytalemodding.impulse.core.plugin.resources.PhysicsMutationHandle;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Internal serialized owner lane for one world physics resource.
 *
 * <p>Callbacks routed through this executor may touch backend runtime state. Implementations define
 * owner-context membership with their own lane token; callers must not assume a stable Java thread
 * owns a world.</p>
 */
public interface PhysicsOwnerExecutor {

    /**
     * Returns whether the current thread is already executing this exact owner lane.
     */
    boolean isOwnerContext();

    void run(@Nonnull String operation,
        @Nonnull PhysicsOwnerMutation mutation);

    @Nonnull
    <T> PhysicsMutationHandle<T> enqueue(@Nonnull String operation,
        @Nullable T value,
        @Nonnull PhysicsOwnerMutation mutation);

    @Nonnull
    <T> CompletableFuture<T> enqueueCall(@Nonnull String operation,
        @Nonnull PhysicsOwnerCallable<T> callable);

    @Nonnull
    <T> T call(@Nonnull String operation,
        @Nonnull PhysicsOwnerCallable<T> callable);
}
