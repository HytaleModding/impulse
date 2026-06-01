package dev.hytalemodding.impulse.api;

import java.util.Objects;
import javax.annotation.Nonnull;

/**
 * Copied body activation event emitted by a backend after a completed step.
 */
public record PhysicsBackendBodyActivationEvent(@Nonnull PhysicsBodyActivationPhase phase,
                                                @Nonnull PhysicsBody body) implements PhysicsBackendEvent {

    public PhysicsBackendBodyActivationEvent {
        phase = Objects.requireNonNull(phase, "phase");
        body = Objects.requireNonNull(body, "body");
    }

    @Nonnull
    @Override
    public PhysicsBackendEventKind kind() {
        return phase.eventKind();
    }
}
