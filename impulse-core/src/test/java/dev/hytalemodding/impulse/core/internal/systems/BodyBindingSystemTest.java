package dev.hytalemodding.impulse.core.internal.systems;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.ComponentRegistry;
import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.EmptyResourceStorage;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.api.BackendId;
import dev.hytalemodding.impulse.api.Impulse;
import dev.hytalemodding.impulse.api.PhysicsAxis;
import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.PhysicsCollisionFilters;
import dev.hytalemodding.impulse.api.ShapeType;
import dev.hytalemodding.impulse.api.testsupport.FakePhysicsBackendRuntimeProvider;
import dev.hytalemodding.impulse.api.testsupport.FakePhysicsBackendRuntimeProvider.FakePhysicsBackendRuntime;
import dev.hytalemodding.impulse.core.internal.modules.physicschunk.PhysicsChunkStoreTypes;
import dev.hytalemodding.impulse.core.internal.registration.PhysicsComponentTypeRegistry;
import dev.hytalemodding.impulse.core.internal.resources.BackendBodyHandle;
import dev.hytalemodding.impulse.core.internal.resources.BackendSpaceHandle;
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
import dev.hytalemodding.impulse.core.plugin.physics.PhysicsEntities;
import java.util.ArrayList;
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
        Impulse.registerRuntimeProvider(provider);
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

    private static UUID uuid(long leastSignificantBits) {
        return new UUID(0L, leastSignificantBits);
    }
}
