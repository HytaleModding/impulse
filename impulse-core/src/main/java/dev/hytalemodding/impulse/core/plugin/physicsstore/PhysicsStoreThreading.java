package dev.hytalemodding.impulse.core.plugin.physicsstore;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import java.util.Objects;
import javax.annotation.Nonnull;

/**
 * Thread assertions for direct PhysicsStore row and backend access.
 */
public final class PhysicsStoreThreading {

    private PhysicsStoreThreading() {
    }

    @Nonnull
    public static World requireWorldThread(@Nonnull Store<PhysicsStore> store,
        @Nonnull String operation) {
        World world = world(store);
        if (!world.isInThread()) {
            throw new IllegalStateException("Cannot " + operation
                + " outside the owning PhysicsStore world thread");
        }
        return world;
    }

    @Nonnull
    public static World world(@Nonnull Store<PhysicsStore> store) {
        return Objects.requireNonNull(store, "store").getExternalData().getWorld();
    }
}
