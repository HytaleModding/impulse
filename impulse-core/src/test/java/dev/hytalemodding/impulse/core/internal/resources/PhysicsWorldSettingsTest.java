package dev.hytalemodding.impulse.core.internal.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.hytalemodding.impulse.core.plugin.settings.PhysicsEventCollectionMode;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsStepMode;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsStepSchedulingMode;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsWorldSettings;
import org.junit.jupiter.api.Test;

class PhysicsWorldSettingsTest {

    @Test
    void defaultsDescribeWorldSimulationPolicy() {
        PhysicsWorldSettings settings = new PhysicsWorldSettings();

        assertEquals(PhysicsWorldSettings.MIN_SIMULATION_STEPS, settings.getSimulationSteps());
        assertEquals(PhysicsStepMode.PROGRESSIVE_REFINEMENT, settings.getStepMode());
        assertEquals(PhysicsWorldSettings.DEFAULT_STEP_SCHEDULING_MODE,
            settings.getStepSchedulingMode());
        assertEquals(PhysicsWorldSettings.DEFAULT_EVENT_COLLECTION_MODE,
            settings.getEventCollectionMode());
        assertEquals(PhysicsWorldSettings.DEFAULT_MAX_STEP_DT, settings.getMaxStepDt(), 0.0001f);
    }

    @Test
    void copyConstructorKeepsIndependentValues() {
        PhysicsWorldSettings original = new PhysicsWorldSettings();
        original.setSimulationSteps(4);
        original.setStepMode(PhysicsStepMode.FIXED);
        original.setStepSchedulingMode(PhysicsStepSchedulingMode.ACCUMULATE_PENDING_DT);
        original.setEventCollectionMode(PhysicsEventCollectionMode.CONTACTS);
        original.setMaxStepDt(0.02f);

        PhysicsWorldSettings copy = new PhysicsWorldSettings(original);
        original.setSimulationSteps(1);
        original.setStepMode(PhysicsStepMode.PROGRESSIVE_REFINEMENT);
        original.setStepSchedulingMode(PhysicsStepSchedulingMode.DROP_PENDING_DT);
        original.setEventCollectionMode(PhysicsEventCollectionMode.DISABLED);
        original.setMaxStepDt(0.05f);

        assertEquals(4, copy.getSimulationSteps());
        assertEquals(PhysicsStepMode.FIXED, copy.getStepMode());
        assertEquals(PhysicsStepSchedulingMode.ACCUMULATE_PENDING_DT,
            copy.getStepSchedulingMode());
        assertEquals(PhysicsEventCollectionMode.CONTACTS, copy.getEventCollectionMode());
        assertEquals(0.02f, copy.getMaxStepDt(), 0.0001f);
    }

    @Test
    void rejectsInvalidSimulationValues() {
        PhysicsWorldSettings settings = new PhysicsWorldSettings();

        assertThrows(IllegalArgumentException.class, () -> settings.setSimulationSteps(0));
        assertThrows(IllegalArgumentException.class, () -> settings.setSimulationSteps(
            PhysicsWorldSettings.MAX_SIMULATION_STEPS + 1));
        assertThrows(IllegalArgumentException.class, () -> settings.setMaxStepDt(0.0f));
        assertThrows(IllegalArgumentException.class, () -> settings.setMaxStepDt(Float.NaN));
    }
}
