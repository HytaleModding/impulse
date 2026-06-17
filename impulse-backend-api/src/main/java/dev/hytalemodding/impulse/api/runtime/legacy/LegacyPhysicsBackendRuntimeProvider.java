package dev.hytalemodding.impulse.api.runtime.legacy;

import dev.hytalemodding.impulse.api.BackendId;
import dev.hytalemodding.impulse.api.PhysicsBackend;
import dev.hytalemodding.impulse.api.runtime.PhysicsBackendRuntime;
import dev.hytalemodding.impulse.api.runtime.PhysicsBackendRuntimeProvider;
import java.util.Objects;
import java.util.logging.Level;
import javax.annotation.Nonnull;

/**
 * Runtime-provider adapter for legacy object-based backends.
 */
@Deprecated(forRemoval = true)
public final class LegacyPhysicsBackendRuntimeProvider implements PhysicsBackendRuntimeProvider {

    @Nonnull
    private final PhysicsBackend backend;

    public LegacyPhysicsBackendRuntimeProvider(@Nonnull PhysicsBackend backend) {
        this.backend = Objects.requireNonNull(backend, "backend");
    }

    @Nonnull
    @Override
    public BackendId getId() {
        return backend.getId();
    }

    @Override
    public void init() {
        backend.init();
    }

    @Override
    public void setInternalLoggingLevel(@Nonnull Level level) {
        backend.setInternalLoggingLevel(level);
    }

    @Nonnull
    @Override
    public PhysicsBackendRuntime createRuntime() {
        return new LegacyPhysicsBackendRuntime(backend);
    }
}
