package dev.hytalemodding.impulse.core.plugin.simulation;

import java.util.Objects;
import javax.annotation.Nonnull;
import org.joml.Vector3f;

/**
 * Value-only ray segment for batched raycast queries.
 */
public record RaycastSegment(float fromX, float fromY, float fromZ, float toX, float toY,
                             float toZ) {

    public RaycastSegment(@Nonnull Vector3f from,
        @Nonnull Vector3f to) {
        this(Objects.requireNonNull(from, "from").x,
            from.y,
            from.z,
            Objects.requireNonNull(to, "to").x,
            to.y,
            to.z);
    }

    public RaycastSegment(@Nonnull RaycastSegment segment) {
        this(Objects.requireNonNull(segment, "segment").fromX,
            segment.fromY,
            segment.fromZ,
            segment.toX,
            segment.toY,
            segment.toZ);
    }

    @Nonnull
    public Vector3f from() {
        return new Vector3f(fromX, fromY, fromZ);
    }

    @Nonnull
    public Vector3f to() {
        return new Vector3f(toX, toY, toZ);
    }

    @Nonnull
    public Vector3f copyFrom(@Nonnull Vector3f target) {
        return target.set(fromX, fromY, fromZ);
    }

    @Nonnull
    public Vector3f copyTo(@Nonnull Vector3f target) {
        return target.set(toX, toY, toZ);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof RaycastSegment(
            float x, float y, float z, float toX1, float toY1, float toZ1
        ))) {
            return false;
        }
        return Float.compare(x, fromX) == 0
            && Float.compare(y, fromY) == 0
            && Float.compare(z, fromZ) == 0
            && Float.compare(toX1, toX) == 0
            && Float.compare(toY1, toY) == 0
            && Float.compare(toZ1, toZ) == 0;
    }

    @Override
    public int hashCode() {
        int result = Float.hashCode(fromX);
        result = 31 * result + Float.hashCode(fromY);
        result = 31 * result + Float.hashCode(fromZ);
        result = 31 * result + Float.hashCode(toX);
        result = 31 * result + Float.hashCode(toY);
        result = 31 * result + Float.hashCode(toZ);
        return result;
    }

    @Nonnull
    @Override
    public String toString() {
        return "RaycastSegment[from=(" + fromX + ", " + fromY + ", " + fromZ
            + "), to=(" + toX + ", " + toY + ", " + toZ + ")]";
    }
}
