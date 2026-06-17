package dev.hytalemodding.impulse.core.internal.modules.physicschunk;

import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.core.internal.modules.physicschunk.commands.PhysicsChunkCommandContributions;
import dev.hytalemodding.impulse.core.plugin.modules.physicschunk.PhysicsChunkTypes;
import dev.hytalemodding.impulse.core.plugin.modules.physicschunk.PhysicsWorldCollision;
import java.util.logging.Level;
import javax.annotation.Nonnull;

/**
 * Plugin module that enables Impulse ChunkStore world-collision integration.
 */
public final class PhysicsChunkModule extends JavaPlugin {

    private static final HytaleLogger LOGGER = HytaleLogger.get("Impulse");

    public PhysicsChunkModule(@Nonnull JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        ComponentRegistryProxy<EntityStore> entityRegistry = getEntityStoreRegistry();
        PhysicsChunkTypes.registerEntityStoreResourceTypes(entityRegistry);
        PhysicsChunkTypes.registerEntityStoreSystems(entityRegistry);
        PhysicsChunkCommandContributions.register();
        PhysicsWorldCollision.enableModule();
        LOGGER.at(Level.INFO).log("Impulse world-collision PhysicsStore terrain producer enabled.");
    }

    @Override
    protected void shutdown() {
        PhysicsWorldCollision.disableModule();
        PhysicsChunkCommandContributions.unregister();
        PhysicsChunkTypes.clearEntityStoreResourceTypes();
    }
}
