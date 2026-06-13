package dev.hytalemodding.impulse.early;

import com.hypixel.hytale.component.IResourceStorage;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nonnull;

public final class PhysicsStoreHooks {

    private PhysicsStoreHooks() {
    }

    public static void start(@Nonnull PhysicsStore physicsStore,
        @Nonnull IResourceStorage resourceStorage) {
        Objects.requireNonNull(physicsStore, "physicsStore")
            .start(Objects.requireNonNull(resourceStorage, "resourceStorage"));
    }

    public static void tickAfterChunk(@Nonnull PhysicsStore physicsStore,
        float dt,
        boolean ticking,
        boolean paused) {
        Store<PhysicsStore> store = Objects.requireNonNull(physicsStore, "physicsStore")
            .getStore();
        if (ticking && !paused) {
            store.tick(dt);
            return;
        }
        store.pausedTick(dt);
    }

    @Nonnull
    public static CompletableFuture<Void> saveResources(@Nonnull PhysicsStore physicsStore) {
        return Objects.requireNonNull(physicsStore, "physicsStore").getStore().saveAllResources();
    }

    public static void shutdown(@Nonnull PhysicsStore physicsStore) {
        Objects.requireNonNull(physicsStore, "physicsStore").shutdown();
    }
}
