package dev.hytalemodding.impulse.core.plugin.settings;

import javax.annotation.Nonnull;

/**
 * Backend solver tuning for a physics space.
 */
public class PhysicsSolverSettings {

    /**
     * Rapier default solver iterations. Lower values are faster but less stable.
     */
    public static final int DEFAULT_SOLVER_ITERATIONS = 4;

    /**
     * Rapier default internal PGS iterations.
     */
    public static final int DEFAULT_INTERNAL_PGS_ITERATIONS = 1;

    /**
     * Rapier default stabilization iterations.
     */
    public static final int DEFAULT_STABILIZATION_ITERATIONS = 1;

    /**
     * Rapier default minimum active-island size for parallel solver batching.
     */
    public static final int DEFAULT_MIN_ISLAND_SIZE = 128;

    /**
     * Default normalized linear velocity threshold for dynamic-body sleep.
     */
    public static final float DEFAULT_DYNAMIC_SLEEP_LINEAR_THRESHOLD = 0.85f;

    /**
     * Default angular velocity threshold for dynamic-body sleep.
     */
    public static final float DEFAULT_DYNAMIC_SLEEP_ANGULAR_THRESHOLD = 0.9f;

    /**
     * Default time a low-energy dynamic body must remain eligible before sleeping.
     */
    public static final float DEFAULT_DYNAMIC_SLEEP_TIME_UNTIL_SLEEP = 0.75f;

    /**
     * Constraint solver iterations for tunable backends.
     */
    private int solverIterations = DEFAULT_SOLVER_ITERATIONS;

    /**
     * Internal PGS iterations per solver iteration for tunable backends.
     */
    private int internalPgsIterations = DEFAULT_INTERNAL_PGS_ITERATIONS;

    /**
     * Stabilization iterations per solver iteration for tunable backends.
     */
    private int stabilizationIterations = DEFAULT_STABILIZATION_ITERATIONS;

    /**
     * Minimum active island size used by tunable parallel solvers.
     */
    private int minIslandSize = DEFAULT_MIN_ISLAND_SIZE;

    /**
     * Dynamic-body sleep tuning for compatible backends.
     */
    private float dynamicSleepLinearThreshold = DEFAULT_DYNAMIC_SLEEP_LINEAR_THRESHOLD;

    /**
     * Dynamic-body angular sleep tuning for compatible backends.
     */
    private float dynamicSleepAngularThreshold = DEFAULT_DYNAMIC_SLEEP_ANGULAR_THRESHOLD;

    /**
     * Seconds a low-energy dynamic body must remain eligible before sleeping.
     */
    private float dynamicSleepTimeUntilSleep = DEFAULT_DYNAMIC_SLEEP_TIME_UNTIL_SLEEP;

    public PhysicsSolverSettings() {
    }

    public PhysicsSolverSettings(@Nonnull PhysicsSolverSettings settings) {
        solverIterations = settings.solverIterations;
        internalPgsIterations = settings.internalPgsIterations;
        stabilizationIterations = settings.stabilizationIterations;
        minIslandSize = settings.minIslandSize;
        dynamicSleepLinearThreshold = settings.dynamicSleepLinearThreshold;
        dynamicSleepAngularThreshold = settings.dynamicSleepAngularThreshold;
        dynamicSleepTimeUntilSleep = settings.dynamicSleepTimeUntilSleep;
    }

    public int getSolverIterations() {
        return solverIterations;
    }

    public void setSolverIterations(int solverIterations) {
        if (solverIterations < 1) {
            throw new IllegalArgumentException("Solver iterations must be positive");
        }
        this.solverIterations = solverIterations;
    }

    public int getInternalPgsIterations() {
        return internalPgsIterations;
    }

    public void setInternalPgsIterations(int internalPgsIterations) {
        if (internalPgsIterations < 1) {
            throw new IllegalArgumentException("Internal PGS iterations must be positive");
        }
        this.internalPgsIterations = internalPgsIterations;
    }

    public int getStabilizationIterations() {
        return stabilizationIterations;
    }

    public void setStabilizationIterations(int stabilizationIterations) {
        if (stabilizationIterations < 0) {
            throw new IllegalArgumentException("Stabilization iterations cannot be negative");
        }
        this.stabilizationIterations = stabilizationIterations;
    }

    public int getMinIslandSize() {
        return minIslandSize;
    }

    public void setMinIslandSize(int minIslandSize) {
        if (minIslandSize < 1) {
            throw new IllegalArgumentException("Minimum island size must be positive");
        }
        this.minIslandSize = minIslandSize;
    }

    public void setDynamicSleepTuning(float linearThreshold,
        float angularThreshold,
        float timeUntilSleep) {
        setDynamicSleepLinearThreshold(linearThreshold);
        setDynamicSleepAngularThreshold(angularThreshold);
        setDynamicSleepTimeUntilSleep(timeUntilSleep);
    }

    public float getDynamicSleepLinearThreshold() {
        return dynamicSleepLinearThreshold;
    }

    public void setDynamicSleepLinearThreshold(float dynamicSleepLinearThreshold) {
        this.dynamicSleepLinearThreshold = PhysicsSettingsValidation.requireFiniteAtLeast(
            "Dynamic sleep linear threshold",
            dynamicSleepLinearThreshold,
            0.0f);
    }

    public float getDynamicSleepAngularThreshold() {
        return dynamicSleepAngularThreshold;
    }

    public void setDynamicSleepAngularThreshold(float dynamicSleepAngularThreshold) {
        this.dynamicSleepAngularThreshold = PhysicsSettingsValidation.requireFiniteAtLeast(
            "Dynamic sleep angular threshold",
            dynamicSleepAngularThreshold,
            0.0f);
    }

    public float getDynamicSleepTimeUntilSleep() {
        return dynamicSleepTimeUntilSleep;
    }

    public void setDynamicSleepTimeUntilSleep(float dynamicSleepTimeUntilSleep) {
        this.dynamicSleepTimeUntilSleep = PhysicsSettingsValidation.requireFiniteAtLeast(
            "Dynamic sleep time",
            dynamicSleepTimeUntilSleep,
            0.0f);
    }

}
