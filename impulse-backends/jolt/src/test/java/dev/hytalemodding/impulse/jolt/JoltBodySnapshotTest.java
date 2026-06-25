package dev.hytalemodding.impulse.jolt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.api.runtime.BackendRuntimeCodes;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class JoltBodySnapshotTest {

    @Test
    void bodySnapshotReportsNativeBodyStateWithJavaBodyId() {
        JoltBackendRuntime runtime = new JoltBackendRuntime(new JoltBackend(), new JoltTestNativeLibrary());
        int spaceId = runtime.createSpace(new SpaceId(21));
        long bodyId = JoltBodyLifecycleTest.createBox(runtime, spaceId, 1.0f);

        runtime.setBodyTransform(spaceId, bodyId, 2.0f, 3.0f, 4.0f, 0.1f, 0.2f, 0.3f, 0.9f);
        runtime.setBodyVelocity(spaceId, bodyId, 5.0f, 6.0f, 7.0f, 0.5f, 0.6f, 0.7f);
        runtime.setBodyType(spaceId, bodyId, BackendRuntimeCodes.BODY_KINEMATIC);
        runtime.sleepBody(spaceId, bodyId);

        JoltTestNativeLibrary.CapturedBodySnapshot snapshot =
            new JoltTestNativeLibrary.CapturedBodySnapshot();
        boolean present = runtime.bodySnapshot(spaceId, bodyId, snapshot);

        assertTrue(present);
        assertEquals(bodyId, snapshot.bodyId);
        assertEquals(BackendRuntimeCodes.SHAPE_BOX, snapshot.shapeTypeCode);
        assertEquals(BackendRuntimeCodes.BODY_KINEMATIC, snapshot.bodyTypeCode);
        assertEquals(2.0f, snapshot.positionX);
        assertEquals(3.0f, snapshot.positionY);
        assertEquals(4.0f, snapshot.positionZ);
        assertEquals(0.1f, snapshot.rotationX);
        assertEquals(0.2f, snapshot.rotationY);
        assertEquals(0.3f, snapshot.rotationZ);
        assertEquals(0.9f, snapshot.rotationW);
        assertEquals(5.0f, snapshot.linearVelocityX);
        assertEquals(6.0f, snapshot.linearVelocityY);
        assertEquals(7.0f, snapshot.linearVelocityZ);
        assertEquals(0.5f, snapshot.angularVelocityX);
        assertEquals(0.6f, snapshot.angularVelocityY);
        assertEquals(0.7f, snapshot.angularVelocityZ);
        assertTrue(snapshot.sleeping);
        assertEquals(2.0f, snapshot.mass);
        assertTrue(snapshot.hasBoxHalfExtents);
        assertEquals(0.5f, snapshot.halfExtentX);
        assertEquals(0.75f, snapshot.halfExtentY);
        assertEquals(1.25f, snapshot.halfExtentZ);
        assertEquals(BackendRuntimeCodes.AXIS_Y, snapshot.axisCode);
    }

    @Test
    void snapshotBodiesEmitsOnlyKnownRequestedBodies() {
        JoltBackendRuntime runtime = new JoltBackendRuntime(new JoltBackend(), new JoltTestNativeLibrary());
        int spaceId = runtime.createSpace(new SpaceId(22));
        long bodyA = JoltBodyLifecycleTest.createBox(runtime, spaceId, 1.0f);
        long bodyB = JoltBodyLifecycleTest.createBox(runtime, spaceId, 2.0f);
        List<Long> emitted = new ArrayList<>();

        runtime.snapshotBodies(spaceId,
            consumer -> {
                consumer.accept(bodyA);
                consumer.accept(404L);
                consumer.accept(bodyB);
            },
            (bodyId, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _) ->
                emitted.add(bodyId));

        assertEquals(List.of(bodyA, bodyB), emitted);
    }

    @Test
    void missingBodySnapshotReturnsFalse() {
        JoltBackendRuntime runtime = new JoltBackendRuntime(new JoltBackend(), new JoltTestNativeLibrary());
        int spaceId = runtime.createSpace(new SpaceId(23));

        assertFalse(runtime.bodySnapshot(spaceId,
            404L,
            new JoltTestNativeLibrary.CapturedBodySnapshot()));
    }
}
