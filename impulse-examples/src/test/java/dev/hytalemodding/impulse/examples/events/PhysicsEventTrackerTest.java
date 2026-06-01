package dev.hytalemodding.impulse.examples.events;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hytalemodding.impulse.api.PhysicsContactPhase;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import dev.hytalemodding.impulse.core.plugin.events.PhysicsContactEvent;
import dev.hytalemodding.impulse.core.plugin.events.PhysicsEventFrame;
import java.util.List;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class PhysicsEventTrackerTest {

    @Test
    void tracksContactEventsFromPublishedFrames() {
        PhysicsEventTracker.reset();
        RigidBodyKey first = RigidBodyKey.of(1L, 2L);
        RigidBodyKey second = RigidBodyKey.of(3L, 4L);
        PhysicsEventFrame frame = new PhysicsEventFrame(12L,
            34L,
            56L,
            78L,
            90L,
            11L,
            List.of(),
            List.of(),
            List.of(),
            List.of(new PhysicsContactEvent(new SpaceId(5),
                PhysicsContactPhase.OBSERVED,
                first,
                second,
                new Vector3f(1.0f, 2.0f, 3.0f),
                new Vector3f(4.0f, 5.0f, 6.0f),
                new Vector3f(0.0f, 1.0f, 0.0f),
                -0.125f,
                2.5f)),
            1);

        PhysicsEventTracker.record(frame);

        String snapshot = PhysicsEventTracker.snapshot();
        assertTrue(snapshot.contains("trackedFrames=1"));
        assertTrue(snapshot.contains("trackedPhysicsEvents=1"));
        assertTrue(snapshot.contains("trackedContacts=1"));
        assertTrue(snapshot.contains("firstContact=observed"));
        assertTrue(snapshot.contains("space=5"));
        assertTrue(snapshot.contains("bodyA=" + first));
        assertTrue(snapshot.contains("bodyB=" + second));
        assertTrue(snapshot.contains("droppedBackendEvents=1"));
    }
}
