package dev.hytalemodding.impulse.core.plugin.simulation.recorder;

import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyKind;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyPersistenceMode;
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import dev.hytalemodding.impulse.core.plugin.joint.JointKey;
import java.util.Objects;
import java.util.function.Consumer;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import dev.hytalemodding.impulse.core.plugin.simulation.PhysicsShapeSpec;
import dev.hytalemodding.impulse.core.plugin.simulation.RigidBodySpawnSettings;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Fluent recorder for copied physics simulation intent.
 *
 * <p>The word "command" here means a compact, value-copied request submitted to the physics
 * owner. It is not a Hytale server command and it should not be modeled as one Java object per
 * mechanical instruction for high-volume cases. Use the bulk/template spawn methods when many
 * bodies share the same shape and settings.</p>
 */
public interface PhysicsCommandRecorder {

    @Nonnull
    RigidBodyCommandRecorder body(@Nonnull RigidBodyKey bodyKey);

    @Nonnull
    PhysicsCommandRecorder body(@Nonnull RigidBodyKey bodyKey,
        @Nonnull Consumer<RigidBodyCommandRecorder> recipe);

    @Nonnull
    PhysicsCommandRecorder setSpaceGravity(@Nonnull SpaceId spaceId,
        float x,
        float y,
        float z);

    @Nonnull
    default PhysicsCommandRecorder setBodyTransform(@Nonnull RigidBodyKey bodyKey,
        @Nonnull Vector3f position,
        @Nonnull Quaternionf rotation) {
        return setBodyTransform(bodyKey, position, rotation, false);
    }

    @Nonnull
    default PhysicsCommandRecorder setBodyTransform(@Nonnull RigidBodyKey bodyKey,
        @Nonnull Vector3f position,
        @Nonnull Quaternionf rotation,
        boolean activate) {
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(rotation, "rotation");
        return setBodyTransform(bodyKey,
            position.x,
            position.y,
            position.z,
            rotation.x,
            rotation.y,
            rotation.z,
            rotation.w,
            activate);
    }

    @Nonnull
    default PhysicsCommandRecorder setBodyTransform(@Nonnull RigidBodyKey bodyKey,
        float positionX,
        float positionY,
        float positionZ,
        float rotationX,
        float rotationY,
        float rotationZ,
        float rotationW,
        boolean activate) {
        body(bodyKey).setTransform(positionX,
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
    default PhysicsCommandRecorder setBodyPosition(@Nonnull RigidBodyKey bodyKey,
        @Nonnull Vector3f position) {
        return setBodyPosition(bodyKey, position, false);
    }

    @Nonnull
    default PhysicsCommandRecorder setBodyPosition(@Nonnull RigidBodyKey bodyKey,
        @Nonnull Vector3f position,
        boolean activate) {
        Objects.requireNonNull(position, "position");
        return setBodyPosition(bodyKey, position.x, position.y, position.z, activate);
    }

    @Nonnull
    default PhysicsCommandRecorder setBodyPosition(@Nonnull RigidBodyKey bodyKey,
        float positionX,
        float positionY,
        float positionZ,
        boolean activate) {
        body(bodyKey).setPosition(positionX, positionY, positionZ, activate);
        return this;
    }

    @Nonnull
    default PhysicsCommandRecorder setBodyVelocity(@Nonnull RigidBodyKey bodyKey,
        @Nonnull Vector3f linearVelocity,
        @Nonnull Vector3f angularVelocity) {
        return setBodyVelocity(bodyKey, linearVelocity, angularVelocity, false);
    }

    @Nonnull
    default PhysicsCommandRecorder setBodyVelocity(@Nonnull RigidBodyKey bodyKey,
        @Nonnull Vector3f linearVelocity,
        @Nonnull Vector3f angularVelocity,
        boolean activate) {
        Objects.requireNonNull(linearVelocity, "linearVelocity");
        Objects.requireNonNull(angularVelocity, "angularVelocity");
        return setBodyVelocity(bodyKey,
            linearVelocity.x,
            linearVelocity.y,
            linearVelocity.z,
            angularVelocity.x,
            angularVelocity.y,
            angularVelocity.z,
            activate);
    }

    @Nonnull
    default PhysicsCommandRecorder setBodyVelocity(@Nonnull RigidBodyKey bodyKey,
        float linearX,
        float linearY,
        float linearZ,
        float angularX,
        float angularY,
        float angularZ,
        boolean activate) {
        body(bodyKey).setVelocity(linearX,
            linearY,
            linearZ,
            angularX,
            angularY,
            angularZ,
            activate);
        return this;
    }

    @Nonnull
    default PhysicsCommandRecorder setBodyType(@Nonnull RigidBodyKey bodyKey,
        @Nonnull PhysicsBodyType bodyType) {
        return setBodyType(bodyKey, bodyType, false);
    }

    @Nonnull
    default PhysicsCommandRecorder setBodyType(@Nonnull RigidBodyKey bodyKey,
        @Nonnull PhysicsBodyType bodyType,
        boolean activate) {
        body(bodyKey).setType(bodyType, activate);
        return this;
    }

    @Nonnull
    default PhysicsCommandRecorder activateBody(@Nonnull RigidBodyKey bodyKey) {
        body(bodyKey).activate();
        return this;
    }

    @Nonnull
    default PhysicsCommandRecorder applyBodyImpulse(@Nonnull RigidBodyKey bodyKey,
        @Nonnull Vector3f impulse) {
        Objects.requireNonNull(impulse, "impulse");
        return applyBodyImpulse(bodyKey, impulse.x, impulse.y, impulse.z);
    }

    @Nonnull
    default PhysicsCommandRecorder applyBodyImpulse(@Nonnull RigidBodyKey bodyKey,
        float x,
        float y,
        float z) {
        body(bodyKey).applyImpulse(x, y, z);
        return this;
    }

    @Nonnull
    default PhysicsCommandRecorder applyBodyImpulse(@Nonnull RigidBodyKey bodyKey,
        @Nonnull Vector3f impulse,
        @Nonnull Vector3f offset) {
        Objects.requireNonNull(impulse, "impulse");
        Objects.requireNonNull(offset, "offset");
        return applyBodyImpulse(bodyKey, impulse.x, impulse.y, impulse.z, offset.x, offset.y, offset.z);
    }

    @Nonnull
    default PhysicsCommandRecorder applyBodyImpulse(@Nonnull RigidBodyKey bodyKey,
        float x,
        float y,
        float z,
        float offsetX,
        float offsetY,
        float offsetZ) {
        body(bodyKey).applyImpulse(x, y, z, offsetX, offsetY, offsetZ);
        return this;
    }

    @Nonnull
    default PhysicsCommandRecorder applyBodyTorqueImpulse(@Nonnull RigidBodyKey bodyKey,
        @Nonnull Vector3f impulse) {
        Objects.requireNonNull(impulse, "impulse");
        return applyBodyTorqueImpulse(bodyKey, impulse.x, impulse.y, impulse.z);
    }

    @Nonnull
    default PhysicsCommandRecorder applyBodyTorqueImpulse(@Nonnull RigidBodyKey bodyKey,
        float x,
        float y,
        float z) {
        body(bodyKey).applyTorqueImpulse(x, y, z);
        return this;
    }

    @Nonnull
    default PhysicsCommandRecorder applyBodyForce(@Nonnull RigidBodyKey bodyKey,
        @Nonnull Vector3f force) {
        Objects.requireNonNull(force, "force");
        return applyBodyForce(bodyKey, force.x, force.y, force.z);
    }

    @Nonnull
    default PhysicsCommandRecorder applyBodyForce(@Nonnull RigidBodyKey bodyKey,
        float x,
        float y,
        float z) {
        body(bodyKey).applyForce(x, y, z);
        return this;
    }

    @Nonnull
    default PhysicsCommandRecorder applyBodyForce(@Nonnull RigidBodyKey bodyKey,
        @Nonnull Vector3f force,
        @Nonnull Vector3f offset) {
        Objects.requireNonNull(force, "force");
        Objects.requireNonNull(offset, "offset");
        return applyBodyForce(bodyKey, force.x, force.y, force.z, offset.x, offset.y, offset.z);
    }

    @Nonnull
    default PhysicsCommandRecorder applyBodyForce(@Nonnull RigidBodyKey bodyKey,
        float x,
        float y,
        float z,
        float offsetX,
        float offsetY,
        float offsetZ) {
        body(bodyKey).applyForce(x, y, z, offsetX, offsetY, offsetZ);
        return this;
    }

    @Nonnull
    default PhysicsCommandRecorder applyBodyTorque(@Nonnull RigidBodyKey bodyKey,
        @Nonnull Vector3f force) {
        Objects.requireNonNull(force, "force");
        return applyBodyTorque(bodyKey, force.x, force.y, force.z);
    }

    @Nonnull
    default PhysicsCommandRecorder applyBodyTorque(@Nonnull RigidBodyKey bodyKey,
        float x,
        float y,
        float z) {
        body(bodyKey).applyTorque(x, y, z);
        return this;
    }

    @Nonnull
    default PhysicsCommandRecorder destroyBody(@Nonnull RigidBodyKey bodyKey) {
        body(bodyKey).destroy();
        return this;
    }

    @Nonnull
    RigidBodySpawnRecorder spawnBody(@Nonnull RigidBodyKey bodyKey);

    @Nonnull
    PhysicsCommandRecorder spawnBody(@Nonnull RigidBodyKey bodyKey,
        @Nonnull Consumer<RigidBodySpawnRecorder> recipe);

    @Nonnull
    PhysicsCommandRecorder spawnBodies(@Nonnull Consumer<RigidBodySpawnBatchRecorder> recipe);

    /**
     * Records a bulk spawn batch with a capacity hint for the number of bodies the recipe will add.
     */
    @Nonnull
    PhysicsCommandRecorder spawnBodies(int expectedBodies,
        @Nonnull Consumer<RigidBodySpawnBatchRecorder> recipe);

    /**
     * Records a compact template spawn batch where each body differs only by key and position.
     */
    @Nonnull
    PhysicsCommandRecorder spawnBodies(int expectedBodies,
        @Nonnull SpaceId spaceId,
        @Nonnull PhysicsShapeSpec shape,
        float mass,
        @Nonnull PhysicsBodyType bodyType,
        @Nonnull RigidBodySpawnSettings settings,
        @Nonnull PhysicsBodyKind kind,
        @Nonnull PhysicsBodyPersistenceMode persistenceMode,
        @Nonnull Consumer<RigidBodySpawnTemplateRecorder> recipe);

    @Nonnull
    JointCommandRecorder joint(@Nonnull JointKey jointKey);

    @Nonnull
    PhysicsCommandRecorder joint(@Nonnull JointKey jointKey,
        @Nonnull Consumer<JointCommandRecorder> recipe);

    @Nonnull
    PhysicsCommandRecorder destroyJoint(@Nonnull JointKey jointKey);

    @Nonnull
    PhysicsCommandRecorder destroyJointBetween(@Nullable JointKey preferredJointKey,
        @Nonnull SpaceId spaceId,
        @Nonnull RigidBodyKey bodyA,
        @Nonnull RigidBodyKey bodyB);
}
