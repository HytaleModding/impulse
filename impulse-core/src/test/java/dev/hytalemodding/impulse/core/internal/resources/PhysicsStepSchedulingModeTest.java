package dev.hytalemodding.impulse.core.internal.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.hytalemodding.impulse.core.plugin.settings.PhysicsStepSchedulingMode;
import org.junit.jupiter.api.Test;

class PhysicsStepSchedulingModeTest {

    @Test
    void parsesSerializedNamesCaseInsensitively() {
        assertEquals(PhysicsStepSchedulingMode.DROP_PENDING_DT,
            PhysicsStepSchedulingMode.parse(" drop_pending_dt "));
        assertEquals(PhysicsStepSchedulingMode.ACCUMULATE_PENDING_DT,
            PhysicsStepSchedulingMode.parse("ACCUMULATE_PENDING_DT"));
    }

    @Test
    void rejectsUnknownSerializedNames() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> PhysicsStepSchedulingMode.parse("burst"));

        assertEquals("Unknown physics step scheduling mode: burst", exception.getMessage());
    }

    @Test
    void describesPendingStepBehavior() {
        assertEquals("drop dt while an owner step is pending",
            PhysicsStepSchedulingMode.DROP_PENDING_DT.describePendingStepBehavior());
        assertEquals("accumulate pending dt for one capped catch-up step",
            PhysicsStepSchedulingMode.ACCUMULATE_PENDING_DT.describePendingStepBehavior());
    }
}
