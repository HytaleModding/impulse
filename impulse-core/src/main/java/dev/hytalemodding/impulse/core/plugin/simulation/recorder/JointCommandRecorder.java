package dev.hytalemodding.impulse.core.plugin.simulation.recorder;

import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import javax.annotation.Nonnull;
import org.joml.Vector3f;

/**
 * Fluent recorder for one joint creation command.
 */
public interface JointCommandRecorder {

    @Nonnull
    JointCommandRecorder space(@Nonnull SpaceId spaceId);

    @Nonnull
    JointCommandRecorder bodies(@Nonnull RigidBodyKey bodyA,
        @Nonnull RigidBodyKey bodyB);

    @Nonnull
    JointCommandRecorder fixed(@Nonnull Vector3f anchorA,
        @Nonnull Vector3f anchorB);

    @Nonnull
    JointCommandRecorder fixed(float anchorAX,
        float anchorAY,
        float anchorAZ,
        float anchorBX,
        float anchorBY,
        float anchorBZ);

    @Nonnull
    JointCommandRecorder point(@Nonnull Vector3f anchorA,
        @Nonnull Vector3f anchorB);

    @Nonnull
    JointCommandRecorder point(float anchorAX,
        float anchorAY,
        float anchorAZ,
        float anchorBX,
        float anchorBY,
        float anchorBZ);

    @Nonnull
    JointCommandRecorder hinge(@Nonnull Vector3f anchorA,
        @Nonnull Vector3f anchorB,
        @Nonnull Vector3f axis);

    @Nonnull
    JointCommandRecorder hinge(float anchorAX,
        float anchorAY,
        float anchorAZ,
        float anchorBX,
        float anchorBY,
        float anchorBZ,
        float axisX,
        float axisY,
        float axisZ);

    @Nonnull
    JointCommandRecorder slider(@Nonnull Vector3f anchorA,
        @Nonnull Vector3f anchorB,
        @Nonnull Vector3f axis);

    @Nonnull
    JointCommandRecorder slider(float anchorAX,
        float anchorAY,
        float anchorAZ,
        float anchorBX,
        float anchorBY,
        float anchorBZ,
        float axisX,
        float axisY,
        float axisZ);

    @Nonnull
    JointCommandRecorder spring(@Nonnull Vector3f anchorA,
        @Nonnull Vector3f anchorB,
        float restLength,
        float stiffness,
        float damping);

    @Nonnull
    JointCommandRecorder spring(float anchorAX,
        float anchorAY,
        float anchorAZ,
        float anchorBX,
        float anchorBY,
        float anchorBZ,
        float restLength,
        float stiffness,
        float damping);

    @Nonnull
    JointCommandRecorder limits(float lowerLimit,
        float upperLimit);

    @Nonnull
    JointCommandRecorder motor(float targetVelocity,
        float maxForce);
}
