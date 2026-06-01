package dev.hytalemodding.impulse.api;

import java.util.Objects;
import javax.annotation.Nonnull;
import org.joml.Vector3f;

/**
 * Copied contact event emitted by a backend after a completed step.
 */
public record PhysicsBackendContactEvent(@Nonnull PhysicsContactPhase phase,
                                         @Nonnull PhysicsBody bodyA,
                                         @Nonnull PhysicsBody bodyB,
                                         @Nonnull Vector3f pointOnA,
                                         @Nonnull Vector3f pointOnB,
                                         @Nonnull Vector3f normalOnB,
                                         float distance,
                                         float impulse) implements PhysicsBackendEvent {

    public PhysicsBackendContactEvent {
        phase = Objects.requireNonNull(phase, "phase");
        bodyA = Objects.requireNonNull(bodyA, "bodyA");
        bodyB = Objects.requireNonNull(bodyB, "bodyB");
        pointOnA = new Vector3f(Objects.requireNonNull(pointOnA, "pointOnA"));
        pointOnB = new Vector3f(Objects.requireNonNull(pointOnB, "pointOnB"));
        normalOnB = new Vector3f(Objects.requireNonNull(normalOnB, "normalOnB"));
    }

    @Nonnull
    @Override
    public PhysicsBackendEventKind kind() {
        return phase.eventKind();
    }

    @Nonnull
    @Override
    public Vector3f pointOnA() {
        return new Vector3f(pointOnA);
    }

    @Nonnull
    @Override
    public Vector3f pointOnB() {
        return new Vector3f(pointOnB);
    }

    @Nonnull
    @Override
    public Vector3f normalOnB() {
        return new Vector3f(normalOnB);
    }
}
