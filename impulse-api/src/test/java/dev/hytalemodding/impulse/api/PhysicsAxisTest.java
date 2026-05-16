package dev.hytalemodding.impulse.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class PhysicsAxisTest {

    @Test
    void resolvesKnownAxisIndexes() {
        assertEquals(PhysicsAxis.X, PhysicsAxis.fromIndex(0));
        assertEquals(PhysicsAxis.Y, PhysicsAxis.fromIndex(1));
        assertEquals(PhysicsAxis.Z, PhysicsAxis.fromIndex(2));
    }

    @Test
    void rejectsUnknownAxisIndexes() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> PhysicsAxis.fromIndex(9));

        assertEquals("Unknown physics axis index: 9", exception.getMessage());
    }
}
