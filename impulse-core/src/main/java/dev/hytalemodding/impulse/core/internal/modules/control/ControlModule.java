package dev.hytalemodding.impulse.core.internal.modules.control;

import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.core.internal.modules.control.systems.PhysicsControllableLifecycleSystem;
import dev.hytalemodding.impulse.core.internal.modules.control.systems.PhysicsControlRuntimeHolderSystem;
import dev.hytalemodding.impulse.core.internal.modules.control.systems.PhysicsControlSessionCleanupSystem;
import dev.hytalemodding.impulse.core.internal.modules.control.systems.PhysicsKinematicControlSystem;
import javax.annotation.Nonnull;

/**
 * Subplugin that enables Impulse kinematic control sessions.
 */
public final class ControlModule extends JavaPlugin {

    public ControlModule(@Nonnull JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        ComponentRegistryProxy<EntityStore> entityRegistry = getEntityStoreRegistry();
        ControlTypeRegistry.registerComponentTypes(entityRegistry);
        entityRegistry.registerSystem(new PhysicsControlRuntimeHolderSystem());
        entityRegistry.registerSystem(new PhysicsControllableLifecycleSystem());
        entityRegistry.registerSystem(new PhysicsControlSessionCleanupSystem());
        entityRegistry.registerSystem(new PhysicsKinematicControlSystem());
        ControlLifecycle.enable();
    }

    @Override
    protected void shutdown() {
        ControlLifecycle.disable();
        ControlTypeRegistry.clearComponentTypes();
    }
}
