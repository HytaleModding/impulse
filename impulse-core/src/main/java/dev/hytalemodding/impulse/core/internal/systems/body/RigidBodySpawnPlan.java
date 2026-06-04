package dev.hytalemodding.impulse.core.internal.systems.body;

import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyKind;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyPersistenceMode;
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import dev.hytalemodding.impulse.core.plugin.components.RigidBodyComponent;
import dev.hytalemodding.impulse.core.plugin.components.RigidBodyComponentValues;
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
                           @Nonnull RigidBodyComponent.Ownership ownership) {

    private static final String MISSING_SPACE =
        "RigidBodyComponent must hold a positive explicit SpaceId";

    RigidBodySpawnPlan {
        Objects.requireNonNull(bodyKey, "bodyKey");
        Objects.requireNonNull(spaceId, "spaceId");
        Objects.requireNonNull(bodyType, "bodyType");
        Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(persistenceMode, "persistenceMode");
        Objects.requireNonNull(ownership, "ownership");
    }

    @Nonnull
    static RigidBodySpawnPlan create(@Nonnull RigidBodyComponent component) {
        Objects.requireNonNull(component, "component");
        if (!RigidBodyComponentValues.hasExplicitSpace(component)) {
            throw new IllegalArgumentException(MISSING_SPACE);
        }
        assert component.getSpaceId() != null;
        RigidBodyComponent.Ownership ownershipValue = component.getOwnership();
        PhysicsShapeSpec shapeSpec = RigidBodyComponentValues.toShapeSpec(component);
        return new RigidBodySpawnPlan(
            component.getBodyKey(),
            component.getSpaceId(),
            shapeSpec,
            component.getBodyType(),
            component.getMass(),
            RigidBodyComponentValues.toSpawnSettings(component),
            component.getPersistenceMode(),
            ownershipValue);
    }

    boolean shouldSpawnBody() {
        return shouldSpawnBody(ownership);
    }

    boolean shouldAttachEntity() {
        return ownership != RigidBodyComponent.Ownership.FULL_DETACHED;
    }

    boolean shouldDestroyOnLifecycleRemoval() {
        return ownership == RigidBodyComponent.Ownership.ENTITY_OWNED;
    }

    @Nonnull
    PhysicsShapeSpec requireShape() {
        if (shape == null) {
            throw new IllegalStateException("Rigid body spawn plan has no shape");
        }
        return shape;
    }

    private static boolean shouldSpawnBody(@Nonnull RigidBodyComponent.Ownership ownership) {
        return ownership != RigidBodyComponent.Ownership.DETACHED_VIEW;
    }
}
