package dev.hytalemodding;

import dev.hytalemodding.impulse.api.BackendId;
import dev.hytalemodding.impulse.api.PhysicsBackend;
import dev.hytalemodding.impulse.api.PhysicsSpace;
import javax.annotation.Nonnull;

public class NativeBackend implements PhysicsBackend {


    @Nonnull
    @Override
    public BackendId getId() {
        return null;
    }

    @Override
    public void init() {

    }

    @Nonnull
    @Override
    public PhysicsSpace createSpace() {
        return null;
    }
}
