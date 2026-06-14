package dev.hytalemodding.impulse.early;

import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import javax.annotation.Nonnull;

/**
 * Compile-time contract implemented by World after the Impulse early plugin transform.
 */
public interface PhysicsStoreWorld {

    @Nonnull
    PhysicsStore getPhysicsStore();
}
