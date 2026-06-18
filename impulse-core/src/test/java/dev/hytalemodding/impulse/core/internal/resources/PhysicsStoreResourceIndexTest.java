package dev.hytalemodding.impulse.core.internal.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.hypixel.hytale.component.Ref;
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
        UUID spaceUuid = UUID.fromString("00000000-0000-0000-0000-000000000003");
        UUID bodyUuid = UUID.fromString("00000000-0000-0000-0000-000000000004");
        BackendSpaceHandle spaceHandle = new BackendSpaceHandle(31);
        BackendBodyHandle bodyHandle = new BackendBodyHandle(42L);
        Ref spaceRef = new TestRef(true);
        Ref bodyRef = new TestRef(true);

        runtime.putRuntime(backendId, backendRuntime);
        runtime.putSpaceBinding(spaceUuid, spaceRef, backendId, spaceHandle);
        runtime.putBodyHandle(bodyUuid, bodyRef, spaceUuid, spaceHandle, bodyHandle);
        runtime.putBodyHitMetadata(bodyHandle, bodyRef, PhysicsBodyType.DYNAMIC, ShapeType.BOX);

        assertSame(backendRuntime, runtime.getRuntime(backendId));
        assertEquals(spaceUuid, runtime.getSpaceUuid(spaceRef));
        assertEquals(spaceHandle, runtime.getSpaceHandle(spaceRef));
        assertEquals(backendId, runtime.getSpaceBackendId(spaceRef));
        assertEquals(bodyHandle, runtime.getBodyHandle(bodyRef));
        assertEquals(spaceHandle, runtime.getBodySpaceHandle(bodyRef));
        assertEquals(bodyUuid, runtime.getBodySnapshotMetadata(bodyHandle.value()).bodyUuid());

        List<Long> handles = new ArrayList<>();
        runtime.forEachBodyHandle(spaceHandle, handles::add);
        assertEquals(List.of(bodyHandle.value()), handles);

        runtime.removeBodyHandle(bodyUuid, bodyRef);

        assertNull(runtime.getBodyHandle(bodyRef));
        assertNull(runtime.getBodySpaceHandle(bodyRef));
        assertNull(runtime.getBodySnapshotMetadata(bodyHandle.value()));
        assertNull(runtime.getBodyHitMetadata(bodyHandle));
        handles.clear();
        runtime.forEachBodyHandle(spaceHandle, handles::add);
        assertEquals(List.of(), handles);
    }

    @Test
    void runtimeIndexesExposeRefsForTopologyCleanup() throws ReflectiveOperationException {
        PhysicsRuntimeResource runtime = new PhysicsRuntimeResource();
        UUID spaceUuid = UUID.fromString("00000000-0000-0000-0000-000000000007");
        UUID bodyUuid = UUID.fromString("00000000-0000-0000-0000-000000000008");
        UUID jointUuid = UUID.fromString("00000000-0000-0000-0000-000000000009");
        BackendId backendId = new BackendId("test:runtime-ref-index");
        BackendSpaceHandle spaceHandle = new BackendSpaceHandle(43);
        BackendBodyHandle bodyHandle = new BackendBodyHandle(44L);
        BackendJointHandle jointHandle = new BackendJointHandle(45L);
        Ref spaceRef = new TestRef(true);
        Ref bodyRef = new TestRef(true);
        Ref jointRef = new TestRef(true);

        runtime.putSpaceBinding(spaceUuid, spaceRef, backendId, spaceHandle);
        runtime.putBodyHandle(bodyUuid, bodyRef, spaceUuid, spaceHandle, bodyHandle);
        runtime.putJointHandle(jointRef, jointUuid, spaceHandle, jointHandle);

        assertEquals(List.of(bodyRef), refsFor(runtime, "bodyRefsForSpaceHandle", spaceHandle));
        assertEquals(List.of(jointRef), refsFor(runtime, "jointRefsForSpaceHandle", spaceHandle));

        runtime.removeBodyHandle(bodyUuid, bodyRef);
        runtime.removeJointHandle(jointUuid, jointRef);

        assertEquals(List.of(), refsFor(runtime, "bodyRefsForSpaceHandle", spaceHandle));
        assertEquals(List.of(), refsFor(runtime, "jointRefsForSpaceHandle", spaceHandle));
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

    private static final class TestRef extends Ref {

        private final boolean valid;

        private TestRef(boolean valid) {
            super(null);
            this.valid = valid;
        }

        @Override
        public boolean isValid() {
            return valid;
        }
    }

    @SuppressWarnings("unchecked")
    private static List<?> refsFor(PhysicsRuntimeResource runtime,
        String methodName,
        BackendSpaceHandle spaceHandle) throws ReflectiveOperationException {
        return (List<?>) PhysicsRuntimeResource.class
            .getMethod(methodName, BackendSpaceHandle.class)
            .invoke(runtime, spaceHandle);
    }
}
