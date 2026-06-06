package dev.hytalemodding.impulse.core.internal.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import dev.hytalemodding.impulse.core.plugin.joint.JointKey;
import org.junit.jupiter.api.Test;

class PhysicsCommandOperationsTest {

    @Test
    void requiredObjectAtReturnsTypedSlotValue() {
        PhysicsCommandOperations operations = new PhysicsCommandOperations(1);
        RigidBodyKey bodyKey = RigidBodyKey.random();
        operations.addDestroyBody(bodyKey);

        RigidBodyKey value = operations.requiredObjectAt(0, 0, RigidBodyKey.class);

        assertSame(bodyKey, value);
    }

    @Test
    void requiredObjectAtRejectsMissingSlotValue() {
        PhysicsCommandOperations operations = new PhysicsCommandOperations(1);
        operations.addDestroyJointBetween(null,
            new SpaceId(1),
            RigidBodyKey.random(),
            RigidBodyKey.random());

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> operations.requiredObjectAt(0, 0, JointKey.class));

        assertEquals("Missing physics command object at index=0 slot=0 type=JointKey",
            exception.getMessage());
    }
}
