package dev.hytalemodding.impulse.core.plugin.simulation;

import dev.hytalemodding.impulse.api.PhysicsBodyType;
import javax.annotation.Nonnull;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Fluent recorder for operations targeting one rigid body key.
 */
public interface RigidBodyCommandRecorder {

    @Nonnull
    RigidBodyCommandRecorder setTransform(@Nonnull Vector3f position,
        @Nonnull Quaternionf rotation);

    @Nonnull
    RigidBodyCommandRecorder setTransform(@Nonnull Vector3f position,
        @Nonnull Quaternionf rotation,
        boolean activate);

    @Nonnull
    RigidBodyCommandRecorder setTransform(float positionX,
        float positionY,
        float positionZ,
        float rotationX,
        float rotationY,
        float rotationZ,
        float rotationW,
        boolean activate);

    @Nonnull
    RigidBodyCommandRecorder setPosition(@Nonnull Vector3f position);

    @Nonnull
    RigidBodyCommandRecorder setPosition(@Nonnull Vector3f position,
        boolean activate);

    @Nonnull
    RigidBodyCommandRecorder setPosition(float positionX,
        float positionY,
        float positionZ,
        boolean activate);

    @Nonnull
    RigidBodyCommandRecorder setVelocity(@Nonnull Vector3f linearVelocity,
        @Nonnull Vector3f angularVelocity);

    @Nonnull
    RigidBodyCommandRecorder setVelocity(@Nonnull Vector3f linearVelocity,
        @Nonnull Vector3f angularVelocity,
        boolean activate);

    @Nonnull
    RigidBodyCommandRecorder setVelocity(float linearX,
        float linearY,
        float linearZ,
        float angularX,
        float angularY,
        float angularZ,
        boolean activate);

    @Nonnull
    RigidBodyCommandRecorder setType(@Nonnull PhysicsBodyType bodyType);

    @Nonnull
    RigidBodyCommandRecorder setType(@Nonnull PhysicsBodyType bodyType,
        boolean activate);

    @Nonnull
    RigidBodyCommandRecorder activate();

    @Nonnull
    RigidBodyCommandRecorder applyImpulse(@Nonnull Vector3f impulse);

    @Nonnull
    RigidBodyCommandRecorder applyImpulse(float x,
        float y,
        float z);

    @Nonnull
    RigidBodyCommandRecorder applyImpulse(@Nonnull Vector3f impulse,
        @Nonnull Vector3f offset);

    @Nonnull
    RigidBodyCommandRecorder applyImpulse(float x,
        float y,
        float z,
        float offsetX,
        float offsetY,
        float offsetZ);

    @Nonnull
    RigidBodyCommandRecorder applyTorqueImpulse(@Nonnull Vector3f impulse);

    @Nonnull
    RigidBodyCommandRecorder applyTorqueImpulse(float x,
        float y,
        float z);

    @Nonnull
    RigidBodyCommandRecorder applyForce(@Nonnull Vector3f force);

    @Nonnull
    RigidBodyCommandRecorder applyForce(float x,
        float y,
        float z);

    @Nonnull
    RigidBodyCommandRecorder applyForce(@Nonnull Vector3f force,
        @Nonnull Vector3f offset);

    @Nonnull
    RigidBodyCommandRecorder applyForce(float x,
        float y,
        float z,
        float offsetX,
        float offsetY,
        float offsetZ);

    @Nonnull
    RigidBodyCommandRecorder applyTorque(@Nonnull Vector3f force);

    @Nonnull
    RigidBodyCommandRecorder applyTorque(float x,
        float y,
        float z);

    @Nonnull
    RigidBodyCommandRecorder destroy();
}
