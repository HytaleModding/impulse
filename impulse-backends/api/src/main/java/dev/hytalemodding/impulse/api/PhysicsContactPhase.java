package dev.hytalemodding.impulse.api;

import javax.annotation.Nonnull;

/**
 * Contact lifecycle phase reported by a backend event batch.
 */
public enum PhysicsContactPhase {
    STARTED(PhysicsBackendEventKind.CONTACT_STARTED),
    PERSISTED(PhysicsBackendEventKind.CONTACT_PERSISTED),
    ENDED(PhysicsBackendEventKind.CONTACT_ENDED),
    OBSERVED(PhysicsBackendEventKind.CONTACT_OBSERVED),
    FORCE(PhysicsBackendEventKind.CONTACT_FORCE);

    @Nonnull
    private final PhysicsBackendEventKind eventKind;

    PhysicsContactPhase(@Nonnull PhysicsBackendEventKind eventKind) {
        this.eventKind = eventKind;
    }

    @Nonnull
    public PhysicsBackendEventKind eventKind() {
        return eventKind;
    }
}
