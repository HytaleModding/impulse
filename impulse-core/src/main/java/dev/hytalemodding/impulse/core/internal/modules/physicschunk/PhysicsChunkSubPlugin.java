package dev.hytalemodding.impulse.core.internal.modules.physicschunk;

import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.core.internal.modules.physicschunk.commands.PhysicsChunkCommandContributions;
import java.util.logging.Level;
import javax.annotation.Nonnull;

/**
 * Bundled subplugin that enables Impulse PhysicsChunk collision integration.
 */
public final class PhysicsChunkSubPlugin extends JavaPlugin {

    private static final HytaleLogger LOGGER = HytaleLogger.get("Impulse");

    public PhysicsChunkSubPlugin(@Nonnull JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        ComponentRegistryProxy<EntityStore> entityRegistry = getEntityStoreRegistry();
        PhysicsChunkTypes.registerEntityStoreResourceTypes(entityRegistry);
        PhysicsChunkTypes.registerEntityStoreSystems(entityRegistry);
        PhysicsChunkCommandContributions.register();
        PhysicsChunkLifecycle.enable();
        LOGGER.at(Level.INFO).log("Impulse PhysicsChunk collision producer enabled.");
    }

    @Override
    protected void shutdown() {
        PhysicsChunkLifecycle.disable();
        PhysicsChunkCommandContributions.unregister();
        PhysicsChunkTypes.clearEntityStoreResourceTypes();
    }
}
