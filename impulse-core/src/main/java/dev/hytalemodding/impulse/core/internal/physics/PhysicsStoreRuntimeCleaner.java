package dev.hytalemodding.impulse.core.internal.physics;

import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsEventResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsProfilingResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsRestoreStatusResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsRuntimeResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsSnapshotResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsSpaceCompatibilityIndexResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsStoreReadQueueResource;
import dev.hytalemodding.impulse.core.plugin.physics.PhysicsThreading;
import dev.hytalemodding.impulse.core.plugin.components.UuidComponent;
import javax.annotation.Nonnull;

/**
 * Internal full reset helper for authoritative PhysicsStore runtime state.
 */
public final class PhysicsStoreRuntimeCleaner {

    private PhysicsStoreRuntimeCleaner() {
    }

    public static void clearAll(@Nonnull Store<PhysicsStore> store) {
        PhysicsThreading.requireBackendIdle(store, "clear PhysicsStore runtime rows");
        store.forEachEntityParallel(UuidComponent.getComponentType(),
            (index, chunk, commandBuffer) -> commandBuffer.removeEntity(
                chunk.getReferenceTo(index),
                RemoveReason.REMOVE));
        store.getResource(PhysicsRuntimeResource.getResourceType()).destroyBackendBindings();
        store.getExternalData().clearUuidIndex();
        store.getResource(PhysicsSpaceCompatibilityIndexResource.getResourceType()).clear();
        store.getResource(PhysicsSnapshotResource.getResourceType()).clear();
        store.getResource(PhysicsEventResource.getResourceType()).clear();
        store.getResource(PhysicsProfilingResource.getResourceType()).reset();
        store.getResource(PhysicsStoreReadQueueResource.getResourceType()).clear();
        PhysicsStoreCleanupHooks.clearFullStoreRuntimeResources(store);
        store.getResource(PhysicsRestoreStatusResource.getResourceType())
            .markRecoveredFromCleanup();
    }
}
