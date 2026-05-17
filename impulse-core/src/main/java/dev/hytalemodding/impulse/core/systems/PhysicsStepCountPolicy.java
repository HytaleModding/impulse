package dev.hytalemodding.impulse.core.systems;

import dev.hytalemodding.impulse.core.resources.PhysicsStepMode;
import dev.hytalemodding.impulse.core.resources.PhysicsWorldResource;
import javax.annotation.Nonnull;

final class PhysicsStepCountPolicy {

    private PhysicsStepCountPolicy() {
    }

    static int resolveStepCount(float dt,
        int simulationSteps,
        float maxStepDt,
        @Nonnull PhysicsStepMode stepMode) {
        int minimumSteps = clampStepCount(simulationSteps);
        return switch (stepMode) {
            case FIXED, CCD -> minimumSteps;
            case ADAPTIVE, PROGRESSIVE_REFINEMENT -> resolveMaxStepCount(dt,
                minimumSteps,
                maxStepDt);
        };
    }

    static int resolveMaxStepCount(float dt, int minimumSteps, float maxStepDt) {
        int baseSteps = clampStepCount(minimumSteps);
        float safeDt = Math.max(dt, 0.0f);
        if (safeDt <= 0.0f) {
            return baseSteps;
        }

        float safeMaxStepDt = maxStepDt > 0f ? maxStepDt : PhysicsWorldResource.DEFAULT_MAX_STEP_DT;
        int steps = Math.max(baseSteps, (int) Math.ceil(safeDt / safeMaxStepDt));
        return clampStepCount(steps);
    }

    private static int clampStepCount(int steps) {
        return Math.max(PhysicsWorldResource.MIN_SIMULATION_STEPS,
            Math.min(steps, PhysicsWorldResource.MAX_SIMULATION_STEPS));
    }
}
