package dev.hytalemodding.impulse.rapier;

import dev.hytalemodding.impulse.api.BackendId;
import dev.hytalemodding.impulse.api.PhysicsBackend;
import dev.hytalemodding.impulse.api.PhysicsSpace;
import dev.hytalemodding.impulse.api.SpaceId;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nonnull;

public final class RapierBackend implements PhysicsBackend {

    public static final BackendId ID = new BackendId("impulse:rapier");
    private static final Logger LOGGER = Logger.getLogger("Impulse");
    private static final AtomicInteger SPACES_CREATED = new AtomicInteger(0);

    private volatile boolean initialized;

    @Nonnull
    @Override
    public BackendId getId() {
        return ID;
    }

    @Override
    public synchronized void init() {
        if (initialized) {
            return;
        }

        RapierNative.load();
        initialized = true;
        LOGGER.log(Level.INFO, "Rapier backend initialized");
    }

    @Nonnull
    @Override
    public PhysicsSpace createSpace() {
        return createSpace(SpaceId.next());
    }

    @Nonnull
    @Override
    public PhysicsSpace createSpace(@Nonnull SpaceId spaceId) {
        int count = SPACES_CREATED.incrementAndGet();
        LOGGER.log(Level.FINE,
            "Creating Rapier physics space #" + count + " on thread "
                + Thread.currentThread().getName());
        return new RapierSpace(spaceId, this, RapierNative.createSpaceNative());
    }
}
