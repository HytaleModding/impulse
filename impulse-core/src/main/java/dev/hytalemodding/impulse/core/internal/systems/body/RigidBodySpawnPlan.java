package dev.hytalemodding.impulse.core.internal.systems.body;

import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyKind;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyPersistenceMode;
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import dev.hytalemodding.impulse.core.plugin.components.RigidBodyCollisionComponent;
import dev.hytalemodding.impulse.core.plugin.components.RigidBodyComponentValues;
import dev.hytalemodding.impulse.core.plugin.components.RigidBodyKeyComponent;
import dev.hytalemodding.impulse.core.plugin.components.RigidBodyMassComponent;
import dev.hytalemodding.impulse.core.plugin.components.RigidBodyMaterialComponent;
import dev.hytalemodding.impulse.core.plugin.components.RigidBodyOwnershipComponent;
import dev.hytalemodding.impulse.core.plugin.components.RigidBodyPersistenceComponent;
import dev.hytalemodding.impulse.core.plugin.components.RigidBodyShapeComponent;
import dev.hytalemodding.impulse.core.plugin.components.RigidBodySpaceComponent;
import dev.hytalemodding.impulse.core.plugin.components.RigidBodyTypeComponent;
import dev.hytalemodding.impulse.core.plugin.simulation.PhysicsShapeSpec;
import dev.hytalemodding.impulse.core.plugin.simulation.RigidBodySpawnSettings;
import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

record RigidBodySpawnPlan(@Nonnull RigidBodyKey bodyKey,
                           @Nonnull SpaceId spaceId,
                           @Nullable PhysicsShapeSpec shape,
                           @Nonnull PhysicsBodyType bodyType,
                           float mass,
                           @Nonnull RigidBodySpawnSettings settings,
                           @Nonnull PhysicsBodyPersistenceMode persistenceMode,
                           @Nonnull RigidBodyOwnershipComponent.Ownership ownership) {

    private static final String MISSING_SPACE =
        "RigidBodySpaceComponent must hold a positive explicit SpaceId";

    RigidBodySpawnPlan {
        Objects.requireNonNull(bodyKey, "bodyKey");
        Objects.requireNonNull(spaceId, "spaceId");
        Objects.requireNonNull(bodyType, "bodyType");
        Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(persistenceMode, "persistenceMode");
        Objects.requireNonNull(ownership, "ownership");
    }

    @Nonnull
    static RigidBodySpawnPlan create(@Nonnull RigidBodyKeyComponent key,
        @Nullable RigidBodySpaceComponent space,
        @Nullable RigidBodyShapeComponent shape,
        @Nullable RigidBodyTypeComponent type,
        @Nullable RigidBodyMassComponent mass,
        @Nullable RigidBodyMaterialComponent material,
        @Nullable RigidBodyCollisionComponent collision,
        @Nullable RigidBodyPersistenceComponent persistence,
        @Nullable RigidBodyOwnershipComponent ownership) {
        Objects.requireNonNull(key, "key");
        if (!RigidBodyComponentValues.hasExplicitSpace(space)) {
            throw new IllegalArgumentException(MISSING_SPACE);
        }
        assert space != null;
        RigidBodyOwnershipComponent.Ownership ownershipValue = ownership != null
            ? ownership.getOwnership()
            : RigidBodyOwnershipComponent.Ownership.ENTITY_OWNED;
        PhysicsShapeSpec shapeSpec = shouldSpawnBody(ownershipValue)
            ? RigidBodyComponentValues.toShapeSpec(requireShape(shape))
            : shape != null ? RigidBodyComponentValues.toShapeSpec(shape) : null;
        return new RigidBodySpawnPlan(
            key.getBodyKey(),
            space.getSpaceId(),
            shapeSpec,
            type != null ? type.getBodyType() : PhysicsBodyType.DYNAMIC,
            mass != null ? mass.getMass() : 1.0f,
            RigidBodyComponentValues.toSpawnSettings(material, collision),
            persistence != null
                ? persistence.getPersistenceMode()
                : PhysicsBodyPersistenceMode.RUNTIME_ONLY,
            ownershipValue);
    }

    boolean shouldSpawnBody() {
        return shouldSpawnBody(ownership);
    }

    boolean shouldAttachEntity() {
        return ownership != RigidBodyOwnershipComponent.Ownership.FULL_DETACHED;
    }

    boolean shouldDestroyOnLifecycleRemoval() {
        return ownership == RigidBodyOwnershipComponent.Ownership.ENTITY_OWNED;
    }

    @Nonnull
    PhysicsShapeSpec requireShape() {
        if (shape == null) {
            throw new IllegalStateException("Rigid body spawn plan has no shape");
        }
        return shape;
    }

    private static boolean shouldSpawnBody(@Nonnull RigidBodyOwnershipComponent.Ownership ownership) {
        return ownership != RigidBodyOwnershipComponent.Ownership.DETACHED_VIEW;
    }

    @Nonnull
    private static RigidBodyShapeComponent requireShape(@Nullable RigidBodyShapeComponent shape) {
        if (shape == null) {
            throw new IllegalArgumentException("RigidBodyShapeComponent is required for owned rigid bodies");
        }
        return shape;
    }
}
