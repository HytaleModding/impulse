package dev.hytalemodding.impulse.api;

/**
 * Optional backend extension for tuning dynamic-body sleep behavior.
 */
public interface PhysicsActivationTuning {

    void setDynamicSleepTuning(float linearThreshold,
        float angularThreshold,
        float timeUntilSleep);
}
