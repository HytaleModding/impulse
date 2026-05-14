package dev.hytalemodding.impulse.api;

import javax.annotation.Nonnull;
import org.joml.Vector3f;

public record PhysicsContact(@Nonnull PhysicsBody bodyA,
                             @Nonnull PhysicsBody bodyB,
                             @Nonnull Vector3f pointOnA,
                             @Nonnull Vector3f pointOnB,
                             @Nonnull Vector3f normalOnB,
                             float distance,
                             float impulse) {

    public PhysicsContact {
        pointOnA = new Vector3f(pointOnA);
        pointOnB = new Vector3f(pointOnB);
        normalOnB = new Vector3f(normalOnB);
    }
}
