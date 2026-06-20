package dev.hytalemodding.impulse.jolt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.api.runtime.BackendRuntimeCodes;
import org.junit.jupiter.api.Test;

class JoltNativeAbiIntegrationTest {

    @Test
    void nativeLibraryRunsSpaceAndBodyAbi() {
        JoltBackendRuntime runtime = new JoltBackendRuntime(new JoltBackend());
        int spaceId = runtime.createSpace(new SpaceId(41));
        runtime.setGravity(spaceId, 0.0f, -10.0f, 0.0f);

        long bodyId = runtime.createBody(spaceId,
            BackendRuntimeCodes.SHAPE_SPHERE,
            0.0f,
            0.0f,
            0.0f,
            0.5f,
            0.0f,
            BackendRuntimeCodes.AXIS_Y,
            0.0f,
            2.0f,
            BackendRuntimeCodes.BODY_DYNAMIC,
            0.0f,
            10.0f,
            0.0f,
            0.0f,
            0.0f,
            0.0f,
            1.0f);

        runtime.setBodyFriction(spaceId, bodyId, 0.4f);
        runtime.setBodyRestitution(spaceId, bodyId, 0.2f);
        runtime.setBodyCollisionFilter(spaceId, bodyId, 3, 7);
        runtime.setBodySensor(spaceId, bodyId, true);
        runtime.setBodyVelocity(spaceId, bodyId, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f);
        runtime.step(spaceId, 0.1f);

        JoltTestNativeLibrary.CapturedBodySnapshot snapshot =
            new JoltTestNativeLibrary.CapturedBodySnapshot();
        assertTrue(runtime.bodySnapshot(spaceId, bodyId, snapshot));
        assertEquals(bodyId, snapshot.bodyId);
        assertEquals(BackendRuntimeCodes.SHAPE_SPHERE, snapshot.shapeTypeCode);
        assertEquals(BackendRuntimeCodes.BODY_DYNAMIC, snapshot.bodyTypeCode);
        assertEquals(0.5f, snapshot.radius);
        assertEquals(0.4f, snapshot.friction);
        assertEquals(0.2f, snapshot.restitution);
        assertEquals(3, snapshot.collisionGroup);
        assertEquals(7, snapshot.collisionMask);
        assertTrue(snapshot.sensor);
        assertTrue(snapshot.positionY < 10.0f);
        assertTrue(snapshot.linearVelocityY < 0.0f);
        assertEquals(1, runtime.bodyCount(spaceId));

        runtime.removeBody(spaceId, bodyId);

        assertFalse(runtime.containsBody(spaceId, bodyId));
        assertEquals(0, runtime.bodyCount(spaceId));
        assertFalse(runtime.bodySnapshot(spaceId,
            bodyId,
            new JoltTestNativeLibrary.CapturedBodySnapshot()));

        runtime.destroySpace(spaceId);

        assertThrows(IllegalArgumentException.class, () -> runtime.bodyCount(spaceId));
    }
}
