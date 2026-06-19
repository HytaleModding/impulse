package dev.hytalemodding.impulse.bullet;

import dev.hytalemodding.impulse.api.BackendId;
import dev.hytalemodding.impulse.api.runtime.PhysicsBackendRuntime;
import dev.hytalemodding.impulse.api.runtime.PhysicsBackendRuntimeProvider;
import dev.hytalemodding.impulse.api.runtime.legacy.LegacyPhysicsBackendRuntime;
import java.util.logging.Level;
import javax.annotation.Nonnull;

/**
 * Runtime-provider service entry point for Bullet.
 */
@SuppressWarnings("removal")
public final class BulletBackendRuntimeProvider implements PhysicsBackendRuntimeProvider {

    private final BulletBackend backend = new BulletBackend();

    @Nonnull
    @Override
    public BackendId getId() {
        return BulletBackend.ID;
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
