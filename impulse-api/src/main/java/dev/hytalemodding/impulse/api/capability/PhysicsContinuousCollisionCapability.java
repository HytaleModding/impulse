package dev.hytalemodding.impulse.api.capability;

/**
 * Optional backend capability indicating support for continuous collision detection.
 */
public interface PhysicsContinuousCollisionCapability extends PhysicsCapability {

    PhysicsCapabilityDescriptor DESCRIPTOR = new PhysicsCapabilityDescriptor(
        new PhysicsCapabilityId("impulse:continuous_collision"),
        "Continuous collision",
        "Supports continuous collision detection on bodies");
}
