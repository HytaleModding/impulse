package dev.hytalemodding.impulse.early;

import com.hypixel.hytale.component.IResourceStorage;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.Consumer;
import javax.annotation.Nonnull;

public final class PhysicsStoreHooks {

    @Nonnull
    private static final Set<Consumer<PhysicsStore>> SHUTDOWN_HOOKS =
        new CopyOnWriteArraySet<>();
    @Nonnull
    private static final Set<TickGate> TICK_GATES = new CopyOnWriteArraySet<>();

    private PhysicsStoreHooks() {
    }

    public static void registerShutdownHook(@Nonnull Consumer<PhysicsStore> hook) {
        SHUTDOWN_HOOKS.add(Objects.requireNonNull(hook, "hook"));
    }

    public static void unregisterShutdownHook(@Nonnull Consumer<PhysicsStore> hook) {
        SHUTDOWN_HOOKS.remove(Objects.requireNonNull(hook, "hook"));
    }

    public static void registerTickGate(@Nonnull TickGate gate) {
        TICK_GATES.add(Objects.requireNonNull(gate, "gate"));
    }

    public static void unregisterTickGate(@Nonnull TickGate gate) {
        TICK_GATES.remove(Objects.requireNonNull(gate, "gate"));
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
        PhysicsStore checked = Objects.requireNonNull(physicsStore, "physicsStore");
        if (!shouldTick(checked, dt)) {
            return;
        }
        Store<PhysicsStore> store = checked.getStore();
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
        PhysicsStore checked = Objects.requireNonNull(physicsStore, "physicsStore");
        RuntimeException hookFailure = null;
        for (Consumer<PhysicsStore> hook : SHUTDOWN_HOOKS) {
            try {
                hook.accept(checked);
            } catch (RuntimeException exception) {
                if (hookFailure == null) {
                    hookFailure = exception;
                } else {
                    hookFailure.addSuppressed(exception);
                }
            }
        }
        try {
            checked.shutdown();
        } catch (RuntimeException exception) {
            if (hookFailure != null) {
                exception.addSuppressed(hookFailure);
            }
            throw exception;
        }
        if (hookFailure != null) {
            throw hookFailure;
        }
    }

    private static boolean shouldTick(@Nonnull PhysicsStore physicsStore, float dt) {
        for (TickGate gate : TICK_GATES) {
            if (!gate.shouldTick(physicsStore, dt)) {
                return false;
            }
        }
        return true;
    }

    @FunctionalInterface
    public interface TickGate {

        boolean shouldTick(@Nonnull PhysicsStore physicsStore, float dt);
    }
}
