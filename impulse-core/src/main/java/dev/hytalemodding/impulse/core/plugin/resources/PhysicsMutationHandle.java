package dev.hytalemodding.impulse.core.plugin.resources;

import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Public completion handle for an asynchronous physics resource mutation.
 *
 * @param <T> reserved logical value returned to the caller before the mutation runs
 */
public final class PhysicsMutationHandle<T> {

    @Nonnull
    private final String operation;
    @Nullable
    private final T value;
    @Nonnull
    private final CompletableFuture<T> completion;

    private PhysicsMutationHandle(@Nonnull String operation,
        @Nullable T value,
        @Nonnull CompletableFuture<T> completion) {
        this.operation = Objects.requireNonNull(operation, "operation");
        this.value = value;
        this.completion = Objects.requireNonNull(completion, "completion");
    }

    @Nonnull
    public static <T> PhysicsMutationHandle<T> completed(@Nonnull String operation,
        @Nullable T value) {
        return new PhysicsMutationHandle<>(operation, value, CompletableFuture.completedFuture(value));
    }

    @Nonnull
    public static <T> PhysicsMutationHandle<T> failed(@Nonnull String operation,
        @Nullable T value,
        @Nonnull Throwable failure) {
        CompletableFuture<T> completion = new CompletableFuture<>();
        completion.completeExceptionally(Objects.requireNonNull(failure, "failure"));
        return new PhysicsMutationHandle<>(operation, value, completion);
    }

    @Nonnull
    public static <T> PhysicsMutationHandle<T> fromCompletion(@Nonnull String operation,
        @Nullable T value,
        @Nonnull CompletionStage<?> source) {
        CompletableFuture<T> completion = Objects.requireNonNull(source, "source")
            .thenApply(_ -> value)
            .toCompletableFuture();
        return new PhysicsMutationHandle<>(operation, value, completion);
    }

    @Nonnull
    public String operation() {
        return operation;
    }

    @Nullable
    public T value() {
        return value;
    }

    @Nonnull
    public CompletionStage<T> completion() {
        return completion.minimalCompletionStage();
    }

    public boolean isDone() {
        return completion.isDone();
    }

    public boolean completedSuccessfully() {
        return completion.isDone()
            && !completion.isCompletedExceptionally()
            && !completion.isCancelled();
    }

    public boolean failed() {
        return failure() != null;
    }

    @Nullable
    public Throwable failure() {
        if (!completion.isDone()) {
            return null;
        }
        try {
            completion.join();
            return null;
        } catch (CompletionException exception) {
            return unwrap(exception);
        } catch (CancellationException exception) {
            return exception;
        }
    }

    @Nullable
    public T join() {
        return completion.join();
    }

    public void throwIfFailed() {
        Throwable failure = failure();
        switch (failure) {
            case null -> {
                return;
            }
            case RuntimeException runtimeException -> throw runtimeException;
            case Error error -> throw error;
            default -> {
            }
        }
        throw new IllegalStateException("Async physics mutation " + operation + " failed",
            failure);
    }

    @Nonnull
    private static Throwable unwrap(@Nonnull CompletionException exception) {
        Throwable cause = exception.getCause();
        return cause != null ? cause : exception;
    }
}
