package dev.hytalemodding.impulse.core.internal.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.api.BackendId;
import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.ShapeType;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.api.runtime.PhysicsBackendRuntime;
import dev.hytalemodding.impulse.api.testsupport.FakePhysicsBackendRuntimeProvider;
import dev.hytalemodding.impulse.core.internal.modules.physicschunk.ChunkCollisionMutation;
import dev.hytalemodding.impulse.core.internal.modules.physicschunk.ChunkCollisionPayload;
import dev.hytalemodding.impulse.core.internal.resources.BackendBodyHandle;
import dev.hytalemodding.impulse.core.internal.resources.BackendJointHandle;
import dev.hytalemodding.impulse.core.internal.resources.BackendSpaceHandle;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsChunkCollisionMutationQueueResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsRuntimeResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsSnapshotResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsSpaceCompatibilityIndexResource;
import dev.hytalemodding.impulse.core.plugin.snapshots.PhysicsBodySnapshot;
import dev.hytalemodding.impulse.core.plugin.snapshots.PhysicsSnapshotFrame;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class PhysicsStoreResourceIndexTest {

    @Test
    void compatibilityIndexMaintainsBothDirectionsWhenMappingsMove() {
        PhysicsSpaceCompatibilityIndexResource index = new PhysicsSpaceCompatibilityIndexResource();
        UUID firstSpaceUuid = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID secondSpaceUuid = UUID.fromString("00000000-0000-0000-0000-000000000002");

        index.putSpace(new SpaceId(7), firstSpaceUuid);
        index.putSpace(new SpaceId(8), firstSpaceUuid);
        index.putSpace(new SpaceId(8), secondSpaceUuid);

        assertNull(index.getSpaceUuid(new SpaceId(7)));
        assertNull(index.getSpaceId(firstSpaceUuid));
        assertEquals(secondSpaceUuid, index.getSpaceUuid(new SpaceId(8)));
        assertEquals(new SpaceId(8), index.getSpaceId(secondSpaceUuid));
        assertEquals(List.of(new SpaceId(8)), List.copyOf(index.spaceIds()));
    }

    @Test
    void runtimeIndexesKeepRefHandlesAndScopedBackendMetadataTogether() {
        PhysicsRuntimeResource runtime = new PhysicsRuntimeResource();
        BackendId backendId = new BackendId("test:runtime-index");
        PhysicsBackendRuntime backendRuntime =
            new FakePhysicsBackendRuntimeProvider(backendId, false, false).createRuntime();
        BackendId otherBackendId = new BackendId("test:runtime-index-other");
        PhysicsBackendRuntime otherBackendRuntime =
            new FakePhysicsBackendRuntimeProvider(otherBackendId, false, false).createRuntime();
        UUID spaceUuid = UUID.fromString("00000000-0000-0000-0000-000000000003");
        UUID collidingSpaceUuid = UUID.fromString("00000000-0000-0000-0000-000000000013");
        UUID bodyUuid = UUID.fromString("00000000-0000-0000-0000-000000000004");
        BackendSpaceHandle oldSpaceHandle = new BackendSpaceHandle(30);
        BackendSpaceHandle spaceHandle = new BackendSpaceHandle(31);
        BackendBodyHandle bodyHandle = new BackendBodyHandle(42L);
        Ref<PhysicsStore> spaceRef = new TestRef(1);
        Ref<PhysicsStore> collidingSpaceRef = new TestRef(13);
        Ref<PhysicsStore> bodyRef = new TestRef(2);

        runtime.putRuntime(backendId, backendRuntime);
        runtime.putRuntime(otherBackendId, otherBackendRuntime);
        runtime.putSpaceHandle(spaceRef, backendId, oldSpaceHandle);
        runtime.putSpaceMetadata(backendId, oldSpaceHandle, spaceUuid, spaceRef);

        runtime.putSpaceHandle(spaceRef, backendId, spaceHandle);
        runtime.putSpaceMetadata(backendId, spaceHandle, spaceUuid, spaceRef);
        assertSame(backendRuntime, runtime.runtimeForSpaceRef(spaceRef));

        runtime.putSpaceHandle(collidingSpaceRef, otherBackendId, spaceHandle);
        runtime.putSpaceMetadata(otherBackendId, spaceHandle, collidingSpaceUuid, collidingSpaceRef);
        assertSame(backendRuntime, runtime.runtimeForSpaceRef(spaceRef));
        assertSame(otherBackendRuntime, runtime.runtimeForSpaceRef(collidingSpaceRef));
        runtime.removeSpaceHandle(collidingSpaceRef);

        runtime.putBodyHandle(bodyRef, spaceRef, spaceHandle, bodyHandle);
        runtime.putBodySnapshotMetadata(backendId,
            spaceHandle,
            bodyHandle,
            bodyUuid,
            bodyRef,
            spaceUuid);
        runtime.putBodyHitMetadata(backendId,
            spaceHandle,
            bodyHandle,
            bodyUuid,
            bodyRef,
            PhysicsBodyType.DYNAMIC,
            ShapeType.BOX);

        assertSame(backendRuntime, runtime.getRuntime(backendId));
        assertEquals(spaceUuid, runtime.getSpaceUuid(spaceRef));
        assertEquals(spaceHandle, runtime.getSpaceHandle(spaceRef));
        assertEquals(backendId, runtime.getSpaceBackendId(spaceRef));
        assertEquals(bodyHandle, runtime.getBodyHandle(bodyRef));
        assertEquals(spaceHandle, runtime.getBodySpaceHandle(bodyRef));
        assertSame(backendRuntime, runtime.runtimeForBodyRef(bodyRef));
        assertEquals(bodyUuid,
            runtime.getBodySnapshotMetadata(backendId, spaceHandle, bodyHandle.value()).bodyUuid());

        List<Long> handles = new ArrayList<>();
        runtime.forEachBodyHandle(backendId, spaceHandle, handles::add);
        assertEquals(List.of(bodyHandle.value()), handles);

        runtime.removeBodyHandle(bodyRef);

        assertNull(runtime.getBodyHandle(bodyRef));
        assertNull(runtime.getBodySpaceHandle(bodyRef));
        assertNull(runtime.runtimeForBodyRef(bodyRef));
        assertNull(runtime.getBodySnapshotMetadata(backendId, spaceHandle, bodyHandle.value()));
        assertNull(runtime.getBodyHitMetadata(backendId, spaceHandle, bodyHandle.value()));
        handles.clear();
        runtime.forEachBodyHandle(backendId, spaceHandle, handles::add);
        assertEquals(List.of(), handles);

        runtime.removeSpaceHandle(spaceRef);

        assertNull(runtime.getSpaceHandle(spaceRef));
    }

    @Test
    void runtimeIndexesExposeRefsForTopologyCleanup() {
        PhysicsRuntimeResource runtime = new PhysicsRuntimeResource();
        UUID spaceUuid = UUID.fromString("00000000-0000-0000-0000-000000000007");
        UUID bodyUuid = UUID.fromString("00000000-0000-0000-0000-000000000008");
        UUID jointUuid = UUID.fromString("00000000-0000-0000-0000-000000000009");
        BackendId backendId = new BackendId("test:runtime-ref-index");
        PhysicsBackendRuntime backendRuntime =
            new FakePhysicsBackendRuntimeProvider(backendId, false, false).createRuntime();
        BackendSpaceHandle spaceHandle = new BackendSpaceHandle(43);
        BackendBodyHandle bodyHandle = new BackendBodyHandle(44L);
        BackendJointHandle jointHandle = new BackendJointHandle(45L);
        BackendJointHandle reboundJointHandle = new BackendJointHandle(46L);
        Ref<PhysicsStore> spaceRef = new TestRef(3);
        Ref<PhysicsStore> bodyRef = new TestRef(4);
        Ref<PhysicsStore> jointRef = new TestRef(5);
        Ref<PhysicsStore> reboundJointRef = new TestRef(6);

        runtime.putRuntime(backendId, backendRuntime);
        runtime.putSpaceHandle(spaceRef, backendId, spaceHandle);
        runtime.putSpaceMetadata(backendId, spaceHandle, spaceUuid, spaceRef);
        runtime.putBodyHandle(bodyRef, spaceRef, spaceHandle, bodyHandle);
        runtime.putBodySnapshotMetadata(backendId,
            spaceHandle,
            bodyHandle,
            bodyUuid,
            bodyRef,
            spaceUuid);
        runtime.putJointHandle(jointRef, spaceRef, spaceHandle, jointHandle);
        runtime.putJointMetadata(backendId, spaceHandle, jointHandle, jointUuid, jointRef);

        assertEquals(List.of(bodyRef), runtime.bodyRefsForSpaceHandle(backendId, spaceHandle));
        assertEquals(List.of(jointRef), runtime.jointRefsForSpaceHandle(backendId, spaceHandle));
        assertSame(backendRuntime, runtime.runtimeForSpaceRef(spaceRef));
        assertSame(backendRuntime, runtime.runtimeForBodyRef(bodyRef));
        assertSame(backendRuntime, runtime.runtimeForJointRef(jointRef));
        assertNull(runtime.runtimeForJointRef(new TestRef(99)));

        runtime.putJointHandle(reboundJointRef, spaceRef, spaceHandle, reboundJointHandle);
        runtime.putJointMetadata(backendId, spaceHandle, reboundJointHandle, jointUuid, reboundJointRef);

        assertNull(runtime.getJointHandle(jointRef));
        assertNull(runtime.runtimeForJointRef(jointRef));
        assertEquals(reboundJointHandle, runtime.getJointHandle(reboundJointRef));
        assertSame(backendRuntime, runtime.runtimeForJointRef(reboundJointRef));
        assertEquals(List.of(reboundJointRef),
            runtime.jointRefsForSpaceHandle(backendId, spaceHandle));

        runtime.removeBodyHandle(bodyRef);
        runtime.removeJointHandle(reboundJointRef);

        assertEquals(List.of(), runtime.bodyRefsForSpaceHandle(backendId, spaceHandle));
        assertEquals(List.of(), runtime.jointRefsForSpaceHandle(backendId, spaceHandle));
    }

    @Test
    void runtimeBodyHandleReplacementRemovesPreviousSpaceIndexEntry() {
        PhysicsRuntimeResource runtime = new PhysicsRuntimeResource();
        UUID spaceUuid = UUID.fromString("00000000-0000-0000-0000-000000000022");
        UUID bodyUuid = UUID.fromString("00000000-0000-0000-0000-000000000023");
        BackendId backendId = new BackendId("test:runtime-body-replace");
        BackendSpaceHandle spaceHandle = new BackendSpaceHandle(61);
        BackendBodyHandle firstHandle = new BackendBodyHandle(62L);
        BackendBodyHandle secondHandle = new BackendBodyHandle(63L);
        Ref<PhysicsStore> spaceRef = new TestRef(40);
        Ref<PhysicsStore> bodyRef = new TestRef(41);

        runtime.putSpaceHandle(spaceRef, backendId, spaceHandle);
        runtime.putSpaceMetadata(backendId, spaceHandle, spaceUuid, spaceRef);
        runtime.putBodyHandle(bodyRef, spaceRef, spaceHandle, firstHandle);
        runtime.putBodySnapshotMetadata(backendId,
            spaceHandle,
            firstHandle,
            bodyUuid,
            bodyRef,
            spaceUuid);
        runtime.putBodyHitMetadata(backendId,
            spaceHandle,
            firstHandle,
            bodyUuid,
            bodyRef,
            PhysicsBodyType.DYNAMIC,
            ShapeType.BOX);

        runtime.putBodyHandle(bodyRef, spaceRef, spaceHandle, secondHandle);
        runtime.putBodySnapshotMetadata(backendId,
            spaceHandle,
            secondHandle,
            bodyUuid,
            bodyRef,
            spaceUuid);
        runtime.putBodyHitMetadata(backendId,
            spaceHandle,
            secondHandle,
            bodyUuid,
            bodyRef,
            PhysicsBodyType.DYNAMIC,
            ShapeType.BOX);

        List<Long> handles = new ArrayList<>();
        runtime.forEachBodyHandle(backendId, spaceHandle, handles::add);
        assertEquals(List.of(secondHandle.value()), handles);
        assertEquals(1, runtime.bodyHandleCount(backendId, spaceHandle));
        assertNull(runtime.getBodySnapshotMetadata(backendId, spaceHandle, firstHandle.value()));
        assertNull(runtime.getBodyHitMetadata(backendId, spaceHandle, firstHandle.value()));
        assertEquals(bodyUuid,
            runtime.getBodySnapshotMetadata(backendId, spaceHandle, secondHandle.value()).bodyUuid());
    }

    @Test
    void runtimeBindsLargeDistinctBodyMetadataSetWithoutQuadraticDuplicateScan() {
        PhysicsRuntimeResource runtime = new PhysicsRuntimeResource();
        UUID spaceUuid = UUID.fromString("00000000-0000-0000-0000-000000000033");
        BackendId backendId = new BackendId("test:runtime-body-metadata-scale");
        BackendSpaceHandle spaceHandle = new BackendSpaceHandle(71);
        Ref<PhysicsStore> spaceRef = new TestRef(70);
        runtime.putSpaceHandle(spaceRef, backendId, spaceHandle);
        runtime.putSpaceMetadata(backendId, spaceHandle, spaceUuid, spaceRef);

        for (int index = 0; index < 20_000; index++) {
            Ref<PhysicsStore> bodyRef = new TestRef(1_000 + index);
            BackendBodyHandle bodyHandle = new BackendBodyHandle(10_000L + index);
            runtime.putBodyHandle(bodyRef, spaceRef, spaceHandle, bodyHandle);
            runtime.putBodySnapshotMetadata(backendId,
                spaceHandle,
                bodyHandle,
                new UUID(0L, 1_000_000L + index),
                bodyRef,
                spaceUuid);
        }
        assertEquals(20_000, runtime.bodyHandleCount(backendId, spaceHandle));
    }

    @Test
    void runtimeRefreshRebuildsRefIndexesFromScopedBackendMetadata() {
        PhysicsRuntimeResource runtime = new PhysicsRuntimeResource();
        PhysicsIdentityIndexResource identity = new PhysicsIdentityIndexResource();
        BackendId backendId = new BackendId("test:runtime-refresh");
        PhysicsBackendRuntime backendRuntime =
            new FakePhysicsBackendRuntimeProvider(backendId, false, false).createRuntime();
        UUID spaceUuid = UUID.fromString("00000000-0000-0000-0000-000000000019");
        UUID bodyUuid = UUID.fromString("00000000-0000-0000-0000-000000000020");
        UUID jointUuid = UUID.fromString("00000000-0000-0000-0000-000000000021");
        BackendSpaceHandle spaceHandle = new BackendSpaceHandle(55);
        BackendBodyHandle bodyHandle = new BackendBodyHandle(56L);
        BackendJointHandle jointHandle = new BackendJointHandle(57L);
        Ref<PhysicsStore> oldSpaceRef = new TestRef(20);
        Ref<PhysicsStore> oldBodyRef = new TestRef(21);
        Ref<PhysicsStore> oldJointRef = new TestRef(22);
        Ref<PhysicsStore> newSpaceRef = new TestRef(30);
        Ref<PhysicsStore> newBodyRef = new TestRef(31);
        Ref<PhysicsStore> newJointRef = new TestRef(32);

        runtime.putRuntime(backendId, backendRuntime);
        runtime.putSpaceHandle(oldSpaceRef, backendId, spaceHandle);
        runtime.putSpaceMetadata(backendId, spaceHandle, spaceUuid, oldSpaceRef);
        runtime.putBodyHandle(oldBodyRef, oldSpaceRef, spaceHandle, bodyHandle);
        runtime.putBodySnapshotMetadata(backendId,
            spaceHandle,
            bodyHandle,
            bodyUuid,
            oldBodyRef,
            spaceUuid);
        runtime.putJointHandle(oldJointRef, oldSpaceRef, spaceHandle, jointHandle);
        runtime.putJointMetadata(backendId, spaceHandle, jointHandle, jointUuid, oldJointRef);
        identity.putUuid(spaceUuid, newSpaceRef);
        identity.putUuid(bodyUuid, newBodyRef);
        identity.putUuid(jointUuid, newJointRef);

        runtime.refreshRowRefs(identity);

        assertNull(runtime.getSpaceHandle(oldSpaceRef));
        assertNull(runtime.getBodyHandle(oldBodyRef));
        assertNull(runtime.getJointHandle(oldJointRef));
        assertEquals(spaceHandle, runtime.getSpaceHandle(newSpaceRef));
        assertEquals(bodyHandle, runtime.getBodyHandle(newBodyRef));
        assertEquals(jointHandle, runtime.getJointHandle(newJointRef));
        assertSame(backendRuntime, runtime.runtimeForSpaceRef(newSpaceRef));
        assertSame(backendRuntime, runtime.runtimeForBodyRef(newBodyRef));
        assertSame(backendRuntime, runtime.runtimeForJointRef(newJointRef));
        assertEquals(newBodyRef,
            runtime.getBodySnapshotMetadata(backendId, spaceHandle, bodyHandle.value()).bodyRef());
    }

    @Test
    void removeBodyHandleClearsPendingBodyOperationsForThatRef() {
        PhysicsRuntimeResource runtime = new PhysicsRuntimeResource();
        UUID firstBodyUuid = UUID.fromString("00000000-0000-0000-0000-000000000011");
        UUID secondBodyUuid = UUID.fromString("00000000-0000-0000-0000-000000000012");
        Ref<PhysicsStore> firstBodyRef = new TestRef(11);
        Ref<PhysicsStore> secondBodyRef = new TestRef(12);

        runtime.enqueuePendingBodyOperation(PhysicsRuntimeResource.PendingBodyOperation.wake(
            firstBodyUuid,
            firstBodyRef));
        runtime.enqueuePendingBodyOperation(PhysicsRuntimeResource.PendingBodyOperation.sleep(
            secondBodyUuid,
            secondBodyRef));

        runtime.removeBodyHandle(firstBodyRef);

        List<PhysicsRuntimeResource.PendingBodyOperation> drained =
            runtime.drainPendingBodyOperations();
        assertEquals(1, drained.size());
        assertEquals(secondBodyUuid, drained.getFirst().bodyUuid());
        assertEquals(secondBodyRef, drained.getFirst().bodyRef());
    }

    @Test
    void snapshotResourceIndexesLatestPublishedFrameByBodyUuid() {
        PhysicsSnapshotResource resource = new PhysicsSnapshotResource();
        UUID spaceUuid = UUID.fromString("00000000-0000-0000-0000-000000000005");
        UUID bodyUuid = UUID.fromString("00000000-0000-0000-0000-000000000006");
        PhysicsBodySnapshot body = new PhysicsBodySnapshot(bodyUuid,
            spaceUuid,
            PhysicsBodyType.KINEMATIC,
            new Vector3f(1.0f, 2.0f, 3.0f),
            new Quaternionf(),
            new Vector3f(4.0f, 5.0f, 6.0f),
            new Vector3f(),
            0.25f,
            false);
        PhysicsSnapshotFrame frame = new PhysicsSnapshotFrame(11L, 0.05f, List.of(body));

        resource.publish(frame);

        assertNotSame(frame, resource.getLatestFrame());
        assertSnapshotEquals(body, resource.getBody(bodyUuid));
        assertNotSame(body, resource.getBody(bodyUuid));
        assertNull(resource.getBody(UUID.randomUUID()));

        resource.clear();

        assertEquals(PhysicsSnapshotFrame.EMPTY, resource.getLatestFrame());
        assertNull(resource.getBody(bodyUuid));
    }

    @Test
    void snapshotResourceCursorReadsCompactBodyStateWithoutFrameMaterialization() {
        PhysicsSnapshotResource resource = new PhysicsSnapshotResource();
        UUID spaceUuid = UUID.fromString("00000000-0000-0000-0000-000000000026");
        UUID firstBodyUuid = UUID.fromString("00000000-0000-0000-0000-000000000027");
        UUID secondBodyUuid = UUID.fromString("00000000-0000-0000-0000-000000000028");
        Ref<PhysicsStore> firstBodyRef = new TestRef(27);
        Ref<PhysicsStore> secondBodyRef = new TestRef(28);
        PhysicsBodySnapshot first = snapshot(firstBodyRef, firstBodyUuid, spaceUuid);
        PhysicsBodySnapshot second = snapshot(secondBodyRef, secondBodyUuid, spaceUuid);
        resource.publish(new PhysicsSnapshotFrame(14L, 0.05f, List.of(first, second)));

        List<UUID> visited = new ArrayList<>();
        resource.forEachBodyCursor(cursor -> {
            visited.add(cursor.bodyUuid());
            assertEquals(spaceUuid, cursor.spaceUuid());
            assertEquals(PhysicsBodyType.KINEMATIC, cursor.bodyType());
            assertTrue(cursor.bodyRef() == firstBodyRef || cursor.bodyRef() == secondBodyRef);
            assertTrue(cursor.positionX() > 0.0f);
            assertEquals(0.0f, cursor.centerOfMassOffsetY(), 0.0001f);
            assertFalse(cursor.sleeping());
        });

        assertEquals(14L, resource.latestSequence());
        assertEquals(List.of(firstBodyUuid, secondBodyUuid), visited);
    }

    @Test
    void snapshotResourceRemovesMultipleBodiesInOneBatch() {
        PhysicsSnapshotResource resource = new PhysicsSnapshotResource();
        UUID spaceUuid = UUID.fromString("00000000-0000-0000-0000-000000000015");
        UUID firstBodyUuid = UUID.fromString("00000000-0000-0000-0000-000000000016");
        UUID secondBodyUuid = UUID.fromString("00000000-0000-0000-0000-000000000017");
        UUID retainedBodyUuid = UUID.fromString("00000000-0000-0000-0000-000000000018");
        Ref<PhysicsStore> firstBodyRef = new TestRef(16);
        Ref<PhysicsStore> secondBodyRef = new TestRef(17);
        Ref<PhysicsStore> retainedBodyRef = new TestRef(18);
        PhysicsBodySnapshot first = snapshot(firstBodyRef, firstBodyUuid, spaceUuid);
        PhysicsBodySnapshot second = snapshot(secondBodyRef, secondBodyUuid, spaceUuid);
        PhysicsBodySnapshot retained = snapshot(retainedBodyRef, retainedBodyUuid, spaceUuid);
        resource.publish(new PhysicsSnapshotFrame(12L, 0.05f, List.of(first, second, retained)));

        resource.removeBodies(List.of(firstBodyUuid, secondBodyUuid));

        assertNull(resource.getBody(firstBodyUuid));
        assertNull(resource.getBody(firstBodyRef));
        assertNull(resource.getBody(secondBodyUuid));
        assertNull(resource.getBody(secondBodyRef));
        assertSnapshotEquals(retained, resource.getBody(retainedBodyUuid));
        assertSnapshotEquals(retained, resource.getBody(retainedBodyRef));
        List<PhysicsBodySnapshot> retainedBodies = resource.getLatestFrame().bodies();
        assertEquals(1, retainedBodies.size());
        assertSnapshotEquals(retained, retainedBodies.getFirst());
    }

    @Test
    void chunkCollisionQueueKeepsOnlyLatestMutationPerSourceBeforeDrain() {
        PhysicsChunkCollisionMutationQueueResource queue =
            new PhysicsChunkCollisionMutationQueueResource();
        UUID spaceUuid = UUID.fromString("00000000-0000-0000-0000-000000000025");
        ChunkCollisionPayload firstPayload = chunkPayload(1.0);
        ChunkCollisionPayload secondPayload = chunkPayload(2.0);

        queue.enqueue(ChunkCollisionMutation.upsert(spaceUuid,
            "0:1:2",
            0,
            1,
            2,
            "chunk-collision/first",
            firstPayload));
        queue.enqueue(ChunkCollisionMutation.upsert(spaceUuid,
            "3:4:5",
            3,
            4,
            5,
            "chunk-collision/other",
            firstPayload));
        queue.enqueue(ChunkCollisionMutation.upsert(spaceUuid,
            "0:1:2",
            0,
            1,
            2,
            "chunk-collision/second",
            secondPayload));

        assertEquals(2, queue.size());
        List<ChunkCollisionMutation> drained = queue.drain();

        assertEquals(2, drained.size());
        assertEquals("3:4:5", drained.get(0).sourceKey());
        assertEquals("0:1:2", drained.get(1).sourceKey());
        assertSame(secondPayload, drained.get(1).payload());
    }

    private static final class TestRef extends Ref<PhysicsStore> {

        private TestRef(int index) {
            super(null, index);
        }

        @Override
        public boolean isValid() {
            return true;
        }
    }

    private static PhysicsBodySnapshot snapshot(Ref<PhysicsStore> bodyRef,
        UUID bodyUuid,
        UUID spaceUuid) {
        return new PhysicsBodySnapshot(bodyRef,
            bodyUuid,
            spaceUuid,
            PhysicsBodyType.KINEMATIC,
            new Vector3f(1.0f, 2.0f, 3.0f),
            new Quaternionf(),
            new Vector3f(),
            new Vector3f(),
            0.0f,
            false);
    }

    private static ChunkCollisionPayload chunkPayload(double centerX) {
        return new ChunkCollisionPayload(1.0f,
            1.0f,
            1.0f,
            new int[0],
            List.of(new ChunkCollisionPayload.BoxPayload(centerX,
                0.0,
                0.0,
                0.5,
                0.5,
                0.5)),
            List.of(),
            false,
            List.of());
    }

    private static void assertSnapshotEquals(PhysicsBodySnapshot expected,
        PhysicsBodySnapshot actual) {
        assertEquals(expected.bodyRef(), actual.bodyRef());
        assertEquals(expected.bodyUuid(), actual.bodyUuid());
        assertEquals(expected.spaceUuid(), actual.spaceUuid());
        assertEquals(expected.bodyType(), actual.bodyType());
        assertEquals(expected.positionX(), actual.positionX());
        assertEquals(expected.positionY(), actual.positionY());
        assertEquals(expected.positionZ(), actual.positionZ());
        assertEquals(expected.rotationX(), actual.rotationX());
        assertEquals(expected.rotationY(), actual.rotationY());
        assertEquals(expected.rotationZ(), actual.rotationZ());
        assertEquals(expected.rotationW(), actual.rotationW());
        assertEquals(expected.linearVelocityX(), actual.linearVelocityX());
        assertEquals(expected.linearVelocityY(), actual.linearVelocityY());
        assertEquals(expected.linearVelocityZ(), actual.linearVelocityZ());
        assertEquals(expected.angularVelocityX(), actual.angularVelocityX());
        assertEquals(expected.angularVelocityY(), actual.angularVelocityY());
        assertEquals(expected.angularVelocityZ(), actual.angularVelocityZ());
        assertEquals(expected.centerOfMassOffsetY(), actual.centerOfMassOffsetY());
        assertEquals(expected.sleeping(), actual.sleeping());
    }
}
