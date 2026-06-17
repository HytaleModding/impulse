package dev.hytalemodding.impulse.core.internal.modules.physicschunk;

import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.core.internal.modules.physicschunk.PhysicsChunkTerrainStreamingResource;
import dev.hytalemodding.impulse.core.internal.modules.physicschunk.profiling.PhysicsChunkProfilingResource;
import dev.hytalemodding.impulse.core.internal.modules.physicschunk.systems.PhysicsChunkTerrainProducerSystem;
import javax.annotation.Nonnull;

/**
 * Registered EntityStore type handles owned by the PhysicsChunk integration module.
 */
final class PhysicsChunkTypes {

    private PhysicsChunkTypes() {
    }

    public static void registerEntityStoreResourceTypes(
        @Nonnull ComponentRegistryProxy<EntityStore> registry) {
        PhysicsChunkProfilingResource.setResourceType(registry.registerResource(
            PhysicsChunkProfilingResource.class,
            PhysicsChunkProfilingResource::new));
        PhysicsChunkTerrainStreamingResource.setResourceType(registry.registerResource(
            PhysicsChunkTerrainStreamingResource.class,
            PhysicsChunkTerrainStreamingResource::new));
    }

    public static void registerEntityStoreSystems(
        @Nonnull ComponentRegistryProxy<EntityStore> registry) {
        registry.registerSystem(new PhysicsChunkTerrainProducerSystem());
    }

    public static void clearEntityStoreResourceTypes() {
        PhysicsChunkProfilingResource.clearResourceType();
        PhysicsChunkTerrainStreamingResource.clearResourceType();
    }
}
