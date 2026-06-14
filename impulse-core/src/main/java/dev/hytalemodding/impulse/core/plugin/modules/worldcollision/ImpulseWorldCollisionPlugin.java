package dev.hytalemodding.impulse.core.plugin.modules.worldcollision;

import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.core.internal.modules.worldcollision.WorldCollisionLifecycle;
import dev.hytalemodding.impulse.core.internal.modules.worldcollision.PhysicsStoreWorldCollisionStreamingResource;
import dev.hytalemodding.impulse.core.internal.modules.worldcollision.systems.PhysicsStoreWorldCollisionProducerSystem;
import dev.hytalemodding.impulse.core.internal.modules.worldcollision.profiling.WorldCollisionProfilingResource;
import java.util.logging.Level;
import javax.annotation.Nonnull;

/**
 * Subplugin that enables Impulse world collision.
 */
public final class ImpulseWorldCollisionPlugin extends JavaPlugin {

    private static final HytaleLogger LOGGER = HytaleLogger.get("Impulse");

    public ImpulseWorldCollisionPlugin(@Nonnull JavaPluginInit init) {
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
        LOGGER.at(Level.INFO).log("Impulse world-collision PhysicsStore request producer enabled.");
    }

    @Override
    protected void shutdown() {
        WorldCollisionLifecycle.disable();
        WorldCollisionProfilingResource.clearResourceType();
        PhysicsStoreWorldCollisionStreamingResource.clearResourceType();
    }
}
