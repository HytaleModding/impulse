package dev.hytalemodding.impulse.api.runtime;

import javax.annotation.Nonnull;
import org.joml.Vector3f;

/**
 * Copied backend contact using backend-local body ids.
 */
public record BackendContact(long bodyAId,
                             long bodyBId,
                             @Nonnull Vector3f pointOnA,
                             @Nonnull Vector3f pointOnB,
                             @Nonnull Vector3f normalOnB,
                             float distance,
                             float impulse) {

    public BackendContact {
        pointOnA = new Vector3f(pointOnA);
        pointOnB = new Vector3f(pointOnB);
        normalOnB = new Vector3f(normalOnB);
    }
}
