package dev.hytalemodding.impulse.core.internal.resources.owner;

import com.hypixel.hytale.component.Resource;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.core.ImpulsePlugin;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsMutationHandle;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Runtime ECS resource that owns serialized live-backend execution for one world.
 */
public interface PhysicsOwnerResource
    extends Resource<EntityStore>, AutoCloseable, PhysicsOwnerHandle {

    void start(@Nonnull String worldName);

    boolean isStarted();

    boolean isClosed();

    @Override
    void close();

    @Nonnull
    PhysicsOwnerResult submitAndDrain(@Nonnull PhysicsOwnerCommand command)
        throws InterruptedException, ExecutionException;

    boolean submitStepIfIdle(@Nonnull PhysicsOwnerCommand command);

    @Nonnull
    PhysicsMutationHandle<Void> submitMutation(@Nonnull String operation,
        @Nonnull PhysicsOwnerCommand command);

    @Nonnull
    <T> PhysicsMutationHandle<T> submitMutation(@Nonnull String operation,
        @Nullable T value,
        @Nonnull PhysicsOwnerCommand command);

    @Nonnull
    CompletableFuture<PhysicsOwnerResult> submitMutationFuture(@Nonnull String operation,
        @Nonnull PhysicsOwnerCommand command);

    @Nonnull
    List<PhysicsOwnerMutationCompletion> pollCompletedMutations(int maxCompletions);

    @Nullable
    PhysicsOwnerStepCompletion pollCompletedStep();

    boolean hasPendingStep();

    long pendingStepAgeNanos();

    int pendingMutations();

    int pendingCommands();

    @Nonnull
    @Override
    PhysicsOwnerResource clone();

    @Nonnull
    static ResourceType<EntityStore, PhysicsOwnerResource> getResourceType() {
        return ImpulsePlugin.get().getPhysicsOwnerResourceType();
    }
}
