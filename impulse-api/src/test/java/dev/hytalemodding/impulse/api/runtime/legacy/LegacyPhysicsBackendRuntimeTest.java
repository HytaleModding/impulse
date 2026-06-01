package dev.hytalemodding.impulse.api.runtime.legacy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hytalemodding.impulse.api.PhysicsAxis;
import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.ShapeType;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.api.runtime.BackendBodySpec;
import dev.hytalemodding.impulse.api.runtime.BackendBodySnapshot;
import dev.hytalemodding.impulse.api.runtime.BackendJointSpec;
import dev.hytalemodding.impulse.api.runtime.BackendJointType;
import dev.hytalemodding.impulse.api.testsupport.FakePhysicsBackend;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class LegacyPhysicsBackendRuntimeTest {

    @Test
    void wrapsLegacyBackendWithNumericBodyAndJointIds() {
        LegacyPhysicsBackendRuntime runtime =
            new LegacyPhysicsBackendRuntime(new FakePhysicsBackend("impulse:test"));

        int spaceId = runtime.createSpace(new SpaceId(9001));
        long bodyA = runtime.createBody(spaceId,
            BackendBodySpec.sphere(0.5f, 1.0f, PhysicsBodyType.DYNAMIC, 0.0f, 3.0f, 0.0f));
        long bodyB = runtime.createBody(spaceId,
            BackendBodySpec.box(0.5f,
                0.5f,
                0.5f,
                1.0f,
                PhysicsBodyType.DYNAMIC,
                1.0f,
                3.0f,
                0.0f));
        long joint = runtime.createJoint(spaceId,
            BackendJointSpec.point(bodyA, bodyB, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f));

        assertEquals(9001, spaceId);
        assertNotEquals(bodyA, bodyB);
        assertTrue(bodyA > 0L);
        assertTrue(bodyB > 0L);
        assertTrue(joint > 0L);
        assertEquals(2, runtime.bodyCount(spaceId));
        assertEquals(1, runtime.jointCount(spaceId));

        Optional<BackendBodySnapshot> snapshot = runtime.bodySnapshot(spaceId, bodyA);

        assertTrue(snapshot.isPresent());
        assertEquals(bodyA, snapshot.orElseThrow().bodyId());
        assertEquals(ShapeType.SPHERE, snapshot.orElseThrow().shapeType());
        assertEquals(PhysicsAxis.Y, snapshot.orElseThrow().shapeAxis());
        assertEquals(BackendJointType.POINT, runtime.jointType(spaceId, joint));
        assertEquals(bodyA, runtime.jointBodyA(spaceId, joint));
        assertEquals(bodyB, runtime.jointBodyB(spaceId, joint));
    }
}
