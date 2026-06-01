package dev.hytalemodding.impulse.core.internal.persistence;

import dev.hytalemodding.impulse.api.PhysicsJointType;
import dev.hytalemodding.impulse.api.runtime.BackendRuntimeCodes;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsSpaceBinding;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsWorldRuntimeResource;
import dev.hytalemodding.impulse.core.internal.resources.body.PhysicsBodyRegistration;
import dev.hytalemodding.impulse.core.internal.resources.joint.PhysicsJointRegistration;
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import dev.hytalemodding.impulse.core.plugin.joint.JointKey;
import dev.hytalemodding.impulse.core.plugin.simulation.JointType;
import javax.annotation.Nonnull;
import org.joml.Vector3f;

/**
 * Shared helpers for bridging persisted physics state to live runtime objects.
 *
 * <p>Used by the hydration and sync systems to construct joints from persisted
 * body-key endpoint definitions and produce stable keys for deduplicating joints
 * across hydration ticks.</p>
 */
public final class PersistentPhysicsRuntimeSupport {

    private PersistentPhysicsRuntimeSupport() {
    }

    @Nonnull
    public static String jointKey(int spaceId,
        @Nonnull RigidBodyKey bodyAId,
        @Nonnull RigidBodyKey bodyBId,
        @Nonnull PhysicsJointRegistration joint) {
        return PersistentPhysicsJointState.from(spaceId, bodyAId, bodyBId, joint).key();
    }

    @Nonnull
    public static JointKey createJoint(@Nonnull PhysicsWorldRuntimeResource runtime,
        @Nonnull PhysicsSpaceBinding space,
        @Nonnull PersistentPhysicsJointState state,
        @Nonnull RigidBodyKey bodyAKey,
        @Nonnull PhysicsBodyRegistration bodyA,
        @Nonnull RigidBodyKey bodyBKey,
        @Nonnull PhysicsBodyRegistration bodyB) {
        JointType type = toRuntimeJointType(state.getType());
        Vector3f anchorA = new Vector3f(state.getAnchorA());
        Vector3f anchorB = new Vector3f(state.getAnchorB());
        Vector3f axis = state.getType() == PhysicsJointType.HINGE || state.getType() == PhysicsJointType.SLIDER
            ? requireAxis(state)
            : new Vector3f();
        long backendJointId = space.runtime().createJoint(space.backendSpaceId(),
            toRuntimeJointTypeCode(state.getType()),
            bodyA.backendBodyId(),
            bodyB.backendBodyId(),
            anchorA.x,
            anchorA.y,
            anchorA.z,
            anchorB.x,
            anchorB.y,
            anchorB.z,
            axis.x,
            axis.y,
            axis.z,
            state.getSpringRestLength(),
            state.getSpringStiffness(),
            state.getSpringDamping(),
            state.getLowerLimit(),
            state.getUpperLimit(),
            state.isMotorEnabled(),
            state.getMotorTargetVelocity(),
            state.getMotorMaxForce());
        JointKey jointKey = JointKey.random();
        runtime.addJointOnOwner(jointKey,
            space.spaceId(),
            backendJointId,
            bodyAKey,
            bodyBKey,
            type,
            anchorA.x,
            anchorA.y,
            anchorA.z,
            anchorB.x,
            anchorB.y,
            anchorB.z,
            axis.x,
            axis.y,
            axis.z,
            state.getSpringRestLength(),
            state.getSpringStiffness(),
            state.getSpringDamping(),
            state.getLowerLimit(),
            state.getUpperLimit(),
            state.isMotorEnabled(),
            state.getMotorTargetVelocity(),
            state.getMotorMaxForce());
        return jointKey;
    }

    @Nonnull
    private static Vector3f requireAxis(@Nonnull PersistentPhysicsJointState state) {
        Vector3f axis = state.getAxis();
        if (axis == null) {
            throw new IllegalStateException("Persisted " + state.getType() + " joint requires an axis");
        }
        return new Vector3f(axis);
    }

    private static int toRuntimeJointTypeCode(@Nonnull PhysicsJointType type) {
        return switch (type) {
            case FIXED -> BackendRuntimeCodes.JOINT_FIXED;
            case POINT -> BackendRuntimeCodes.JOINT_POINT;
            case HINGE -> BackendRuntimeCodes.JOINT_HINGE;
            case SLIDER -> BackendRuntimeCodes.JOINT_SLIDER;
            case SPRING -> BackendRuntimeCodes.JOINT_SPRING;
        };
    }

    @Nonnull
    private static JointType toRuntimeJointType(@Nonnull PhysicsJointType type) {
        return switch (type) {
            case FIXED -> JointType.FIXED;
            case POINT -> JointType.POINT;
            case HINGE -> JointType.HINGE;
            case SLIDER -> JointType.SLIDER;
            case SPRING -> JointType.SPRING;
        };
    }
}
