package dev.hytalemodding.impulse.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hytalemodding.impulse.api.testsupport.FakePhysicsBackend;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class PhysicsBackendEventBufferTest {

    @Test
    void buffersCopiedContactEventsUntilCapacityAndReportsDrops() {
        FakePhysicsBackend backend = new FakePhysicsBackend("test:event-buffer");
        PhysicsSpace space = backend.createSpace();
        PhysicsBody first = space.createBox(0.5f, 0.5f, 0.5f, 1.0f);
        PhysicsBody second = space.createBox(0.5f, 0.5f, 0.5f, 1.0f);
        Vector3f pointOnA = new Vector3f(1.0f, 2.0f, 3.0f);
        Vector3f pointOnB = new Vector3f(4.0f, 5.0f, 6.0f);
        Vector3f normalOnB = new Vector3f(0.0f, 1.0f, 0.0f);
        PhysicsBackendEventBuffer buffer = new PhysicsBackendEventBuffer(1);

        assertTrue(buffer.contact(PhysicsContactPhase.OBSERVED,
            first,
            second,
            pointOnA,
            pointOnB,
            normalOnB,
            -0.125f,
            2.5f));
        pointOnA.set(7.0f, 8.0f, 9.0f);
        assertFalse(buffer.contact(PhysicsContactPhase.OBSERVED,
            first,
            second,
            pointOnA,
            pointOnB,
            normalOnB,
            -0.25f,
            3.5f));

        PhysicsBackendEventBatch batch = buffer.drain();

        assertEquals(1, batch.size());
        assertEquals(1, batch.droppedEventCount());
        PhysicsBackendContactEvent event = (PhysicsBackendContactEvent) batch.events().getFirst();
        assertEquals(PhysicsBackendEventKind.CONTACT_OBSERVED, event.kind());
        assertEquals(PhysicsContactPhase.OBSERVED, event.phase());
        assertEquals(first, event.bodyA());
        assertEquals(second, event.bodyB());
        assertEquals(new Vector3f(1.0f, 2.0f, 3.0f), event.pointOnA());
        assertEquals(new Vector3f(4.0f, 5.0f, 6.0f), event.pointOnB());
        assertEquals(new Vector3f(0.0f, 1.0f, 0.0f), event.normalOnB());
        assertEquals(-0.125f, event.distance());
        assertEquals(2.5f, event.impulse());
        assertTrue(buffer.drain().isEmpty());
    }
}
