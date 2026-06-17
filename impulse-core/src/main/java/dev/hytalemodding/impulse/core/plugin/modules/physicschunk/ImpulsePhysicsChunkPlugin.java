package dev.hytalemodding.impulse.core.plugin.modules.physicschunk;

import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.core.internal.modules.physicschunk.WorldCollisionLifecycle;
import dev.hytalemodding.impulse.core.internal.modules.physicschunk.PhysicsStoreWorldCollisionStreamingResource;
import dev.hytalemodding.impulse.core.internal.modules.physicschunk.systems.PhysicsStoreWorldCollisionProducerSystem;
import dev.hytalemodding.impulse.core.internal.modules.physicschunk.profiling.WorldCollisionProfilingResource;
import java.util.logging.Level;
import javax.annotation.Nonnull;

/**
 * Subplugin that enables Impulse world collision.
 */
public final class ImpulsePhysicsChunkPlugin extends JavaPlugin {

    private static final HytaleLogger LOGGER = HytaleLogger.get("Impulse");

    public ImpulsePhysicsChunkPlugin(@Nonnull JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        ComponentRegistryProxy<EntityStore> entityRegistry = getEntityStoreRegistry();
        WorldCollisionProfilingResource.setResourceType(entityRegistry.registerResource(
            WorldCollisionProfilingResource.class,
            WorldCollisionProfilingResource::new));
        PhysicsStoreWorldCollisionStreamingResource.setResourceType(entityRegistry.registerResource(
            PhysicsStoreWorldCollisionStreamingResource.class,
            PhysicsStoreWorldCollisionStreamingResource::new));
        entityRegistry.registerSystem(new PhysicsStoreWorldCollisionProducerSystem());
        WorldCollisionLifecycle.enable();
        LOGGER.at(Level.INFO).log("Impulse world-collision PhysicsStore terrain producer enabled.");
    }

    @Override
    protected void shutdown() {
        WorldCollisionLifecycle.disable();
        WorldCollisionProfilingResource.clearResourceType();
        PhysicsStoreWorldCollisionStreamingResource.clearResourceType();
    }
}
