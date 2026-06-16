package dev.hytalemodding.impulse.core.internal.resources.joint;

import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.internal.resources.BackendJointHandle;
import dev.hytalemodding.impulse.core.plugin.joint.JointKey;
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import dev.hytalemodding.impulse.core.plugin.simulation.JointType;
import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nonnull;

/**
 * Store tick registration for a stable joint UUID and backend-local joint handle.
 */
public record PhysicsJointRegistration(@Nonnull UUID jointUuid,
    @Nonnull BackendJointHandle backendJointHandle,
    @Nonnull SpaceId spaceId,
    @Nonnull UUID bodyAUuid,
    @Nonnull UUID bodyBUuid,
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

    public PhysicsJointRegistration {
        Objects.requireNonNull(jointUuid, "jointUuid");
        Objects.requireNonNull(backendJointHandle, "backendJointHandle");
        Objects.requireNonNull(spaceId, "spaceId");
        Objects.requireNonNull(bodyAUuid, "bodyAUuid");
        Objects.requireNonNull(bodyBUuid, "bodyBUuid");
        Objects.requireNonNull(type, "type");
    }

    public PhysicsJointRegistration(@Nonnull JointKey jointKey,
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
        this(Objects.requireNonNull(jointKey, "jointKey").value(),
            backendJointHandle,
            spaceId,
            Objects.requireNonNull(bodyA, "bodyA").value(),
            Objects.requireNonNull(bodyB, "bodyB").value(),
            type,
            anchorAX,
            anchorAY,
            anchorAZ,
            anchorBX,
            anchorBY,
            anchorBZ,
            axisX,
            axisY,
            axisZ,
            restLength,
            stiffness,
            damping,
            lowerLimit,
            upperLimit,
            motorEnabled,
            motorTargetVelocity,
            motorMaxForce);
    }

    @Nonnull
    public JointKey jointKey() {
        return JointKey.of(jointUuid);
    }

    @Nonnull
    public RigidBodyKey bodyA() {
        return RigidBodyKey.of(bodyAUuid);
    }

    @Nonnull
    public RigidBodyKey bodyB() {
        return RigidBodyKey.of(bodyBUuid);
    }
}
