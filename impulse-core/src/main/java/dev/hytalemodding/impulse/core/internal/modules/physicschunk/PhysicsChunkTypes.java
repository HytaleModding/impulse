package dev.hytalemodding.impulse.core.internal.modules.physicschunk;

import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.core.internal.modules.physicschunk.PhysicsChunkCollisionStreamingResource;
import dev.hytalemodding.impulse.core.internal.modules.physicschunk.profiling.PhysicsChunkProfilingResource;
import dev.hytalemodding.impulse.core.internal.modules.physicschunk.systems.PhysicsChunkCollisionProducerSystem;
import javax.annotation.Nonnull;

/**
 * Registered EntityStore type handles owned by the PhysicsChunk subplugin.
 */
final class PhysicsChunkTypes {

    private PhysicsChunkTypes() {
    }

    public static void registerEntityStoreResourceTypes(
        @Nonnull ComponentRegistryProxy<EntityStore> registry) {
        PhysicsChunkProfilingResource.setResourceType(registry.registerResource(
            PhysicsChunkProfilingResource.class,
            PhysicsChunkProfilingResource::new));
        PhysicsChunkCollisionStreamingResource.setResourceType(registry.registerResource(
            PhysicsChunkCollisionStreamingResource.class,
            PhysicsChunkCollisionStreamingResource::new));
    }

    public static void registerEntityStoreSystems(
        @Nonnull ComponentRegistryProxy<EntityStore> registry) {
        registry.registerSystem(new PhysicsChunkCollisionProducerSystem());
    }

    public static void clearEntityStoreResourceTypes() {
        PhysicsChunkProfilingResource.clearResourceType();
        PhysicsChunkCollisionStreamingResource.clearResourceType();
    }
}
