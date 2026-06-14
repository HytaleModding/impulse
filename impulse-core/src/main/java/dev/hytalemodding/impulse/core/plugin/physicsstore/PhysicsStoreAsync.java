package dev.hytalemodding.impulse.core.plugin.physicsstore;

import com.hypixel.hytale.server.core.universe.world.World;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import javax.annotation.Nonnull;

/**
 * Helpers for consuming copied PhysicsStore async results from world-thread code.
 */
public final class PhysicsStoreAsync {

    private PhysicsStoreAsync() {
    }

    @Nonnull
    public static <T> CompletableFuture<Void> acceptOnWorldThread(@Nonnull World world,
        @Nonnull CompletionStage<T> stage,
        @Nonnull Consumer<T> consumer) {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(stage, "stage");
        Objects.requireNonNull(consumer, "consumer");
        CompletableFuture<Void> completion = new CompletableFuture<>();
        stage.whenComplete((value, failure) -> {
            if (failure != null) {
                completion.completeExceptionally(unwrap(failure));
                return;
            }
            try {
                world.execute(() -> accept(value, consumer, completion));
            } catch (RuntimeException exception) {
                completion.completeExceptionally(exception);
            }
        });
        return completion;
    }

    private static <T> void accept(T value,
        @Nonnull Consumer<T> consumer,
        @Nonnull CompletableFuture<Void> completion) {
        try {
            consumer.accept(value);
            completion.complete(null);
        } catch (RuntimeException | Error exception) {
            completion.completeExceptionally(exception);
        }
    }

    @Nonnull
    private static Throwable unwrap(@Nonnull Throwable failure) {
        if (failure instanceof CompletionException completionException
            && completionException.getCause() != null) {
            return completionException.getCause();
        }
        return failure;
    }
}
