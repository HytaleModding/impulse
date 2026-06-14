package dev.hytalemodding.impulse.core.internal.physicsstore;

import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsIdentityIndexResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsEventResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsProfilingResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsRuntimeResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsSnapshotResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsSpaceCompatibilityIndexResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsStoreReadQueueResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsTerrainMutationQueueResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsTerrainPayloadResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsWorldCollisionIndexResource;
import dev.hytalemodding.impulse.core.plugin.physicsstore.PhysicsStoreThreading;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.UuidComponent;
import javax.annotation.Nonnull;

/**
 * Internal full reset helper for authoritative PhysicsStore runtime state.
 */
public final class PhysicsStoreRuntimeCleaner {

    private PhysicsStoreRuntimeCleaner() {
    }

    public static void clearAll(@Nonnull Store<PhysicsStore> store) {
        PhysicsStoreThreading.requireWorldThread(store, "clear PhysicsStore runtime rows");
        store.forEachEntityParallel(UuidComponent.getComponentType(),
            (index, chunk, commandBuffer) -> commandBuffer.removeEntity(
                chunk.getReferenceTo(index),
                RemoveReason.REMOVE));
        store.getResource(PhysicsTerrainMutationQueueResource.getResourceType()).clear();
        store.getResource(PhysicsRuntimeResource.getResourceType()).destroyBackendBindings();
        store.getResource(PhysicsIdentityIndexResource.getResourceType()).clear();
        store.getResource(PhysicsSpaceCompatibilityIndexResource.getResourceType()).clear();
        store.getResource(PhysicsSnapshotResource.getResourceType()).clear();
        store.getResource(PhysicsEventResource.getResourceType()).clear();
        store.getResource(PhysicsProfilingResource.getResourceType()).reset();
        store.getResource(PhysicsStoreReadQueueResource.getResourceType()).clear();
        store.getResource(PhysicsTerrainPayloadResource.getResourceType()).clear();
        store.getResource(PhysicsWorldCollisionIndexResource.getResourceType()).clear();
    }
}
