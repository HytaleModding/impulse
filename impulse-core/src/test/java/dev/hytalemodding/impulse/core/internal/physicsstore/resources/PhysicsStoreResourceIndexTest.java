package dev.hytalemodding.impulse.core.internal.physicsstore.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import dev.hytalemodding.impulse.api.BackendId;
import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.ShapeType;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.api.runtime.PhysicsBackendRuntime;
import dev.hytalemodding.impulse.api.testsupport.FakePhysicsBackendRuntimeProvider;
import dev.hytalemodding.impulse.core.internal.resources.BackendBodyHandle;
import dev.hytalemodding.impulse.core.internal.resources.BackendSpaceHandle;
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import dev.hytalemodding.impulse.core.plugin.physicsstore.snapshots.PhysicsStoreBodySnapshot;
import dev.hytalemodding.impulse.core.plugin.physicsstore.snapshots.PhysicsStoreSnapshotFrame;
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
        RigidBodyKey bodyKey = RigidBodyKey.random();

        runtime.putRuntime(backendId, backendRuntime);
        runtime.putSpaceBinding(spaceUuid, backendId, spaceHandle);
        runtime.putBodyHandle(bodyUuid, spaceUuid, spaceHandle, bodyHandle);
        runtime.putBodyHitMetadata(bodyHandle, bodyKey, PhysicsBodyType.DYNAMIC, ShapeType.BOX);

        assertSame(backendRuntime, runtime.getRuntime(backendId));
        assertEquals(spaceHandle, runtime.getSpaceHandle(spaceUuid));
        assertEquals(backendId, runtime.getSpaceBackendId(spaceUuid));
        assertEquals(bodyHandle, runtime.getBodyHandle(bodyUuid));
        assertEquals(spaceHandle, runtime.getBodySpaceHandle(bodyUuid));
        assertEquals(bodyUuid, runtime.getBodySnapshotMetadata(bodyHandle.value()).bodyUuid());
        assertEquals(bodyKey, runtime.getBodyHitMetadata(bodyHandle).bodyKey());

        List<Long> handles = new ArrayList<>();
        runtime.forEachBodyHandle(spaceHandle, handles::add);
        assertEquals(List.of(bodyHandle.value()), handles);

        runtime.removeBodyHandle(bodyUuid);

        assertNull(runtime.getBodyHandle(bodyUuid));
        assertNull(runtime.getBodySpaceHandle(bodyUuid));
        assertNull(runtime.getBodySnapshotMetadata(bodyHandle.value()));
        assertNull(runtime.getBodyHitMetadata(bodyHandle));
        handles.clear();
        runtime.forEachBodyHandle(spaceHandle, handles::add);
        assertEquals(List.of(), handles);
    }

    @Test
    void snapshotResourceIndexesLatestPublishedFrameByBodyUuid() {
        PhysicsSnapshotResource resource = new PhysicsSnapshotResource();
        UUID spaceUuid = UUID.fromString("00000000-0000-0000-0000-000000000005");
        UUID bodyUuid = UUID.fromString("00000000-0000-0000-0000-000000000006");
        PhysicsStoreBodySnapshot body = new PhysicsStoreBodySnapshot(bodyUuid,
            spaceUuid,
            PhysicsBodyType.KINEMATIC,
            new Vector3f(1.0f, 2.0f, 3.0f),
            new Quaternionf(),
            new Vector3f(4.0f, 5.0f, 6.0f),
            new Vector3f(),
            0.25f,
            false);
        PhysicsStoreSnapshotFrame frame = new PhysicsStoreSnapshotFrame(11L, 0.05f, List.of(body));

        resource.publish(frame);

        assertEquals(frame, resource.getLatestFrame());
        assertEquals(body, resource.getBody(bodyUuid));
        assertNull(resource.getBody(UUID.randomUUID()));

        resource.clear();

        assertEquals(PhysicsStoreSnapshotFrame.EMPTY, resource.getLatestFrame());
        assertNull(resource.getBody(bodyUuid));
    }
}
