package dev.hytalemodding.impulse.bullet;

import dev.hytalemodding.impulse.api.PhysicsBackend;
import dev.hytalemodding.impulse.api.testsupport.PhysicsBackendContractTest;
import javax.annotation.Nonnull;

class BulletBackendContractTest extends PhysicsBackendContractTest {

    @Nonnull
    @Override
    protected PhysicsBackend createBackend() {
        return new BulletBackend();
    }
}
