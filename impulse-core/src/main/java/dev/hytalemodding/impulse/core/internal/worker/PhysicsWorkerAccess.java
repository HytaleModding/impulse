package dev.hytalemodding.impulse.core.internal.worker;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsWorldWorkerResource;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.Nonnull;

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
            throw new IllegalStateException("Physics worker operation " + operation + " failed",
                exception);
        }
    }

    @Nonnull
    private static RuntimeException workerFailure(@Nonnull String operation,
        @Nonnull Throwable cause) {
        if (cause instanceof RuntimeException runtimeException) {
            return runtimeException;
        }
        return new IllegalStateException("Physics worker operation " + operation + " failed",
            cause);
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
