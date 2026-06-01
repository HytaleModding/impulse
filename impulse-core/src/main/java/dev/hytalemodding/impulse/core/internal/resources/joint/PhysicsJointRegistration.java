package dev.hytalemodding.impulse.core.internal.resources.joint;

import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.internal.resources.BackendJointHandle;
import dev.hytalemodding.impulse.core.plugin.joint.JointKey;
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import dev.hytalemodding.impulse.core.plugin.simulation.JointType;
import javax.annotation.Nonnull;

/**
 * Owner-lane registration for a backend joint id.
 */
public record PhysicsJointRegistration(@Nonnull JointKey jointKey,
    @Nonnull BackendJointHandle backendJointHandle,
    @Nonnull SpaceId spaceId,
    @Nonnull RigidBodyKey bodyA,
    @Nonnull RigidBodyKey bodyB,
    @Nonnull JointType type,
    float anchorAX,
    float anchorAY,
    float anchorAZ,
    float anchorBX,
    float anchorBY,
    float anchorBZ,
    float axisX,
    float axisY,
    float axisZ,
    float restLength,
    float stiffness,
    float damping,
    float lowerLimit,
    float upperLimit,
    boolean motorEnabled,
    float motorTargetVelocity,
    float motorMaxForce) {
}
