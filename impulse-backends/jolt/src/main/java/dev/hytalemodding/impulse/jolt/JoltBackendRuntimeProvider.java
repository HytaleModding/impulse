package dev.hytalemodding.impulse.jolt;

import dev.hytalemodding.impulse.api.BackendId;
import dev.hytalemodding.impulse.api.runtime.PhysicsBackendRuntime;
import dev.hytalemodding.impulse.api.runtime.PhysicsBackendRuntimeProvider;
import javax.annotation.Nonnull;

/**
 * Runtime-provider service entry point for Jolt.
 */
public final class JoltBackendRuntimeProvider implements PhysicsBackendRuntimeProvider {

    private final JoltBackend backend = new JoltBackend();

    @Nonnull
    @Override
    public BackendId getId() {
        return JoltBackend.ID;
    }

    @Override
    public void init() {
        backend.init();
    }

    @Nonnull
    @Override
    public PhysicsBackendRuntime createRuntime() {
        return new JoltBackendRuntime(backend);
    }
}
