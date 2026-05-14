package dev.hytalemodding.impulse.api;

import javax.annotation.Nonnull;
import org.joml.Vector3f;

public record PhysicsRayHit(@Nonnull PhysicsBody body,
                            @Nonnull Vector3f point,
                            @Nonnull Vector3f normal,
                            float fraction,
                            float distance) {

    public PhysicsRayHit {
        point = new Vector3f(point);
        normal = new Vector3f(normal);
    }
}
