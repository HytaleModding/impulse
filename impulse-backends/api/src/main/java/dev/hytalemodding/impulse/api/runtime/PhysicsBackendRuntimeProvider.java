package dev.hytalemodding.impulse.api.runtime;

import dev.hytalemodding.impulse.api.BackendId;
import javax.annotation.Nonnull;

/**
 * Factory for id-only backend runtimes.
 */
public interface PhysicsBackendRuntimeProvider {

    @Nonnull
    BackendId getId();

    default void init() {
    }

    @Nonnull
    PhysicsBackendRuntime createRuntime();
}
