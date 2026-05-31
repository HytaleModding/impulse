package dev.hytalemodding.impulse.core.internal.worker;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsWorldWorkerResource;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsMutationHandle;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Synchronous bridge for code paths that must mutate live physics state on the
 * world physics worker.
 */
public final class PhysicsWorkerAccess {

    private PhysicsWorkerAccess() {
    }

    public static void run(@Nonnull Store<EntityStore> store,
        @Nonnull String operation,
        @Nonnull PhysicsWorkerMutation mutation) {
        run(worker(store), operation, mutation);
    }

    public static void run(@Nonnull PhysicsWorldWorkerResource worker,
        @Nonnull String operation,
        @Nonnull PhysicsWorkerMutation mutation) {
        call(worker, operation, () -> {
            mutation.run();
            return null;
        });
    }

    @Nonnull
    public static PhysicsMutationHandle<Void> runAsync(@Nonnull Store<EntityStore> store,
        @Nonnull String operation,
        @Nonnull PhysicsWorkerMutation mutation) {
        return runAsync(worker(store), operation, mutation);
    }

    @Nonnull
    public static PhysicsMutationHandle<Void> runAsync(@Nonnull PhysicsWorldWorkerResource worker,
        @Nonnull String operation,
        @Nonnull PhysicsWorkerMutation mutation) {
        return runAsync(worker, operation, null, mutation);
    }

    @Nonnull
    public static <T> PhysicsMutationHandle<T> runAsync(@Nonnull Store<EntityStore> store,
        @Nonnull String operation,
        @Nullable T value,
        @Nonnull PhysicsWorkerMutation mutation) {
        return runAsync(worker(store), operation, value, mutation);
    }

    @Nonnull
    public static <T> PhysicsMutationHandle<T> runAsync(@Nonnull PhysicsWorldWorkerResource worker,
        @Nonnull String operation,
        @Nullable T value,
        @Nonnull PhysicsWorkerMutation mutation) {
        Objects.requireNonNull(worker, "worker");
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(mutation, "mutation");
        if (worker.isWorkerThread()) {
            return runInlineAsync(operation, value, mutation);
        }

        PhysicsWorkerCommand command = () -> {
            mutation.run();
            return PhysicsWorkerSnapshot.empty();
        };
        try {
            return worker.submitMutation(operation, value, command);
        } catch (RejectedExecutionException exception) {
            return PhysicsMutationHandle.failed(operation, value, exception);
        }
    }

    @Nonnull
    public static <T> T call(@Nonnull Store<EntityStore> store,
        @Nonnull String operation,
        @Nonnull PhysicsWorkerCallable<T> callable) {
        return call(worker(store), operation, callable);
    }

    @Nonnull
    public static <T> T call(@Nonnull PhysicsWorldWorkerResource worker,
        @Nonnull String operation,
        @Nonnull PhysicsWorkerCallable<T> callable) {
        Objects.requireNonNull(worker, "worker");
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(callable, "callable");
        if (worker.isWorkerThread()) {
            return callInline(operation, callable);
        }

        AtomicReference<T> value = new AtomicReference<>();
        PhysicsWorkerCommand command = () -> {
            value.set(callable.call());
            return PhysicsWorkerSnapshot.empty();
        };
        try {
            worker.submitAndDrain(command);
            return value.get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while running physics worker operation "
                + operation, exception);
        } catch (ExecutionException exception) {
            throw workerFailure(operation, exception.getCause());
        } catch (RejectedExecutionException exception) {
            throw workerFailure(operation, exception);
        }
    }

    @Nonnull
    public static <T> CompletableFuture<T> callAsync(@Nonnull PhysicsWorldWorkerResource worker,
        @Nonnull String operation,
        @Nonnull PhysicsWorkerCallable<T> callable) {
        Objects.requireNonNull(worker, "worker");
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(callable, "callable");
        if (worker.isWorkerThread()) {
            return callInlineAsync(operation, callable);
        }

        CompletableFuture<T> completion = new CompletableFuture<>();
        PhysicsWorkerCommand command = () -> {
            completion.complete(callable.call());
            return PhysicsWorkerSnapshot.empty();
        };
        try {
            worker.submitMutationFuture(operation, command)
                .whenComplete((ignored, failure) -> {
                    if (failure != null) {
                        completion.completeExceptionally(failure);
                    }
                });
        } catch (RejectedExecutionException exception) {
            completion.completeExceptionally(exception);
        }
        return completion;
    }

    @Nonnull
    private static PhysicsWorldWorkerResource worker(@Nonnull Store<EntityStore> store) {
        return store.getResource(PhysicsWorldWorkerResource.getResourceType());
    }

    @Nonnull
    private static <T> T callInline(@Nonnull String operation,
        @Nonnull PhysicsWorkerCallable<T> callable) {
        try {
            return callable.call();
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException(workerFailureMessage(operation, exception), exception);
        }
    }

    @Nonnull
    private static <T> PhysicsMutationHandle<T> runInlineAsync(@Nonnull String operation,
        @Nullable T value,
        @Nonnull PhysicsWorkerMutation mutation) {
        try {
            mutation.run();
            return PhysicsMutationHandle.completed(operation, value);
        } catch (Throwable throwable) {
            return PhysicsMutationHandle.failed(operation, value, throwable);
        }
    }

    @Nonnull
    private static <T> CompletableFuture<T> callInlineAsync(@Nonnull String operation,
        @Nonnull PhysicsWorkerCallable<T> callable) {
        try {
            return CompletableFuture.completedFuture(callable.call());
        } catch (Throwable throwable) {
            CompletableFuture<T> completion = new CompletableFuture<>();
            completion.completeExceptionally(throwable);
            return completion;
        }
    }

    @Nonnull
    private static RuntimeException workerFailure(@Nonnull String operation,
        @Nonnull Throwable cause) {
        if (cause instanceof RuntimeException runtimeException) {
            return runtimeException;
        }
        return new IllegalStateException(workerFailureMessage(operation, cause), cause);
    }

    @Nonnull
    private static String workerFailureMessage(@Nonnull String operation,
        @Nonnull Throwable cause) {
        StringBuilder message = new StringBuilder("Physics worker operation ")
            .append(operation)
            .append(" failed: ")
            .append(cause.getClass().getSimpleName());
        String causeMessage = cause.getMessage();
        if (causeMessage != null && !causeMessage.isBlank()) {
            message.append(": ").append(causeMessage);
        }
        return message.toString();
    }

    @FunctionalInterface
    public interface PhysicsWorkerCallable<T> {

        T call() throws Exception;
    }

    @FunctionalInterface
    public interface PhysicsWorkerMutation {

        void run() throws Exception;
    }
}
