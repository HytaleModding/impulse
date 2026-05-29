package dev.hytalemodding.impulse.api.capability;

/**
 * Backend-neutral dynamic body sleep tuning values.
 *
 * @param linearSleepThreshold linear velocity threshold for sleeping
 * @param angularSleepThreshold angular velocity threshold for sleeping
 * @param timeUntilSleep quiet time before a body may sleep
 */
public record PhysicsActivationTuning(float linearSleepThreshold,
                                      float angularSleepThreshold,
                                      float timeUntilSleep) {

    public PhysicsActivationTuning {
        validateNonnegativeFinite(linearSleepThreshold, "linearSleepThreshold");
        validateNonnegativeFinite(angularSleepThreshold, "angularSleepThreshold");
        validateNonnegativeFinite(timeUntilSleep, "timeUntilSleep");
    }

    private static void validateNonnegativeFinite(float value, String name) {
        if (!Float.isFinite(value) || value < 0.0f) {
            throw new IllegalArgumentException(name + " must be finite and nonnegative");
        }
    }
}
