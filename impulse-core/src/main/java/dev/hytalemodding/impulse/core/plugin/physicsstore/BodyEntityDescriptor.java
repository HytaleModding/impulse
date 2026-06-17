package dev.hytalemodding.impulse.core.plugin.physicsstore;

import dev.hytalemodding.impulse.core.plugin.components.BodyComponent;
import dev.hytalemodding.impulse.core.plugin.components.ColliderComponent;
import dev.hytalemodding.impulse.core.plugin.components.CollisionFilterComponent;
import dev.hytalemodding.impulse.core.plugin.components.DynamicsComponent;
import dev.hytalemodding.impulse.core.plugin.components.MaterialComponent;
import dev.hytalemodding.impulse.core.plugin.components.ShapeComponent;
import dev.hytalemodding.impulse.core.plugin.components.TargetComponent;
import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Copied component graph for one PhysicsStore body entity.
 */
public record BodyEntityDescriptor(@Nonnull UUID bodyUuid,
                                   @Nonnull BodyComponent body,
                                   @Nonnull DynamicsComponent dynamics,
                                   @Nullable TargetComponent target,
                                   @Nonnull UUID colliderUuid,
                                   @Nonnull ColliderComponent collider,
                                   @Nonnull UUID shapeUuid,
                                   @Nonnull ShapeComponent shape,
                                   @Nonnull UUID materialUuid,
                                   @Nonnull MaterialComponent material,
                                   @Nonnull UUID filterUuid,
                                   @Nonnull CollisionFilterComponent filter) {

    public BodyEntityDescriptor {
        Objects.requireNonNull(bodyUuid, "bodyUuid");
        body = Objects.requireNonNull(body, "body").clone();
        dynamics = Objects.requireNonNull(dynamics, "dynamics").clone();
        target = target != null ? target.clone() : null;
        Objects.requireNonNull(colliderUuid, "colliderUuid");
        collider = Objects.requireNonNull(collider, "collider").clone();
        Objects.requireNonNull(shapeUuid, "shapeUuid");
        shape = Objects.requireNonNull(shape, "shape").clone();
        Objects.requireNonNull(materialUuid, "materialUuid");
        material = Objects.requireNonNull(material, "material").clone();
        Objects.requireNonNull(filterUuid, "filterUuid");
        filter = Objects.requireNonNull(filter, "filter").clone();
    }

    @Nonnull
    public static BodyEntityDescriptor of(@Nonnull UUID bodyUuid,
        @Nonnull BodyComponent body,
        @Nonnull DynamicsComponent dynamics,
        @Nullable TargetComponent target,
        @Nonnull UUID colliderUuid,
        @Nonnull ColliderComponent collider,
        @Nonnull UUID shapeUuid,
        @Nonnull ShapeComponent shape,
        @Nonnull UUID materialUuid,
        @Nonnull MaterialComponent material,
        @Nonnull UUID filterUuid,
        @Nonnull CollisionFilterComponent filter) {
        return new BodyEntityDescriptor(bodyUuid,
            body,
            dynamics,
            target,
            colliderUuid,
            collider,
            shapeUuid,
            shape,
            materialUuid,
            material,
            filterUuid,
            filter);
    }
}
