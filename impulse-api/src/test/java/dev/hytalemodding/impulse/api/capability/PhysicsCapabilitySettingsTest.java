package dev.hytalemodding.impulse.api.capability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class PhysicsCapabilitySettingsTest {

    @Test
    void descriptorRejectsBlankDisplayName() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> new PhysicsCapabilityDescriptor(new PhysicsCapabilityId("impulse:test"), " ", "Test capability"));

        assertEquals("displayName cannot be blank", exception.getMessage());
    }

    @Test
    void descriptorRejectsBlankDescription() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> new PhysicsCapabilityDescriptor(new PhysicsCapabilityId("impulse:test"), "Test", " "));

        assertEquals("description cannot be blank", exception.getMessage());
    }

    @Test
    void rejectsNonPositiveSolverIterations() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> new PhysicsSolverTuning(0, 1));

        assertEquals("solverIterations must be positive", exception.getMessage());
    }

    @Test
    void rejectsNegativeStabilizationIterations() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> new PhysicsSolverTuning(1, -1));

        assertEquals("stabilizationIterations cannot be negative", exception.getMessage());
    }

    @Test
    void rejectsNegativeActivationThresholds() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> new PhysicsActivationTuning(-0.1f, 0.2f, 1.0f));

        assertEquals("linearSleepThreshold must be finite and nonnegative", exception.getMessage());
    }

    @Test
    void rejectsNonFiniteActivationSleepTime() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> new PhysicsActivationTuning(0.1f, 0.2f, Float.POSITIVE_INFINITY));

        assertEquals("timeUntilSleep must be finite and nonnegative", exception.getMessage());
    }
}
