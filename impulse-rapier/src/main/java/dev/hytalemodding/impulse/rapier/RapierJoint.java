package dev.hytalemodding.impulse.rapier;

import dev.hytalemodding.impulse.api.PhysicsBody;
import dev.hytalemodding.impulse.api.PhysicsJoint;
import dev.hytalemodding.impulse.api.PhysicsJointType;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3f;

final class RapierJoint implements PhysicsJoint {

    private final RapierSpace space;
    private final PhysicsJointType type;
    private final RapierBody bodyA;
    private final RapierBody bodyB;
    private final long jointHandle;
    private final Vector3f anchorA;
    private final Vector3f anchorB;
    private final Vector3f axis;
    private float lowerLimit;
    private float upperLimit;
    private boolean motorEnabled;
    private float motorTargetVelocity;
    private float motorMaxForce;
    private final float springRestLength;
    private final float springStiffness;
    private final float springDamping;

    RapierJoint(@Nonnull RapierSpace space,
        @Nonnull PhysicsJointType type,
        @Nonnull RapierBody bodyA,
        @Nonnull RapierBody bodyB,
        long jointHandle,
        @Nonnull Vector3f anchorA,
        @Nonnull Vector3f anchorB,
        @Nullable Vector3f axis,
        float springRestLength,
        float springStiffness,
        float springDamping) {
        this.space = space;
        this.type = type;
        this.bodyA = bodyA;
        this.bodyB = bodyB;
        this.jointHandle = jointHandle;
        this.anchorA = new Vector3f(anchorA);
        this.anchorB = new Vector3f(anchorB);
        this.axis = axis != null ? new Vector3f(axis) : null;
        this.springRestLength = springRestLength;
        this.springStiffness = springStiffness;
        this.springDamping = springDamping;
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
        return RapierNative.isJointEnabledNative(space.getNativeSpaceHandle(), jointHandle);
    }

    @Override
    public void setEnabled(boolean enabled) {
        RapierNative.setJointEnabledNative(space.getNativeSpaceHandle(), jointHandle, enabled);
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
        RapierNative.setJointLimitsNative(space.getNativeSpaceHandle(), jointHandle,
            lowerLimit, upperLimit);
    }

    @Override
    public boolean isMotorEnabled() {
        return motorEnabled;
    }

    @Override
    public void setMotorEnabled(boolean enabled) {
        motorEnabled = enabled;
        pushMotor();
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
        pushMotor();
    }

    @Override
    public float getSpringRestLength() {
        return springRestLength;
    }

    @Override
    public float getSpringStiffness() {
        return springStiffness;
    }

    @Override
    public float getSpringDamping() {
        return springDamping;
    }

    long getJointHandle() {
        return jointHandle;
    }

    private void pushMotor() {
        RapierNative.setJointMotorNative(space.getNativeSpaceHandle(), jointHandle,
            motorEnabled, motorTargetVelocity, motorMaxForce);
    }
}
