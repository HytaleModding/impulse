package dev.hytalemodding.impulse.core.plugin.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.core.internal.simulation.recorder.MutablePhysicsCommandContext;
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import java.util.UUID;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

class PhysicsCommandBufferTest {

    @Test
    void bufferRecordsOperationsIntoExistingCommandContext() {
        RigidBodyKey bodyKey = RigidBodyKey.of(UUID.fromString("00000000-0000-0000-0000-000000000031"));
        MutablePhysicsCommandContext context = new MutablePhysicsCommandContext(88L, 12L, 5);
        PhysicsCommandBuffer buffer = PhysicsCommandBuffer.recording(context);

        buffer.applyImpulse(bodyKey, new Vector3d(1.0, 2.0, 3.0))
            .applyForce(bodyKey, new Vector3d(4.0, 5.0, 6.0))
            .setVelocity(bodyKey, new Vector3d(7.0, 8.0, 9.0), new Vector3d(0.1, 0.2, 0.3))
            .setType(bodyKey, PhysicsBodyType.KINEMATIC)
            .destroyBody(bodyKey);

        PhysicsCommandBatch batch = buffer.freezeForTesting(44L);

        assertEquals(88L, batch.metadata().submittedServerTick());
        assertEquals(44L, batch.metadata().commandBatchSequence());
        assertEquals(5, batch.commandCount());
    }

    @Test
    void bufferCannotRecordAfterSubmitOrFreeze() {
        RigidBodyKey bodyKey = RigidBodyKey.of(UUID.fromString("00000000-0000-0000-0000-000000000032"));
        PhysicsCommandBuffer buffer = PhysicsCommandBuffer.recording(
            new MutablePhysicsCommandContext(89L, 12L, 1));

        buffer.destroyBody(bodyKey);
        buffer.freezeForTesting(45L);

        assertThrows(IllegalStateException.class,
            () -> buffer.applyImpulse(bodyKey, new Vector3d(1.0, 0.0, 0.0)));
    }
}
