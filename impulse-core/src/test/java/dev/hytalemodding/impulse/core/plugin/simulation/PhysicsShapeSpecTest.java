package dev.hytalemodding.impulse.core.plugin.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.hytalemodding.impulse.api.PhysicsAxis;
import org.junit.jupiter.api.Test;

class PhysicsShapeSpecTest {

    @Test
    void centerOfMassOffsetMatchesShapeSupportHeight() {
        assertEquals(0.5f, PhysicsShapeSpec.box(0.5f, 0.5f, 0.5f).centerOfMassOffsetY(), 0.0001f);
        assertEquals(0.75f, PhysicsShapeSpec.sphere(0.75f).centerOfMassOffsetY(), 0.0001f);
        assertEquals(1.25f, PhysicsShapeSpec.capsule(0.25f, 1.0f, PhysicsAxis.Y).centerOfMassOffsetY(),
            0.0001f);
        assertEquals(0.25f, PhysicsShapeSpec.capsule(0.25f, 1.0f, PhysicsAxis.X).centerOfMassOffsetY(),
            0.0001f);
        assertEquals(1.0f, PhysicsShapeSpec.cylinder(0.25f, 1.0f, PhysicsAxis.Y).centerOfMassOffsetY(),
            0.0001f);
        assertEquals(0.25f, PhysicsShapeSpec.cone(0.25f, 1.0f, PhysicsAxis.Z).centerOfMassOffsetY(),
            0.0001f);
    }
}
