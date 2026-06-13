package dev.hytalemodding.impulse.core.plugin.physicsstore.requests;

import dev.hytalemodding.impulse.core.plugin.physicsstore.components.BodyComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.ColliderComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.CollisionFilterComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.DynamicsComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.MaterialComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.ShapeComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.TargetComponent;
import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Copied request that authors a single-body graph in PhysicsStore.
 */
public record BodyUpsertRequest(@Nonnull UUID requestUuid,
                                @Nonnull UUID bodyUuid,
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
                                @Nonnull CollisionFilterComponent filter)
    implements PhysicsStoreRequest {

    public BodyUpsertRequest {
        Objects.requireNonNull(requestUuid, "requestUuid");
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
    public static BodyUpsertRequest of(@Nonnull UUID bodyUuid,
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
        return new BodyUpsertRequest(UUID.randomUUID(),
            bodyUuid,
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
