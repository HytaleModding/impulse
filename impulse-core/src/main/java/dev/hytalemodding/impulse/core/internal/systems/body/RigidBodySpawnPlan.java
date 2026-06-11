package dev.hytalemodding.impulse.core.internal.systems.body;

import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyPersistenceMode;
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import dev.hytalemodding.impulse.core.plugin.components.PhysicsBodyCollisionComponent;
import dev.hytalemodding.impulse.core.plugin.components.PhysicsBodyComponentValues;
import dev.hytalemodding.impulse.core.plugin.components.PhysicsBodyDynamicsComponent;
import dev.hytalemodding.impulse.core.plugin.components.PhysicsBodyIdentityComponent;
import dev.hytalemodding.impulse.core.plugin.components.PhysicsBodyMaterialComponent;
import dev.hytalemodding.impulse.core.plugin.components.PhysicsBodyShapeComponent;
import dev.hytalemodding.impulse.core.plugin.simulation.PhysicsShapeSpec;
import dev.hytalemodding.impulse.core.plugin.simulation.RigidBodySpawnSettings;
import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

record RigidBodySpawnPlan(@Nonnull RigidBodyKey bodyKey,
                           @Nonnull SpaceId spaceId,
                           @Nonnull PhysicsShapeSpec shape,
                           @Nonnull PhysicsBodyType bodyType,
                           float mass,
                           @Nonnull RigidBodySpawnSettings settings,
                           @Nonnull PhysicsBodyPersistenceMode persistenceMode) {

    private static final String MISSING_SPACE =
        "PhysicsBodyIdentityComponent must hold a positive explicit SpaceId";

    RigidBodySpawnPlan {
        Objects.requireNonNull(bodyKey, "bodyKey");
        Objects.requireNonNull(spaceId, "spaceId");
        Objects.requireNonNull(shape, "shape");
        Objects.requireNonNull(bodyType, "bodyType");
        Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(persistenceMode, "persistenceMode");
    }

    @Nonnull
    static RigidBodySpawnPlan create(@Nonnull PhysicsBodyIdentityComponent identity,
        @Nullable PhysicsBodyShapeComponent shape,
        @Nullable PhysicsBodyDynamicsComponent dynamics,
        @Nullable PhysicsBodyMaterialComponent material,
        @Nullable PhysicsBodyCollisionComponent collision) {
        Objects.requireNonNull(identity, "identity");
        if (!PhysicsBodyComponentValues.hasExplicitSpace(identity)) {
            throw new IllegalArgumentException(MISSING_SPACE);
        }
        if (shape == null) {
            throw new IllegalArgumentException("PhysicsBodyShapeComponent is required");
        }
        if (dynamics == null) {
            throw new IllegalArgumentException("PhysicsBodyDynamicsComponent is required");
        }
        if (material == null) {
            throw new IllegalArgumentException("PhysicsBodyMaterialComponent is required");
        }
        if (collision == null) {
            throw new IllegalArgumentException("PhysicsBodyCollisionComponent is required");
        }
        assert identity.getSpaceId() != null;
        return new RigidBodySpawnPlan(
            identity.getBodyKey(),
            identity.getSpaceId(),
            PhysicsBodyComponentValues.toShapeSpec(shape),
            dynamics.getBodyType(),
            dynamics.getMass(),
            PhysicsBodyComponentValues.toSpawnSettings(dynamics, material, collision),
            identity.getPersistenceMode());
    }

    @Nonnull
    PhysicsShapeSpec requireShape() {
        return shape;
    }
}
