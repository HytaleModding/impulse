package dev.hytalemodding.impulse.core.plugin.modules.control;

import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.core.internal.modules.control.ControlLifecycle;
import dev.hytalemodding.impulse.core.internal.modules.control.components.PhysicsControlSessionComponent;
import dev.hytalemodding.impulse.core.internal.modules.control.systems.PhysicsControllableLifecycleSystem;
import dev.hytalemodding.impulse.core.internal.modules.control.systems.PhysicsControlRuntimeHolderSystem;
import dev.hytalemodding.impulse.core.internal.modules.control.systems.PhysicsControlSessionCleanupSystem;
import dev.hytalemodding.impulse.core.internal.modules.control.systems.PhysicsKinematicControlSystem;
import javax.annotation.Nonnull;

/**
 * Subplugin that enables Impulse kinematic control sessions.
 */
public final class ImpulseControlPlugin extends JavaPlugin {

    public ImpulseControlPlugin(@Nonnull JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        ComponentRegistryProxy<EntityStore> entityRegistry = getEntityStoreRegistry();
        ImpulseControllableComponent.setComponentType(entityRegistry.registerComponent(
            ImpulseControllableComponent.class,
            "ImpulseControllable",
            ImpulseControllableComponent.CODEC));
        PhysicsControlSessionComponent.setComponentType(entityRegistry.registerComponent(
            PhysicsControlSessionComponent.class, PhysicsControlSessionComponent::new));
        entityRegistry.registerSystem(new PhysicsControlRuntimeHolderSystem());
        entityRegistry.registerSystem(new PhysicsControllableLifecycleSystem());
        entityRegistry.registerSystem(new PhysicsControlSessionCleanupSystem());
        entityRegistry.registerSystem(new PhysicsKinematicControlSystem());
        ControlLifecycle.enable();
    }

    @Override
    protected void shutdown() {
        ControlLifecycle.disable();
        ImpulseControllableComponent.clearComponentType();
        PhysicsControlSessionComponent.clearComponentType();
    }
}
