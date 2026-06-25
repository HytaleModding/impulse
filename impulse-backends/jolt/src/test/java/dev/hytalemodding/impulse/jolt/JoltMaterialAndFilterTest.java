package dev.hytalemodding.impulse.jolt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hytalemodding.impulse.api.SpaceId;
import org.junit.jupiter.api.Test;

class JoltMaterialAndFilterTest {

    @Test
    void materialFilterSensorAndActivationRoundTripThroughSnapshot() {
        JoltBackendRuntime runtime = new JoltBackendRuntime(new JoltBackend(), new JoltTestNativeLibrary());
        int spaceId = runtime.createSpace(new SpaceId(31));
        long bodyId = JoltBodyLifecycleTest.createBox(runtime, spaceId, 1.0f);

        runtime.setBodyDamping(spaceId, bodyId, 0.2f, 0.3f);
        runtime.setBodyFriction(spaceId, bodyId, 0.65f);
        runtime.setBodyRestitution(spaceId, bodyId, 0.15f);
        runtime.setBodyCollisionFilter(spaceId, bodyId, 2, 3);
        runtime.setBodySensor(spaceId, bodyId, true);
        runtime.setBodyContinuousCollision(spaceId, bodyId, true);
        runtime.sleepBody(spaceId, bodyId);
        runtime.activateBody(spaceId, bodyId);
        runtime.applyBodyImpulse(spaceId, bodyId, 1.0f, 0.0f, 0.0f, false, 0.0f, 0.0f, 0.0f, false);
        runtime.applyBodyForce(spaceId, bodyId, 0.0f, 1.0f, 0.0f, false, 0.0f, 0.0f, 0.0f, false);

        JoltTestNativeLibrary.CapturedBodySnapshot snapshot =
            new JoltTestNativeLibrary.CapturedBodySnapshot();
        runtime.bodySnapshot(spaceId, bodyId, snapshot);

        assertEquals(0.2f, snapshot.linearDamping);
        assertEquals(0.3f, snapshot.angularDamping);
        assertEquals(0.65f, snapshot.friction);
        assertEquals(0.15f, snapshot.restitution);
        assertEquals(2, snapshot.collisionGroup);
        assertEquals(3, snapshot.collisionMask);
        assertTrue(snapshot.sensor);
        assertTrue(snapshot.continuousCollisionEnabled);
        assertTrue(runtime.isBodyContinuousCollisionEnabled(spaceId, bodyId));
        assertFalse(snapshot.sleeping);
    }
}
