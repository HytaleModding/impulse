package dev.hytalemodding.impulse.core.systems;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.hytalemodding.impulse.core.resources.PhysicsStepMode;
import dev.hytalemodding.impulse.core.resources.PhysicsWorldResource;
import org.junit.jupiter.api.Test;

class PhysicsStepCountPolicyTest {

    @Test
    void fixedModeUsesConfiguredSimulationSteps() {
        assertEquals(4,
            PhysicsStepCountPolicy.resolveStepCount(0.05f, 4, 0.01f, PhysicsStepMode.FIXED));
    }

    @Test
    void ccdModeUsesConfiguredSimulationSteps() {
        assertEquals(6,
            PhysicsStepCountPolicy.resolveStepCount(0.05f, 6, 0.01f, PhysicsStepMode.CCD));
    }

    @Test
    void progressiveRefinementKeepsMinimumSimulationStepsWhenDtIsSmall() {
        assertEquals(3,
            PhysicsStepCountPolicy.resolveStepCount(0.02f,
                3,
                PhysicsWorldResource.DEFAULT_MAX_STEP_DT,
                PhysicsStepMode.PROGRESSIVE_REFINEMENT));
    }

    @Test
    void progressiveRefinementIncreasesStepCountWhenDtExceedsMaxStepDt() {
        assertEquals(5,
            PhysicsStepCountPolicy.resolveStepCount(0.10f,
                2,
                0.02f,
                PhysicsStepMode.PROGRESSIVE_REFINEMENT));
    }

    @Test
    void progressiveRefinementFallsBackToDefaultMaxStepDtWhenConfiguredValueIsInvalid() {
        assertEquals(3,
            PhysicsStepCountPolicy.resolveStepCount(0.12f,
                1,
                0.0f,
                PhysicsStepMode.PROGRESSIVE_REFINEMENT));
    }

    @Test
    void progressiveRefinementCapsAtMaximumSimulationSteps() {
        assertEquals(PhysicsWorldResource.MAX_SIMULATION_STEPS,
            PhysicsStepCountPolicy.resolveStepCount(2.0f,
                1,
                0.01f,
                PhysicsStepMode.PROGRESSIVE_REFINEMENT));
    }
}
