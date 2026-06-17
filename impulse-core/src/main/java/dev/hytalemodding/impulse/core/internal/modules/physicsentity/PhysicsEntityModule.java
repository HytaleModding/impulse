package dev.hytalemodding.impulse.core.internal.modules.physicsentity;

import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.core.plugin.modules.physicsentity.PhysicsEntityTypes;
import javax.annotation.Nonnull;

/**
 * Plugin module that integrates authoritative PhysicsStore bodies with EntityStore entities.
 */
public final class PhysicsEntityModule extends JavaPlugin {

    public PhysicsEntityModule(@Nonnull JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        ComponentRegistryProxy<EntityStore> entityRegistry = getEntityStoreRegistry();
        PhysicsEntityTypes.registerComponentTypes(entityRegistry);
        PhysicsEntityTypes.registerResourceTypes(entityRegistry);
        PhysicsEntityTypes.registerEventTypes(entityRegistry);
        PhysicsEntityTypes.registerSystemGroups(entityRegistry);
        PhysicsEntityTypes.registerSystems(entityRegistry);
        PhysicsEntityLifecycle.enable();
    }

    @Override
    protected void shutdown() {
        PhysicsEntityLifecycle.disable();
        PhysicsEntityTypes.clearEntityStoreTypes();
    }
}
