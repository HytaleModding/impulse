package dev.hytalemodding.impulse.core.internal.simulation;

import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import dev.hytalemodding.impulse.core.plugin.simulation.RigidBodyCommandRecorder;
import java.util.Objects;
import javax.annotation.Nonnull;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Fluent recorder for operations targeting one rigid body key.
 */
public final class MutableRigidBodyCommandRecorder implements RigidBodyCommandRecorder {

    @Nonnull
    private final MutablePhysicsCommandContext recorder;
    @Nonnull
    private final RigidBodyKey bodyKey;
    private boolean sealed;

    MutableRigidBodyCommandRecorder(@Nonnull MutablePhysicsCommandContext recorder,
        @Nonnull RigidBodyKey bodyKey) {
        this.recorder = Objects.requireNonNull(recorder, "recorder");
        this.bodyKey = Objects.requireNonNull(bodyKey, "bodyKey");
    }

    @Nonnull
    @Override
    public RigidBodyCommandRecorder setTransform(@Nonnull Vector3f position,
        @Nonnull Quaternionf rotation) {
        return setTransform(position, rotation, false);
    }

    @Nonnull
    @Override
    public RigidBodyCommandRecorder setTransform(@Nonnull Vector3f position,
        @Nonnull Quaternionf rotation,
        boolean activate) {
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(rotation, "rotation");
        return setTransform(position.x,
            position.y,
            position.z,
            rotation.x,
            rotation.y,
            rotation.z,
            rotation.w,
            activate);
    }

    @Nonnull
    @Override
    public RigidBodyCommandRecorder setTransform(float positionX,
        float positionY,
        float positionZ,
        float rotationX,
        float rotationY,
        float rotationZ,
        float rotationW,
        boolean activate) {
        assertOpen();
        recorder.recordSetTransform(bodyKey,
            positionX,
            positionY,
            positionZ,
            rotationX,
            rotationY,
            rotationZ,
            rotationW,
            activate);
        return this;
    }

    @Nonnull
    @Override
    public RigidBodyCommandRecorder setPosition(@Nonnull Vector3f position) {
        return setPosition(position, false);
    }

    @Nonnull
    @Override
    public RigidBodyCommandRecorder setPosition(@Nonnull Vector3f position,
        boolean activate) {
        Objects.requireNonNull(position, "position");
        return setPosition(position.x, position.y, position.z, activate);
    }

    @Nonnull
    @Override
    public RigidBodyCommandRecorder setPosition(float positionX,
        float positionY,
        float positionZ,
        boolean activate) {
        assertOpen();
        recorder.recordSetPosition(bodyKey, positionX, positionY, positionZ, activate);
        return this;
    }

    @Nonnull
    @Override
    public RigidBodyCommandRecorder setVelocity(@Nonnull Vector3f linearVelocity,
        @Nonnull Vector3f angularVelocity) {
        return setVelocity(linearVelocity, angularVelocity, false);
    }

    @Nonnull
    @Override
    public RigidBodyCommandRecorder setVelocity(@Nonnull Vector3f linearVelocity,
        @Nonnull Vector3f angularVelocity,
        boolean activate) {
        Objects.requireNonNull(linearVelocity, "linearVelocity");
        Objects.requireNonNull(angularVelocity, "angularVelocity");
        return setVelocity(linearVelocity.x,
            linearVelocity.y,
            linearVelocity.z,
            angularVelocity.x,
            angularVelocity.y,
            angularVelocity.z,
            activate);
    }

    @Nonnull
    @Override
    public RigidBodyCommandRecorder setVelocity(float linearX,
        float linearY,
        float linearZ,
        float angularX,
        float angularY,
        float angularZ,
        boolean activate) {
        assertOpen();
        recorder.recordSetVelocity(bodyKey,
            linearX,
            linearY,
            linearZ,
            angularX,
            angularY,
            angularZ,
            activate);
        return this;
    }

    @Nonnull
    @Override
    public RigidBodyCommandRecorder setType(@Nonnull PhysicsBodyType bodyType) {
        return setType(bodyType, false);
    }

    @Nonnull
    @Override
    public RigidBodyCommandRecorder setType(@Nonnull PhysicsBodyType bodyType,
        boolean activate) {
        assertOpen();
        recorder.recordSetType(bodyKey, bodyType, activate);
        return this;
    }

    @Nonnull
    @Override
    public RigidBodyCommandRecorder activate() {
        assertOpen();
        recorder.recordActivate(bodyKey);
        return this;
    }

    @Nonnull
    @Override
    public RigidBodyCommandRecorder applyImpulse(@Nonnull Vector3f impulse) {
        Objects.requireNonNull(impulse, "impulse");
        return applyImpulse(impulse.x, impulse.y, impulse.z);
    }

    @Nonnull
    @Override
    public RigidBodyCommandRecorder applyImpulse(float x,
        float y,
        float z) {
        assertOpen();
        recorder.recordImpulse(bodyKey, x, y, z, false, 0.0f, 0.0f, 0.0f, false);
        return this;
    }

    @Nonnull
    @Override
    public RigidBodyCommandRecorder applyImpulse(@Nonnull Vector3f impulse,
        @Nonnull Vector3f offset) {
        Objects.requireNonNull(impulse, "impulse");
        Objects.requireNonNull(offset, "offset");
        return applyImpulse(impulse.x, impulse.y, impulse.z, offset.x, offset.y, offset.z);
    }

    @Nonnull
    @Override
    public RigidBodyCommandRecorder applyImpulse(float x,
        float y,
        float z,
        float offsetX,
        float offsetY,
        float offsetZ) {
        assertOpen();
        recorder.recordImpulse(bodyKey, x, y, z, true, offsetX, offsetY, offsetZ, false);
        return this;
    }

    @Nonnull
    @Override
    public RigidBodyCommandRecorder applyTorqueImpulse(@Nonnull Vector3f impulse) {
        Objects.requireNonNull(impulse, "impulse");
        return applyTorqueImpulse(impulse.x, impulse.y, impulse.z);
    }

    @Nonnull
    @Override
    public RigidBodyCommandRecorder applyTorqueImpulse(float x,
        float y,
        float z) {
        assertOpen();
        recorder.recordImpulse(bodyKey, x, y, z, false, 0.0f, 0.0f, 0.0f, true);
        return this;
    }

    @Nonnull
    @Override
    public RigidBodyCommandRecorder applyForce(@Nonnull Vector3f force) {
        Objects.requireNonNull(force, "force");
        return applyForce(force.x, force.y, force.z);
    }

    @Nonnull
    @Override
    public RigidBodyCommandRecorder applyForce(float x,
        float y,
        float z) {
        assertOpen();
        recorder.recordForce(bodyKey, x, y, z, false, 0.0f, 0.0f, 0.0f, false);
        return this;
    }

    @Nonnull
    @Override
    public RigidBodyCommandRecorder applyForce(@Nonnull Vector3f force,
        @Nonnull Vector3f offset) {
        Objects.requireNonNull(force, "force");
        Objects.requireNonNull(offset, "offset");
        return applyForce(force.x, force.y, force.z, offset.x, offset.y, offset.z);
    }

    @Nonnull
    @Override
    public RigidBodyCommandRecorder applyForce(float x,
        float y,
        float z,
        float offsetX,
        float offsetY,
        float offsetZ) {
        assertOpen();
        recorder.recordForce(bodyKey, x, y, z, true, offsetX, offsetY, offsetZ, false);
        return this;
    }

    @Nonnull
    @Override
    public RigidBodyCommandRecorder applyTorque(@Nonnull Vector3f force) {
        Objects.requireNonNull(force, "force");
        return applyTorque(force.x, force.y, force.z);
    }

    @Nonnull
    @Override
    public RigidBodyCommandRecorder applyTorque(float x,
        float y,
        float z) {
        assertOpen();
        recorder.recordForce(bodyKey, x, y, z, false, 0.0f, 0.0f, 0.0f, true);
        return this;
    }

    @Nonnull
    @Override
    public RigidBodyCommandRecorder destroy() {
        assertOpen();
        recorder.recordDestroyBody(bodyKey);
        return this;
    }

    void seal() {
        sealed = true;
    }

    private void assertOpen() {
        if (sealed) {
            throw new IllegalStateException("Rigid body command recorder is no longer active");
        }
    }
}
