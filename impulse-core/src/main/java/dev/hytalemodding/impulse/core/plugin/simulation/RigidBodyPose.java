package dev.hytalemodding.impulse.core.plugin.simulation;

import java.util.Objects;
import javax.annotation.Nonnull;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Copied rigid body transform.
 */
public record RigidBodyPose(@Nonnull Vector3f position,
                            @Nonnull Quaternionf rotation) {

    public RigidBodyPose {
        position = new Vector3f(Objects.requireNonNull(position, "position"));
        rotation = new Quaternionf(Objects.requireNonNull(rotation, "rotation"));
    }

    @Nonnull
    public static RigidBodyPose of(@Nonnull Vector3f position,
        @Nonnull Quaternionf rotation) {
        return new RigidBodyPose(position, rotation);
    }

    @Nonnull
    @Override
    public Vector3f position() {
        return new Vector3f(position);
    }

    @Nonnull
    @Override
    public Quaternionf rotation() {
        return new Quaternionf(rotation);
    }
}
