package dev.hytalemodding.impulse.builtin.control;

import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.builtin.control.internal.ControlLifecycle;
import dev.hytalemodding.impulse.builtin.control.internal.ControlTypeRegistry;
import dev.hytalemodding.impulse.builtin.control.internal.PhysicsControlRuntimeStates;
import dev.hytalemodding.impulse.builtin.control.internal.systems.PhysicsControllableLifecycleSystem;
import dev.hytalemodding.impulse.builtin.control.internal.systems.PhysicsControlRuntimeHolderSystem;
import dev.hytalemodding.impulse.builtin.control.internal.systems.PhysicsControlSessionCleanupSystem;
import dev.hytalemodding.impulse.builtin.control.internal.systems.PhysicsKinematicControlSystem;
import dev.hytalemodding.impulse.core.plugin.physics.PhysicsCleanupHooks;
import java.util.UUID;
import java.util.function.Consumer;
import javax.annotation.Nonnull;

/**
 * Builtin plugin that enables Impulse kinematic control sessions.
 */
public final class ImpulseControlPlugin extends JavaPlugin {

    @Nonnull
    private static final PhysicsCleanupHooks.EntityStoreCleanup ENTITY_STORE_CLEANUP =
        ControlLifecycle::cleanupStoreForExternalCleanup;
    @Nonnull
    private static final PhysicsCleanupHooks.SelectedEntityStoreCleanup SELECTED_ENTITY_STORE_CLEANUP =
        ControlLifecycle::cleanupSelectedStoreForExternalCleanup;
    @Nonnull
    private static final Consumer<Store<PhysicsStore>> BODY_RUNTIME_CLEANUP =
        PhysicsControlRuntimeStates::clear;
    @Nonnull
    private static final PhysicsCleanupHooks.BodyRowCleanup BODY_ROW_CLEANUP =
        ImpulseControlPlugin::clearControlledBody;

    public ImpulseControlPlugin(@Nonnull JavaPluginInit init) {
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
        registerCleanupHooks();
        ControlLifecycle.enable();
    }

    @Override
    protected void shutdown() {
        ControlLifecycle.disable();
        unregisterCleanupHooks();
        ControlTypeRegistry.clearComponentTypes();
    }

    private static void registerCleanupHooks() {
        PhysicsCleanupHooks.registerDetachableEntityMarker(
            ImpulseControllableComponent.getComponentType());
        PhysicsCleanupHooks.registerEntityStoreCleanup(ENTITY_STORE_CLEANUP);
        PhysicsCleanupHooks.registerSelectedEntityStoreCleanup(SELECTED_ENTITY_STORE_CLEANUP);
        PhysicsCleanupHooks.registerBodyRuntimeCleanup(BODY_RUNTIME_CLEANUP);
        PhysicsCleanupHooks.registerBodyRowCleanup(BODY_ROW_CLEANUP);
    }

    private static void unregisterCleanupHooks() {
        if (ImpulseControllableComponent.isComponentTypeRegistered()) {
            PhysicsCleanupHooks.unregisterDetachableEntityMarker(
                ImpulseControllableComponent.getComponentType());
        }
        PhysicsCleanupHooks.unregisterEntityStoreCleanup(ENTITY_STORE_CLEANUP);
        PhysicsCleanupHooks.unregisterSelectedEntityStoreCleanup(SELECTED_ENTITY_STORE_CLEANUP);
        PhysicsCleanupHooks.unregisterBodyRuntimeCleanup(BODY_RUNTIME_CLEANUP);
        PhysicsCleanupHooks.unregisterBodyRowCleanup(BODY_ROW_CLEANUP);
    }

    private static void clearControlledBody(@Nonnull Store<PhysicsStore> store,
        @Nonnull UUID bodyUuid,
        @Nonnull Ref<PhysicsStore> bodyRef) {
        PhysicsControlRuntimeStates.clearControlled(bodyRef);
    }
}
