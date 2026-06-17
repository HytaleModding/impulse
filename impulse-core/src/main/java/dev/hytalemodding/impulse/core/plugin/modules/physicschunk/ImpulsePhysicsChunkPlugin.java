package dev.hytalemodding.impulse.core.plugin.modules.physicschunk;

import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.early.PhysicsStoreHooks;
import dev.hytalemodding.impulse.core.internal.modules.physicschunk.commands.WorldCollisionCommandContributions;
import dev.hytalemodding.impulse.core.internal.modules.physicschunk.WorldCollisionLifecycle;
import dev.hytalemodding.impulse.core.internal.modules.physicschunk.PhysicsStoreWorldCollisionStreamingResource;
import dev.hytalemodding.impulse.core.internal.modules.physicschunk.systems.PhysicsStoreWorldCollisionProducerSystem;
import dev.hytalemodding.impulse.core.internal.modules.physicschunk.profiling.WorldCollisionProfilingResource;
import dev.hytalemodding.impulse.core.internal.registration.PhysicsStoreRegistration;
import java.util.logging.Level;
import java.util.function.Consumer;
import javax.annotation.Nonnull;

/**
 * Subplugin that enables Impulse world collision.
 */
public final class ImpulsePhysicsChunkPlugin extends JavaPlugin {

    private static final HytaleLogger LOGGER = HytaleLogger.get("Impulse");
    private static final Consumer<PhysicsStore> SHUTDOWN_CLEANUP =
        PhysicsChunkTypes::clearRuntimeStateBeforeShutdown;

    public ImpulsePhysicsChunkPlugin(@Nonnull JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        ComponentRegistryProxy<PhysicsStore> physicsStoreRegistry =
            PhysicsStoreRegistration.physicsStoreRegistry(this);
        PhysicsChunkTypes.registerComponentTypes(physicsStoreRegistry);
        PhysicsChunkTypes.registerResourceTypes(physicsStoreRegistry);
        PhysicsChunkTypes.registerSystems(physicsStoreRegistry);
        PhysicsStoreHooks.registerShutdownHook(SHUTDOWN_CLEANUP);

        ComponentRegistryProxy<EntityStore> entityRegistry = getEntityStoreRegistry();
        WorldCollisionProfilingResource.setResourceType(entityRegistry.registerResource(
            WorldCollisionProfilingResource.class,
            WorldCollisionProfilingResource::new));
        PhysicsStoreWorldCollisionStreamingResource.setResourceType(entityRegistry.registerResource(
            PhysicsStoreWorldCollisionStreamingResource.class,
            PhysicsStoreWorldCollisionStreamingResource::new));
        entityRegistry.registerSystem(new PhysicsStoreWorldCollisionProducerSystem());
        WorldCollisionCommandContributions.register();
        WorldCollisionLifecycle.enable();
        LOGGER.at(Level.INFO).log("Impulse world-collision PhysicsStore terrain producer enabled.");
    }

    @Override
    protected void shutdown() {
        WorldCollisionLifecycle.disable();
        WorldCollisionCommandContributions.unregister();
        PhysicsStoreHooks.unregisterShutdownHook(SHUTDOWN_CLEANUP);
        WorldCollisionProfilingResource.clearResourceType();
        PhysicsStoreWorldCollisionStreamingResource.clearResourceType();
    }
}
