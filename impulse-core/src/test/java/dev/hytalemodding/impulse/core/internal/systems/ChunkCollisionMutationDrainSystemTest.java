package dev.hytalemodding.impulse.core.internal.systems;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.ComponentRegistry;
import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.EmptyResourceStorage;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import com.hypixel.hytale.server.core.util.thread.TickingThread;
import dev.hytalemodding.impulse.api.BackendId;
import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.ShapeType;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.api.runtime.PhysicsBackendRuntime;
import dev.hytalemodding.impulse.api.testsupport.FakePhysicsBackendRuntimeProvider;
import dev.hytalemodding.impulse.core.internal.modules.physicschunk.ChunkCollisionMutation;
import dev.hytalemodding.impulse.core.internal.modules.physicschunk.ChunkCollisionPayload;
import dev.hytalemodding.impulse.core.internal.modules.physicschunk.ChunkCollisionPayload.BoxPayload;
import dev.hytalemodding.impulse.core.internal.physicsstore.PhysicsStoreTopologyMutations;
import dev.hytalemodding.impulse.core.internal.registration.PhysicsComponentTypeRegistry;
import dev.hytalemodding.impulse.core.internal.resources.BackendSpaceHandle;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsChunkCollisionMutationQueueResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsChunkCollisionPayloadResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsIdentityIndexResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsResourceTypes;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsRestoreStatusResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsRuntimeResource;
import dev.hytalemodding.impulse.core.internal.testsupport.TestInstanceFactory;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyKind;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyPersistenceMode;
import dev.hytalemodding.impulse.core.plugin.components.BodyComponent;
import dev.hytalemodding.impulse.core.plugin.components.CollisionFilterComponent;
import dev.hytalemodding.impulse.core.plugin.components.DynamicsComponent;
import dev.hytalemodding.impulse.core.plugin.components.MaterialComponent;
import dev.hytalemodding.impulse.core.plugin.components.ShapeComponent;
import dev.hytalemodding.impulse.core.plugin.components.SpaceComponent;
import dev.hytalemodding.impulse.core.plugin.components.TargetComponent;
import dev.hytalemodding.impulse.core.plugin.components.UuidComponent;
import dev.hytalemodding.impulse.core.plugin.modules.physicschunk.components.ChunkCollisionSourceComponent;
import dev.hytalemodding.impulse.core.plugin.modules.physicschunk.components.ChunkCollisionSourceComponent.PartKind;
import dev.hytalemodding.impulse.core.plugin.physicsstore.PhysicsEntities;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class ChunkCollisionMutationDrainSystemTest {

    private static final float DELTA = 0.000001f;

    @Test
    void upsertCreatesGeneratedRowsWithReusableMaterialAndFilterComponents() {
        ComponentRegistry<PhysicsStore> registry = new ComponentRegistry<>();
        ComponentRegistryProxy<PhysicsStore> proxy =
            new ComponentRegistryProxy<>(new ArrayList<>(), registry);
        PhysicsComponentTypeRegistry.registerComponentTypes(proxy);
        PhysicsResourceTypes.registerResourceTypes(proxy);
        Store<PhysicsStore> store = registry.addStore(
            new PhysicsStore(TestInstanceFactory.world("chunk-collision-drain-row-test")),
            EmptyResourceStorage.get());
        try {
            UUID spaceUuid = uuid(1);
            BackendId backendId = new BackendId("test:chunk-collision-drain");
            Ref<PhysicsStore> spaceRef = addBoundSpace(store, spaceUuid, backendId);
            String sourceKey = "0:1:2";
            String payloadKey = "chunk-collision/0/1/2";
            BoxPayload fullCubeBox = new BoxPayload(10.0, 20.0, 30.0, 1.5, 2.5, 3.5);
            BoxPayload detailBox = new BoxPayload(40.0, 50.0, 60.0, 0.25, 0.5, 0.75);
            ChunkCollisionPayload payload = new ChunkCollisionPayload(1.0f,
                1.0f,
                1.0f,
                new int[0],
                List.of(fullCubeBox),
                List.of(detailBox),
                false,
                0.82f,
                0.18f,
                0x40,
                0x07,
                List.of());

            PhysicsChunkCollisionMutationQueueResource queue = store.getResource(
                PhysicsChunkCollisionMutationQueueResource.getResourceType());
            queue.enqueue(ChunkCollisionMutation.upsert(spaceUuid,
                sourceKey,
                0,
                1,
                2,
                payloadKey,
                payload));

            new ChunkCollisionMutationDrainSystem().tick(0.0f, 0, store);

            assertEquals(0, queue.size());
            assertNull(store.getResource(PhysicsChunkCollisionPayloadResource.getResourceType())
                .get(payloadKey));
            assertSoftSkipsEmpty(store);
            assertGeneratedBox(store,
                spaceUuid,
                spaceRef,
                sourceKey,
                payloadKey,
                PartKind.BOX,
                0,
                fullCubeBox,
                payload);
            assertGeneratedBox(store,
                spaceUuid,
                spaceRef,
                sourceKey,
                payloadKey,
                PartKind.DETAIL_BOX,
                0,
                detailBox,
                payload);
        } finally {
            registry.removeStore(store);
            registry.shutdown();
        }
    }

    @Test
    void upsertCreatesNativeVoxelRowWhenBackendSupportsVoxelTerrain() {
        ComponentRegistry<PhysicsStore> registry = new ComponentRegistry<>();
        ComponentRegistryProxy<PhysicsStore> proxy =
            new ComponentRegistryProxy<>(new ArrayList<>(), registry);
        PhysicsComponentTypeRegistry.registerComponentTypes(proxy);
        PhysicsResourceTypes.registerResourceTypes(proxy);
        Store<PhysicsStore> store = registry.addStore(
            new PhysicsStore(TestInstanceFactory.world("chunk-collision-drain-voxel-test")),
            EmptyResourceStorage.get());
        try {
            UUID spaceUuid = uuid(11);
            BackendId backendId = new BackendId("test:chunk-collision-drain-voxel");
            Ref<PhysicsStore> spaceRef = addBoundSpace(store, spaceUuid, backendId, true);
            String sourceKey = "2:3:4";
            String payloadKey = "chunk-collision/2/3/4";
            ChunkCollisionPayload payload = new ChunkCollisionPayload(1.0f,
                1.0f,
                1.0f,
                new int[] {0, 0, 0, 1, 0, 0},
                List.of(),
                List.of(),
                true,
                0.7f,
                0.05f,
                0x20,
                0x03,
                List.of());

            PhysicsChunkCollisionMutationQueueResource queue = store.getResource(
                PhysicsChunkCollisionMutationQueueResource.getResourceType());
            queue.enqueue(ChunkCollisionMutation.upsert(spaceUuid,
                sourceKey,
                2,
                3,
                4,
                payloadKey,
                payload));

            new ChunkCollisionMutationDrainSystem().tick(0.0f, 0, store);

            assertEquals(0, queue.size());
            assertVoxelOnlyPayload(store.getResource(PhysicsChunkCollisionPayloadResource.getResourceType())
                .get(payloadKey));
            assertSoftSkipsEmpty(store);
            assertGeneratedVoxel(store,
                spaceUuid,
                spaceRef,
                sourceKey,
                payloadKey,
                payload);
        } finally {
            registry.removeStore(store);
            registry.shutdown();
        }
    }

    @Test
    void destroyingDetailRowDoesNotRemoveNativeVoxelPayloadForSiblingRow() {
        ComponentRegistry<PhysicsStore> registry = new ComponentRegistry<>();
        ComponentRegistryProxy<PhysicsStore> proxy =
            new ComponentRegistryProxy<>(new ArrayList<>(), registry);
        PhysicsComponentTypeRegistry.registerComponentTypes(proxy);
        PhysicsResourceTypes.registerResourceTypes(proxy);
        Store<PhysicsStore> store = registry.addStore(
            new PhysicsStore(TestInstanceFactory.world("chunk-collision-drain-payload-lifetime-test")),
            EmptyResourceStorage.get());
        try {
            markCurrentThreadAsWorldThread(store);
            UUID spaceUuid = uuid(31);
            BackendId backendId = new BackendId("test:chunk-collision-drain-payload-lifetime");
            Ref<PhysicsStore> spaceRef = addBoundSpace(store, spaceUuid, backendId, true);
            String sourceKey = "2:3:4";
            String payloadKey = "chunk-collision/2/3/4";
            ChunkCollisionPayload payload = new ChunkCollisionPayload(1.0f,
                1.0f,
                1.0f,
                new int[] {0, 0, 0},
                List.of(),
                List.of(new BoxPayload(10.0, 20.0, 30.0, 0.25, 0.5, 0.75)),
                true,
                0.7f,
                0.05f,
                0x20,
                0x03,
                List.of());

            PhysicsChunkCollisionMutationQueueResource queue = store.getResource(
                PhysicsChunkCollisionMutationQueueResource.getResourceType());
            queue.enqueue(ChunkCollisionMutation.upsert(spaceUuid,
                sourceKey,
                2,
                3,
                4,
                payloadKey,
                payload));
            new ChunkCollisionMutationDrainSystem().tick(0.0f, 0, store);

            assertGeneratedVoxel(store, spaceUuid, spaceRef, sourceKey, payloadKey, payload);
            UUID detailUuid = ChunkCollisionMutationDrainSystem.chunkCollisionBodyUuid(spaceUuid,
                sourceKey,
                PartKind.DETAIL_BOX,
                0);
            assertNotNull(store.getResource(PhysicsIdentityIndexResource.getResourceType())
                .getByUuid(detailUuid));
            ChunkCollisionPayload retainedPayload = store.getResource(
                    PhysicsChunkCollisionPayloadResource.getResourceType())
                .get(payloadKey);
            assertVoxelOnlyPayload(retainedPayload);

            PhysicsStoreTopologyMutations.destroyBody(store, detailUuid);

            assertSame(retainedPayload,
                store.getResource(PhysicsChunkCollisionPayloadResource.getResourceType())
                    .get(payloadKey));
            assertSoftSkipsEmpty(store);
        } finally {
            registry.removeStore(store);
            registry.shutdown();
        }
    }

    private static void assertVoxelOnlyPayload(@Nullable ChunkCollisionPayload payload) {
        assertNotNull(payload);
        assertTrue(payload.hasFullCubeVoxels());
        assertTrue(payload.mergedFullCubeBoxes().isEmpty());
        assertTrue(payload.detailBoxes().isEmpty());
    }

    @Test
    void removeDeletesGeneratedRowsAndPayloadResource() {
        ComponentRegistry<PhysicsStore> registry = new ComponentRegistry<>();
        ComponentRegistryProxy<PhysicsStore> proxy =
            new ComponentRegistryProxy<>(new ArrayList<>(), registry);
        PhysicsComponentTypeRegistry.registerComponentTypes(proxy);
        PhysicsResourceTypes.registerResourceTypes(proxy);
        Store<PhysicsStore> store = registry.addStore(
            new PhysicsStore(TestInstanceFactory.world("chunk-collision-drain-remove-test")),
            EmptyResourceStorage.get());
        try {
            markCurrentThreadAsWorldThread(store);
            UUID spaceUuid = uuid(21);
            BackendId backendId = new BackendId("test:chunk-collision-drain-remove");
            addBoundSpace(store, spaceUuid, backendId);
            String sourceKey = "5:6:7";
            String payloadKey = "chunk-collision/5/6/7";
            ChunkCollisionPayload payload = new ChunkCollisionPayload(1.0f,
                1.0f,
                1.0f,
                new int[0],
                List.of(new BoxPayload(1.0, 2.0, 3.0, 0.5, 0.5, 0.5)),
                List.of(new BoxPayload(4.0, 5.0, 6.0, 0.25, 0.25, 0.25)),
                false,
                0.6f,
                0.1f,
                0x10,
                0x0F,
                List.of());

            PhysicsChunkCollisionMutationQueueResource queue = store.getResource(
                PhysicsChunkCollisionMutationQueueResource.getResourceType());
            queue.enqueue(ChunkCollisionMutation.upsert(spaceUuid,
                sourceKey,
                5,
                6,
                7,
                payloadKey,
                payload));
            new ChunkCollisionMutationDrainSystem().tick(0.0f, 0, store);

            PhysicsIdentityIndexResource identity =
                store.getResource(PhysicsIdentityIndexResource.getResourceType());
            UUID boxUuid = ChunkCollisionMutationDrainSystem.chunkCollisionBodyUuid(spaceUuid,
                sourceKey,
                PartKind.BOX,
                0);
            UUID detailUuid = ChunkCollisionMutationDrainSystem.chunkCollisionBodyUuid(spaceUuid,
                sourceKey,
                PartKind.DETAIL_BOX,
                0);
            assertNotNull(identity.getByUuid(boxUuid));
            assertNotNull(identity.getByUuid(detailUuid));

            queue.enqueue(ChunkCollisionMutation.remove(spaceUuid, sourceKey, 5, 6, 7));
            new ChunkCollisionMutationDrainSystem().tick(0.0f, 0, store);

            assertEquals(0, queue.size());
            assertNull(identity.getByUuid(boxUuid));
            assertNull(identity.getByUuid(detailUuid));
            assertNull(store.getResource(PhysicsChunkCollisionPayloadResource.getResourceType())
                .get(payloadKey));
            assertSoftSkipsEmpty(store);
        } finally {
            registry.removeStore(store);
            registry.shutdown();
        }
    }

    @Nonnull
    private static Ref<PhysicsStore> addBoundSpace(@Nonnull Store<PhysicsStore> store,
        @Nonnull UUID spaceUuid,
        @Nonnull BackendId backendId) {
        return addBoundSpace(store, spaceUuid, backendId, false);
    }

    @Nonnull
    private static Ref<PhysicsStore> addBoundSpace(@Nonnull Store<PhysicsStore> store,
        @Nonnull UUID spaceUuid,
        @Nonnull BackendId backendId,
        boolean voxelTerrain) {
        Ref<PhysicsStore> spaceRef = store.addEntity(PhysicsEntities.spaceHolder(store,
                spaceUuid,
                new SpaceComponent(backendId, new Vector3f(0.0f, -9.81f, 0.0f))),
            AddReason.SPAWN);
        assertNotNull(spaceRef);
        store.getResource(PhysicsIdentityIndexResource.getResourceType())
            .putUuid(spaceUuid, spaceRef);
        store.getExternalData().putRefForUUID(spaceUuid, spaceRef);

        PhysicsBackendRuntime backendRuntime =
            new FakePhysicsBackendRuntimeProvider(backendId, false, voxelTerrain).createRuntime();
        int spaceHandle = backendRuntime.createSpace(new SpaceId(42));
        PhysicsRuntimeResource runtime = store.getResource(PhysicsRuntimeResource.getResourceType());
        runtime.putRuntime(backendId, backendRuntime);
        runtime.putSpaceBinding(spaceUuid,
            spaceRef,
            backendId,
            new BackendSpaceHandle(spaceHandle));
        return spaceRef;
    }

    private static void assertGeneratedVoxel(@Nonnull Store<PhysicsStore> store,
        @Nonnull UUID spaceUuid,
        @Nonnull Ref<PhysicsStore> spaceRef,
        @Nonnull String sourceKey,
        @Nonnull String payloadKey,
        @Nonnull ChunkCollisionPayload payload) {
        UUID bodyUuid = ChunkCollisionMutationDrainSystem.chunkCollisionBodyUuid(spaceUuid,
            sourceKey,
            PartKind.VOXEL_TERRAIN,
            0);
        Ref<PhysicsStore> bodyRef = store
            .getResource(PhysicsIdentityIndexResource.getResourceType())
            .getByUuid(bodyUuid);
        assertNotNull(bodyRef);

        BodyComponent body = store.getComponent(bodyRef, BodyComponent.getComponentType());
        assertNotNull(body);
        assertEquals(spaceUuid, body.getSpaceUuid());
        assertNotNull(body.getSpaceRef());
        assertEquals(spaceRef.getIndex(), body.getSpaceRef().getIndex());
        assertEquals(PhysicsBodyKind.TERRAIN, body.getKind());
        assertEquals(PhysicsBodyPersistenceMode.RUNTIME_ONLY, body.getPersistenceMode());

        ShapeComponent shape = store.getComponent(bodyRef, ShapeComponent.getComponentType());
        assertNotNull(shape);
        assertEquals(ShapeType.VOXELS, shape.getShapeType());
        assertEquals(payloadKey, shape.getResourceKey());

        TargetComponent target = store.getComponent(bodyRef, TargetComponent.getComponentType());
        assertNotNull(target);
        assertVectorEquals(2 << ChunkUtil.BITS,
            3 << ChunkUtil.BITS,
            4 << ChunkUtil.BITS,
            target.getPosition());

        MaterialComponent material = store.getComponent(bodyRef,
            MaterialComponent.getComponentType());
        assertNotNull(material);
        assertEquals(payload.friction(), material.getFriction(), DELTA);
        assertEquals(payload.restitution(), material.getRestitution(), DELTA);

        CollisionFilterComponent filter = store.getComponent(bodyRef,
            CollisionFilterComponent.getComponentType());
        assertNotNull(filter);
        assertEquals(payload.collisionGroup(), filter.getCollisionGroup());
        assertEquals(payload.collisionMask(), filter.getCollisionMask());

        ChunkCollisionSourceComponent source = store.getComponent(bodyRef,
            ChunkCollisionSourceComponent.getComponentType());
        assertNotNull(source);
        assertEquals(sourceKey, source.getSourceKey());
        assertEquals(payloadKey, source.getPayloadResourceKey());
        assertEquals(PartKind.VOXEL_TERRAIN, source.getPartKind());
        assertEquals(0, source.getPartIndex());
    }

    private static void assertGeneratedBox(@Nonnull Store<PhysicsStore> store,
        @Nonnull UUID spaceUuid,
        @Nonnull Ref<PhysicsStore> spaceRef,
        @Nonnull String sourceKey,
        @Nonnull String payloadKey,
        @Nonnull PartKind partKind,
        int partIndex,
        @Nonnull BoxPayload box,
        @Nonnull ChunkCollisionPayload payload) {
        UUID bodyUuid = ChunkCollisionMutationDrainSystem.chunkCollisionBodyUuid(spaceUuid,
            sourceKey,
            partKind,
            partIndex);
        Ref<PhysicsStore> bodyRef = store
            .getResource(PhysicsIdentityIndexResource.getResourceType())
            .getByUuid(bodyUuid);
        assertNotNull(bodyRef);
        assertEquals(bodyUuid,
            store.getComponent(bodyRef, UuidComponent.getComponentType()).getUuid());

        BodyComponent body = store.getComponent(bodyRef, BodyComponent.getComponentType());
        assertNotNull(body);
        assertEquals(spaceUuid, body.getSpaceUuid());
        assertNotNull(body.getSpaceRef());
        assertEquals(spaceRef.getIndex(), body.getSpaceRef().getIndex());
        assertEquals(PhysicsBodyKind.TERRAIN, body.getKind());
        assertEquals(PhysicsBodyPersistenceMode.RUNTIME_ONLY, body.getPersistenceMode());

        DynamicsComponent dynamics = store.getComponent(bodyRef,
            DynamicsComponent.getComponentType());
        assertNotNull(dynamics);
        assertEquals(PhysicsBodyType.STATIC, dynamics.getBodyType());
        assertEquals(0.0f, dynamics.getMass(), DELTA);
        assertFalse(dynamics.isContinuousCollisionEnabled());

        TargetComponent target = store.getComponent(bodyRef, TargetComponent.getComponentType());
        assertNotNull(target);
        assertVectorEquals((float) box.centerX(),
            (float) box.centerY(),
            (float) box.centerZ(),
            target.getPosition());

        ShapeComponent shape = store.getComponent(bodyRef, ShapeComponent.getComponentType());
        assertNotNull(shape);
        assertEquals(ShapeType.BOX, shape.getShapeType());
        assertEquals((float) box.halfX(), shape.getHalfExtentX(), DELTA);
        assertEquals((float) box.halfY(), shape.getHalfExtentY(), DELTA);
        assertEquals((float) box.halfZ(), shape.getHalfExtentZ(), DELTA);
        assertEquals("", shape.getResourceKey());

        MaterialComponent material = store.getComponent(bodyRef,
            MaterialComponent.getComponentType());
        assertNotNull(material);
        assertEquals(payload.friction(), material.getFriction(), DELTA);
        assertEquals(payload.restitution(), material.getRestitution(), DELTA);

        CollisionFilterComponent filter = store.getComponent(bodyRef,
            CollisionFilterComponent.getComponentType());
        assertNotNull(filter);
        assertEquals(payload.collisionGroup(), filter.getCollisionGroup());
        assertEquals(payload.collisionMask(), filter.getCollisionMask());

        ChunkCollisionSourceComponent source = store.getComponent(bodyRef,
            ChunkCollisionSourceComponent.getComponentType());
        assertNotNull(source);
        assertEquals(sourceKey, source.getSourceKey());
        assertEquals(0, source.getChunkX());
        assertEquals(1, source.getSectionY());
        assertEquals(2, source.getChunkZ());
        assertEquals("", source.getPayloadResourceKey());
        assertEquals(partKind, source.getPartKind());
        assertEquals(partIndex, source.getPartIndex());
    }

    private static void assertVectorEquals(float expectedX,
        float expectedY,
        float expectedZ,
        @Nonnull Vector3f actual) {
        assertEquals(expectedX, actual.x, DELTA);
        assertEquals(expectedY, actual.y, DELTA);
        assertEquals(expectedZ, actual.z, DELTA);
    }

    private static void assertSoftSkipsEmpty(@Nonnull Store<PhysicsStore> store) {
        assertEquals(0,
            store.getResource(PhysicsRestoreStatusResource.getResourceType())
                .getSoftSkipsByReason()
                .size());
    }

    private static void markCurrentThreadAsWorldThread(@Nonnull Store<PhysicsStore> store) {
        try {
            Method setThread = TickingThread.class.getDeclaredMethod("setThread", Thread.class);
            setThread.setAccessible(true);
            setThread.invoke(store.getExternalData().getWorld(), Thread.currentThread());
        } catch (NoSuchMethodException | IllegalAccessException exception) {
            throw new AssertionError("Could not mark test world thread", exception);
        } catch (InvocationTargetException exception) {
            throw new AssertionError("Could not mark test world thread", exception.getCause());
        }
    }

    @Nonnull
    private static UUID uuid(long leastSignificantBits) {
        return new UUID(0L, leastSignificantBits);
    }
}
