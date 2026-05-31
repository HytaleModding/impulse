package dev.hytalemodding.impulse.core.internal.systems.publication;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.core.internal.resources.profiling.PhysicsRuntimeProfilingResource;
import dev.hytalemodding.impulse.core.internal.resources.owner.PhysicsOwnerResource;
import dev.hytalemodding.impulse.core.internal.systems.collision.PhysicsChunkBoundarySystem;
import dev.hytalemodding.impulse.core.internal.systems.collision.PhysicsCollisionLodSystem;
import dev.hytalemodding.impulse.core.internal.systems.collision.PhysicsWorldCollisionStreamingSystem;
import dev.hytalemodding.impulse.core.internal.systems.sync.PhysicsSyncSystem;
import dev.hytalemodding.impulse.core.internal.systems.visual.PhysicsDetachedVisualMaterializationSystem;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsWorldResource;
import java.util.Set;
import javax.annotation.Nonnull;

/**
 * Entity-tick publication stage for completed physics owner-lane output.
 *
 * <p>The command-buffer visibility chain is: recorded, queued, owner completed, snapshot captured,
 * reader-side applied, then ECS systems consume the reader view. This system owns the reader-side
 * apply step and intentionally never waits for an in-flight owner-lane step.</p>
 */
public final class PhysicsSnapshotPublicationSystem extends TickingSystem<EntityStore> {

    private static final Set<Dependency<EntityStore>> DEPENDENCIES = Set.of(
        new SystemDependency<>(Order.BEFORE, PhysicsCollisionLodSystem.class),
        new SystemDependency<>(Order.BEFORE, PhysicsChunkBoundarySystem.class),
        new SystemDependency<>(Order.BEFORE, PhysicsWorldCollisionStreamingSystem.class),
        new SystemDependency<>(Order.BEFORE, PhysicsDetachedVisualMaterializationSystem.class),
        new SystemDependency<>(Order.BEFORE, PhysicsSyncSystem.class)
    );

    @Override
    public void tick(float dt, int systemIndex, @Nonnull Store<EntityStore> store) {
        PhysicsOwnerResource owner = store.getResource(
            PhysicsOwnerResource.getResourceType());
        if (owner.isClosed()) {
            return;
        }
        PhysicsWorldResource resource = store.getResource(PhysicsWorldResource.getResourceType());
        PhysicsRuntimeProfilingResource profiling = store.getResource(
            PhysicsRuntimeProfilingResource.getResourceType());
        // Mutation futures are drained for failures/completion only; snapshot inclusion is still
        // determined by the next captured and applied PublishedPhysicsSnapshotFrame.
        PhysicsPublicationPipeline.publishCompletedMutations(owner);
        long publicationServerTick = Math.max(0L, store.getExternalData().getWorld().getTick());
        PhysicsPublicationPipeline.publishCompletedStep(owner, resource, profiling, publicationServerTick);
    }

    static int publishCompletedMutations(@Nonnull PhysicsOwnerResource owner) {
        return PhysicsPublicationPipeline.publishCompletedMutations(owner);
    }

    static void publishCompletedStep(@Nonnull PhysicsOwnerResource owner,
        @Nonnull PhysicsWorldResource resource,
        @Nonnull PhysicsRuntimeProfilingResource profiling) {
        PhysicsPublicationPipeline.publishCompletedStep(owner, resource, profiling);
    }

    static void publishCompletedStep(@Nonnull PhysicsOwnerResource owner,
        @Nonnull PhysicsWorldResource resource,
        @Nonnull PhysicsRuntimeProfilingResource profiling,
        long publicationServerTick) {
        PhysicsPublicationPipeline.publishCompletedStep(owner, resource, profiling, publicationServerTick);
    }

    @Nonnull
    @Override
    public Set<Dependency<EntityStore>> getDependencies() {
        return DEPENDENCIES;
    }
}
