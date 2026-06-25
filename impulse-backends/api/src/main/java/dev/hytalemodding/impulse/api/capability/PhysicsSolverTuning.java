package dev.hytalemodding.impulse.api.capability;

/**
 * Backend-neutral solver tuning values.
 *
 * @param solverIterations primary solver iteration count
 * @param stabilizationIterations stabilization pass iteration count
 */
public record PhysicsSolverTuning(int solverIterations, int stabilizationIterations) {

    public PhysicsSolverTuning {
        if (solverIterations <= 0) {
            throw new IllegalArgumentException("solverIterations must be positive");
        }
        if (stabilizationIterations < 0) {
            throw new IllegalArgumentException("stabilizationIterations cannot be negative");
        }
    }
}
