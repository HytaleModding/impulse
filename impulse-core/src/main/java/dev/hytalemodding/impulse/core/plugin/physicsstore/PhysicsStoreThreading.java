package dev.hytalemodding.impulse.core.plugin.physicsstore;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.early.PhysicsStoreWorld;
import dev.hytalemodding.impulse.core.internal.physicsstore.PhysicsStoreAsyncCompletions;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
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

    @Nonnull
    public static CompletionStage<Void> executeOnWorldThread(@Nonnull World world,
        @Nonnull String operation,
        @Nonnull Consumer<Store<PhysicsStore>> mutation) {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(mutation, "mutation");
        CompletableFuture<Void> completion = new CompletableFuture<>();
        Runnable task = () -> execute(world, operation, mutation, completion);
        try {
            if (world.isInThread()) {
                task.run();
            } else {
                world.execute(task);
            }
        } catch (RuntimeException exception) {
            PhysicsStoreAsyncCompletions.fail(completion, exception);
        }
        return completion.minimalCompletionStage();
    }

    private static void execute(@Nonnull World world,
        @Nonnull String operation,
        @Nonnull Consumer<Store<PhysicsStore>> mutation,
        @Nonnull CompletableFuture<Void> completion) {
        try {
            Store<PhysicsStore> store = store(world);
            requireWorldThread(store, operation);
            mutation.accept(store);
            PhysicsStoreAsyncCompletions.complete(completion, null);
        } catch (RuntimeException | Error throwable) {
            PhysicsStoreAsyncCompletions.fail(completion, throwable);
        }
    }

    @Nonnull
    private static Store<PhysicsStore> store(@Nonnull World world) {
        return ((PhysicsStoreWorld) Objects.requireNonNull(world, "world")).getPhysicsStore()
            .getStore();
    }
}
