package dev.hytalemodding.impulse.api.capability;

import javax.annotation.Nonnull;

/**
 * Optional backend capability for tuning dynamic body sleep behavior.
 */
public interface PhysicsActivationTuningCapability extends PhysicsCapability {

    PhysicsCapabilityDescriptor DESCRIPTOR = new PhysicsCapabilityDescriptor(
        new PhysicsCapabilityId("impulse:activation_tuning"),
        "Activation tuning",
        "Configures dynamic body sleep thresholds");

    void setActivationTuning(@Nonnull PhysicsActivationTuning tuning);
}
