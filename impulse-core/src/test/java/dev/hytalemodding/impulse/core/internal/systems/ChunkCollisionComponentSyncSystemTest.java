package dev.hytalemodding.impulse.core.internal.systems;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.ComponentRegistry;
import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.EmptyResourceStorage;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.api.BackendId;
import dev.hytalemodding.impulse.api.PhysicsAxis;
import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.ShapeType;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.api.runtime.BackendRuntimeCodes;
import dev.hytalemodding.impulse.api.testsupport.FakePhysicsBackendRuntimeProvider;
import dev.hytalemodding.impulse.api.testsupport.FakePhysicsBackendRuntimeProvider.FakePhysicsBackendRuntime;
import dev.hytalemodding.impulse.core.internal.modules.physicschunk.PhysicsChunkStoreTypes;
import dev.hytalemodding.impulse.core.internal.modules.physicschunk.components.ChunkCollisionSourceComponent;
import dev.hytalemodding.impulse.core.internal.modules.physicschunk.components.ChunkCollisionSourceComponent.PartKind;
import dev.hytalemodding.impulse.core.internal.registration.PhysicsComponentTypeRegistry;
import dev.hytalemodding.impulse.core.internal.resources.BackendBodyHandle;
import dev.hytalemodding.impulse.core.internal.resources.BackendSpaceHandle;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsIdentityIndexResource;
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
import javax.annotation.Nonnull;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class ChunkCollisionComponentSyncSystemTest {

    private static final float DELTA = 0.000001f;

    @Test
    void spaceSurfaceComponentsSyncGeneratedRowsAndBoundBackends() {
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
        proxy.registerSystem(new PhysicsChunkSettingsIndexSystem());
        proxy.registerSystem(new ChunkCollisionMutationDrainSystem());
        proxy.registerSystem(new ChunkCollisionComponentSyncSystem());
        Store<PhysicsStore> store = registry.addStore(
            new PhysicsStore(TestInstanceFactory.world("chunk-collision-component-sync-test")),
            EmptyResourceStorage.get());
        try {
            PhysicsRestoreStatusResource restore = store.getResource(
                PhysicsRestoreStatusResource.getResourceType());
            restore.markComplete();
            restore.markHydrated();
            UUID spaceUuid = uuid(1);
            RuntimeFixture runtime = addBoundSpace(store,
                spaceUuid,
                new BackendId("test:chunk-collision-component-sync"));
            MaterialComponent expectedMaterial = new MaterialComponent(0.84f, 0.16f);
            CollisionFilterComponent expectedFilter = new CollisionFilterComponent(0x40, 0x07);
            putSpaceSurface(store, runtime.spaceRef(), expectedMaterial, expectedFilter);

            GeneratedRow boundRow = addGeneratedBoxRow(store, runtime, spaceUuid, "0:0:0", true);
            GeneratedRow unboundRow = addGeneratedBoxRow(store, runtime, spaceUuid, "1:0:0", false);

            store.tick(0.0f);

            assertSurface(store, boundRow.ref(), expectedMaterial, expectedFilter);
            assertSurface(store, unboundRow.ref(), expectedMaterial, expectedFilter);
            assertEquals(expectedMaterial.getFriction(),
                runtime.backendRuntime().bodyFriction(runtime.spaceHandle().value(),
                    boundRow.bodyHandle().value()),
                DELTA);
            assertEquals(expectedMaterial.getRestitution(),
                runtime.backendRuntime().bodyRestitution(runtime.spaceHandle().value(),
                    boundRow.bodyHandle().value()),
                DELTA);
            assertEquals(expectedFilter.getCollisionGroup(),
                runtime.backendRuntime().bodyCollisionGroup(runtime.spaceHandle().value(),
                    boundRow.bodyHandle().value()));
            assertEquals(expectedFilter.getCollisionMask(),
                runtime.backendRuntime().bodyCollisionMask(runtime.spaceHandle().value(),
                    boundRow.bodyHandle().value()));
            BackendBodyHandle newlyBoundHandle = store
                .getResource(PhysicsRuntimeResource.getResourceType())
                .getBodyHandle(unboundRow.ref());
            assertNotNull(newlyBoundHandle);
            assertEquals(expectedMaterial.getFriction(),
                runtime.backendRuntime().bodyFriction(runtime.spaceHandle().value(),
                    newlyBoundHandle.value()),
                DELTA);
            assertEquals(expectedMaterial.getRestitution(),
                runtime.backendRuntime().bodyRestitution(runtime.spaceHandle().value(),
                    newlyBoundHandle.value()),
                DELTA);
            assertEquals(expectedFilter.getCollisionGroup(),
                runtime.backendRuntime().bodyCollisionGroup(runtime.spaceHandle().value(),
                    newlyBoundHandle.value()));
            assertEquals(expectedFilter.getCollisionMask(),
                runtime.backendRuntime().bodyCollisionMask(runtime.spaceHandle().value(),
                    newlyBoundHandle.value()));
            assertSoftSkipsEmpty(store);
        } finally {
            registry.removeStore(store);
            registry.shutdown();
        }
    }

    @Nonnull
    private static RuntimeFixture addBoundSpace(@Nonnull Store<PhysicsStore> store,
        @Nonnull UUID spaceUuid,
        @Nonnull BackendId backendId) {
        Ref<PhysicsStore> spaceRef = store.addEntity(PhysicsEntities.spaceHolder(store,
                spaceUuid,
                new SpaceComponent(backendId, new Vector3f(0.0f, -9.81f, 0.0f))),
            AddReason.SPAWN);
        assertNotNull(spaceRef);
        PhysicsIdentityIndexResource identity = store.getResource(
            PhysicsIdentityIndexResource.getResourceType());
        identity.putUuid(spaceUuid, spaceRef);
        store.getExternalData().putRefForUUID(spaceUuid, spaceRef);

        FakePhysicsBackendRuntime runtime = (FakePhysicsBackendRuntime)
            new FakePhysicsBackendRuntimeProvider(backendId, false, false).createRuntime();
        BackendSpaceHandle spaceHandle = new BackendSpaceHandle(runtime.createSpace(new SpaceId(42)));
        PhysicsRuntimeResource runtimeResource = store.getResource(
            PhysicsRuntimeResource.getResourceType());
        runtimeResource.putRuntime(backendId, runtime);
        runtimeResource.putSpaceBinding(spaceUuid, spaceRef, backendId, spaceHandle);
        identity.putSpaceHandle(spaceHandle, spaceRef);
        return new RuntimeFixture(spaceRef, spaceHandle, runtime);
    }

    private static void putSpaceSurface(@Nonnull Store<PhysicsStore> store,
        @Nonnull Ref<PhysicsStore> spaceRef,
        @Nonnull MaterialComponent material,
        @Nonnull CollisionFilterComponent filter) {
        store.putComponent(spaceRef, MaterialComponent.getComponentType(), material);
        store.putComponent(spaceRef, CollisionFilterComponent.getComponentType(), filter);
    }

    @Nonnull
    private static GeneratedRow addGeneratedBoxRow(@Nonnull Store<PhysicsStore> store,
        @Nonnull RuntimeFixture runtime,
        @Nonnull UUID spaceUuid,
        @Nonnull String sourceKey,
        boolean bindRuntime) {
        UUID bodyUuid = ChunkCollisionMutationDrainSystem.chunkCollisionBodyUuid(spaceUuid,
            sourceKey,
            PartKind.BOX,
            0);
        BodyComponent body = new BodyComponent(spaceUuid);
        body.setSpaceRef(runtime.spaceRef());
        Holder<PhysicsStore> holder = PhysicsEntities.bodyHolder(store,
            bodyUuid,
            body,
            new DynamicsComponent(PhysicsBodyType.STATIC, 0.0f, 0.0f, 0.0f, false),
            target(),
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
            new MaterialComponent(0.11f, 0.01f),
            new CollisionFilterComponent(0x01, 0x02));
        holder.addComponent(ChunkCollisionSourceComponent.getComponentType(),
            new ChunkCollisionSourceComponent(sourceKey, 0, 0, 0, "", PartKind.BOX, 0));
        Ref<PhysicsStore> bodyRef = store.addEntity(holder, AddReason.SPAWN);
        assertNotNull(bodyRef);

        PhysicsIdentityIndexResource identity = store.getResource(
            PhysicsIdentityIndexResource.getResourceType());
        identity.putUuid(bodyUuid, bodyRef);
        store.getExternalData().putRefForUUID(bodyUuid, bodyRef);

        if (!bindRuntime) {
            return new GeneratedRow(bodyRef, new BackendBodyHandle(Long.MIN_VALUE));
        }
        long bodyHandle = runtime.backendRuntime()
            .createBody(runtime.spaceHandle().value(),
                BackendRuntimeCodes.shapeTypeCode(ShapeType.BOX),
                0.5f,
                0.5f,
                0.5f,
                0.0f,
                0.0f,
                BackendRuntimeCodes.axisCode(PhysicsAxis.Y),
                0.0f,
                0.0f,
                BackendRuntimeCodes.bodyTypeCode(PhysicsBodyType.STATIC),
                0.0f,
                0.0f,
                0.0f,
                0.0f,
                0.0f,
                0.0f,
                1.0f);
        runtime.backendRuntime().setBodyFriction(runtime.spaceHandle().value(), bodyHandle, 0.11f);
        runtime.backendRuntime().setBodyRestitution(runtime.spaceHandle().value(), bodyHandle, 0.01f);
        runtime.backendRuntime().setBodyCollisionFilter(runtime.spaceHandle().value(),
            bodyHandle,
            0x01,
            0x02);
        BackendBodyHandle backendBodyHandle = new BackendBodyHandle(bodyHandle);
        store.getResource(PhysicsRuntimeResource.getResourceType())
            .putBodyHandle(bodyUuid,
                bodyRef,
                spaceUuid,
                runtime.spaceHandle(),
                backendBodyHandle);
        identity.putBodyHandle(backendBodyHandle, bodyRef);
        return new GeneratedRow(bodyRef, backendBodyHandle);
    }

    @Nonnull
    private static TargetComponent target() {
        TargetComponent target = new TargetComponent();
        target.setPosition(new Vector3f(1.0f, 2.0f, 3.0f));
        return target;
    }

    private static void assertSurface(@Nonnull Store<PhysicsStore> store,
        @Nonnull Ref<PhysicsStore> bodyRef,
        @Nonnull MaterialComponent expectedMaterial,
        @Nonnull CollisionFilterComponent expectedFilter) {
        MaterialComponent material = store.getComponent(bodyRef,
            MaterialComponent.getComponentType());
        assertNotNull(material);
        assertEquals(expectedMaterial.getFriction(), material.getFriction(), DELTA);
        assertEquals(expectedMaterial.getRestitution(), material.getRestitution(), DELTA);
        CollisionFilterComponent filter = store.getComponent(bodyRef,
            CollisionFilterComponent.getComponentType());
        assertNotNull(filter);
        assertEquals(expectedFilter.getCollisionGroup(), filter.getCollisionGroup());
        assertEquals(expectedFilter.getCollisionMask(), filter.getCollisionMask());
    }

    private static void assertSoftSkipsEmpty(@Nonnull Store<PhysicsStore> store) {
        assertEquals(0,
            store.getResource(PhysicsRestoreStatusResource.getResourceType())
                .getSoftSkipsByReason()
                .size());
    }

    @Nonnull
    private static UUID uuid(long leastSignificantBits) {
        return new UUID(0L, leastSignificantBits);
    }

    private record RuntimeFixture(@Nonnull Ref<PhysicsStore> spaceRef,
                                  @Nonnull BackendSpaceHandle spaceHandle,
                                  @Nonnull FakePhysicsBackendRuntime backendRuntime) {
    }

    private record GeneratedRow(@Nonnull Ref<PhysicsStore> ref,
                                @Nonnull BackendBodyHandle bodyHandle) {
    }
}
