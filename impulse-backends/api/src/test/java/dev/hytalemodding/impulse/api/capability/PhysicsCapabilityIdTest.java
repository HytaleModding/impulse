package dev.hytalemodding.impulse.api.capability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class PhysicsCapabilityIdTest {

    @Test
    void rejectsBlankValues() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> new PhysicsCapabilityId("   "));

        assertEquals("PhysicsCapabilityId value cannot be blank", exception.getMessage());
    }

    @Test
    void toStringReturnsUnderlyingValue() {
        PhysicsCapabilityId id = new PhysicsCapabilityId("impulse:solver_tuning");

        assertEquals("impulse:solver_tuning", id.toString());
    }
}
