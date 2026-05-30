package dev.hytalemodding.impulse.core.plugin.simulation;

import dev.hytalemodding.impulse.api.PhysicsAxis;
import dev.hytalemodding.impulse.api.ShapeType;
import java.util.Objects;
import javax.annotation.Nonnull;
import org.joml.Vector3f;

/**
 * Value-only rigid body shape description.
 */
public final class PhysicsShapeSpec {

    @Nonnull
    private final ShapeType type;
    private final float halfExtentX;
    private final float halfExtentY;
    private final float halfExtentZ;
    private final float radius;
    private final float halfHeight;
    @Nonnull
    private final PhysicsAxis axis;
    private final float groundY;

    public PhysicsShapeSpec(@Nonnull ShapeType type,
        @Nonnull Vector3f halfExtents,
        float radius,
        float halfHeight,
        @Nonnull PhysicsAxis axis,
        float groundY) {
        this(type,
            Objects.requireNonNull(halfExtents, "halfExtents").x,
            halfExtents.y,
            halfExtents.z,
            radius,
            halfHeight,
            axis,
            groundY);
    }

    private PhysicsShapeSpec(@Nonnull ShapeType type,
        float halfExtentX,
        float halfExtentY,
        float halfExtentZ,
        float radius,
        float halfHeight,
        @Nonnull PhysicsAxis axis,
        float groundY) {
        this.type = Objects.requireNonNull(type, "type");
        this.halfExtentX = halfExtentX;
        this.halfExtentY = halfExtentY;
        this.halfExtentZ = halfExtentZ;
        this.radius = radius;
        this.halfHeight = halfHeight;
        this.axis = Objects.requireNonNull(axis, "axis");
        this.groundY = groundY;
    }

    @Nonnull
    public static PhysicsShapeSpec box(float halfX,
        float halfY,
        float halfZ) {
        return new PhysicsShapeSpec(ShapeType.BOX,
            halfX,
            halfY,
            halfZ,
            0.0f,
            0.0f,
            PhysicsAxis.Y,
            0.0f);
    }

    @Nonnull
    public static PhysicsShapeSpec sphere(float radius) {
        return new PhysicsShapeSpec(ShapeType.SPHERE,
            0.0f,
            0.0f,
            0.0f,
            radius,
            0.0f,
            PhysicsAxis.Y,
            0.0f);
    }

    @Nonnull
    public static PhysicsShapeSpec capsule(float radius,
        float halfHeight,
        @Nonnull PhysicsAxis axis) {
        return new PhysicsShapeSpec(ShapeType.CAPSULE,
            0.0f,
            0.0f,
            0.0f,
            radius,
            halfHeight,
            axis,
            0.0f);
    }

    @Nonnull
    public static PhysicsShapeSpec cylinder(float radius,
        float halfHeight,
        @Nonnull PhysicsAxis axis) {
        return new PhysicsShapeSpec(ShapeType.CYLINDER,
            0.0f,
            0.0f,
            0.0f,
            radius,
            halfHeight,
            axis,
            0.0f);
    }

    @Nonnull
    public static PhysicsShapeSpec cone(float radius,
        float halfHeight,
        @Nonnull PhysicsAxis axis) {
        return new PhysicsShapeSpec(ShapeType.CONE,
            0.0f,
            0.0f,
            0.0f,
            radius,
            halfHeight,
            axis,
            0.0f);
    }

    @Nonnull
    public static PhysicsShapeSpec plane(float groundY) {
        return new PhysicsShapeSpec(ShapeType.PLANE,
            0.0f,
            0.0f,
            0.0f,
            0.0f,
            0.0f,
            PhysicsAxis.Y,
            groundY);
    }

    @Nonnull
    public ShapeType type() {
        return type;
    }

    @Nonnull
    public Vector3f halfExtents() {
        return new Vector3f(halfExtentX, halfExtentY, halfExtentZ);
    }

    public float halfExtentX() {
        return halfExtentX;
    }

    public float halfExtentY() {
        return halfExtentY;
    }

    public float halfExtentZ() {
        return halfExtentZ;
    }

    public float radius() {
        return radius;
    }

    public float halfHeight() {
        return halfHeight;
    }

    @Nonnull
    public PhysicsAxis axis() {
        return axis;
    }

    public float groundY() {
        return groundY;
    }

    @Override
    public boolean equals(Object other) {
        return this == other
            || other instanceof PhysicsShapeSpec spec
                && type == spec.type
                && Float.floatToIntBits(halfExtentX) == Float.floatToIntBits(spec.halfExtentX)
                && Float.floatToIntBits(halfExtentY) == Float.floatToIntBits(spec.halfExtentY)
                && Float.floatToIntBits(halfExtentZ) == Float.floatToIntBits(spec.halfExtentZ)
                && Float.floatToIntBits(radius) == Float.floatToIntBits(spec.radius)
                && Float.floatToIntBits(halfHeight) == Float.floatToIntBits(spec.halfHeight)
                && axis == spec.axis
                && Float.floatToIntBits(groundY) == Float.floatToIntBits(spec.groundY);
    }

    @Override
    public int hashCode() {
        int result = type.hashCode();
        result = 31 * result + Float.hashCode(halfExtentX);
        result = 31 * result + Float.hashCode(halfExtentY);
        result = 31 * result + Float.hashCode(halfExtentZ);
        result = 31 * result + Float.hashCode(radius);
        result = 31 * result + Float.hashCode(halfHeight);
        result = 31 * result + axis.hashCode();
        return 31 * result + Float.hashCode(groundY);
    }

    @Nonnull
    @Override
    public String toString() {
        return "PhysicsShapeSpec[type=" + type
            + ", halfExtentX=" + halfExtentX
            + ", halfExtentY=" + halfExtentY
            + ", halfExtentZ=" + halfExtentZ
            + ", radius=" + radius
            + ", halfHeight=" + halfHeight
            + ", axis=" + axis
            + ", groundY=" + groundY
            + ']';
    }
}
