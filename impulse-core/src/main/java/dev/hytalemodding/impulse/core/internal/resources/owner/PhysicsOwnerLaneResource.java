package dev.hytalemodding.impulse.core.internal.resources.owner;

import dev.hytalemodding.impulse.core.internal.resources.owner.PhysicsOwnerCommand;
import dev.hytalemodding.impulse.core.internal.resources.owner.PhysicsOwnerMutationCompletion;
import dev.hytalemodding.impulse.core.internal.resources.owner.PhysicsOwnerResult;
import dev.hytalemodding.impulse.core.internal.resources.owner.PhysicsOwnerSnapshot;
import dev.hytalemodding.impulse.core.internal.resources.owner.PhysicsOwnerStepCommand;
import dev.hytalemodding.impulse.core.internal.resources.owner.PhysicsOwnerStepCompletion;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsMutationHandle;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Per-world owner lane backed by a shared {@link PhysicsOwnerLaneScheduler}.
 */
public final class PhysicsOwnerLaneResource implements PhysicsOwnerResource {

    private final Object lock = new Object();
    private final PhysicsOwnerLaneScheduler scheduler;
    private final int queueCapacity;
    @Nonnull
    private final Duration closeTimeout;
    private final ArrayDeque<QueuedCommand> mutationQueue = new ArrayDeque<>();
    private final ArrayDeque<QueuedCommand> stepQueue = new ArrayDeque<>();
    private final ArrayDeque<PendingMutation> pendingMutations = new ArrayDeque<>();
    private long nextSequence = 1L;
    private boolean started;
    private boolean accepting;
    private boolean closing;
    private boolean closed;
    private boolean active;
    @Nullable
    private QueuedCommand activeCommand;
    @Nullable
    private PendingStep pendingStep;

    PhysicsOwnerLaneResource(@Nonnull PhysicsOwnerLaneScheduler scheduler,
        int queueCapacity,
        @Nonnull Duration closeTimeout) {
        if (queueCapacity < 1) {
            throw new IllegalArgumentException("queueCapacity must be positive");
        }
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.queueCapacity = queueCapacity;
        this.closeTimeout = Objects.requireNonNull(closeTimeout, "closeTimeout");
    }

    @Override
    public void start(@Nonnull String worldName) {
        Objects.requireNonNull(worldName, "worldName");
        synchronized (lock) {
            if (closed || started) {
                return;
            }
            started = true;
            accepting = true;
        }
    }

    @Override
    public boolean isStarted() {
        synchronized (lock) {
            return started && accepting && !closed;
        }
    }

    @Override
    public boolean isClosed() {
        synchronized (lock) {
            return closed;
        }
    }

    @Nonnull
    @Override
    public PhysicsOwnerResult submitAndDrain(@Nonnull PhysicsOwnerCommand command)
        throws InterruptedException, ExecutionException {
        Objects.requireNonNull(command, "command");
        if (isOwnerContext()) {
            return runInline(command);
        }
        rejectSynchronousCrossLaneWait();

        CompletableFuture<PhysicsOwnerResult> future;
        synchronized (lock) {
            future = enqueueLocked(CommandKind.MUTATION, command).future();
            dispatchIfIdleLocked();
        }
        return future.get();
    }

    @Override
    public boolean submitStepIfIdle(@Nonnull PhysicsOwnerCommand command) {
        Objects.requireNonNull(command, "command");
        if (!(command instanceof PhysicsOwnerStepCommand stepCommand)) {
            throw new IllegalArgumentException("Physics owner step command must capture step metadata");
        }
        synchronized (lock) {
            requireAcceptingLocked();
            if (pendingStep != null) {
                return false;
            }
            QueuedCommand queuedCommand = enqueueLocked(CommandKind.STEP, stepCommand);
            pendingStep = new PendingStep(stepCommand,
                queuedCommand.future(),
                queuedCommand.submittedNanos());
            dispatchIfIdleLocked();
            return true;
        }
    }

    @Nonnull
    @Override
    public PhysicsMutationHandle<Void> submitMutation(@Nonnull String operation,
        @Nonnull PhysicsOwnerCommand command) {
        return submitMutation(operation, null, command);
    }

    @Nonnull
    @Override
    public <T> PhysicsMutationHandle<T> submitMutation(@Nonnull String operation,
        @Nullable T value,
        @Nonnull PhysicsOwnerCommand command) {
        CompletableFuture<PhysicsOwnerResult> future = submitMutationFuture(operation, command);
        return PhysicsMutationHandle.fromCompletion(operation, value, future);
    }

    @Nonnull
    @Override
    public CompletableFuture<PhysicsOwnerResult> submitMutationFuture(@Nonnull String operation,
        @Nonnull PhysicsOwnerCommand command) {
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(command, "command");
        synchronized (lock) {
            CompletableFuture<PhysicsOwnerResult> future =
                enqueueLocked(CommandKind.MUTATION, command).future();
            pendingMutations.add(new PendingMutation(operation, future));
            dispatchIfIdleLocked();
            return future;
        }
    }

    @Nonnull
    @Override
    public List<PhysicsOwnerMutationCompletion> pollCompletedMutations(int maxCompletions) {
        int limit = Math.max(0, maxCompletions);
        if (limit == 0) {
            return Collections.emptyList();
        }
        synchronized (lock) {
            PendingMutation first = pendingMutations.peek();
            if (first == null || !first.future().isDone()) {
                return Collections.emptyList();
            }
            List<PhysicsOwnerMutationCompletion> completions = new ArrayList<>(limit);
            while (completions.size() < limit) {
                PendingMutation current = pendingMutations.peek();
                if (current == null || !current.future().isDone()) {
                    break;
                }
                pendingMutations.poll();
                completions.add(toMutationCompletion(current));
            }
            return completions;
        }
    }

    @Nullable
    @Override
    public PhysicsOwnerStepCompletion pollCompletedStep() {
        PendingStep completed;
        synchronized (lock) {
            PendingStep current = pendingStep;
            if (current == null || !current.future().isDone()) {
                return null;
            }
            pendingStep = null;
            completed = current;
        }

        try {
            PhysicsOwnerResult result = completed.future().get();
            return new PhysicsOwnerStepCompletion(result,
                completed.command().publishedFrame(),
                completed.command().eventFrame(),
                completed.command().failure(),
                null);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return new PhysicsOwnerStepCompletion(null, null, null, null, exception);
        } catch (ExecutionException exception) {
            return new PhysicsOwnerStepCompletion(null, null, null, null, exception.getCause());
        }
    }

    @Override
    public boolean hasPendingStep() {
        synchronized (lock) {
            return pendingStep != null;
        }
    }

    @Override
    public long pendingStepAgeNanos() {
        synchronized (lock) {
            PendingStep current = pendingStep;
            return current == null ? 0L : Math.max(0L, System.nanoTime() - current.submittedNanos());
        }
    }

    @Override
    public int pendingMutations() {
        synchronized (lock) {
            return pendingMutations.size();
        }
    }

    @Override
    public int pendingCommands() {
        synchronized (lock) {
            return queuedCommandCountLocked() + (activeCommandPendingLocked() ? 1 : 0);
        }
    }

    @Override
    public boolean isOwnerContext() {
        return scheduler.isCurrentLane(this);
    }

    @Override
    public void run(@Nonnull String operation,
        @Nonnull PhysicsOwnerMutation mutation) {
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(mutation, "mutation");
        if (isOwnerContext()) {
            runDirect(operation, mutation);
            return;
        }
        rejectSynchronousCrossLaneWait();
        try {
            submitAndDrain(() -> {
                mutation.run();
                return PhysicsOwnerSnapshot.empty();
            });
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while running physics owner lane operation "
                + operation, exception);
        } catch (ExecutionException exception) {
            throw ownerFailure(operation, exception.getCause());
        }
    }

    @Nonnull
    @Override
    public <T> PhysicsMutationHandle<T> enqueue(@Nonnull String operation,
        @Nullable T value,
        @Nonnull PhysicsOwnerMutation mutation) {
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(mutation, "mutation");
        if (isOwnerContext()) {
            return runDirectAsync(operation, value, mutation);
        }
        return submitMutation(operation, value, () -> {
            mutation.run();
            return PhysicsOwnerSnapshot.empty();
        });
    }

    @Nonnull
    @Override
    public <T> CompletableFuture<T> enqueueCall(@Nonnull String operation,
        @Nonnull PhysicsOwnerCallable<T> callable) {
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(callable, "callable");
        if (isOwnerContext()) {
            return callDirectAsync(operation, callable);
        }

        CompletableFuture<T> completion = new CompletableFuture<>();
        try {
            submitMutationFuture(operation, () -> {
                completion.complete(callable.call());
                return PhysicsOwnerSnapshot.empty();
            }).whenComplete((ignored, failure) -> {
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
    @Override
    public <T> T call(@Nonnull String operation,
        @Nonnull PhysicsOwnerCallable<T> callable) {
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(callable, "callable");
        if (isOwnerContext()) {
            return callDirect(operation, callable);
        }
        rejectSynchronousCrossLaneWait();

        AtomicReference<T> value = new AtomicReference<>();
        try {
            submitAndDrain(() -> {
                value.set(callable.call());
                return PhysicsOwnerSnapshot.empty();
            });
            return value.get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while running physics owner lane operation "
                + operation, exception);
        } catch (ExecutionException exception) {
            throw ownerFailure(operation, exception.getCause());
        }
    }

    @Override
    public void close() {
        synchronized (lock) {
            if (closed) {
                return;
            }
            closing = true;
            accepting = false;
            dispatchIfIdleLocked();
        }

        RuntimeException timeout = null;
        long timeoutNanos = Math.max(0L, closeTimeout.toNanos());
        long deadline = timeoutNanos == 0L
            ? Long.MAX_VALUE
            : System.nanoTime() + timeoutNanos;
        boolean interrupted = false;
        synchronized (lock) {
            while (!closed && (active || !mutationQueue.isEmpty() || !stepQueue.isEmpty())) {
                long remainingNanos = deadline - System.nanoTime();
                if (remainingNanos <= 0L) {
                    timeout = new IllegalStateException(
                        "Physics owner lane did not stop within " + closeTimeout);
                    break;
                }
                try {
                    long millis = TimeUnit.NANOSECONDS.toMillis(remainingNanos);
                    int nanos = (int) (remainingNanos - TimeUnit.MILLISECONDS.toNanos(millis));
                    lock.wait(millis, nanos);
                } catch (InterruptedException exception) {
                    interrupted = true;
                }
            }
            if (timeout == null && !closed) {
                closed = true;
                scheduler.unregister(this);
                lock.notifyAll();
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
        if (timeout != null) {
            throw timeout;
        }
    }

    @Nonnull
    @Override
    public PhysicsOwnerLaneResource clone() {
        return scheduler.createLane();
    }

    private void runQueuedCommand(@Nonnull QueuedCommand command) {
        long startNanos = System.nanoTime();
        PhysicsOwnerResult result = null;
        Throwable failure = null;
        try {
            PhysicsOwnerSnapshot snapshot = command.command().run();
            long completedNanos = System.nanoTime();
            result = new PhysicsOwnerResult(command.sequence(),
                snapshot,
                startNanos - command.submittedNanos(),
                completedNanos - startNanos,
                completedNanos);
        } catch (Throwable throwable) {
            failure = throwable;
        }

        if (failure == null) {
            command.future().complete(result);
        } else {
            command.future().completeExceptionally(failure);
        }

        synchronized (lock) {
            active = false;
            activeCommand = null;
            if (closing && mutationQueue.isEmpty() && stepQueue.isEmpty()) {
                closed = true;
                scheduler.unregister(this);
            }
            dispatchIfIdleLocked();
            lock.notifyAll();
        }
    }

    @Nonnull
    private PhysicsOwnerResult runInline(@Nonnull PhysicsOwnerCommand command)
        throws ExecutionException {
        long sequence;
        synchronized (lock) {
            sequence = nextSequence++;
        }
        long startNanos = System.nanoTime();
        try {
            PhysicsOwnerSnapshot snapshot = command.run();
            long completedNanos = System.nanoTime();
            return new PhysicsOwnerResult(sequence,
                snapshot,
                0L,
                completedNanos - startNanos,
                completedNanos);
        } catch (Throwable throwable) {
            throw new ExecutionException(throwable);
        }
    }

    @Nonnull
    private QueuedCommand enqueueLocked(
        @Nonnull CommandKind kind,
        @Nonnull PhysicsOwnerCommand command) {
        requireAcceptingLocked();
        if (queuedCommandCountLocked() >= queueCapacity) {
            throw new RejectedExecutionException("physics owner lane command queue is full");
        }
        CompletableFuture<PhysicsOwnerResult> future = new CompletableFuture<>();
        QueuedCommand queuedCommand = new QueuedCommand(nextSequence++,
            command,
            System.nanoTime(),
            future);
        if (kind == CommandKind.STEP) {
            stepQueue.addLast(queuedCommand);
        } else {
            mutationQueue.addLast(queuedCommand);
        }
        return queuedCommand;
    }

    private void dispatchIfIdleLocked() {
        if (!started || closed || active) {
            return;
        }
        QueuedCommand next = !stepQueue.isEmpty()
            ? stepQueue.pollFirst()
            : mutationQueue.pollFirst();
        if (next == null) {
            if (closing) {
                closed = true;
                scheduler.unregister(this);
                lock.notifyAll();
            }
            return;
        }
        active = true;
        activeCommand = next;
        try {
            scheduler.execute(this, () -> runQueuedCommand(next));
        } catch (RejectedExecutionException exception) {
            active = false;
            activeCommand = null;
            next.future().completeExceptionally(exception);
            if (closing && mutationQueue.isEmpty() && stepQueue.isEmpty()) {
                closed = true;
                scheduler.unregister(this);
                lock.notifyAll();
                return;
            }
            dispatchIfIdleLocked();
        }
    }

    private int queuedCommandCountLocked() {
        return mutationQueue.size() + stepQueue.size();
    }

    private boolean activeCommandPendingLocked() {
        if (!active) {
            return false;
        }
        return activeCommand == null || !activeCommand.future().isDone();
    }

    private void requireAcceptingLocked() {
        if (!started) {
            throw new RejectedExecutionException("physics owner lane is not started");
        }
        if (!accepting || closing || closed) {
            throw new RejectedExecutionException("physics owner lane is closed");
        }
    }

    private void rejectSynchronousCrossLaneWait() {
        PhysicsOwnerLaneResource currentLane = scheduler.currentLane();
        if (currentLane != null && currentLane != this) {
            throw new RejectedExecutionException(
                "cannot synchronously wait for a different physics owner lane");
        }
    }

    @Nonnull
    private static PhysicsOwnerMutationCompletion toMutationCompletion(
        @Nonnull PendingMutation mutation) {
        try {
            return new PhysicsOwnerMutationCompletion(mutation.operation(),
                mutation.future().get(),
                null);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return new PhysicsOwnerMutationCompletion(mutation.operation(), null, exception);
        } catch (ExecutionException exception) {
            return new PhysicsOwnerMutationCompletion(mutation.operation(),
                null,
                exception.getCause());
        }
    }

    private static void runDirect(@Nonnull String operation,
        @Nonnull PhysicsOwnerMutation mutation) {
        try {
            mutation.run();
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Physics operation " + operation + " failed",
                exception);
        }
    }

    @Nonnull
    private static <T> PhysicsMutationHandle<T> runDirectAsync(@Nonnull String operation,
        @Nullable T value,
        @Nonnull PhysicsOwnerMutation mutation) {
        try {
            mutation.run();
            return PhysicsMutationHandle.completed(operation, value);
        } catch (Throwable throwable) {
            return PhysicsMutationHandle.failed(operation, value, throwable);
        }
    }

    @Nonnull
    private static <T> CompletableFuture<T> callDirectAsync(@Nonnull String operation,
        @Nonnull PhysicsOwnerCallable<T> callable) {
        try {
            return CompletableFuture.completedFuture(callable.call());
        } catch (Throwable throwable) {
            CompletableFuture<T> completion = new CompletableFuture<>();
            completion.completeExceptionally(throwable);
            return completion;
        }
    }

    @Nonnull
    private static <T> T callDirect(@Nonnull String operation,
        @Nonnull PhysicsOwnerCallable<T> callable) {
        try {
            return callable.call();
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Physics operation " + operation + " failed",
                exception);
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

    private enum CommandKind {
        STEP,
        MUTATION
    }

    private record QueuedCommand(long sequence,
                                 @Nonnull PhysicsOwnerCommand command,
                                 long submittedNanos,
                                 @Nonnull CompletableFuture<PhysicsOwnerResult> future) {
    }

    private record PendingStep(@Nonnull PhysicsOwnerStepCommand command,
                               @Nonnull CompletableFuture<PhysicsOwnerResult> future,
                               long submittedNanos) {
    }

    private record PendingMutation(@Nonnull String operation,
                                   @Nonnull CompletableFuture<PhysicsOwnerResult> future) {
    }
}
