package dev.hytalemodding.impulse.core.internal.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.api.BackendId;
import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.ShapeType;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.api.runtime.PhysicsBackendRuntime;
import dev.hytalemodding.impulse.api.testsupport.FakePhysicsBackendRuntimeProvider;
import dev.hytalemodding.impulse.core.internal.resources.BackendBodyHandle;
import dev.hytalemodding.impulse.core.internal.resources.BackendJointHandle;
import dev.hytalemodding.impulse.core.internal.resources.BackendSpaceHandle;
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
    void runtimeIndexesKeepBackendHandlesAndClearHotPathMetadataTogether() {
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
        runtime.putSpaceBinding(spaceUuid, spaceRef, backendId, oldSpaceHandle);
        assertSame(backendRuntime, runtime.runtimeForSpaceHandle(oldSpaceHandle));

        runtime.putSpaceBinding(spaceUuid, spaceRef, backendId, spaceHandle);
        assertNull(runtime.runtimeForSpaceHandle(oldSpaceHandle));
        assertSame(backendRuntime, runtime.runtimeForSpaceHandle(spaceHandle));
        assertSame(backendRuntime, runtime.runtimeForSpaceRef(spaceRef));

        runtime.putSpaceBinding(collidingSpaceUuid, collidingSpaceRef, otherBackendId, spaceHandle);
        assertNull(runtime.runtimeForSpaceHandle(spaceHandle));
        assertSame(backendRuntime, runtime.runtimeForSpaceRef(spaceRef));
        assertSame(otherBackendRuntime, runtime.runtimeForSpaceRef(collidingSpaceRef));
        runtime.removeSpaceHandle(collidingSpaceUuid);
        assertSame(backendRuntime, runtime.runtimeForSpaceHandle(spaceHandle));

        runtime.putBodyHandle(bodyUuid, bodyRef, spaceUuid, spaceHandle, bodyHandle);
        runtime.putBodyHitMetadata(bodyHandle, bodyRef, PhysicsBodyType.DYNAMIC, ShapeType.BOX);

        assertSame(backendRuntime, runtime.getRuntime(backendId));
        assertEquals(spaceUuid, runtime.getSpaceUuid(spaceRef));
        assertEquals(spaceHandle, runtime.getSpaceHandle(spaceRef));
        assertEquals(backendId, runtime.getSpaceBackendId(spaceRef));
        assertEquals(bodyHandle, runtime.getBodyHandle(bodyRef));
        assertEquals(spaceHandle, runtime.getBodySpaceHandle(bodyRef));
        assertSame(backendRuntime, runtime.runtimeForBodyRef(bodyRef));
        assertEquals(bodyUuid, runtime.getBodySnapshotMetadata(bodyHandle.value()).bodyUuid());

        List<Long> handles = new ArrayList<>();
        runtime.forEachBodyHandle(spaceHandle, handles::add);
        assertEquals(List.of(bodyHandle.value()), handles);

        runtime.removeBodyHandle(bodyUuid, bodyRef);

        assertNull(runtime.getBodyHandle(bodyRef));
        assertNull(runtime.getBodySpaceHandle(bodyRef));
        assertNull(runtime.runtimeForBodyRef(bodyRef));
        assertNull(runtime.getBodySnapshotMetadata(bodyHandle.value()));
        assertNull(runtime.getBodyHitMetadata(bodyHandle));
        handles.clear();
        runtime.forEachBodyHandle(spaceHandle, handles::add);
        assertEquals(List.of(), handles);

        runtime.removeSpaceHandle(spaceUuid);

        assertNull(runtime.runtimeForSpaceHandle(spaceHandle));
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
        runtime.putSpaceBinding(spaceUuid, spaceRef, backendId, spaceHandle);
        runtime.putBodyHandle(bodyUuid, bodyRef, spaceUuid, spaceHandle, bodyHandle);
        runtime.putJointHandle(jointRef, jointUuid, backendId, spaceHandle, jointHandle);

        assertEquals(List.of(bodyRef), runtime.bodyRefsForSpaceHandle(spaceHandle));
        assertEquals(List.of(jointRef), runtime.jointRefsForSpaceHandle(spaceHandle));
        assertSame(backendRuntime, runtime.runtimeForSpaceRef(spaceRef));
        assertSame(backendRuntime, runtime.runtimeForBodyRef(bodyRef));
        assertSame(backendRuntime, runtime.runtimeForJointRef(jointRef));
        assertNull(runtime.runtimeForJointRef(new TestRef(99)));

        runtime.putJointHandle(reboundJointRef,
            jointUuid,
            backendId,
            spaceHandle,
            reboundJointHandle);

        assertNull(runtime.getJointHandle(jointRef));
        assertNull(runtime.runtimeForJointRef(jointRef));
        assertEquals(reboundJointHandle, runtime.getJointHandle(reboundJointRef));
        assertSame(backendRuntime, runtime.runtimeForJointRef(reboundJointRef));
        assertEquals(List.of(reboundJointRef), runtime.jointRefsForSpaceHandle(spaceHandle));

        runtime.removeBodyHandle(bodyUuid, bodyRef);
        runtime.removeJointHandle(jointUuid, reboundJointRef);

        assertEquals(List.of(), runtime.bodyRefsForSpaceHandle(spaceHandle));
        assertEquals(List.of(), runtime.jointRefsForSpaceHandle(spaceHandle));
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
            firstBodyRef,
            null,
            null));
        runtime.enqueuePendingBodyOperation(PhysicsRuntimeResource.PendingBodyOperation.sleep(
            secondBodyUuid,
            secondBodyRef,
            null,
            null));

        runtime.removeBodyHandle(firstBodyUuid, firstBodyRef);

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

        assertEquals(frame, resource.getLatestFrame());
        assertEquals(body, resource.getBody(bodyUuid));
        assertNull(resource.getBody(UUID.randomUUID()));

        resource.clear();

        assertEquals(PhysicsSnapshotFrame.EMPTY, resource.getLatestFrame());
        assertNull(resource.getBody(bodyUuid));
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
}
