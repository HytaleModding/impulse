package dev.hytalemodding.impulse.api.capability;

import javax.annotation.Nonnull;

/**
 * Optional backend capability for tuning solver cost versus stability.
 */
@Deprecated(forRemoval = true)
public interface PhysicsSolverTuningCapability extends PhysicsCapability {

    PhysicsCapabilityDescriptor DESCRIPTOR = new PhysicsCapabilityDescriptor(
        new PhysicsCapabilityId("impulse:solver_tuning"),
        "Solver tuning",
        "Configures solver and stabilization iterations");

    void setSolverTuning(@Nonnull PhysicsSolverTuning tuning);
}
