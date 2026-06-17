package dev.hytalemodding.impulse.physicschunk;

import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.core.internal.modules.physicschunk.WorldCollisionLifecycle;
import dev.hytalemodding.impulse.physicschunk.commands.WorldCollisionCommandContributions;
import dev.hytalemodding.impulse.core.internal.registration.PhysicsStoreRegistration;
import dev.hytalemodding.impulse.core.plugin.modules.physicschunk.PhysicsChunkTypes;
import dev.hytalemodding.impulse.early.PhysicsStoreHooks;
import java.util.function.Consumer;
import java.util.logging.Level;
import javax.annotation.Nonnull;

/**
 * Plugin module that enables Impulse ChunkStore world-collision integration.
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
        PhysicsChunkTypes.registerEntityStoreResourceTypes(entityRegistry);
        PhysicsChunkTypes.registerEntityStoreSystems(entityRegistry);
        WorldCollisionCommandContributions.register();
        WorldCollisionLifecycle.enable();
        LOGGER.at(Level.INFO).log("Impulse world-collision PhysicsStore terrain producer enabled.");
    }

    @Override
    protected void shutdown() {
        WorldCollisionLifecycle.disable();
        WorldCollisionCommandContributions.unregister();
        PhysicsStoreHooks.unregisterShutdownHook(SHUTDOWN_CLEANUP);
        PhysicsChunkTypes.clearEntityStoreResourceTypes();
    }
}
