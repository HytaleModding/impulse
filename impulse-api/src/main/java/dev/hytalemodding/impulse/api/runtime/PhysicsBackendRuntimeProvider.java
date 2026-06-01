package dev.hytalemodding.impulse.api.runtime;

import dev.hytalemodding.impulse.api.BackendId;
import java.util.logging.Level;
import javax.annotation.Nonnull;

/**
 * Factory for id-only backend runtimes.
 */
public interface PhysicsBackendRuntimeProvider {

    @Nonnull
    BackendId getId();

    default void init() {
    }

    default void setInternalLoggingLevel(@Nonnull Level level) {
    }

    @Nonnull
    PhysicsBackendRuntime createRuntime();
}
