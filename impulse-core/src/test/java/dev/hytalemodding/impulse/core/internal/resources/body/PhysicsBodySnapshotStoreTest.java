package dev.hytalemodding.impulse.core.internal.resources.body;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hytalemodding.impulse.api.PhysicsAxis;
import dev.hytalemodding.impulse.api.PhysicsBodySnapshot;
import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.ShapeType;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.api.runtime.BackendRuntimeCodes;
import dev.hytalemodding.impulse.api.runtime.PhysicsBackendRuntime;
import dev.hytalemodding.impulse.api.testsupport.FakePhysicsBackendRuntimeProvider;
import dev.hytalemodding.impulse.core.internal.resources.BackendBodyHandle;
import dev.hytalemodding.impulse.core.internal.resources.BackendSpaceHandle;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsSpaceBinding;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyKind;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyPersistenceMode;
import dev.hytalemodding.impulse.core.plugin.snapshot.PublishedPhysicsBodySnapshot;
import dev.hytalemodding.impulse.core.plugin.snapshot.PublishedPhysicsSnapshotFrame;
import dev.hytalemodding.impulse.core.plugin.snapshot.PublishedPhysicsSpaceFrame;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class PhysicsBodySnapshotStoreTest {

    @Test
    void refreshPassesLazySelectedBodiesToBackend() {
        FakePhysicsBackendRuntimeProvider provider =
            new FakePhysicsBackendRuntimeProvider("test:snapshot-store-lazy-refresh");
        PhysicsBackendRuntime runtime = provider.createRuntime();
        SpaceId spaceId = new SpaceId(1);
        int backendSpaceId = runtime.createSpace(spaceId);
        long backendBodyId = runtime.createBody(backendSpaceId,
            BackendRuntimeCodes.SHAPE_BOX,
            0.5f,
            0.5f,
            0.5f,
            0.0f,
            0.0f,
            BackendRuntimeCodes.AXIS_Y,
            0.0f,
            1.0f,
            BackendRuntimeCodes.BODY_DYNAMIC,
            0.0f,
            0.0f,
            0.0f,
            0.0f,
            0.0f,
            0.0f,
            1.0f);
        UUID bodyId = new UUID(0L, 1L);
        PhysicsSpaceBinding binding = new PhysicsSpaceBinding(provider.getId(),
            spaceId,
            new BackendSpaceHandle(backendSpaceId),
            runtime);
        PhysicsBodyRegistry registry = new PhysicsBodyRegistry();
        registry.registerBody(bodyId,
            new BackendBodyHandle(backendBodyId),
            spaceId,
            PhysicsBodyKind.BODY,
            PhysicsBodyPersistenceMode.RUNTIME_ONLY);
        PhysicsBodySnapshotStore store = new PhysicsBodySnapshotStore();

        assertEquals(1, store.refresh(List.of(binding), registry));

        assertEquals(1, store.bodyCount());
    }

    @Test
    void appliesPublishedFramesIncrementallyWithoutReinsertingUnchangedBodies() {
        SpaceId spaceId = new SpaceId(1);
        UUID bodyId = new UUID(0L, 1L);
        PhysicsBodySnapshotStore store = new PhysicsBodySnapshotStore();

        PhysicsBodySnapshotStore.ApplyStats firstApply = store.applyPublishedFrame(
            frame(spaceId, bodyId, 1L, new Vector3f(1.0f, 2.0f, 3.0f)));
        PhysicsBodySnapshotStore.ApplyStats secondApply = store.applyPublishedFrame(
            frame(spaceId, bodyId, 2L, new Vector3f(2.0f, 2.0f, 3.0f)));

        assertEquals(1, firstApply.applied());
        assertEquals(1, firstApply.inserted());
        assertEquals(0, firstApply.removed());
        assertEquals(1, secondApply.applied());
        assertEquals(0, secondApply.inserted());
        assertEquals(0, secondApply.removed());
        assertEquals(1, store.bodyCount());
        assertEquals(1, store.bodyCount(spaceId));
        assertEquals(1, store.cellCount());
    }

    @Test
    void applyPublishedFrameUsesFrameMetadataWithoutLiveRegistry() {
        SpaceId spaceId = new SpaceId(1);
        UUID bodyId = new UUID(0L, 12L);
        PhysicsBodySnapshotStore store = new PhysicsBodySnapshotStore();

        PhysicsBodySnapshotStore.ApplyStats apply = store.applyPublishedFrame(
            frame(spaceId, bodyId, 1L, new Vector3f(1.0f, 2.0f, 3.0f)));

        assertEquals(1, apply.applied());
        assertEquals(1, apply.inserted());
        assertEquals(1, store.bodyCount());
        assertEquals(1, store.bodyCount(spaceId));
    }

    @Test
    void applyPublishedFrameReusesSnapshotWhenBodyStateIsUnchanged() {
        SpaceId spaceId = new SpaceId(1);
        UUID bodyId = new UUID(0L, 2L);
        PhysicsBodySnapshotStore store = new PhysicsBodySnapshotStore();

        store.applyPublishedFrame(frame(spaceId, bodyId, 1L, new Vector3f(1.0f, 2.0f, 3.0f)));
        var firstSnapshot = store.get(bodyId);
        store.applyPublishedFrame(frame(spaceId, bodyId, 2L, new Vector3f(1.0f, 2.0f, 3.0f)));

        assertSame(firstSnapshot, store.get(bodyId));
    }

    @Test
    void internalNearVisitorExposesSnapshotMetadataWithoutEntryDto() {
        SpaceId spaceId = new SpaceId(1);
        UUID nearBodyId = new UUID(0L, 10L);
        UUID farBodyId = new UUID(0L, 11L);
        PhysicsBodySnapshot nearSnapshot = snapshotAt(1.0f, 2.0f, 3.0f);
        PhysicsBodySnapshot farSnapshot = snapshotAt(100.0f, 2.0f, 3.0f);
        PhysicsBodySnapshotStore store = new PhysicsBodySnapshotStore();
        store.put(nearBodyId,
            nearSnapshot,
            spaceId,
            PhysicsBodyKind.BODY,
            PhysicsBodyPersistenceMode.RUNTIME_ONLY);
        store.put(farBodyId,
            farSnapshot,
            spaceId,
            PhysicsBodyKind.TEMPORARY,
            PhysicsBodyPersistenceMode.PERSISTENT);

        List<UUID> visited = new ArrayList<>();
        int candidates = store.forEachIndexedNear(spaceId,
            new Vector3f(0.0f, 2.0f, 3.0f),
            4.0f,
            (bodyId, snapshot, bodySpaceId, kind, persistenceMode) -> {
                visited.add(bodyId);
                assertSame(nearSnapshot, snapshot);
                assertEquals(spaceId, bodySpaceId);
                assertEquals(PhysicsBodyKind.BODY, kind);
                assertEquals(PhysicsBodyPersistenceMode.RUNTIME_ONLY, persistenceMode);
            });

        assertEquals(1, candidates);
        assertEquals(List.of(nearBodyId), visited);
    }

    private static PublishedPhysicsSnapshotFrame frame(SpaceId spaceId,
        UUID bodyId,
        long frameEpoch,
        Vector3f position) {
        PublishedPhysicsBodySnapshot body = new PublishedPhysicsBodySnapshot(bodyId,
            spaceId,
            frameEpoch,
            0L,
            0L,
            0L,
            PhysicsBodyKind.BODY,
            PhysicsBodyPersistenceMode.RUNTIME_ONLY,
            position,
            new Quaternionf(),
            new Vector3f(),
            new Vector3f(),
            PhysicsBodyType.DYNAMIC,
            false,
            false,
            0.0f,
            ShapeType.BOX,
            new Vector3f(0.5f, 0.5f, 0.5f),
            0.0f,
            0.0f,
            PhysicsAxis.Y);
        return new PublishedPhysicsSnapshotFrame(frameEpoch,
            0L,
            frameEpoch,
            frameEpoch,
            PublishedPhysicsSnapshotFrame.Status.COMPLETE,
            1,
            0L,
            0L,
            List.of(new PublishedPhysicsSpaceFrame(spaceId, frameEpoch, 0L, 0L, List.of(body))));
    }

    private static PhysicsBodySnapshot snapshotAt(float x, float y, float z) {
        return new PhysicsBodySnapshot(new Vector3f(x, y, z),
            new Quaternionf(),
            new Vector3f(),
            new Vector3f(),
            PhysicsBodyType.DYNAMIC,
            false,
            false,
            0.0f,
            ShapeType.BOX,
            new Vector3f(0.5f, 0.5f, 0.5f),
            0.0f,
            0.0f,
            PhysicsAxis.Y);
    }

}
