package dev.hytalemodding.impulse.core.internal.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.hytalemodding.impulse.core.plugin.settings.PhysicsStepMode;
import org.junit.jupiter.api.Test;

class PhysicsStepModeTest {

    @Test
    void parsesSerializedNamesCaseInsensitively() {
        assertEquals(PhysicsStepMode.PROGRESSIVE_REFINEMENT,
            PhysicsStepMode.parse(" progressive_refinement "));
        assertEquals(PhysicsStepMode.FIXED, PhysicsStepMode.parse("FIXED"));
        assertEquals(PhysicsStepMode.CCD, PhysicsStepMode.parse("ccd"));
    }

    @Test
    void rejectsUnknownSerializedNames() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> PhysicsStepMode.parse("burst"));

        assertEquals("Unknown physics step mode: burst", exception.getMessage());
    }

    @Test
    void describesSimulationStepsByMode() {
        assertEquals("minimum substeps",
            PhysicsStepMode.PROGRESSIVE_REFINEMENT.describeSimulationSteps());
        assertEquals("fixed substeps", PhysicsStepMode.FIXED.describeSimulationSteps());
        assertEquals("fixed substeps", PhysicsStepMode.CCD.describeSimulationSteps());
    }
}
