package dev.hytalemodding.impulse.core.plugin.simulation.query;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import javax.annotation.Nonnull;

/**
 * Completion handle for a typed physics owner query.
 *
 * <p>Queries run on the physics owner and return copied values. They are intended for
 * read-only inspection that needs live backend state without exposing backend handles to plugin
 * code.</p>
 */
public final class PhysicsQueryHandle<R> {

    @Nonnull
    private final PhysicsQuery<R> query;
    @Nonnull
    private final CompletableFuture<R> completion;

    private PhysicsQueryHandle(@Nonnull PhysicsQuery<R> query,
        @Nonnull CompletableFuture<R> completion) {
        this.query = Objects.requireNonNull(query, "query");
        this.completion = Objects.requireNonNull(completion, "completion");
    }

    @Nonnull
    public static <R> PhysicsQueryHandle<R> completed(@Nonnull PhysicsQuery<R> query,
        @Nonnull R value) {
        return new PhysicsQueryHandle<>(query, CompletableFuture.completedFuture(value));
    }

    @Nonnull
    public static <R> PhysicsQueryHandle<R> failed(@Nonnull PhysicsQuery<R> query,
        @Nonnull Throwable failure) {
        CompletableFuture<R> completion = new CompletableFuture<>();
        completion.completeExceptionally(Objects.requireNonNull(failure, "failure"));
        return new PhysicsQueryHandle<>(query, completion);
    }

    @Nonnull
    public static <R> PhysicsQueryHandle<R> fromCompletion(@Nonnull PhysicsQuery<R> query,
        @Nonnull CompletionStage<R> completion) {
        return new PhysicsQueryHandle<>(query,
            Objects.requireNonNull(completion, "completion").toCompletableFuture());
    }

    @Nonnull
    public PhysicsQuery<R> query() {
        return query;
    }

    @Nonnull
    public CompletionStage<R> completion() {
        return completion.minimalCompletionStage();
    }
}
