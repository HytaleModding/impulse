package dev.hytalemodding.impulse.api;

import javax.annotation.Nonnull;

/**
 * Body activation lifecycle phase reported by a backend event batch.
 */
public enum PhysicsBodyActivationPhase {
    ACTIVATED(PhysicsBackendEventKind.BODY_ACTIVATED),
    DEACTIVATED(PhysicsBackendEventKind.BODY_DEACTIVATED);

    @Nonnull
    private final PhysicsBackendEventKind eventKind;

    PhysicsBodyActivationPhase(@Nonnull PhysicsBackendEventKind eventKind) {
        this.eventKind = eventKind;
    }

    @Nonnull
    public PhysicsBackendEventKind eventKind() {
        return eventKind;
    }
}
