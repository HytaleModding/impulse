package dev.hytalemodding.impulse.core.internal.modules.physicsentity;

import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.core.internal.modules.physicsentity.commands.PhysicsEntityCommandSet;
import javax.annotation.Nonnull;

/**
 * Bundled subplugin that integrates authoritative PhysicsStore bodies with EntityStore entities.
 */
public final class PhysicsEntitySubPlugin extends JavaPlugin {

    public PhysicsEntitySubPlugin(@Nonnull JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        ComponentRegistryProxy<EntityStore> entityRegistry = getEntityStoreRegistry();
        PhysicsEntityTypeRegistry.registerComponentTypes(entityRegistry);
        PhysicsEntityTypeRegistry.registerResourceTypes(entityRegistry);
        PhysicsEntityTypeRegistry.registerEventTypes(entityRegistry);
        PhysicsEntityTypeRegistry.registerSystemGroups(entityRegistry);
        PhysicsEntityTypeRegistry.registerSystems(entityRegistry);
        PhysicsEntityCommandSet.register();
        PhysicsEntityLifecycle.enable();
    }

    @Override
    protected void shutdown() {
        PhysicsEntityLifecycle.disable();
        PhysicsEntityCommandSet.unregister();
        PhysicsEntityTypeRegistry.clearEntityStoreTypes();
    }
}
