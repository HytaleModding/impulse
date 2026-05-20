package dev.hytalemodding.impulse.api;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Backend-agnostic rigid body facade.
 * TODO: rigid body for now, soft body and particles need to be addressed
 * <p>
 * The API defines shared behavior for different backends, but backends may still differ in
 * solver details, contact reporting, and exact motion response.
 */
public interface PhysicsBody {

    void setPosition(float x, float y, float z);

    void setPosition(@Nonnull Vector3f pos);

    @Nonnull
    Vector3f getPosition();

    /**
     * Copy the current world-space center of mass into the provided vector.
     * Backends should override this to avoid allocating on hot sync paths.
     */
    default void getPosition(@Nonnull Vector3f out) {
        out.set(getPosition());
    }

    void setRotation(float x, float y, float z, float w);

    void setRotation(@Nonnull Quaternionf rot);

    @Nonnull
    Quaternionf getRotation();

    /**
     * Copy the current world-space rotation into the provided quaternion.
     * Backends should override this to avoid allocating on hot sync paths.
     */
    default void getRotation(@Nonnull Quaternionf out) {
        out.set(getRotation());
    }

    void setRestitution(float restitution);

    float getRestitution();

    void setFriction(float friction);

    float getFriction();

    @Nonnull
    PhysicsBodyType getBodyType();

    void setBodyType(@Nonnull PhysicsBodyType bodyType);

    boolean isStatic();

    default boolean isDynamic() {
        return getBodyType() == PhysicsBodyType.DYNAMIC;
    }

    boolean isKinematic();

    void setKinematic(boolean kinematic);

    void activate();

    boolean isActive();

    boolean isSleeping();

    void sleep();

    float getMass();

    void setMass(float mass);

    @Nonnull
    Vector3f getLinearVelocity();

    /**
     * Copy the current linear velocity into the provided vector.
     */
    default void getLinearVelocity(@Nonnull Vector3f out) {
        out.set(getLinearVelocity());
    }

    void setLinearVelocity(@Nonnull Vector3f vel);

    void setLinearVelocity(float x, float y, float z);

    @Nonnull
    Vector3f getAngularVelocity();

    /**
     * Copy the current angular velocity into the provided vector.
     */
    default void getAngularVelocity(@Nonnull Vector3f out) {
        out.set(getAngularVelocity());
    }

    void setAngularVelocity(@Nonnull Vector3f vel);

    void setAngularVelocity(float x, float y, float z);

    float getLinearDamping();

    void setLinearDamping(float damping);

    float getAngularDamping();

    void setAngularDamping(float damping);

    default void setDamping(float linearDamping, float angularDamping) {
        setLinearDamping(linearDamping);
        setAngularDamping(angularDamping);
    }

    void applyCentralForce(@Nonnull Vector3f force);

    void applyCentralForce(float x, float y, float z);

    void applyForce(@Nonnull Vector3f force, @Nonnull Vector3f offset);

    void applyCentralImpulse(@Nonnull Vector3f impulse);

    void applyCentralImpulse(float x, float y, float z);

    void applyImpulse(@Nonnull Vector3f impulse, @Nonnull Vector3f offset);

    void applyTorque(@Nonnull Vector3f torque);

    void applyTorqueImpulse(@Nonnull Vector3f torqueImpulse);

    void clearForces();

    /**
     * Returns whether this body is configured as a sensor/trigger.
     * <p>
     * Sensor bodies participate in overlap/contact callbacks but do not produce
     * normal physical contact response or collision resolution.
     */
    boolean isSensor();

    /**
     * Sets whether this body should behave as a sensor/trigger.
     * <p>
     * When enabled, the body can overlap other bodies without acting as a solid
     * collider.
     */
    void setSensor(boolean sensor);

    int getCollisionGroup();

    int getCollisionMask();

    void setCollisionFilter(int group, int mask);

    boolean isContinuousCollisionEnabled();

    void setContinuousCollisionEnabled(boolean enabled);

    @Nonnull
    ShapeType getShapeType();

    @Nullable
    Vector3f getBoxHalfExtents();

    float getSphereRadius();

    float getHalfHeight();

    @Nonnull
    PhysicsAxis getShapeAxis();

    float getCenterOfMassOffsetY();

    /**
     * Returns the configured ground Y for plane shapes.
     * <p>
     * Returns {@link Float#NaN} for non-plane shapes.
     */
    default float getPlaneGroundY() {
        return Float.NaN;
    }
}
