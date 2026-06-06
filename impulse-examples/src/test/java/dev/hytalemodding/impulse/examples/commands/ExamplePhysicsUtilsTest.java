package dev.hytalemodding.impulse.examples.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.hytalemodding.impulse.core.plugin.simulation.PhysicsShapeSpec;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

class ExamplePhysicsUtilsTest {

    @Test
    void physicsBodyCenterConvertsBackToVisualBasePosition() {
        Vector3d visualPosition = ExamplePhysicsOriginMath.visualPositionFromBodyCenter(new Vector3d(1.0, 2.5, 3.0),
            PhysicsShapeSpec.box(0.5f, 0.5f, 0.5f));

        assertEquals(1.0, visualPosition.x, 0.0001);
        assertEquals(2.0, visualPosition.y, 0.0001);
        assertEquals(3.0, visualPosition.z, 0.0001);
    }
}
