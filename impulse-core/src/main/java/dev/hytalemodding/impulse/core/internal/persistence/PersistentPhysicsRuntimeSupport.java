package dev.hytalemodding.impulse.core.internal.persistence;

import dev.hytalemodding.impulse.api.PhysicsBody;
import dev.hytalemodding.impulse.api.PhysicsJoint;
import dev.hytalemodding.impulse.api.PhysicsJointType;
import dev.hytalemodding.impulse.api.PhysicsSpace;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsBodyId;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsWorldResource;
import javax.annotation.Nonnull;
import org.joml.Vector3f;

/**
 * Shared helpers for bridging persisted physics state to live runtime objects.
 *
 * <p>Used by the hydration and sync systems to construct joints from persisted
 * body-id endpoint definitions and produce stable keys for deduplicating joints
 * across hydration ticks.</p>
 */
public final class PersistentPhysicsRuntimeSupport {

    private PersistentPhysicsRuntimeSupport() {
    }

    @Nonnull
    public static String jointKey(int spaceId,
        @Nonnull PhysicsBodyId bodyAId,
        @Nonnull PhysicsBodyId bodyBId,
        @Nonnull PhysicsJoint joint) {
        return PersistentPhysicsJointState.from(spaceId, bodyAId, bodyBId, joint).key();
    }

    @Nonnull
    public static PhysicsJoint createJoint(@Nonnull PhysicsSpace space,
        @Nonnull PersistentPhysicsJointState state,
        @Nonnull PhysicsBody bodyA,
        @Nonnull PhysicsBody bodyB) {
        Vector3f anchorA = new Vector3f(state.getAnchorA());
        Vector3f anchorB = new Vector3f(state.getAnchorB());
        Vector3f axis = state.getAxis() != null ? new Vector3f(state.getAxis()) : new Vector3f(0.0f, 1.0f, 0.0f);
        PhysicsJoint joint = switch (state.getType()) {
            case FIXED -> space.createFixedJoint(bodyA, bodyB, anchorA, anchorB);
            case POINT -> space.createPointJoint(bodyA, bodyB, anchorA, anchorB);
            case HINGE -> space.createHingeJoint(bodyA, bodyB, anchorA, anchorB, axis);
            case SLIDER -> space.createSliderJoint(bodyA, bodyB, anchorA, anchorB, axis);
            case SPRING -> space.createSpringJoint(bodyA,
                bodyB,
                anchorA,
                anchorB,
                state.getSpringRestLength(),
                state.getSpringStiffness(),
                state.getSpringDamping());
        };
        if (state.getType() == PhysicsJointType.HINGE || state.getType() == PhysicsJointType.SLIDER) {
            joint.setLimits(state.getLowerLimit(), state.getUpperLimit());
            joint.setMotor(state.getMotorTargetVelocity(), state.getMotorMaxForce());
            joint.setMotorEnabled(state.isMotorEnabled());
        }
        joint.setEnabled(state.isEnabled());
        return joint;
    }
}
