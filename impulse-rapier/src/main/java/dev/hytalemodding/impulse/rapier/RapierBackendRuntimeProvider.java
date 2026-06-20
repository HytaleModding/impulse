package dev.hytalemodding.impulse.rapier;

import dev.hytalemodding.impulse.api.BackendId;
import dev.hytalemodding.impulse.api.runtime.PhysicsBackendRuntime;
import dev.hytalemodding.impulse.api.runtime.PhysicsBackendRuntimeProvider;
import javax.annotation.Nonnull;

/**
 * Runtime-provider service entry point for Rapier.
 */
public final class RapierBackendRuntimeProvider implements PhysicsBackendRuntimeProvider {

    private final RapierBackend backend = new RapierBackend();

    @Nonnull
    @Override
    public BackendId getId() {
        return RapierBackend.ID;
    }

    @Override
    public void init() {
        backend.init();
    }

    @Nonnull
    @Override
    public PhysicsBackendRuntime createRuntime() {
        return new RapierBackendRuntime();
    }
}
