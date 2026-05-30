package dev.hytalemodding.impulse.core.plugin.simulation;

import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.ShapeType;
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3f;

/**
 * Copied raycast result that does not expose a live backend body.
 */
public record RaycastHitView(@Nullable RigidBodyKey bodyKey,
                             @Nonnull PhysicsBodyType bodyType,
                             float pointX,
                             float pointY,
                             float pointZ,
                             float normalX,
                             float normalY,
                             float normalZ,
                             @Nonnull ShapeType shapeType,
                             float fraction,
                             float distance) {

    public RaycastHitView(@Nullable RigidBodyKey bodyKey,
        @Nonnull PhysicsBodyType bodyType,
        @Nonnull Vector3f point,
        @Nonnull Vector3f normal,
        @Nonnull ShapeType shapeType,
        float fraction,
        float distance) {
        this(bodyKey,
            bodyType,
            Objects.requireNonNull(point, "point").x,
            point.y,
            point.z,
            Objects.requireNonNull(normal, "normal").x,
            normal.y,
            normal.z,
            shapeType,
            fraction,
            distance);
    }

    public RaycastHitView {
        Objects.requireNonNull(bodyType, "bodyType");
        Objects.requireNonNull(shapeType, "shapeType");
    }

    @Nonnull
    public Vector3f point() {
        return new Vector3f(pointX, pointY, pointZ);
    }

    @Nonnull
    public Vector3f normal() {
        return new Vector3f(normalX, normalY, normalZ);
    }

    @Nonnull
    public Vector3f copyPointTo(@Nonnull Vector3f target) {
        return Objects.requireNonNull(target, "target").set(pointX, pointY, pointZ);
    }

    @Nonnull
    public Vector3f copyNormalTo(@Nonnull Vector3f target) {
        return Objects.requireNonNull(target, "target").set(normalX, normalY, normalZ);
    }
}
