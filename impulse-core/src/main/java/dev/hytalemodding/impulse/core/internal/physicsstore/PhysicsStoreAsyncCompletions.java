package dev.hytalemodding.impulse.core.internal.physicsstore;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadFactory;
import javax.annotation.Nonnull;

/**
 * Completes public PhysicsStore futures outside the store tick lane.
 */
public final class PhysicsStoreAsyncCompletions {

    private static final ThreadFactory COMPLETION_THREADS =
        Thread.ofVirtual().name("Impulse PhysicsStore completion ", 1).factory();

    private PhysicsStoreAsyncCompletions() {
    }

    public static <T> void complete(@Nonnull CompletableFuture<T> completion, T value) {
        dispatch(() -> completion.complete(value));
    }

    public static void fail(@Nonnull CompletableFuture<?> completion,
        @Nonnull Throwable failure) {
        Objects.requireNonNull(failure, "failure");
        dispatch(() -> completion.completeExceptionally(failure));
    }

    private static void dispatch(@Nonnull Runnable completion) {
        COMPLETION_THREADS.newThread(Objects.requireNonNull(completion, "completion")).start();
    }
}
