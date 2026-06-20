package dev.hytalemodding.impulse.core.plugin.physics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.ComponentRegistry;
import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import com.hypixel.hytale.server.core.util.thread.TickingThread;
import com.hypixel.hytale.component.EmptyResourceStorage;
import dev.hytalemodding.impulse.api.BackendId;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.internal.registration.PhysicsComponentTypeRegistry;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsResourceTypes;
import dev.hytalemodding.impulse.core.internal.testsupport.TestInstanceFactory;
import dev.hytalemodding.impulse.core.plugin.components.ExtensionSettingsComponent;
import dev.hytalemodding.impulse.core.plugin.components.SolverSettingsComponent;
import dev.hytalemodding.impulse.core.plugin.components.SpaceComponent;
import dev.hytalemodding.impulse.core.plugin.modules.physicsentity.components.VisualMaterializationSettingsComponent;
import dev.hytalemodding.impulse.core.plugin.modules.physicsentity.components.VisualSyncSettingsComponent;
import dev.hytalemodding.impulse.core.plugin.modules.physicschunk.components.ChunkCollisionSettingsComponent;
import dev.hytalemodding.impulse.core.plugin.modules.physicschunk.components.CollisionLodSettingsComponent;
import dev.hytalemodding.impulse.core.plugin.modules.physicschunk.settings.PhysicsCollisionLodSettings;
import dev.hytalemodding.impulse.core.plugin.modules.physicsentity.settings.PhysicsVisualMaterializationSettings;
import dev.hytalemodding.impulse.core.plugin.modules.physicsentity.settings.PhysicsVisualSyncSettings;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsSolverSettings;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.UUID;
import javax.annotation.Nonnull;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class PhysicsSpacesSettingsComponentTest {

    @Test
    void defaultSpaceSettingsHolderDoesNotMaterializeDefaultComponents() {
        ComponentRegistry<PhysicsStore> registry = new ComponentRegistry<>();
        ComponentRegistryProxy<PhysicsStore> proxy =
            new ComponentRegistryProxy<>(new ArrayList<>(), registry);
        PhysicsComponentTypeRegistry.registerComponentTypes(proxy);
        PhysicsResourceTypes.registerResourceTypes(proxy);
        Store<PhysicsStore> store = registry.addStore(
            new PhysicsStore(TestInstanceFactory.world("physics-space-default-holder-test")),
            EmptyResourceStorage.get());
        try {
            Holder<PhysicsStore> holder = PhysicsEntities.spaceHolder(store,
                uuid(1),
                new SpaceComponent(new BackendId("test:settings-holder"),
                    new Vector3f(0.0f, -9.81f, 0.0f)),
                new ChunkCollisionSettingsComponent(),
                new SolverSettingsComponent(),
                new VisualSyncSettingsComponent(),
                new VisualMaterializationSettingsComponent(),
                new CollisionLodSettingsComponent(),
                new ExtensionSettingsComponent());
            Ref<PhysicsStore> ref = store.addEntity(holder, AddReason.SPAWN);
            assertNotNull(ref);

            assertNull(store.getComponent(ref, ChunkCollisionSettingsComponent.getComponentType()));
            assertNull(store.getComponent(ref, SolverSettingsComponent.getComponentType()));
            assertNull(store.getComponent(ref, VisualSyncSettingsComponent.getComponentType()));
            assertNull(store.getComponent(ref,
                VisualMaterializationSettingsComponent.getComponentType()));
            assertNull(store.getComponent(ref, CollisionLodSettingsComponent.getComponentType()));
            assertNull(store.getComponent(ref, ExtensionSettingsComponent.getComponentType()));
        } finally {
            registry.removeStore(store);
            registry.shutdown();
        }
    }

    @Test
    void domainSettingWritesAddAndRemoveOnlyTheirOwnComponents() {
        ComponentRegistry<PhysicsStore> registry = new ComponentRegistry<>();
        ComponentRegistryProxy<PhysicsStore> proxy =
            new ComponentRegistryProxy<>(new ArrayList<>(), registry);
        PhysicsComponentTypeRegistry.registerComponentTypes(proxy);
        PhysicsResourceTypes.registerResourceTypes(proxy);
        Store<PhysicsStore> store = registry.addStore(
            new PhysicsStore(TestInstanceFactory.world("physics-space-domain-settings-test")),
            EmptyResourceStorage.get());
        try {
            markCurrentThreadAsWorldThread(store);
            Ref<PhysicsStore> spaceRef = PhysicsSpaces.create(store,
                uuid(2),
                new SpaceId(2001),
                new BackendId("test:settings-domain"));

            PhysicsSolverSettings solverSettings = PhysicsSpaces.solverSettings(store, spaceRef);
            assertNotNull(solverSettings);
            assertEquals(PhysicsSolverSettings.DEFAULT_SOLVER_ITERATIONS,
                solverSettings.getSolverIterations());
            assertNull(store.getComponent(spaceRef, SolverSettingsComponent.getComponentType()));

            solverSettings.setSolverIterations(6);
            PhysicsSpaces.putSolverSettings(store, spaceRef, solverSettings);
            assertNotNull(store.getComponent(spaceRef,
                SolverSettingsComponent.getComponentType()));
            assertNull(store.getComponent(spaceRef,
                CollisionLodSettingsComponent.getComponentType()));

            PhysicsCollisionLodSettings collisionLodSettings =
                PhysicsSpaces.collisionLodSettings(store, spaceRef);
            assertNotNull(collisionLodSettings);
            collisionLodSettings.setCollisionLodEnabled(true);
            PhysicsSpaces.putCollisionLodSettings(store, spaceRef, collisionLodSettings);
            assertNotNull(store.getComponent(spaceRef,
                CollisionLodSettingsComponent.getComponentType()));
            assertNotNull(store.getComponent(spaceRef,
                SolverSettingsComponent.getComponentType()));

            PhysicsVisualSyncSettings visualSyncSettings =
                PhysicsSpaces.visualSyncSettings(store, spaceRef);
            assertNotNull(visualSyncSettings);
            assertNull(store.getComponent(spaceRef,
                VisualSyncSettingsComponent.getComponentType()));
            visualSyncSettings.setVisualMidSyncIntervalTicks(2);
            PhysicsSpaces.putVisualSyncSettings(store, spaceRef, visualSyncSettings);
            assertNotNull(store.getComponent(spaceRef,
                VisualSyncSettingsComponent.getComponentType()));
            assertNotNull(store.getComponent(spaceRef,
                CollisionLodSettingsComponent.getComponentType()));

            PhysicsVisualMaterializationSettings visualMaterializationSettings =
                PhysicsSpaces.visualMaterializationSettings(store, spaceRef);
            assertNotNull(visualMaterializationSettings);
            assertNull(store.getComponent(spaceRef,
                VisualMaterializationSettingsComponent.getComponentType()));
            visualMaterializationSettings.setDetachedVisualMaterializationEnabled(true);
            PhysicsSpaces.putVisualMaterializationSettings(store,
                spaceRef,
                visualMaterializationSettings);
            assertNotNull(store.getComponent(spaceRef,
                VisualMaterializationSettingsComponent.getComponentType()));
            assertNotNull(store.getComponent(spaceRef,
                VisualSyncSettingsComponent.getComponentType()));

            solverSettings.setSolverIterations(PhysicsSolverSettings.DEFAULT_SOLVER_ITERATIONS);
            PhysicsSpaces.putSolverSettings(store, spaceRef, solverSettings);
            assertNull(store.getComponent(spaceRef, SolverSettingsComponent.getComponentType()));
            assertNotNull(store.getComponent(spaceRef,
                CollisionLodSettingsComponent.getComponentType()));

            visualSyncSettings.setVisualMidSyncIntervalTicks(
                PhysicsVisualSyncSettings.DEFAULT_VISUAL_MID_SYNC_INTERVAL_TICKS);
            PhysicsSpaces.putVisualSyncSettings(store, spaceRef, visualSyncSettings);
            assertNull(store.getComponent(spaceRef,
                VisualSyncSettingsComponent.getComponentType()));
            assertNotNull(store.getComponent(spaceRef,
                VisualMaterializationSettingsComponent.getComponentType()));

            visualMaterializationSettings.setDetachedVisualMaterializationEnabled(
                PhysicsVisualMaterializationSettings
                    .DEFAULT_DETACHED_VISUAL_MATERIALIZATION_ENABLED);
            PhysicsSpaces.putVisualMaterializationSettings(store,
                spaceRef,
                visualMaterializationSettings);
            assertNull(store.getComponent(spaceRef,
                VisualMaterializationSettingsComponent.getComponentType()));
            assertNotNull(store.getComponent(spaceRef,
                CollisionLodSettingsComponent.getComponentType()));
        } finally {
            registry.removeStore(store);
            registry.shutdown();
        }
    }

    @Nonnull
    private static UUID uuid(int lowBits) {
        return new UUID(0L, lowBits);
    }

    private static void markCurrentThreadAsWorldThread(@Nonnull Store<PhysicsStore> store) {
        try {
            Method setThread = TickingThread.class.getDeclaredMethod("setThread", Thread.class);
            setThread.setAccessible(true);
            setThread.invoke(store.getExternalData().getWorld(), Thread.currentThread());
        } catch (NoSuchMethodException | IllegalAccessException exception) {
            throw new AssertionError("Could not mark test world thread", exception);
        } catch (InvocationTargetException exception) {
            throw new AssertionError("Could not mark test world thread",
                exception.getTargetException());
        }
    }
}
