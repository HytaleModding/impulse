package dev.hytalemodding.impulse.core.internal.systems;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.ComponentRegistry;
import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.EmptyResourceStorage;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.api.*;
import dev.hytalemodding.impulse.api.testsupport.FakePhysicsBackendRuntimeProvider;
import dev.hytalemodding.impulse.api.testsupport.FakePhysicsBackendRuntimeProvider.FakePhysicsBackendRuntime;
import dev.hytalemodding.impulse.core.internal.modules.physicschunk.PhysicsChunkLifecycle;
import dev.hytalemodding.impulse.core.internal.modules.physicschunk.PhysicsChunkStoreTypes;
import dev.hytalemodding.impulse.core.internal.modules.physicschunk.components.ChunkCollisionRestoreDependencyComponent;
import dev.hytalemodding.impulse.core.internal.modules.physicschunk.components.ChunkCollisionSourceComponent;
import dev.hytalemodding.impulse.core.internal.modules.physicschunk.components.ChunkCollisionSourceComponent.PartKind;
import dev.hytalemodding.impulse.core.internal.modules.physicschunk.systems.ChunkCollisionMutationDrainSystem;
import dev.hytalemodding.impulse.core.internal.PhysicsComponentTypeRegistry;
import dev.hytalemodding.impulse.core.internal.resources.BackendBodyHandle;
import dev.hytalemodding.impulse.core.internal.resources.BackendSpaceHandle;
import dev.hytalemodding.impulse.core.internal.modules.physicschunk.resources.PhysicsChunkSettingsIndexResource;
import dev.hytalemodding.impulse.core.internal.modules.physicschunk.resources.PhysicsChunkSettingsIndexResource.PhysicsChunkSpaceSettings;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsResourceTypes;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsRestoreStatusResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsRuntimeResource;
import dev.hytalemodding.impulse.core.internal.systems.binding.BodyBindingSystem;
import dev.hytalemodding.impulse.core.internal.systems.binding.SpaceBindingSystem;
import dev.hytalemodding.impulse.core.internal.testsupport.TestInstanceFactory;
import dev.hytalemodding.impulse.core.plugin.components.BodyComponent;
import dev.hytalemodding.impulse.core.plugin.components.ColliderComponent;
import dev.hytalemodding.impulse.core.plugin.components.CollisionFilterComponent;
import dev.hytalemodding.impulse.core.plugin.components.DynamicsComponent;
import dev.hytalemodding.impulse.core.plugin.components.MaterialComponent;
import dev.hytalemodding.impulse.core.plugin.components.ShapeComponent;
import dev.hytalemodding.impulse.core.plugin.components.SpaceComponent;
import dev.hytalemodding.impulse.core.plugin.components.TargetComponent;
import dev.hytalemodding.impulse.core.plugin.modules.physicschunk.PhysicsChunkCollisionMode;
import dev.hytalemodding.impulse.core.plugin.modules.physicschunk.settings.EntityChunkBoundaryMode;
import dev.hytalemodding.impulse.core.plugin.physics.PhysicsEntities;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class BodyBindingSystemTest {

    private static final BackendId BACKEND_ID = new BackendId("test:body-binding-no-physicschunk");

    @Test
    void nonVoxelBodyBindingDoesNotRequirePhysicsChunkPayloadResource() {
        PhysicsChunkStoreTypes.clearPhysicsStoreResourceTypes();
        FakePhysicsBackendRuntimeProvider provider =
            new FakePhysicsBackendRuntimeProvider(BACKEND_ID, false, false);
        ImpulseBackendRegistry.registerRuntimeProvider(provider);
        ComponentRegistry<PhysicsStore> registry = new ComponentRegistry<>();
        ComponentRegistryProxy<PhysicsStore> proxy =
            new ComponentRegistryProxy<>(new ArrayList<>(), registry);
        PhysicsComponentTypeRegistry.registerComponentTypes(proxy);
        PhysicsResourceTypes.registerResourceTypes(proxy);
        proxy.registerSystem(new PersistenceHydrationSystem());
        proxy.registerSystem(new IdentityIndexSystem());
        proxy.registerSystem(new SpaceBindingSystem());
        proxy.registerSystem(new SpaceSettingsApplicationSystem());
        proxy.registerSystem(new BodyBindingSystem());
        Store<PhysicsStore> store = registry.addStore(
            new PhysicsStore(TestInstanceFactory.world("body-binding-no-physicschunk-test")),
            EmptyResourceStorage.get());
        try {
            PhysicsRestoreStatusResource restore = store.getResource(
                PhysicsRestoreStatusResource.getResourceType());
            restore.markComplete();
            restore.markHydrated();
            Ref<PhysicsStore> spaceRef = addSpace(store, uuid(1));
            Ref<PhysicsStore> bodyRef = addBody(store, uuid(2), uuid(1), spaceRef);

            assertDoesNotThrow(() -> store.tick(0.0f));

            assertFalse(restore.isFailed(), restore.getFailureMessage());
            assertEquals(0, restore.getSoftSkipsByReason().size());
            PhysicsRuntimeResource runtime = store.getResource(PhysicsRuntimeResource.getResourceType());
            BackendSpaceHandle spaceHandle = runtime.getSpaceHandle(spaceRef);
            BackendBodyHandle bodyHandle = runtime.getBodyHandle(bodyRef);
            assertNotNull(spaceHandle);
            assertNotNull(bodyHandle);
            FakePhysicsBackendRuntime backendRuntime = provider.createdRuntimes().get(0);
            assertEquals(1, backendRuntime.bodyCount(spaceHandle.value()));
        } finally {
            if (!store.isShutdown()) {
                registry.removeStore(store);
            }
            registry.shutdown();
            PhysicsChunkStoreTypes.clearPhysicsStoreResourceTypes();
        }
    }

    @Test
    void nonVoxelBodyBindingSeedsInitialPropertiesWithoutSeparateMutationCalls() {
        PhysicsChunkStoreTypes.clearPhysicsStoreResourceTypes();
        FakePhysicsBackendRuntimeProvider provider =
            new FakePhysicsBackendRuntimeProvider(BACKEND_ID, true, false);
        ImpulseBackendRegistry.registerRuntimeProvider(provider);
        ComponentRegistry<PhysicsStore> registry = new ComponentRegistry<>();
        ComponentRegistryProxy<PhysicsStore> proxy =
            new ComponentRegistryProxy<>(new ArrayList<>(), registry);
        PhysicsComponentTypeRegistry.registerComponentTypes(proxy);
        PhysicsResourceTypes.registerResourceTypes(proxy);
        proxy.registerSystem(new PersistenceHydrationSystem());
        proxy.registerSystem(new IdentityIndexSystem());
        proxy.registerSystem(new SpaceBindingSystem());
        proxy.registerSystem(new SpaceSettingsApplicationSystem());
        proxy.registerSystem(new BodyBindingSystem());
        Store<PhysicsStore> store = registry.addStore(
            new PhysicsStore(TestInstanceFactory.world("body-binding-configured-create-test")),
            EmptyResourceStorage.get());
        try {
            PhysicsRestoreStatusResource restore = store.getResource(
                PhysicsRestoreStatusResource.getResourceType());
            restore.markComplete();
            restore.markHydrated();
            UUID spaceUuid = uuid(1);
            Ref<PhysicsStore> spaceRef = addSpace(store, spaceUuid);
            Ref<PhysicsStore> bodyRef = addConfiguredBody(store, uuid(2), spaceUuid, spaceRef);

            store.tick(0.0f);

            assertFalse(restore.isFailed(), restore.getFailureMessage());
            PhysicsRuntimeResource runtime = store.getResource(PhysicsRuntimeResource.getResourceType());
            BackendSpaceHandle spaceHandle = runtime.getSpaceHandle(spaceRef);
            BackendBodyHandle bodyHandle = runtime.getBodyHandle(bodyRef);
            assertNotNull(spaceHandle);
            assertNotNull(bodyHandle);
            FakePhysicsBackendRuntime backendRuntime = provider.createdRuntimes().get(0);
            assertEquals(1, backendRuntime.createBodyWithInitialStateCalls());
            assertEquals(0, backendRuntime.setBodyDampingCalls());
            assertEquals(0, backendRuntime.setBodyFrictionCalls());
            assertEquals(0, backendRuntime.setBodyRestitutionCalls());
            assertEquals(0, backendRuntime.setBodyCollisionFilterCalls());
            assertEquals(0, backendRuntime.setBodySensorCalls());
            assertEquals(0, backendRuntime.setBodyContinuousCollisionCalls());
            assertEquals(0.2f,
                backendRuntime.bodyLinearDamping(spaceHandle.value(), bodyHandle.value()));
            assertEquals(0.3f,
                backendRuntime.bodyAngularDamping(spaceHandle.value(), bodyHandle.value()));
            assertEquals(0.72f, backendRuntime.bodyFriction(spaceHandle.value(), bodyHandle.value()));
            assertEquals(0.18f,
                backendRuntime.bodyRestitution(spaceHandle.value(), bodyHandle.value()));
            assertEquals(PhysicsCollisionFilters.TERRAIN,
                backendRuntime.bodyCollisionGroup(spaceHandle.value(), bodyHandle.value()));
            assertEquals(PhysicsCollisionFilters.DYNAMIC_BODY,
                backendRuntime.bodyCollisionMask(spaceHandle.value(), bodyHandle.value()));
            assertTrue(backendRuntime.bodySensor(spaceHandle.value(), bodyHandle.value()));
            assertTrue(backendRuntime.isBodyContinuousCollisionEnabled(spaceHandle.value(),
                bodyHandle.value()));
        } finally {
            if (!store.isShutdown()) {
                registry.removeStore(store);
            }
            registry.shutdown();
            PhysicsChunkStoreTypes.clearPhysicsStoreResourceTypes();
        }
    }

    @Test
    void chunkRestoreDependencyWaitsWithoutBlockingUnrelatedBodies() {
        PhysicsChunkLifecycle.enable();
        FakePhysicsBackendRuntimeProvider provider =
            new FakePhysicsBackendRuntimeProvider(BACKEND_ID, false, false);
        ImpulseBackendRegistry.registerRuntimeProvider(provider);
        ComponentRegistry<PhysicsStore> registry = new ComponentRegistry<>();
        ComponentRegistryProxy<PhysicsStore> proxy =
            new ComponentRegistryProxy<>(new ArrayList<>(), registry);
        PhysicsComponentTypeRegistry.registerComponentTypes(proxy);
        PhysicsResourceTypes.registerResourceTypes(proxy);
        PhysicsChunkStoreTypes.registerPhysicsStoreResourceTypes(proxy);
        proxy.registerSystem(new PersistenceHydrationSystem());
        proxy.registerSystem(new IdentityIndexSystem());
        proxy.registerSystem(new SpaceBindingSystem());
        proxy.registerSystem(new SpaceSettingsApplicationSystem());
        proxy.registerSystem(new BodyBindingSystem());
        Store<PhysicsStore> store = registry.addStore(
            new PhysicsStore(TestInstanceFactory.world("body-binding-restore-context-test")),
            EmptyResourceStorage.get());
        try {
            UUID spaceUuid = uuid(1);
            PhysicsRestoreStatusResource restore = store.getResource(
                PhysicsRestoreStatusResource.getResourceType());
            restore.markComplete();
            restore.markHydrated();
            Ref<PhysicsStore> spaceRef = addSpace(store, spaceUuid);
            store.getResource(PhysicsChunkSettingsIndexResource.getResourceType())
                .replaceAll(Map.of(spaceUuid,
                    new PhysicsChunkSpaceSettings(spaceUuid,
                        PhysicsChunkCollisionMode.STREAMING,
                        EntityChunkBoundaryMode.PAUSE_UNTIL_LOADED,
                        false,
                        8,
                        4,
                        100)));
            Ref<PhysicsStore> waitingBodyRef = addBody(store, uuid(2), spaceUuid, spaceRef);
            store.putComponent(waitingBodyRef,
                ChunkCollisionRestoreDependencyComponent.getComponentType(),
                new ChunkCollisionRestoreDependencyComponent(spaceUuid,
                    new Vector3f(1.0f, 2.0f, 3.0f),
                    4,
                    PhysicsChunkCollisionMode.STREAMING));
            Ref<PhysicsStore> ordinaryBodyRef = addBody(store, uuid(3), spaceUuid, spaceRef);

            store.tick(0.0f);

            assertFalse(restore.isFailed(), restore.getFailureMessage());
            PhysicsRuntimeResource runtime = store.getResource(PhysicsRuntimeResource.getResourceType());
            assertNotNull(runtime.getBodyHandle(ordinaryBodyRef));
            assertNull(runtime.getBodyHandle(waitingBodyRef));
            assertEquals(1,
                restore.getSoftSkipsByReason()
                    .getInt("Body restore dependency pending chunk collision"));
            BackendSpaceHandle spaceHandle = runtime.getSpaceHandle(spaceRef);
            assertNotNull(spaceHandle);
            assertEquals(1, provider.createdRuntimes().get(0).bodyCount(spaceHandle.value()));
        } finally {
            if (!store.isShutdown()) {
                registry.removeStore(store);
            }
            registry.shutdown();
            PhysicsChunkStoreTypes.clearPhysicsStoreResourceTypes();
            PhysicsChunkLifecycle.disable();
        }
    }

    @Test
    void chunkRestoreDependencyBindsWhenGeneratedSupportRowExists() {
        PhysicsChunkLifecycle.enable();
        FakePhysicsBackendRuntimeProvider provider =
            new FakePhysicsBackendRuntimeProvider(BACKEND_ID, false, false);
        ImpulseBackendRegistry.registerRuntimeProvider(provider);
        ComponentRegistry<PhysicsStore> registry = new ComponentRegistry<>();
        ComponentRegistryProxy<PhysicsStore> proxy =
            new ComponentRegistryProxy<>(new ArrayList<>(), registry);
        PhysicsComponentTypeRegistry.registerComponentTypes(proxy);
        PhysicsResourceTypes.registerResourceTypes(proxy);
        PhysicsChunkStoreTypes.registerPhysicsStoreResourceTypes(proxy);
        proxy.registerSystem(new PersistenceHydrationSystem());
        proxy.registerSystem(new IdentityIndexSystem());
        proxy.registerSystem(new SpaceBindingSystem());
        proxy.registerSystem(new SpaceSettingsApplicationSystem());
        proxy.registerSystem(new BodyBindingSystem());
        Store<PhysicsStore> store = registry.addStore(
            new PhysicsStore(TestInstanceFactory.world("body-binding-restore-support-test")),
            EmptyResourceStorage.get());
        try {
            UUID spaceUuid = uuid(1);
            PhysicsRestoreStatusResource restore = store.getResource(
                PhysicsRestoreStatusResource.getResourceType());
            restore.markComplete();
            restore.markHydrated();
            Ref<PhysicsStore> spaceRef = addSpace(store, spaceUuid);
            store.getResource(PhysicsChunkSettingsIndexResource.getResourceType())
                .replaceAll(Map.of(spaceUuid,
                    new PhysicsChunkSpaceSettings(spaceUuid,
                        PhysicsChunkCollisionMode.STREAMING,
                        EntityChunkBoundaryMode.PAUSE_UNTIL_LOADED,
                        false,
                        8,
                        4,
                        100)));
            Ref<PhysicsStore> waitingBodyRef = addBody(store, uuid(2), spaceUuid, spaceRef);
            store.putComponent(waitingBodyRef,
                ChunkCollisionRestoreDependencyComponent.getComponentType(),
                new ChunkCollisionRestoreDependencyComponent(spaceUuid,
                    new Vector3f(1.0f, 2.0f, 3.0f),
                    4,
                    PhysicsChunkCollisionMode.STREAMING));
            Ref<PhysicsStore> generatedBodyRef = addGeneratedChunkBody(store,
                spaceUuid,
                spaceRef);

            store.tick(0.0f);

            assertFalse(restore.isFailed(), restore.getFailureMessage());
            assertEquals(0, restore.getSoftSkipsByReason().size());
            PhysicsRuntimeResource runtime = store.getResource(PhysicsRuntimeResource.getResourceType());
            assertNotNull(runtime.getBodyHandle(waitingBodyRef));
            assertNotNull(runtime.getBodyHandle(generatedBodyRef));
            BackendSpaceHandle spaceHandle = runtime.getSpaceHandle(spaceRef);
            assertNotNull(spaceHandle);
            assertEquals(2, provider.createdRuntimes().get(0).bodyCount(spaceHandle.value()));
        } finally {
            if (!store.isShutdown()) {
                registry.removeStore(store);
            }
            registry.shutdown();
            PhysicsChunkStoreTypes.clearPhysicsStoreResourceTypes();
            PhysicsChunkLifecycle.disable();
        }
    }

    private static Ref<PhysicsStore> addSpace(Store<PhysicsStore> store, UUID spaceUuid) {
        Ref<PhysicsStore> spaceRef = store.addEntity(PhysicsEntities.spaceHolder(store,
                spaceUuid,
                new SpaceComponent(BACKEND_ID, new Vector3f(0.0f, -9.81f, 0.0f))),
            AddReason.SPAWN);
        assertNotNull(spaceRef);
        return spaceRef;
    }

    private static Ref<PhysicsStore> addBody(Store<PhysicsStore> store,
        UUID bodyUuid,
        UUID spaceUuid,
        Ref<PhysicsStore> spaceRef) {
        BodyComponent body = new BodyComponent(spaceUuid);
        body.setSpaceRef(spaceRef);
        TargetComponent target = new TargetComponent();
        target.setActive(true);
        Ref<PhysicsStore> bodyRef = store.addEntity(PhysicsEntities.bodyHolder(store,
                bodyUuid,
                body,
                new DynamicsComponent(PhysicsBodyType.DYNAMIC, 1.0f, 0.0f, 0.0f, false),
                target,
                new ColliderComponent(new Vector3f(), new Quaternionf(), false),
                new ShapeComponent(ShapeType.BOX,
                    0.5f,
                    0.5f,
                    0.5f,
                    0.0f,
                    0.0f,
                    PhysicsAxis.Y,
                    0.0f,
                    ""),
                new MaterialComponent(0.6f, 0.1f),
                new CollisionFilterComponent(PhysicsCollisionFilters.DYNAMIC_BODY,
                    PhysicsCollisionFilters.ALL)),
            AddReason.SPAWN);
        assertNotNull(bodyRef);
        return bodyRef;
    }

    private static Ref<PhysicsStore> addConfiguredBody(Store<PhysicsStore> store,
        UUID bodyUuid,
        UUID spaceUuid,
        Ref<PhysicsStore> spaceRef) {
        BodyComponent body = new BodyComponent(spaceUuid);
        body.setSpaceRef(spaceRef);
        TargetComponent target = new TargetComponent();
        target.setActive(true);
        Ref<PhysicsStore> bodyRef = store.addEntity(PhysicsEntities.bodyHolder(store,
                bodyUuid,
                body,
                new DynamicsComponent(PhysicsBodyType.DYNAMIC, 2.0f, 0.2f, 0.3f, true),
                target,
                new ColliderComponent(new Vector3f(), new Quaternionf(), true),
                new ShapeComponent(ShapeType.BOX,
                    0.5f,
                    0.5f,
                    0.5f,
                    0.0f,
                    0.0f,
                    PhysicsAxis.Y,
                    0.0f,
                    ""),
                new MaterialComponent(0.72f, 0.18f),
                new CollisionFilterComponent(PhysicsCollisionFilters.TERRAIN,
                    PhysicsCollisionFilters.DYNAMIC_BODY)),
            AddReason.SPAWN);
        assertNotNull(bodyRef);
        return bodyRef;
    }

    private static Ref<PhysicsStore> addGeneratedChunkBody(Store<PhysicsStore> store,
        UUID spaceUuid,
        Ref<PhysicsStore> spaceRef) {
        String sourceKey = "chunk:0:0:0";
        UUID bodyUuid = ChunkCollisionMutationDrainSystem.chunkCollisionBodyUuid(spaceUuid,
            sourceKey,
            PartKind.BOX,
            0);
        BodyComponent body = new BodyComponent(spaceUuid);
        body.setSpaceRef(spaceRef);
        TargetComponent target = new TargetComponent();
        target.setActive(true);
        Ref<PhysicsStore> bodyRef = store.addEntity(PhysicsEntities.bodyHolder(store,
                bodyUuid,
                body,
                new DynamicsComponent(PhysicsBodyType.STATIC, 0.0f, 0.0f, 0.0f, false),
                target,
                new ColliderComponent(new Vector3f(), new Quaternionf(), false),
                new ShapeComponent(ShapeType.BOX,
                    8.0f,
                    8.0f,
                    8.0f,
                    0.0f,
                    0.0f,
                    PhysicsAxis.Y,
                    0.0f,
                    ""),
                new MaterialComponent(0.6f, 0.1f),
                new CollisionFilterComponent(PhysicsCollisionFilters.TERRAIN,
                    PhysicsCollisionFilters.ALL)),
            AddReason.SPAWN);
        assertNotNull(bodyRef);
        store.putComponent(bodyRef,
            ChunkCollisionSourceComponent.getComponentType(),
            new ChunkCollisionSourceComponent(sourceKey,
                0,
                0,
                0,
                "",
                PartKind.BOX,
                0));
        return bodyRef;
    }

    private static UUID uuid(long leastSignificantBits) {
        return new UUID(0L, leastSignificantBits);
    }
}
