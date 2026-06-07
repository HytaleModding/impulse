package dev.hytalemodding.impulse.core.internal.simulation.recorder;

import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import dev.hytalemodding.impulse.core.plugin.joint.JointKey;
import dev.hytalemodding.impulse.core.plugin.simulation.recorder.JointCommandRecorder;
import dev.hytalemodding.impulse.core.plugin.simulation.JointType;
import java.util.Objects;
import javax.annotation.Nonnull;
import org.joml.Vector3f;

/**
 * Fluent recorder for one joint creation command.
 */
public final class MutableJointCommandRecorder implements JointCommandRecorder {

    @Nonnull
    private final MutablePhysicsCommandContext recorder;
    @Nonnull
    private final JointKey jointKey;
    private SpaceId spaceId;
    private RigidBodyKey bodyA;
    private RigidBodyKey bodyB;
    private JointType type;
    private float anchorAX;
    private float anchorAY;
    private float anchorAZ;
    private float anchorBX;
    private float anchorBY;
    private float anchorBZ;
    private float axisX;
    private float axisY;
    private float axisZ;
    private float restLength;
    private float stiffness;
    private float damping;
    private float lowerLimit;
    private float upperLimit;
    private boolean motorEnabled;
    private float motorTargetVelocity;
    private float motorMaxForce;
    private boolean sealed;

    MutableJointCommandRecorder(@Nonnull MutablePhysicsCommandContext recorder,
        @Nonnull JointKey jointKey) {
        this.recorder = Objects.requireNonNull(recorder, "recorder");
        this.jointKey = Objects.requireNonNull(jointKey, "jointKey");
    }

    @Nonnull
    @Override
    public JointCommandRecorder space(@Nonnull SpaceId spaceId) {
        assertOpen();
        this.spaceId = Objects.requireNonNull(spaceId, "spaceId");
        return this;
    }

    @Nonnull
    @Override
    public JointCommandRecorder bodies(@Nonnull RigidBodyKey bodyA,
        @Nonnull RigidBodyKey bodyB) {
        assertOpen();
        this.bodyA = Objects.requireNonNull(bodyA, "bodyA");
        this.bodyB = Objects.requireNonNull(bodyB, "bodyB");
        return this;
    }

    @Nonnull
    @Override
    public JointCommandRecorder fixed(@Nonnull Vector3f anchorA,
        @Nonnull Vector3f anchorB) {
        Objects.requireNonNull(anchorA, "anchorA");
        Objects.requireNonNull(anchorB, "anchorB");
        return fixed(anchorA.x, anchorA.y, anchorA.z, anchorB.x, anchorB.y, anchorB.z);
    }

    @Nonnull
    @Override
    public JointCommandRecorder fixed(float anchorAX,
        float anchorAY,
        float anchorAZ,
        float anchorBX,
        float anchorBY,
        float anchorBZ) {
        return anchors(JointType.FIXED,
            anchorAX,
            anchorAY,
            anchorAZ,
            anchorBX,
            anchorBY,
            anchorBZ,
            0.0f,
            0.0f,
            0.0f);
    }

    @Nonnull
    @Override
    public JointCommandRecorder point(@Nonnull Vector3f anchorA,
        @Nonnull Vector3f anchorB) {
        Objects.requireNonNull(anchorA, "anchorA");
        Objects.requireNonNull(anchorB, "anchorB");
        return point(anchorA.x, anchorA.y, anchorA.z, anchorB.x, anchorB.y, anchorB.z);
    }

    @Nonnull
    @Override
    public JointCommandRecorder point(float anchorAX,
        float anchorAY,
        float anchorAZ,
        float anchorBX,
        float anchorBY,
        float anchorBZ) {
        return anchors(JointType.POINT,
            anchorAX,
            anchorAY,
            anchorAZ,
            anchorBX,
            anchorBY,
            anchorBZ,
            0.0f,
            0.0f,
            0.0f);
    }

    @Nonnull
    @Override
    public JointCommandRecorder hinge(@Nonnull Vector3f anchorA,
        @Nonnull Vector3f anchorB,
        @Nonnull Vector3f axis) {
        Objects.requireNonNull(anchorA, "anchorA");
        Objects.requireNonNull(anchorB, "anchorB");
        Objects.requireNonNull(axis, "axis");
        return hinge(anchorA.x,
            anchorA.y,
            anchorA.z,
            anchorB.x,
            anchorB.y,
            anchorB.z,
            axis.x,
            axis.y,
            axis.z);
    }

    @Nonnull
    @Override
    public JointCommandRecorder hinge(float anchorAX,
        float anchorAY,
        float anchorAZ,
        float anchorBX,
        float anchorBY,
        float anchorBZ,
        float axisX,
        float axisY,
        float axisZ) {
        return anchors(JointType.HINGE,
            anchorAX,
            anchorAY,
            anchorAZ,
            anchorBX,
            anchorBY,
            anchorBZ,
            axisX,
            axisY,
            axisZ);
    }

    @Nonnull
    @Override
    public JointCommandRecorder slider(@Nonnull Vector3f anchorA,
        @Nonnull Vector3f anchorB,
        @Nonnull Vector3f axis) {
        Objects.requireNonNull(anchorA, "anchorA");
        Objects.requireNonNull(anchorB, "anchorB");
        Objects.requireNonNull(axis, "axis");
        return slider(anchorA.x,
            anchorA.y,
            anchorA.z,
            anchorB.x,
            anchorB.y,
            anchorB.z,
            axis.x,
            axis.y,
            axis.z);
    }

    @Nonnull
    @Override
    public JointCommandRecorder slider(float anchorAX,
        float anchorAY,
        float anchorAZ,
        float anchorBX,
        float anchorBY,
        float anchorBZ,
        float axisX,
        float axisY,
        float axisZ) {
        return anchors(JointType.SLIDER,
            anchorAX,
            anchorAY,
            anchorAZ,
            anchorBX,
            anchorBY,
            anchorBZ,
            axisX,
            axisY,
            axisZ);
    }

    @Nonnull
    @Override
    public JointCommandRecorder spring(@Nonnull Vector3f anchorA,
        @Nonnull Vector3f anchorB,
        float restLength,
        float stiffness,
        float damping) {
        Objects.requireNonNull(anchorA, "anchorA");
        Objects.requireNonNull(anchorB, "anchorB");
        spring(anchorA.x,
            anchorA.y,
            anchorA.z,
            anchorB.x,
            anchorB.y,
            anchorB.z,
            restLength,
            stiffness,
            damping);
        return this;
    }

    @Nonnull
    @Override
    public JointCommandRecorder spring(float anchorAX,
        float anchorAY,
        float anchorAZ,
        float anchorBX,
        float anchorBY,
        float anchorBZ,
        float restLength,
        float stiffness,
        float damping) {
        anchors(JointType.SPRING,
            anchorAX,
            anchorAY,
            anchorAZ,
            anchorBX,
            anchorBY,
            anchorBZ,
            0.0f,
            0.0f,
            0.0f);
        this.restLength = restLength;
        this.stiffness = stiffness;
        this.damping = damping;
        return this;
    }

    @Nonnull
    @Override
    public JointCommandRecorder limits(float lowerLimit,
        float upperLimit) {
        assertOpen();
        this.lowerLimit = lowerLimit;
        this.upperLimit = upperLimit;
        return this;
    }

    @Nonnull
    @Override
    public JointCommandRecorder motor(float targetVelocity,
        float maxForce) {
        assertOpen();
        this.motorEnabled = true;
        this.motorTargetVelocity = targetVelocity;
        this.motorMaxForce = maxForce;
        return this;
    }

    void record() {
        assertOpen();
        validate();
        recorder.recordJoint(jointKey,
            spaceId,
            bodyA,
            bodyB,
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

    void validate() {
        if (spaceId == null) {
            throw new IllegalStateException("Joint command requires a physics space");
        }
        if (bodyA == null || bodyB == null) {
            throw new IllegalStateException("Joint command requires both rigid bodies");
        }
        if (type == null) {
            throw new IllegalStateException("Joint command requires a joint type");
        }
    }

    @Nonnull
    private JointCommandRecorder anchors(@Nonnull JointType type,
        float anchorAX,
        float anchorAY,
        float anchorAZ,
        float anchorBX,
        float anchorBY,
        float anchorBZ,
        float axisX,
        float axisY,
        float axisZ) {
        assertOpen();
        this.type = type;
        this.anchorAX = anchorAX;
        this.anchorAY = anchorAY;
        this.anchorAZ = anchorAZ;
        this.anchorBX = anchorBX;
        this.anchorBY = anchorBY;
        this.anchorBZ = anchorBZ;
        this.axisX = axisX;
        this.axisY = axisY;
        this.axisZ = axisZ;
        return this;
    }

    void seal() {
        sealed = true;
    }

    private void assertOpen() {
        if (sealed) {
            throw new IllegalStateException("Joint command recorder is no longer active");
        }
    }
}
