package dev.hytalemodding.impulse.core.plugin.simulation;

import dev.hytalemodding.impulse.api.PhysicsAxis;
import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyKind;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyPersistenceMode;
import javax.annotation.Nonnull;
import org.joml.Vector3f;

/**
 * Fluent recorder for one rigid body spawn request.
 */
public interface RigidBodySpawnRecorder {

    @Nonnull
    RigidBodySpawnRecorder space(@Nonnull SpaceId spaceId);

    @Nonnull
    RigidBodySpawnRecorder box(float halfX,
        float halfY,
        float halfZ);

    @Nonnull
    RigidBodySpawnRecorder shape(@Nonnull PhysicsShapeSpec shape);

    @Nonnull
    RigidBodySpawnRecorder sphere(float radius);

    @Nonnull
    RigidBodySpawnRecorder capsule(float radius,
        float halfHeight,
        @Nonnull PhysicsAxis axis);

    @Nonnull
    RigidBodySpawnRecorder cylinder(float radius,
        float halfHeight,
        @Nonnull PhysicsAxis axis);

    @Nonnull
    RigidBodySpawnRecorder cone(float radius,
        float halfHeight,
        @Nonnull PhysicsAxis axis);

    @Nonnull
    RigidBodySpawnRecorder plane(float groundY);

    @Nonnull
    RigidBodySpawnRecorder mass(float mass);

    @Nonnull
    RigidBodySpawnRecorder type(@Nonnull PhysicsBodyType bodyType);

    @Nonnull
    RigidBodySpawnRecorder dynamic();

    @Nonnull
    RigidBodySpawnRecorder kinematic();

    @Nonnull
    RigidBodySpawnRecorder position(@Nonnull Vector3f position);

    @Nonnull
    RigidBodySpawnRecorder position(float x,
        float y,
        float z);

    @Nonnull
    RigidBodySpawnRecorder settings(@Nonnull RigidBodySpawnSettings settings);

    @Nonnull
    RigidBodySpawnRecorder sensor(boolean sensor);

    @Nonnull
    RigidBodySpawnRecorder collisionFilter(int group,
        int mask);

    @Nonnull
    RigidBodySpawnRecorder kind(@Nonnull PhysicsBodyKind kind);

    @Nonnull
    RigidBodySpawnRecorder temporary();

    @Nonnull
    RigidBodySpawnRecorder persistence(@Nonnull PhysicsBodyPersistenceMode persistenceMode);

    @Nonnull
    RigidBodySpawnRecorder runtimeOnly();

    @Nonnull
    RigidBodySpawnRecorder persistent();
}
