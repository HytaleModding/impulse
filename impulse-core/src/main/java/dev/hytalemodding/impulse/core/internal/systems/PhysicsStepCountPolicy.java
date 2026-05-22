package dev.hytalemodding.impulse.core.internal.systems;

import dev.hytalemodding.impulse.core.plugin.resources.PhysicsStepMode;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsWorldResource;
import javax.annotation.Nonnull;

/**
 * Resolves world-level substep counts from the configured step mode.
 *
 * <p>Fixed-style modes use the configured count directly. Adaptive-style modes
 * treat it as a minimum and raise the count when the tick dt exceeds the
 * configured max substep dt. The worker step command layers body-risk
 * refinement on top for {@link PhysicsStepMode#ADAPTIVE}.</p>
 */
public final class PhysicsStepCountPolicy {

    private PhysicsStepCountPolicy() {
    }

    /**
     * Resolves the count used by modes that do not inspect individual bodies.
     */
    public static int resolveStepCount(float dt,
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

    /**
     * Raises the minimum count enough to keep each adaptive substep at or below
     * {@code maxStepDt}, falling back to the default threshold when configured
     * with a non-positive value.
     */
    public static int resolveMaxStepCount(float dt, int minimumSteps, float maxStepDt) {
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
        return Math.clamp(steps, PhysicsWorldResource.MIN_SIMULATION_STEPS,
            PhysicsWorldResource.MAX_SIMULATION_STEPS);
    }
}
