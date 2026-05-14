package dev.hytalemodding.impulse.bullet;

import com.jme3.bullet.joints.Constraint;
import com.jme3.bullet.joints.HingeJoint;
import com.jme3.bullet.joints.SliderJoint;
import dev.hytalemodding.impulse.api.PhysicsBody;
import dev.hytalemodding.impulse.api.PhysicsJoint;
import dev.hytalemodding.impulse.api.PhysicsJointType;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3f;

final class BulletJoint implements PhysicsJoint {

    private final PhysicsJointType type;
    private final BulletBody bodyA;
    private final BulletBody bodyB;
    private final com.jme3.bullet.joints.PhysicsJoint joint;
    private final Vector3f anchorA;
    private final Vector3f anchorB;
    private final Vector3f axis;
    private float lowerLimit;
    private float upperLimit;
    private boolean motorEnabled;
    private float motorTargetVelocity;
    private float motorMaxForce;

    BulletJoint(@Nonnull PhysicsJointType type,
        @Nonnull BulletBody bodyA,
        @Nonnull BulletBody bodyB,
        @Nonnull com.jme3.bullet.joints.PhysicsJoint joint,
        @Nonnull Vector3f anchorA,
        @Nonnull Vector3f anchorB,
        @Nullable Vector3f axis) {
        this.type = type;
        this.bodyA = bodyA;
        this.bodyB = bodyB;
        this.joint = joint;
        this.anchorA = new Vector3f(anchorA);
        this.anchorB = new Vector3f(anchorB);
        this.axis = axis != null ? new Vector3f(axis) : null;
    }

    @Nonnull
    @Override
    public PhysicsJointType getType() {
        return type;
    }

    @Nonnull
    @Override
    public PhysicsBody getBodyA() {
        return bodyA;
    }

    @Nonnull
    @Override
    public PhysicsBody getBodyB() {
        return bodyB;
    }

    @Override
    public boolean isEnabled() {
        return joint.isEnabled();
    }

    @Override
    public void setEnabled(boolean enabled) {
        if (joint instanceof Constraint constraint) {
            constraint.setEnabled(enabled);
        }
    }

    @Nonnull
    @Override
    public Vector3f getAnchorA() {
        return new Vector3f(anchorA);
    }

    @Nonnull
    @Override
    public Vector3f getAnchorB() {
        return new Vector3f(anchorB);
    }

    @Nullable
    @Override
    public Vector3f getAxis() {
        return axis != null ? new Vector3f(axis) : null;
    }

    @Override
    public float getLowerLimit() {
        return lowerLimit;
    }

    @Override
    public float getUpperLimit() {
        return upperLimit;
    }

    @Override
    public void setLimits(float lowerLimit, float upperLimit) {
        this.lowerLimit = lowerLimit;
        this.upperLimit = upperLimit;
        if (joint instanceof HingeJoint hinge) {
            hinge.setLimit(lowerLimit, upperLimit);
        } else if (joint instanceof SliderJoint slider) {
            slider.setLowerLinLimit(lowerLimit);
            slider.setUpperLinLimit(upperLimit);
        }
    }

    @Override
    public boolean isMotorEnabled() {
        return motorEnabled;
    }

    @Override
    public void setMotorEnabled(boolean enabled) {
        motorEnabled = enabled;
        if (joint instanceof HingeJoint hinge) {
            hinge.enableMotor(enabled, motorTargetVelocity, motorMaxForce);
        } else if (joint instanceof SliderJoint slider) {
            slider.setPoweredLinMotor(enabled);
        }
    }

    @Override
    public float getMotorTargetVelocity() {
        return motorTargetVelocity;
    }

    @Override
    public float getMotorMaxForce() {
        return motorMaxForce;
    }

    @Override
    public void setMotor(float targetVelocity, float maxForce) {
        motorTargetVelocity = targetVelocity;
        motorMaxForce = maxForce;
        if (joint instanceof HingeJoint hinge) {
            hinge.enableMotor(motorEnabled, targetVelocity, maxForce);
        } else if (joint instanceof SliderJoint slider) {
            slider.setTargetLinMotorVelocity(targetVelocity);
            slider.setMaxLinMotorForce(maxForce);
        }
    }

    @Nonnull
    com.jme3.bullet.joints.PhysicsJoint getNativeJoint() {
        return joint;
    }
}
