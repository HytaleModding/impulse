package dev.hytalemodding.impulse.api;

import javax.annotation.Nonnull;
import org.joml.Vector3f;

@Deprecated(forRemoval = true)
public record PhysicsRayHit(@Nonnull PhysicsBody body,
                            @Nonnull Vector3f point,
                            @Nonnull Vector3f normal,
                            float fraction,
                            float distance) {

    public PhysicsRayHit {
        point = new Vector3f(point);
        normal = new Vector3f(normal);
    }

    @Nonnull
    @Override
    public Vector3f point() {
        return new Vector3f(point);
    }

    @Nonnull
    @Override
    public Vector3f normal() {
        return new Vector3f(normal);
    }
}
