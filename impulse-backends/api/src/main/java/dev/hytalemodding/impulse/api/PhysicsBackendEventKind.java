package dev.hytalemodding.impulse.api;

/**
 * Backend event discriminator for bounded post-step batches.
 */
public enum PhysicsBackendEventKind {
    CONTACT_STARTED,
    CONTACT_PERSISTED,
    CONTACT_ENDED,
    CONTACT_OBSERVED,
    CONTACT_FORCE,
    BODY_ACTIVATED,
    BODY_DEACTIVATED,
    JOINT_BROKEN
}
