package dev.hytalemodding.impulse.core.internal.modules.physicschunk;

import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsChunkCollisionMutationQueueResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsChunkCollisionPayloadResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsChunkComponentSyncResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsChunkSettingsIndexResource;
import dev.hytalemodding.impulse.core.internal.systems.ChunkCollisionComponentSyncSystem;
import dev.hytalemodding.impulse.core.internal.systems.ChunkCollisionMutationDrainSystem;
import dev.hytalemodding.impulse.core.internal.systems.ChunkCollisionVoxelStitchingSystem;
import dev.hytalemodding.impulse.core.internal.systems.PhysicsChunkSettingsIndexSystem;
import javax.annotation.Nonnull;

/**
 * PhysicsStore-side type registration owned by the PhysicsChunk module.
 */
public final class PhysicsChunkStoreTypes {

    private PhysicsChunkStoreTypes() {
    }

    public static void registerPhysicsStoreResourceTypes(
        @Nonnull ComponentRegistryProxy<PhysicsStore> registry) {
        PhysicsChunkCollisionMutationQueueResource.setResourceType(registry.registerResource(
            PhysicsChunkCollisionMutationQueueResource.class,
            PhysicsChunkCollisionMutationQueueResource::new));
        PhysicsChunkCollisionPayloadResource.setResourceType(registry.registerResource(
            PhysicsChunkCollisionPayloadResource.class,
            PhysicsChunkCollisionPayloadResource::new));
        PhysicsChunkSettingsIndexResource.setResourceType(registry.registerResource(
            PhysicsChunkSettingsIndexResource.class,
            PhysicsChunkSettingsIndexResource::new));
        PhysicsChunkComponentSyncResource.setResourceType(registry.registerResource(
            PhysicsChunkComponentSyncResource.class,
            PhysicsChunkComponentSyncResource::new));
    }

    public static void registerPhysicsStoreSystems(
        @Nonnull ComponentRegistryProxy<PhysicsStore> registry) {
        registry.registerSystem(new PhysicsChunkSettingsIndexSystem());
        registry.registerSystem(new ChunkCollisionMutationDrainSystem());
        registry.registerSystem(new ChunkCollisionComponentSyncSystem());
        registry.registerSystem(new ChunkCollisionVoxelStitchingSystem());
    }

    public static void clearPhysicsStoreRuntimeResources(@Nonnull Store<PhysicsStore> store) {
        store.getResource(PhysicsChunkCollisionMutationQueueResource.getResourceType()).clear();
        store.getResource(PhysicsChunkCollisionPayloadResource.getResourceType()).clear();
        store.getResource(PhysicsChunkSettingsIndexResource.getResourceType()).clear();
        store.getResource(PhysicsChunkComponentSyncResource.getResourceType()).clear();
    }
}
