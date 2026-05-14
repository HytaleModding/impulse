package dev.hytalemodding.impulse.api;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3f;

/**
 * A live joint constraint between two bodies.
 * Anchors are local to each body.
 * Axis is only meaningful for hinge and slider joints.
 * Limits and motor controls are mainly for hinge and slider joints.
 */
public interface PhysicsJoint {

    @Nonnull
    PhysicsJointType getType();

    @Nonnull
    PhysicsBody getBodyA();

    @Nonnull
    PhysicsBody getBodyB();

    boolean isEnabled();

    void setEnabled(boolean enabled);

    @Nonnull
    Vector3f getAnchorA();

    @Nonnull
    Vector3f getAnchorB();

    /**
     * Return the joint axis when the joint type uses one.
     * Fixed, point, and spring joints may return null.
     */
    @Nullable
    Vector3f getAxis();

    /**
     * Return the lower joint limit when the joint supports limits.
     * Hinge and slider joints use this value.
     */
    float getLowerLimit();

    /**
     * Return the upper joint limit when the joint supports limits.
     * Hinge and slider joints use this value.
     */
    float getUpperLimit();

    /**
     * Set the joint limits when the joint supports them.
     * Backends may ignore this for joints that do not use limits.
     */
    void setLimits(float lowerLimit, float upperLimit);

    /**
     * Return whether the motor is enabled for joints that support motors.
     */
    boolean isMotorEnabled();

    /**
     * Enable or disable the motor when the joint supports motors.
     */
    void setMotorEnabled(boolean enabled);

    /**
     * Return the configured motor target velocity.
     */
    float getMotorTargetVelocity();

    /**
     * Return the configured motor max force.
     */
    float getMotorMaxForce();

    /**
     * Configure the joint motor when the joint supports motors.
     */
    void setMotor(float targetVelocity, float maxForce);
}
