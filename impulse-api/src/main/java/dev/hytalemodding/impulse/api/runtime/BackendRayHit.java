package dev.hytalemodding.impulse.api.runtime;

import javax.annotation.Nonnull;
import org.joml.Vector3f;

/**
 * Copied backend ray hit using backend-local body id.
 */
public record BackendRayHit(long bodyId,
                            @Nonnull Vector3f point,
                            @Nonnull Vector3f normal,
                            float fraction,
                            float distance) {

    public BackendRayHit {
        point = new Vector3f(point);
        normal = new Vector3f(normal);
    }
}
