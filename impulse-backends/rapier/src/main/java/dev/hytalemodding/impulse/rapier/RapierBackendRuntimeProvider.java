package dev.hytalemodding.impulse.rapier;

import dev.hytalemodding.impulse.api.BackendId;
import dev.hytalemodding.impulse.api.runtime.PhysicsBackendRuntime;
import dev.hytalemodding.impulse.api.runtime.PhysicsBackendRuntimeProvider;
import javax.annotation.Nonnull;

/**
 * Runtime-provider service entry point for Rapier.
 */
public final class RapierBackendRuntimeProvider implements PhysicsBackendRuntimeProvider {

    private static final BackendId ID = new BackendId("impulse:rapier");

    @Nonnull
    @Override
    public BackendId getId() {
        return ID;
    }

    @Override
    public void init() {
        RapierNative.load();
    }

    @Nonnull
    @Override
    public PhysicsBackendRuntime createRuntime() {
        return new RapierBackendRuntime();
    }
}
