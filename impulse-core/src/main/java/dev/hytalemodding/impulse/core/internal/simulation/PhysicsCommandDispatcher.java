package dev.hytalemodding.impulse.core.internal.simulation;

import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyKind;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyPersistenceMode;
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import dev.hytalemodding.impulse.core.plugin.joint.JointKey;
import dev.hytalemodding.impulse.core.plugin.simulation.JointType;
import dev.hytalemodding.impulse.core.plugin.simulation.PhysicsShapeSpec;
import dev.hytalemodding.impulse.core.plugin.simulation.RigidBodySpawnSettings;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Internal execution target for frozen physics command batches.
 */
public interface PhysicsCommandDispatcher {

    void spawnRigidBody(@Nonnull RigidBodyKey bodyKey,
        @Nonnull SpaceId spaceId,
        @Nonnull PhysicsShapeSpec shape,
        float mass,
        @Nonnull PhysicsBodyType bodyType,
        float positionX,
        float positionY,
        float positionZ,
        @Nonnull RigidBodySpawnSettings settings,
        @Nonnull PhysicsBodyKind kind,
        @Nonnull PhysicsBodyPersistenceMode persistenceMode);

    default void spawnRigidBodies(int bodyCount,
        @Nonnull RigidBodyKey[] bodyKeys,
        @Nonnull SpaceId spaceId,
        @Nonnull PhysicsShapeSpec shape,
        float mass,
        @Nonnull PhysicsBodyType bodyType,
        @Nonnull float[] positions,
        @Nonnull RigidBodySpawnSettings settings,
        @Nonnull PhysicsBodyKind kind,
        @Nonnull PhysicsBodyPersistenceMode persistenceMode) {
        for (int index = 0; index < bodyCount; index++) {
            int positionOffset = index * 3;
            spawnRigidBody(bodyKeys[index],
                spaceId,
                shape,
                mass,
                bodyType,
                positions[positionOffset],
                positions[positionOffset + 1],
                positions[positionOffset + 2],
                settings,
                kind,
                persistenceMode);
        }
    }

    default void spawnRigidBodies(int bodyCount,
        @Nonnull long[] bodyKeyMostSignificantBits,
        @Nonnull long[] bodyKeyLeastSignificantBits,
        @Nonnull SpaceId spaceId,
        @Nonnull PhysicsShapeSpec shape,
        float mass,
        @Nonnull PhysicsBodyType bodyType,
        @Nonnull float[] positions,
        @Nonnull RigidBodySpawnSettings settings,
        @Nonnull PhysicsBodyKind kind,
        @Nonnull PhysicsBodyPersistenceMode persistenceMode) {
        for (int index = 0; index < bodyCount; index++) {
            int positionOffset = index * 3;
            RigidBodyKey bodyKey = RigidBodyKey.of(bodyKeyMostSignificantBits[index],
                bodyKeyLeastSignificantBits[index]);
            spawnRigidBody(bodyKey,
                spaceId,
                shape,
                mass,
                bodyType,
                positions[positionOffset],
                positions[positionOffset + 1],
                positions[positionOffset + 2],
                settings,
                kind,
                persistenceMode);
        }
    }

    void destroyRigidBody(@Nonnull RigidBodyKey bodyKey);

    void setSpaceGravity(@Nonnull SpaceId spaceId,
        float x,
        float y,
        float z);

    void setRigidBodyTransform(@Nonnull RigidBodyKey bodyKey,
        float positionX,
        float positionY,
        float positionZ,
        float rotationX,
        float rotationY,
        float rotationZ,
        float rotationW,
        boolean activate);

    void setRigidBodyPosition(@Nonnull RigidBodyKey bodyKey,
        float positionX,
        float positionY,
        float positionZ,
        boolean activate);

    void setRigidBodyVelocity(@Nonnull RigidBodyKey bodyKey,
        float linearX,
        float linearY,
        float linearZ,
        float angularX,
        float angularY,
        float angularZ,
        boolean activate);

    void setRigidBodyType(@Nonnull RigidBodyKey bodyKey,
        @Nonnull PhysicsBodyType bodyType,
        boolean activate);

    void activateRigidBody(@Nonnull RigidBodyKey bodyKey);

    void applyRigidBodyImpulse(@Nonnull RigidBodyKey bodyKey,
        float x,
        float y,
        float z,
        boolean hasOffset,
        float offsetX,
        float offsetY,
        float offsetZ,
        boolean torque);

    void applyRigidBodyForce(@Nonnull RigidBodyKey bodyKey,
        float x,
        float y,
        float z,
        boolean hasOffset,
        float offsetX,
        float offsetY,
        float offsetZ,
        boolean torque);

    void createJoint(@Nonnull JointKey jointKey,
        @Nonnull SpaceId spaceId,
        @Nonnull RigidBodyKey bodyA,
        @Nonnull RigidBodyKey bodyB,
        @Nonnull JointType type,
        float anchorAX,
        float anchorAY,
        float anchorAZ,
        float anchorBX,
        float anchorBY,
        float anchorBZ,
        float axisX,
        float axisY,
        float axisZ,
        float restLength,
        float stiffness,
        float damping,
        float lowerLimit,
        float upperLimit,
        boolean motorEnabled,
        float motorTargetVelocity,
        float motorMaxForce);

    void destroyJoint(@Nonnull JointKey jointKey);

    void destroyJointBetween(@Nullable JointKey preferredJointKey,
        @Nonnull SpaceId spaceId,
        @Nonnull RigidBodyKey bodyA,
        @Nonnull RigidBodyKey bodyB);
}
