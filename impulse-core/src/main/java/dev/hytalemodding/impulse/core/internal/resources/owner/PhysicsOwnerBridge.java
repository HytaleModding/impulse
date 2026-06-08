package dev.hytalemodding.impulse.core.internal.resources.owner;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsMutationHandle;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Synchronous bridge for code paths that must mutate live physics state in the
 * world physics owner context.
 */
public final class PhysicsOwnerBridge {

    private PhysicsOwnerBridge() {
    }

    public static void run(@Nonnull Store<EntityStore> store,
        @Nonnull String operation,
        @Nonnull OwnerMutation mutation) {
        run(owner(store), operation, mutation);
    }

    public static void run(@Nonnull PhysicsOwnerResource owner,
        @Nonnull String operation,
        @Nonnull OwnerMutation mutation) {
        call(owner, operation, () -> {
            mutation.run();
            return null;
        });
    }

    @Nonnull
    public static PhysicsMutationHandle<Void> runAsync(@Nonnull Store<EntityStore> store,
        @Nonnull String operation,
        @Nonnull OwnerMutation mutation) {
        return runAsync(owner(store), operation, mutation);
    }

    @Nonnull
    public static PhysicsMutationHandle<Void> runAsync(@Nonnull PhysicsOwnerResource owner,
        @Nonnull String operation,
        @Nonnull OwnerMutation mutation) {
        return runAsync(owner, operation, null, mutation);
    }

    @Nonnull
    public static <T> PhysicsMutationHandle<T> runAsync(@Nonnull Store<EntityStore> store,
        @Nonnull String operation,
        @Nullable T value,
        @Nonnull OwnerMutation mutation) {
        return runAsync(owner(store), operation, value, mutation);
    }

    @Nonnull
    public static <T> PhysicsMutationHandle<T> runAsync(@Nonnull PhysicsOwnerResource owner,
        @Nonnull String operation,
        @Nullable T value,
        @Nonnull OwnerMutation mutation) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(mutation, "mutation");
        if (owner.isOwnerContext()) {
            return runInlineAsync(operation, value, mutation);
        }

        PhysicsOwnerCommand command = () -> {
            mutation.run();
            return PhysicsOwnerSnapshot.empty();
        };
        try {
            return owner.submitMutation(operation, value, command);
        } catch (RejectedExecutionException exception) {
            return PhysicsMutationHandle.failed(operation, value, exception);
        }
    }

    @Nonnull
    public static <T> T call(@Nonnull Store<EntityStore> store,
        @Nonnull String operation,
        @Nonnull OwnerCallable<T> callable) {
        return call(owner(store), operation, callable);
    }

    @Nonnull
    public static <T> T call(@Nonnull PhysicsOwnerResource owner,
        @Nonnull String operation,
        @Nonnull OwnerCallable<T> callable) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(callable, "callable");
        if (owner.isOwnerContext()) {
            return callInline(operation, callable);
        }

        AtomicReference<T> value = new AtomicReference<>();
        PhysicsOwnerCommand command = () -> {
            value.set(callable.call());
            return PhysicsOwnerSnapshot.empty();
        };
        try {
            owner.submitAndDrain(command);
            return value.get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while running physics owner lane operation "
                + operation, exception);
        } catch (ExecutionException exception) {
            throw ownerFailure(operation, exception.getCause());
        } catch (RejectedExecutionException exception) {
            throw ownerFailure(operation, exception);
        }
    }

    @Nonnull
    public static <T> CompletableFuture<T> callAsync(@Nonnull PhysicsOwnerResource owner,
        @Nonnull String operation,
        @Nonnull OwnerCallable<T> callable) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(callable, "callable");
        if (owner.isOwnerContext()) {
            return callInlineAsync(callable);
        }

        CompletableFuture<T> completion = new CompletableFuture<>();
        PhysicsOwnerCommand command = () -> {
            completion.complete(callable.call());
            return PhysicsOwnerSnapshot.empty();
        };
        try {
            owner.submitMutationFuture(operation, command)
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
    private static PhysicsOwnerResource owner(@Nonnull Store<EntityStore> store) {
        return store.getResource(PhysicsOwnerResource.getResourceType());
    }

    @Nonnull
    private static <T> T callInline(@Nonnull String operation,
        @Nonnull OwnerCallable<T> callable) {
        try {
            return callable.call();
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException(ownerFailureMessage(operation, exception), exception);
        }
    }

    @Nonnull
    private static <T> PhysicsMutationHandle<T> runInlineAsync(@Nonnull String operation,
        @Nullable T value,
        @Nonnull OwnerMutation mutation) {
        try {
            mutation.run();
            return PhysicsMutationHandle.completed(operation, value);
        } catch (Throwable throwable) {
            return PhysicsMutationHandle.failed(operation, value, throwable);
        }
    }

    @Nonnull
    private static <T> CompletableFuture<T> callInlineAsync(@Nonnull OwnerCallable<T> callable) {
        try {
            return CompletableFuture.completedFuture(callable.call());
        } catch (Throwable throwable) {
            CompletableFuture<T> completion = new CompletableFuture<>();
            completion.completeExceptionally(throwable);
            return completion;
        }
    }

    @Nonnull
    private static RuntimeException ownerFailure(@Nonnull String operation,
        @Nonnull Throwable cause) {
        if (cause instanceof RuntimeException runtimeException) {
            return runtimeException;
        }
        return new IllegalStateException(ownerFailureMessage(operation, cause), cause);
    }

    @Nonnull
    private static String ownerFailureMessage(@Nonnull String operation,
        @Nonnull Throwable cause) {
        StringBuilder message = new StringBuilder("Physics owner lane operation ")
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
    public interface OwnerCallable<T> {

        T call() throws Exception;
    }

    @FunctionalInterface
    public interface OwnerMutation {

        void run() throws Exception;
    }
}
