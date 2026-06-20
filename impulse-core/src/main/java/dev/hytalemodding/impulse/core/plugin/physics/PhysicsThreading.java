package dev.hytalemodding.impulse.core.plugin.physics;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.early.PhysicsStoreWorld;
import dev.hytalemodding.impulse.core.internal.physics.PhysicsAsyncCompletions;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsStepSchedulerResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsStoreReadQueueResource;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Thread assertions for direct PhysicsStore entity and backend access.
 */
public final class PhysicsThreading {

    private PhysicsThreading() {
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

    public static void requireBackendIdle(@Nonnull Store<PhysicsStore> store,
        @Nonnull String operation) {
        requireWorldThread(store, operation);
        PhysicsStepSchedulerResource scheduler = store.getResource(
            PhysicsStepSchedulerResource.getResourceType());
        if (scheduler.isStepPending()) {
            throw new IllegalStateException("Cannot " + operation
                + " while a PhysicsStore owner-lane step is pending");
        }
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
            PhysicsAsyncCompletions.fail(completion, exception);
        }
        return completion.minimalCompletionStage();
    }

    @Nonnull
    public static <R> CompletionStage<R> enqueueReadOnWorldThread(
        @Nonnull Store<PhysicsStore> store,
        @Nonnull String operation,
        @Nonnull Function<Store<PhysicsStore>, R> read) {
        return enqueueReadOnWorldThread(world(store), operation, read);
    }

    @Nonnull
    public static <R> CompletionStage<R> enqueueReadOnWorldThread(@Nonnull World world,
        @Nonnull String operation,
        @Nonnull Function<Store<PhysicsStore>, R> read) {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(read, "read");
        CompletableFuture<R> completion = new CompletableFuture<>();
        Runnable task = () -> enqueueRead(world, operation, read, completion);
        try {
            if (world.isInThread()) {
                task.run();
            } else {
                world.execute(task);
            }
        } catch (RuntimeException exception) {
            PhysicsAsyncCompletions.fail(completion, exception);
        }
        return completion.minimalCompletionStage();
    }

    @Nonnull
    public static <R> CompletionStage<R> callWhenBackendIdleOnWorldThread(@Nonnull World world,
        @Nonnull String operation,
        @Nonnull Function<Store<PhysicsStore>, R> action) {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(action, "action");
        CompletableFuture<R> completion = new CompletableFuture<>();
        Runnable task = () -> callWhenBackendIdle(world, operation, action, completion);
        try {
            if (world.isInThread()) {
                task.run();
            } else {
                world.execute(task);
            }
        } catch (RuntimeException exception) {
            PhysicsAsyncCompletions.fail(completion, exception);
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
            PhysicsAsyncCompletions.complete(completion, null);
        } catch (RuntimeException | Error throwable) {
            PhysicsAsyncCompletions.fail(completion, throwable);
        }
    }

    private static <R> void callWhenBackendIdle(@Nonnull World world,
        @Nonnull String operation,
        @Nonnull Function<Store<PhysicsStore>, R> action,
        @Nonnull CompletableFuture<R> completion) {
        try {
            Store<PhysicsStore> store = store(world);
            requireWorldThread(store, operation);
            PhysicsStepSchedulerResource scheduler = store.getResource(
                PhysicsStepSchedulerResource.getResourceType());
            if (scheduler.isStepPending()) {
                scheduler.whenIdle()
                    .whenComplete((_, failure) -> rescheduleBackendIdleCall(world,
                        operation,
                        action,
                        completion,
                        failure));
                return;
            }
            PhysicsAsyncCompletions.complete(completion, action.apply(store));
        } catch (RuntimeException | Error throwable) {
            PhysicsAsyncCompletions.fail(completion, throwable);
        }
    }

    private static <R> void rescheduleBackendIdleCall(@Nonnull World world,
        @Nonnull String operation,
        @Nonnull Function<Store<PhysicsStore>, R> action,
        @Nonnull CompletableFuture<R> completion,
        Throwable failure) {
        if (failure != null) {
            PhysicsAsyncCompletions.fail(completion, failure);
            return;
        }
        try {
            world.execute(() -> callWhenBackendIdle(world, operation, action, completion));
        } catch (RuntimeException exception) {
            PhysicsAsyncCompletions.fail(completion, exception);
        }
    }

    private static <R> void enqueueRead(@Nonnull World world,
        @Nonnull String operation,
        @Nonnull Function<Store<PhysicsStore>, R> read,
        @Nonnull CompletableFuture<R> completion) {
        try {
            Store<PhysicsStore> store = store(world);
            requireWorldThread(store, operation);
            store.getResource(PhysicsStoreReadQueueResource.getResourceType())
                .enqueueRead(read)
                .whenComplete((value, failure) -> {
                    if (failure != null) {
                        PhysicsAsyncCompletions.fail(completion, failure);
                    } else {
                        PhysicsAsyncCompletions.complete(completion, value);
                    }
                });
        } catch (RuntimeException | Error throwable) {
            PhysicsAsyncCompletions.fail(completion, throwable);
        }
    }

    @Nonnull
    public static Store<PhysicsStore> store(@Nonnull World world) {
        return ((PhysicsStoreWorld) Objects.requireNonNull(world, "world")).getPhysicsStore()
            .getStore();
    }

    @Nullable
    public static Store<PhysicsStore> storeOrNull(@Nonnull World world) {
        if (!(Objects.requireNonNull(world, "world") instanceof PhysicsStoreWorld physicsStoreWorld)) {
            return null;
        }
        Store<PhysicsStore> store = physicsStoreWorld.getPhysicsStore().getStore();
        return store.isShutdown() ? null : store;
    }
}
