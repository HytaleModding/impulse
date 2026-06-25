package dev.hytalemodding.impulse.core.plugin.physics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.ComponentRegistry;
import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.EmptyResourceStorage;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import com.hypixel.hytale.server.core.util.thread.TickingThread;
import dev.hytalemodding.impulse.api.BackendId;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.internal.PhysicsComponentTypeRegistry;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsResourceTypes;
import dev.hytalemodding.impulse.core.internal.testsupport.TestInstanceFactory;
import dev.hytalemodding.impulse.core.plugin.components.ExtensionSettingsComponent;
import dev.hytalemodding.impulse.core.plugin.components.SolverSettingsComponent;
import dev.hytalemodding.impulse.core.plugin.components.SpaceComponent;
import dev.hytalemodding.impulse.core.plugin.modules.physicschunk.components.ChunkCollisionSettingsComponent;
import dev.hytalemodding.impulse.core.plugin.modules.physicschunk.components.CollisionLodSettingsComponent;
import dev.hytalemodding.impulse.core.plugin.modules.physicschunk.PhysicsChunkCollisionMode;
import dev.hytalemodding.impulse.core.plugin.modules.physicschunk.settings.PhysicsChunkCollisionSettings;
import dev.hytalemodding.impulse.core.plugin.modules.physicschunk.settings.PhysicsCollisionLodSettings;
import dev.hytalemodding.impulse.core.plugin.modules.physicsentity.components.VisualMaterializationSettingsComponent;
import dev.hytalemodding.impulse.core.plugin.modules.physicsentity.components.VisualSyncSettingsComponent;
import dev.hytalemodding.impulse.core.plugin.modules.physicsentity.settings.PhysicsVisualMaterializationSettings;
import dev.hytalemodding.impulse.core.plugin.modules.physicsentity.settings.PhysicsVisualSyncSettings;
import dev.hytalemodding.impulse.core.plugin.modules.physicsentity.settings.VisualOcclusionMode;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsBackendExtensionId;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsExtensionSettings;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsSolverSettings;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.UUID;
import javax.annotation.Nonnull;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class PhysicsSpacesSettingsComponentTest {

    private static final PhysicsBackendExtensionId RAPIER_SOLVER_EXTENSION_ID =
        new PhysicsBackendExtensionId("impulse:rapier_solver");
    private static final String RAPIER_INTERNAL_PGS_ITERATIONS = "internalPgsIterations";
    private static final String RAPIER_MIN_ISLAND_SIZE = "minIslandSize";

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

    @Test
    void explicitSpaceLifecycleCleansCompatibilityIndexAndSettings() {
        ComponentRegistry<PhysicsStore> registry = new ComponentRegistry<>();
        ComponentRegistryProxy<PhysicsStore> proxy =
            new ComponentRegistryProxy<>(new ArrayList<>(), registry);
        PhysicsComponentTypeRegistry.registerComponentTypes(proxy);
        PhysicsResourceTypes.registerResourceTypes(proxy);
        Store<PhysicsStore> store = registry.addStore(
            new PhysicsStore(TestInstanceFactory.world("physics-space-lifecycle-round-trip")),
            EmptyResourceStorage.get());
        try {
            markCurrentThreadAsWorldThread(store);
            int previousCount = PhysicsSpaces.count(store);
            SpaceId spaceId = new SpaceId(2002);

            PhysicsSpaces.create(store,
                uuid(3),
                spaceId,
                new BackendId("test:settings-lifecycle"));
            PhysicsSpaces.putChunkCollisionSettings(store,
                spaceId,
                populatedChunkCollisionSettings());

            PhysicsChunkCollisionSettings settings =
                PhysicsSpaces.chunkCollisionSettings(store, spaceId);
            assertNotNull(settings);
            assertEquals(PhysicsChunkCollisionMode.STREAMING, settings.getMode());
            assertTrue(PhysicsSpaces.hasSpace(store, spaceId));
            assertNotNull(PhysicsSpaces.resolveRef(store, spaceId));

            PhysicsSpaces.removeEmpty(store, spaceId);

            assertEquals(previousCount, PhysicsSpaces.count(store));
            assertFalse(PhysicsSpaces.hasSpace(store, spaceId));
            assertNull(PhysicsSpaces.resolveRef(store, spaceId));
        } finally {
            registry.removeStore(store);
            registry.shutdown();
        }
    }

    @Test
    void populatedSpaceSettingsRoundTripThroughPublicFacade() {
        ComponentRegistry<PhysicsStore> registry = new ComponentRegistry<>();
        ComponentRegistryProxy<PhysicsStore> proxy =
            new ComponentRegistryProxy<>(new ArrayList<>(), registry);
        PhysicsComponentTypeRegistry.registerComponentTypes(proxy);
        PhysicsResourceTypes.registerResourceTypes(proxy);
        Store<PhysicsStore> store = registry.addStore(
            new PhysicsStore(TestInstanceFactory.world("physics-space-settings-round-trip")),
            EmptyResourceStorage.get());
        try {
            markCurrentThreadAsWorldThread(store);
            SpaceId spaceId = new SpaceId(2003);
            Ref<PhysicsStore> spaceRef = PhysicsSpaces.create(store,
                uuid(4),
                spaceId,
                new BackendId("test:settings-round-trip"));

            PhysicsSpaces.putChunkCollisionSettings(store,
                spaceId,
                populatedChunkCollisionSettings());
            PhysicsSpaces.putVisualSyncSettings(store, spaceId, populatedVisualSyncSettings());
            PhysicsSpaces.putSolverSettings(store, spaceId, populatedSolverSettings());
            PhysicsSpaces.putExtensionSettings(store, spaceId, populatedExtensionSettings());
            PhysicsSpaces.putVisualMaterializationSettings(store,
                spaceId,
                populatedVisualMaterializationSettings());

            PhysicsChunkCollisionSettings chunkCollision =
                PhysicsSpaces.chunkCollisionSettings(store, spaceRef);
            PhysicsVisualSyncSettings visualSync = PhysicsSpaces.visualSyncSettings(store, spaceRef);
            PhysicsSolverSettings solver = PhysicsSpaces.solverSettings(store, spaceRef);
            PhysicsExtensionSettings extension = PhysicsSpaces.extensionSettings(store, spaceRef);
            PhysicsVisualMaterializationSettings visualMaterialization =
                PhysicsSpaces.visualMaterializationSettings(store, spaceRef);
            assertNotNull(chunkCollision);
            assertNotNull(visualSync);
            assertNotNull(solver);
            assertNotNull(extension);
            assertNotNull(visualMaterialization);
            assertEquals(PhysicsChunkCollisionMode.STREAMING, chunkCollision.getMode());
            assertEquals(9, chunkCollision.getRadius());
            assertEquals(5, chunkCollision.getBodyRadius());
            assertEquals(77, chunkCollision.getTtlTicks());
            assertEquals(48, visualSync.getVisualFullSyncRadius());
            assertEquals(96, visualSync.getVisualMaxSyncRadius());
            assertFalse(visualSync.isVisualFarSyncCutoffEnabled());
            assertEquals(3, visualSync.getVisualMidSyncIntervalTicks());
            assertEquals(17, visualSync.getVisualFarSyncIntervalTicks());
            assertEquals(VisualOcclusionMode.PRIORITY, visualSync.getVisualOcclusionMode());
            assertEquals(31, visualSync.getVisualOcclusionRaycastsPerTick());
            assertEquals(7, visualSync.getVisualOcclusionCacheTicks());
            assertTrue(visualSync.isEntityVisualSyncCullingEnabled());
            assertTrue(visualSync.isVisualVisibilityCullingEnabled());
            assertEquals(5, solver.getSolverIterations());
            assertEquals(1, solver.getStabilizationIterations());
            assertEquals(2, extension.getInt(RAPIER_SOLVER_EXTENSION_ID,
                RAPIER_INTERNAL_PGS_ITERATIONS).orElseThrow());
            assertEquals(64, extension.getInt(RAPIER_SOLVER_EXTENSION_ID,
                RAPIER_MIN_ISLAND_SIZE).orElseThrow());
            assertTrue(visualMaterialization.isDetachedVisualMaterializationEnabled());
            assertEquals(48, visualMaterialization.getDetachedVisualMaterializationRadius());
            assertEquals(72, visualMaterialization.getDetachedVisualDematerializationRadius());
            assertEquals(33, visualMaterialization.getDetachedVisualMaxSpawnsPerTick());
            assertEquals(444, visualMaterialization.getDetachedVisualMaxMaterialized());
            assertEquals("Rock_Stone", visualMaterialization.getDetachedVisualBlockType());
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

    @Nonnull
    private static PhysicsChunkCollisionSettings populatedChunkCollisionSettings() {
        PhysicsChunkCollisionSettings settings = new PhysicsChunkCollisionSettings();
        settings.setMode(PhysicsChunkCollisionMode.STREAMING);
        settings.setRadius(9);
        settings.setBodyRadius(5);
        settings.setTtlTicks(77);
        return settings;
    }

    @Nonnull
    private static PhysicsVisualSyncSettings populatedVisualSyncSettings() {
        PhysicsVisualSyncSettings settings = new PhysicsVisualSyncSettings();
        settings.setVisualMaxSyncRadius(96);
        settings.setVisualFullSyncRadius(48);
        settings.setVisualFarSyncCutoffEnabled(false);
        settings.setVisualMidSyncIntervalTicks(3);
        settings.setVisualFarSyncIntervalTicks(17);
        settings.setVisualOcclusionMode(VisualOcclusionMode.PRIORITY);
        settings.setVisualOcclusionRaycastsPerTick(31);
        settings.setVisualOcclusionCacheTicks(7);
        settings.setEntityVisualSyncCullingEnabled(true);
        settings.setVisualVisibilityCullingEnabled(true);
        return settings;
    }

    @Nonnull
    private static PhysicsSolverSettings populatedSolverSettings() {
        PhysicsSolverSettings settings = new PhysicsSolverSettings();
        settings.setSolverIterations(5);
        settings.setStabilizationIterations(1);
        return settings;
    }

    @Nonnull
    private static PhysicsExtensionSettings populatedExtensionSettings() {
        PhysicsExtensionSettings settings = new PhysicsExtensionSettings();
        settings.setInt(RAPIER_SOLVER_EXTENSION_ID, RAPIER_INTERNAL_PGS_ITERATIONS, 2);
        settings.setInt(RAPIER_SOLVER_EXTENSION_ID, RAPIER_MIN_ISLAND_SIZE, 64);
        return settings;
    }

    @Nonnull
    private static PhysicsVisualMaterializationSettings populatedVisualMaterializationSettings() {
        PhysicsVisualMaterializationSettings settings =
            new PhysicsVisualMaterializationSettings();
        settings.setDetachedVisualMaterializationEnabled(true);
        settings.setDetachedVisualDematerializationRadius(72);
        settings.setDetachedVisualMaterializationRadius(48);
        settings.setDetachedVisualMaxSpawnsPerTick(33);
        settings.setDetachedVisualMaxMaterialized(444);
        settings.setDetachedVisualBlockType("Rock_Stone");
        return settings;
    }
}
