package dev.hytalemodding.impulse.api.runtime;

import dev.hytalemodding.impulse.api.PhysicsAxis;
import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.ShapeType;
import java.util.Objects;
import javax.annotation.Nonnull;

/**
 * Copied backend body creation request.
 */
public record BackendBodySpec(@Nonnull ShapeType shapeType,
                              float halfExtentX,
                              float halfExtentY,
                              float halfExtentZ,
                              float radius,
                              float halfHeight,
                              @Nonnull PhysicsAxis axis,
                              float groundY,
                              float mass,
                              @Nonnull PhysicsBodyType bodyType,
                              float positionX,
                              float positionY,
                              float positionZ,
                              float rotationX,
                              float rotationY,
                              float rotationZ,
                              float rotationW) {

    public BackendBodySpec {
        Objects.requireNonNull(shapeType, "shapeType");
        Objects.requireNonNull(axis, "axis");
        Objects.requireNonNull(bodyType, "bodyType");
    }

    @Nonnull
    public static BackendBodySpec box(float halfX,
        float halfY,
        float halfZ,
        float mass,
        @Nonnull PhysicsBodyType bodyType,
        float positionX,
        float positionY,
        float positionZ) {
        return new BackendBodySpec(ShapeType.BOX,
            halfX,
            halfY,
            halfZ,
            0.0f,
            0.0f,
            PhysicsAxis.Y,
            0.0f,
            mass,
            bodyType,
            positionX,
            positionY,
            positionZ,
            0.0f,
            0.0f,
            0.0f,
            1.0f);
    }

    @Nonnull
    public static BackendBodySpec sphere(float radius,
        float mass,
        @Nonnull PhysicsBodyType bodyType,
        float positionX,
        float positionY,
        float positionZ) {
        return new BackendBodySpec(ShapeType.SPHERE,
            0.0f,
            0.0f,
            0.0f,
            radius,
            0.0f,
            PhysicsAxis.Y,
            0.0f,
            mass,
            bodyType,
            positionX,
            positionY,
            positionZ,
            0.0f,
            0.0f,
            0.0f,
            1.0f);
    }
}
