package dev.hytalemodding.impulse.core.plugin.snapshot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.hytalemodding.impulse.api.PhysicsAxis;
import dev.hytalemodding.impulse.api.PhysicsBodySnapshot;
import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.ShapeType;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyId;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyKind;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyPersistenceMode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class PublishedPhysicsSnapshotFrameTest {

    private static final PhysicsBodyId BODY_ID =
        PhysicsBodyId.of(UUID.fromString("00000000-0000-0000-0000-000000000001"));
    private static final SpaceId SPACE_ID = new SpaceId(7);

    @Test
    void bodySnapshotCopiesPoseAndVelocityOnConstructionAndAccess() {
        Vector3f position = new Vector3f(1.0f, 2.0f, 3.0f);
        Quaternionf rotation = new Quaternionf().rotateXYZ(0.25f, 0.5f, 0.75f);
        Vector3f linearVelocity = new Vector3f(4.0f, 5.0f, 6.0f);
        Vector3f angularVelocity = new Vector3f(7.0f, 8.0f, 9.0f);

        PublishedPhysicsBodySnapshot snapshot = new PublishedPhysicsBodySnapshot(BODY_ID,
            SPACE_ID,
            10L,
            20L,
            30L,
            40L,
            PhysicsBodyKind.BODY,
            PhysicsBodyPersistenceMode.RUNTIME_ONLY,
            position,
            rotation,
            linearVelocity,
            angularVelocity,
            PhysicsBodyType.DYNAMIC,
            false,
            true,
            0.25f,
            ShapeType.BOX,
            new Vector3f(0.5f, 0.75f, 1.0f),
            -1.0f,
            -1.0f,
            PhysicsAxis.Y);

        position.zero();
        rotation.identity();
        linearVelocity.zero();
        angularVelocity.zero();

        assertEquals(new Vector3f(1.0f, 2.0f, 3.0f), snapshot.position());
        assertEquals(new Quaternionf().rotateXYZ(0.25f, 0.5f, 0.75f), snapshot.rotation());
        assertEquals(new Vector3f(4.0f, 5.0f, 6.0f), snapshot.linearVelocity());
        assertEquals(new Vector3f(7.0f, 8.0f, 9.0f), snapshot.angularVelocity());

        snapshot.position().zero();
        snapshot.rotation().identity();
        snapshot.linearVelocity().zero();
        snapshot.angularVelocity().zero();

        assertEquals(new Vector3f(1.0f, 2.0f, 3.0f), snapshot.position());
        assertEquals(new Quaternionf().rotateXYZ(0.25f, 0.5f, 0.75f), snapshot.rotation());
        assertEquals(new Vector3f(4.0f, 5.0f, 6.0f), snapshot.linearVelocity());
        assertEquals(new Vector3f(7.0f, 8.0f, 9.0f), snapshot.angularVelocity());
    }

    @Test
    void factoryCopiesExistingSnapshotData() {
        PhysicsBodySnapshot ownerThreadSnapshot = new PhysicsBodySnapshot(new Vector3f(1.0f, 0.0f, 0.0f),
            new Quaternionf().rotateY(0.5f),
            new Vector3f(0.0f, 2.0f, 0.0f),
            new Vector3f(0.0f, 0.0f, 3.0f),
            PhysicsBodyType.KINEMATIC,
            true,
            false,
            0.5f,
            ShapeType.BOX,
            new Vector3f(0.25f, 0.5f, 0.75f),
            -1.0f,
            -1.0f,
            PhysicsAxis.Y);

        PublishedPhysicsBodySnapshot published = PublishedPhysicsBodySnapshot.from(BODY_ID,
            SPACE_ID,
            1L,
            2L,
            3L,
            4L,
            PhysicsBodyKind.BODY,
            PhysicsBodyPersistenceMode.PERSISTENT,
            ownerThreadSnapshot);
        ownerThreadSnapshot.position().zero();
        ownerThreadSnapshot.rotation().identity();
        ownerThreadSnapshot.linearVelocity().zero();
        ownerThreadSnapshot.angularVelocity().zero();

        assertEquals(new Vector3f(1.0f, 0.0f, 0.0f), published.position());
        assertEquals(new Quaternionf().rotateY(0.5f), published.rotation());
        assertEquals(new Vector3f(0.0f, 2.0f, 0.0f), published.linearVelocity());
        assertEquals(new Vector3f(0.0f, 0.0f, 3.0f), published.angularVelocity());
        assertEquals(4L, published.registrationGeneration());
        assertEquals(PhysicsBodyKind.BODY, published.kind());
        assertEquals(PhysicsBodyPersistenceMode.PERSISTENT, published.persistenceMode());
        assertEquals(PhysicsBodyType.KINEMATIC, published.bodyType());
        assertEquals(ShapeType.BOX, published.shapeType());
        assertEquals(new Vector3f(0.25f, 0.5f, 0.75f), published.boxHalfExtents());
    }

    @Test
    void framesCopyListsAndExposeCounts() {
        PublishedPhysicsBodySnapshot body = bodySnapshot(10L, 20L, 30L, SPACE_ID);
        List<PublishedPhysicsBodySnapshot> bodies = new ArrayList<>();
        bodies.add(body);
        PublishedPhysicsSpaceFrame spaceFrame =
            new PublishedPhysicsSpaceFrame(SPACE_ID, 10L, 20L, 30L, bodies);
        bodies.clear();

        assertEquals(1, spaceFrame.bodyCount());
        assertThrows(UnsupportedOperationException.class, () -> spaceFrame.bodies().clear());

        List<PublishedPhysicsSpaceFrame> spaces = new ArrayList<>();
        spaces.add(spaceFrame);
        PublishedPhysicsSnapshotFrame frame = new PublishedPhysicsSnapshotFrame(10L,
            20L,
            40L,
            50L,
            PublishedPhysicsSnapshotFrame.Status.COMPLETE,
            12,
            100L,
            200L,
            spaces);
        spaces.clear();

        assertEquals(PublishedPhysicsSnapshotFrame.Status.COMPLETE, frame.status());
        assertEquals(12, frame.spatialIndexCellCount());
        assertEquals(100L, frame.stepNanos());
        assertEquals(200L, frame.snapshotNanos());
        assertEquals(1, frame.spaceCount());
        assertEquals(1, frame.bodyCount());
        assertThrows(UnsupportedOperationException.class, () -> frame.spaces().clear());
    }

    @Test
    void framesRejectMismatchedEpochs() {
        PublishedPhysicsBodySnapshot bodyWithWrongSpaceEpoch =
            bodySnapshot(10L, 20L, 31L, SPACE_ID);
        assertThrows(IllegalArgumentException.class,
            () -> new PublishedPhysicsSpaceFrame(SPACE_ID,
                10L,
                20L,
                30L,
                List.of(bodyWithWrongSpaceEpoch)));

        PublishedPhysicsSpaceFrame spaceFrame = new PublishedPhysicsSpaceFrame(SPACE_ID,
            10L,
            20L,
            30L,
            List.of(bodySnapshot(10L, 20L, 30L, SPACE_ID)));
        assertThrows(IllegalArgumentException.class,
            () -> new PublishedPhysicsSnapshotFrame(11L,
                20L,
                0L,
                0L,
                PublishedPhysicsSnapshotFrame.Status.COMPLETE,
                0,
                0L,
                0L,
                List.of(spaceFrame)));
    }

    @Test
    void framesRejectNegativeEpochs() {
        assertThrows(IllegalArgumentException.class,
            () -> bodySnapshot(-1L, 20L, 30L, SPACE_ID));
        assertThrows(IllegalArgumentException.class,
            () -> new PublishedPhysicsSpaceFrame(SPACE_ID, 10L, -1L, 30L, List.of()));
        assertThrows(IllegalArgumentException.class,
            () -> PublishedPhysicsSnapshotFrame.empty(10L, -1L));
        assertThrows(IllegalArgumentException.class,
            () -> new PublishedPhysicsSnapshotFrame(10L,
                20L,
                30L,
                40L,
                PublishedPhysicsSnapshotFrame.Status.COMPLETE,
                -1,
                0L,
                0L,
                List.of()));
    }

    private static PublishedPhysicsBodySnapshot bodySnapshot(long frameEpoch,
        long worldEpoch,
        long spaceEpoch,
        SpaceId spaceId) {
        return new PublishedPhysicsBodySnapshot(BODY_ID,
            spaceId,
            frameEpoch,
            worldEpoch,
            spaceEpoch,
            40L,
            PhysicsBodyKind.BODY,
            PhysicsBodyPersistenceMode.RUNTIME_ONLY,
            new Vector3f(1.0f, 2.0f, 3.0f),
            new Quaternionf(),
            new Vector3f(4.0f, 5.0f, 6.0f),
            new Vector3f(7.0f, 8.0f, 9.0f),
            PhysicsBodyType.DYNAMIC,
            false,
            false,
            0.0f,
            ShapeType.BOX,
            new Vector3f(0.5f),
            -1.0f,
            -1.0f,
            PhysicsAxis.Y);
    }
}
