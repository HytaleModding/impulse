package dev.hytalemodding.impulse.api;

/**
 * Optional backend extension for tuning solver cost versus stability.
 *
 * <p>Backends that do not implement this interface keep their native/default
 * solver behavior. Higher-level Impulse settings can still persist the desired
 * values and will apply them when a compatible backend is used.</p>
 */
public interface PhysicsSolverTuning {

    void setSolverTuning(int solverIterations,
        int internalPgsIterations,
        int stabilizationIterations,
        int minIslandSize);
}
