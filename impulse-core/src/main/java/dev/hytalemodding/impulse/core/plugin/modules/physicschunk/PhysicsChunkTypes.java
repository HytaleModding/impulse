package dev.hytalemodding.impulse.core.plugin.modules.physicschunk;

import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.core.internal.modules.physicschunk.PhysicsStoreWorldCollisionStreamingResource;
import dev.hytalemodding.impulse.core.internal.modules.physicschunk.profiling.WorldCollisionProfilingResource;
import dev.hytalemodding.impulse.core.internal.modules.physicschunk.systems.PhysicsStoreWorldCollisionProducerSystem;
import javax.annotation.Nonnull;

/**
 * Registered EntityStore type handles owned by the PhysicsChunk integration module.
 */
public final class PhysicsChunkTypes {

    private PhysicsChunkTypes() {
    }

    public static void registerEntityStoreResourceTypes(
        @Nonnull ComponentRegistryProxy<EntityStore> registry) {
        WorldCollisionProfilingResource.setResourceType(registry.registerResource(
            WorldCollisionProfilingResource.class,
            WorldCollisionProfilingResource::new));
        PhysicsStoreWorldCollisionStreamingResource.setResourceType(registry.registerResource(
            PhysicsStoreWorldCollisionStreamingResource.class,
            PhysicsStoreWorldCollisionStreamingResource::new));
    }

    public static void registerEntityStoreSystems(
        @Nonnull ComponentRegistryProxy<EntityStore> registry) {
        registry.registerSystem(new PhysicsStoreWorldCollisionProducerSystem());
    }

    public static void clearEntityStoreResourceTypes() {
        WorldCollisionProfilingResource.clearResourceType();
        PhysicsStoreWorldCollisionStreamingResource.clearResourceType();
    }
}
