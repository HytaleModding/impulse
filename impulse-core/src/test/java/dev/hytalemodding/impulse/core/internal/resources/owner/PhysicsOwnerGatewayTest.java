package dev.hytalemodding.impulse.core.internal.resources.owner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hytalemodding.impulse.core.plugin.resources.PhysicsMutationHandle;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.junit.jupiter.api.Test;

class PhysicsOwnerGatewayTest {

    @Test
    void routesThroughInterfaceBackedOwnerExecutor() {
        PhysicsOwnerGateway gateway = new PhysicsOwnerGateway();
        RecordingOwnerExecutor executor = new RecordingOwnerExecutor();
        AtomicInteger mutations = new AtomicInteger();

        gateway.attachOwnerExecutor(executor);

        assertFalse(gateway.canAccessLiveBackendDirectly());

        gateway.run("record routed mutation", mutations::incrementAndGet);

        assertEquals(1, mutations.get());
        assertEquals(1, executor.routedOperations());
    }

    @Test
    void nestedOwnerCallsInlineOnlyForSameOwnerContext() {
        PhysicsOwnerGateway gateway = new PhysicsOwnerGateway();
        RecordingOwnerExecutor executor = new RecordingOwnerExecutor();
        AtomicInteger nestedMutations = new AtomicInteger();

        gateway.attachOwnerExecutor(executor);

        gateway.run("outer mutation", () -> {
            assertTrue(gateway.canAccessLiveBackendDirectly());
            gateway.run("nested mutation", nestedMutations::incrementAndGet);
        });

        assertEquals(1, nestedMutations.get());
        assertEquals(1, executor.routedOperations());
    }

    @Test
    void routesCallAndAsyncOperationsThroughInterfaceBackedOwnerExecutor() throws Exception {
        PhysicsOwnerGateway gateway = new PhysicsOwnerGateway();
        RecordingOwnerExecutor executor = new RecordingOwnerExecutor();
        AtomicInteger mutations = new AtomicInteger();

        gateway.attachOwnerExecutor(executor);

        PhysicsMutationHandle<String> mutation = gateway.enqueue("record async mutation",
            "done",
            mutations::incrementAndGet);
        CompletableFuture<Integer> asyncCall = gateway.enqueueCall("record async call",
            mutations::incrementAndGet);
        int syncCall = gateway.call("record sync call", mutations::incrementAndGet);

        assertEquals("done", mutation.join());
        assertEquals(2, asyncCall.get());
        assertEquals(3, syncCall);
        assertEquals(3, mutations.get());
        assertEquals(3, executor.routedOperations());
    }

    @Test
    void detachOwnerExecutorRestoresDirectExecution() {
        PhysicsOwnerGateway gateway = new PhysicsOwnerGateway();
        RecordingOwnerExecutor executor = new RecordingOwnerExecutor();
        AtomicInteger mutations = new AtomicInteger();

        gateway.attachOwnerExecutor(executor);
        gateway.detachOwnerExecutor(executor);

        assertTrue(gateway.canAccessLiveBackendDirectly());

        gateway.run("direct mutation after detach", mutations::incrementAndGet);

        assertEquals(1, mutations.get());
        assertEquals(0, executor.routedOperations());
    }

    @Test
    void routedFailuresPropagateThroughHandlesAndFutures() {
        PhysicsOwnerGateway gateway = new PhysicsOwnerGateway();
        RecordingOwnerExecutor executor = new RecordingOwnerExecutor();

        gateway.attachOwnerExecutor(executor);

        PhysicsMutationHandle<String> mutation = gateway.enqueue("failing mutation",
            "value",
            () -> {
                throw new IllegalStateException("boom");
            });
        CompletableFuture<String> asyncCall = gateway.enqueueCall("failing call",
            () -> {
                throw new IllegalArgumentException("bad");
            });

        assertTrue(mutation.failed());
        assertInstanceOf(IllegalStateException.class, mutation.failure());
        ExecutionException thrown = assertThrowsExecution(asyncCall);
        assertInstanceOf(IllegalArgumentException.class, thrown.getCause());
    }

    @Test
    void routedAsyncRejectionsReturnFailedHandlesAndFutures() {
        PhysicsOwnerGateway gateway = new PhysicsOwnerGateway();
        RecordingOwnerExecutor executor = new RecordingOwnerExecutor();
        gateway.attachOwnerExecutor(executor);
        executor.rejectAsync = true;

        PhysicsMutationHandle<String> mutation = gateway.enqueue("rejected mutation",
            "value",
            () -> {
            });
        CompletableFuture<String> asyncCall = gateway.enqueueCall("rejected call", () -> "ignored");

        assertTrue(mutation.failed());
        assertInstanceOf(RejectedExecutionException.class, mutation.failure());
        ExecutionException thrown = assertThrowsExecution(asyncCall);
        assertInstanceOf(RejectedExecutionException.class, thrown.getCause());
    }

    @Test
    void synchronousWaitGuardRejectsCompletionCallbackContext() {
        PhysicsOwnerGateway gateway = new PhysicsOwnerGateway();
        RecordingOwnerExecutor executor = new RecordingOwnerExecutor();
        gateway.attachOwnerExecutor(executor);

        executor.completionCallbackContext = true;

        assertThrows(RejectedExecutionException.class,
            () -> gateway.rejectSynchronousCompletionCallbackWait("release control session"));
    }

    private static final class RecordingOwnerExecutor implements PhysicsOwnerExecutor {

        private final ThreadLocal<Boolean> ownerContext = ThreadLocal.withInitial(() -> false);
        private final AtomicInteger routedOperations = new AtomicInteger();
        private boolean completionCallbackContext;
        private boolean rejectAsync;

        @Override
        public boolean isOwnerContext() {
            return ownerContext.get();
        }

        @Override
        public boolean isCompletionCallbackContext() {
            return completionCallbackContext;
        }

        @Override
        public void run(@Nonnull String operation,
            @Nonnull PhysicsOwnerMutation mutation) {
            routedOperations.incrementAndGet();
            runInOwnerContext(() -> {
                mutation.run();
                return null;
            });
        }

        @Nonnull
        @Override
        public <T> PhysicsMutationHandle<T> enqueue(@Nonnull String operation,
            @Nullable T value,
            @Nonnull PhysicsOwnerMutation mutation) {
            if (rejectAsync) {
                throw new RejectedExecutionException("forced async rejection");
            }
            try {
                run(operation, mutation);
                return PhysicsMutationHandle.completed(operation, value);
            } catch (Throwable throwable) {
                return PhysicsMutationHandle.failed(operation, value, throwable);
            }
        }

        @Nonnull
        @Override
        public <T> CompletableFuture<T> enqueueCall(@Nonnull String operation,
            @Nonnull PhysicsOwnerCallable<T> callable) {
            if (rejectAsync) {
                throw new RejectedExecutionException("forced async call rejection");
            }
            try {
                return CompletableFuture.completedFuture(call(operation, callable));
            } catch (Throwable throwable) {
                CompletableFuture<T> completion = new CompletableFuture<>();
                completion.completeExceptionally(throwable);
                return completion;
            }
        }

        @Nonnull
        @Override
        public <T> T call(@Nonnull String operation,
            @Nonnull PhysicsOwnerCallable<T> callable) {
            routedOperations.incrementAndGet();
            return Objects.requireNonNull(runInOwnerContext(callable));
        }

        int routedOperations() {
            return routedOperations.get();
        }

        @Nullable
        private <T> T runInOwnerContext(@Nonnull PhysicsOwnerCallable<T> callable) {
            boolean previous = ownerContext.get();
            ownerContext.set(true);
            try {
                return callable.call();
            } catch (RuntimeException exception) {
                throw exception;
            } catch (Exception exception) {
                throw new IllegalStateException("owner operation failed", exception);
            } finally {
                ownerContext.set(previous);
            }
        }
    }

    @Nonnull
    private static ExecutionException assertThrowsExecution(@Nonnull CompletableFuture<?> future) {
        try {
            future.get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while waiting for future", exception);
        } catch (ExecutionException exception) {
            return exception;
        }
        throw new AssertionError("Expected future to fail");
    }
}
