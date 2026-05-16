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
        return switch (stepMode) {
            case FIXED, CCD -> simulationSteps;
            case PROGRESSIVE_REFINEMENT -> {
                float safeMaxStepDt = maxStepDt > 0f ? maxStepDt : PhysicsWorldResource.DEFAULT_MAX_STEP_DT;
                int steps = Math.max(simulationSteps, (int) Math.ceil(dt / safeMaxStepDt));
                yield Math.min(steps, PhysicsWorldResource.MAX_SIMULATION_STEPS);
            }
        };
    }
}
