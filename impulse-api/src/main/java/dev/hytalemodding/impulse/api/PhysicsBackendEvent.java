package dev.hytalemodding.impulse.api;

import javax.annotation.Nonnull;

/**
 * Copied backend event produced by a completed backend step.
 */
@Deprecated(forRemoval = true)
public interface PhysicsBackendEvent {

    @Nonnull
    PhysicsBackendEventKind kind();
}
