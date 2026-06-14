package dev.hytalemodding.impulse.examples.commands;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hytalemodding.impulse.api.PhysicsContactPhase;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import dev.hytalemodding.impulse.core.plugin.events.PhysicsContactEvent;
import dev.hytalemodding.impulse.core.plugin.events.PhysicsEventFrame;
import dev.hytalemodding.impulse.examples.events.PhysicsEventSummary;
import java.util.List;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class EventsCommandTest {

    @Test
    void formatsLatestContactEventSummary() {
        RigidBodyKey first = RigidBodyKey.of(1L, 2L);
        RigidBodyKey second = RigidBodyKey.of(3L, 4L);
        PhysicsEventFrame frame = new PhysicsEventFrame(12L,
            34L,
            56L,
            78L,
            90L,
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

        String summary = PhysicsEventSummary.format(frame);

        assertTrue(summary.contains("contacts=1"));
        assertTrue(summary.contains("firstContact=observed"));
        assertTrue(summary.contains("space=5"));
        assertTrue(summary.contains("bodyA=" + first));
        assertTrue(summary.contains("bodyB=" + second));
        assertTrue(summary.contains("impulse=2.500"));
        assertTrue(summary.contains("droppedBackendEvents=1"));
    }
}
