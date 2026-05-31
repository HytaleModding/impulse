package dev.hytalemodding.impulse.core.internal.resources.owner;

import dev.hytalemodding.impulse.core.plugin.resources.PhysicsMutationHandle;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Test fixture that exposes a pooled owner lane as an ECS resource.
 */
public final class TestPhysicsOwnerLane implements PhysicsOwnerResource {

    private final int poolSize;
    private final int queueCapacity;
    @Nonnull
    private final Duration closeTimeout;
    private final PhysicsOwnerLaneScheduler scheduler;
    private final PhysicsOwnerLaneResource lane;

    public TestPhysicsOwnerLane() {
        this(2,
            PhysicsOwnerLaneScheduler.DEFAULT_QUEUE_CAPACITY,
            PhysicsOwnerLaneScheduler.DEFAULT_CLOSE_TIMEOUT);
    }

    public TestPhysicsOwnerLane(int queueCapacity, @Nonnull Duration closeTimeout) {
        this(2, queueCapacity, closeTimeout);
    }

    public TestPhysicsOwnerLane(int poolSize,
        int queueCapacity,
        @Nonnull Duration closeTimeout) {
        this.poolSize = poolSize;
        this.queueCapacity = queueCapacity;
        this.closeTimeout = closeTimeout;
        scheduler = new PhysicsOwnerLaneScheduler(poolSize, queueCapacity, closeTimeout);
        lane = scheduler.createLane();
    }

    @Override
    public void start(@Nonnull String worldName) {
        lane.start(worldName);
    }

    @Override
    public boolean isStarted() {
        return lane.isStarted();
    }

    @Override
    public boolean isClosed() {
        return lane.isClosed();
    }

    @Override
    public void close() {
        scheduler.close();
    }

    @Nonnull
    @Override
    public PhysicsOwnerResult submitAndDrain(@Nonnull PhysicsOwnerCommand command)
        throws InterruptedException, ExecutionException {
        return lane.submitAndDrain(command);
    }

    @Override
    public boolean submitStepIfIdle(@Nonnull PhysicsOwnerCommand command) {
        return lane.submitStepIfIdle(command);
    }

    @Nonnull
    @Override
    public PhysicsMutationHandle<Void> submitMutation(@Nonnull String operation,
        @Nonnull PhysicsOwnerCommand command) {
        return lane.submitMutation(operation, command);
    }

    @Nonnull
    @Override
    public <T> PhysicsMutationHandle<T> submitMutation(@Nonnull String operation,
        @Nullable T value,
        @Nonnull PhysicsOwnerCommand command) {
        return lane.submitMutation(operation, value, command);
    }

    @Nonnull
    @Override
    public CompletableFuture<PhysicsOwnerResult> submitMutationFuture(@Nonnull String operation,
        @Nonnull PhysicsOwnerCommand command) {
        return lane.submitMutationFuture(operation, command);
    }

    @Nonnull
    @Override
    public List<PhysicsOwnerMutationCompletion> pollCompletedMutations(int maxCompletions) {
        return lane.pollCompletedMutations(maxCompletions);
    }

    @Nullable
    @Override
    public PhysicsOwnerStepCompletion pollCompletedStep() {
        return lane.pollCompletedStep();
    }

    @Override
    public boolean hasPendingStep() {
        return lane.hasPendingStep();
    }

    @Override
    public long pendingStepAgeNanos() {
        return lane.pendingStepAgeNanos();
    }

    @Override
    public int pendingMutations() {
        return lane.pendingMutations();
    }

    @Override
    public int pendingCommands() {
        return lane.pendingCommands();
    }

    @Override
    public boolean isOwnerContext() {
        return lane.isOwnerContext();
    }

    @Override
    public void run(@Nonnull String operation, @Nonnull PhysicsOwnerMutation mutation) {
        lane.run(operation, mutation);
    }

    @Nonnull
    @Override
    public <T> PhysicsMutationHandle<T> enqueue(@Nonnull String operation,
        @Nullable T value,
        @Nonnull PhysicsOwnerMutation mutation) {
        return lane.enqueue(operation, value, mutation);
    }

    @Nonnull
    @Override
    public <T> CompletableFuture<T> enqueueCall(@Nonnull String operation,
        @Nonnull PhysicsOwnerCallable<T> callable) {
        return lane.enqueueCall(operation, callable);
    }

    @Nonnull
    @Override
    public <T> T call(@Nonnull String operation, @Nonnull PhysicsOwnerCallable<T> callable) {
        return lane.call(operation, callable);
    }

    @Nonnull
    @Override
    public PhysicsOwnerResource clone() {
        return new TestPhysicsOwnerLane(poolSize, queueCapacity, closeTimeout);
    }
}
