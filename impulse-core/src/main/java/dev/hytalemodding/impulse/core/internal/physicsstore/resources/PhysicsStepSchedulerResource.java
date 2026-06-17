package dev.hytalemodding.impulse.core.internal.physicsstore.resources;

import com.hypixel.hytale.component.Resource;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsResourceTypes;
import dev.hytalemodding.impulse.api.PhysicsStepPhaseStats;
import dev.hytalemodding.impulse.core.plugin.snapshots.PhysicsBodySnapshot;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsStepSchedulingMode;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Per-PhysicsStore backend owner lane.
 *
 * <p>Only the backend step itself runs off the world thread. PhysicsStore ECS systems are skipped
 * while a step is pending, so backend mutations and snapshot reads remain serialized around the
 * owner lane.</p>
 */
public final class PhysicsStepSchedulerResource implements Resource<PhysicsStore>, AutoCloseable {

    private static final AtomicInteger THREAD_COUNTER = new AtomicInteger();

    @Nonnull
    private final ExecutorService ownerLane = Executors.newSingleThreadExecutor(
        new OwnerLaneThreadFactory());
    @Nullable
    private PendingStep pendingStep;
    @Nullable
    private CompletedStep completedStep;
    private float backlogDtSeconds;
    private boolean closed;

    public PhysicsStepSchedulerResource() {
    }

    @Nonnull
    public synchronized TickDecision beforeStoreTick(float dtSeconds,
        @Nonnull PhysicsStepSchedulingMode mode,
        float maxSubmittedDtSeconds,
        long nowNanos) {
        pollPendingStep();
        if (pendingStep == null) {
            return TickDecision.tick();
        }
        StepInput skipped = accumulatePendingDt(dtSeconds, mode, maxSubmittedDtSeconds);
        return TickDecision.skip(pendingAgeNanos(nowNanos), skipped);
    }

    @Nonnull
    public synchronized StepInput acceptStepInput(float dtSeconds,
        @Nonnull PhysicsStepSchedulingMode mode,
        float maxSubmittedDtSeconds) {
        pollPendingStep();
        if (pendingStep != null) {
            throw new IllegalStateException("Cannot submit a PhysicsStore step while another step is pending");
        }
        float inputDtSeconds = safeDt(dtSeconds);
        float candidateDtSeconds = inputDtSeconds;
        if (mode == PhysicsStepSchedulingMode.ACCUMULATE_PENDING_DT) {
            candidateDtSeconds += backlogDtSeconds;
        } else {
            backlogDtSeconds = 0.0f;
        }
        SubmittedDt submittedDt = capSubmittedDt(candidateDtSeconds, maxSubmittedDtSeconds);
        backlogDtSeconds = 0.0f;
        return new StepInput(inputDtSeconds,
            submittedDt.submittedDtSeconds(),
            backlogDtSeconds,
            submittedDt.droppedDtSeconds(),
            submittedDt.dtCapHit());
    }

    public boolean submitStep(@Nonnull StepInput input,
        @Nonnull StepTask task,
        long nowNanos) {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(task, "task");
        synchronized (this) {
            pollPendingStep();
            if (closed || pendingStep != null) {
                return false;
            }
            CompletableFuture<CompletedStep> future = CompletableFuture.supplyAsync(
                () -> task.run().withInput(input),
                ownerLane);
            pendingStep = new PendingStep(input, future, Math.max(0L, nowNanos));
            return true;
        }
    }

    public synchronized boolean isStepPending() {
        pollPendingStep();
        return pendingStep != null;
    }

    @Nonnull
    public synchronized CompletionStage<Void> whenIdle() {
        pollPendingStep();
        PendingStep pending = pendingStep;
        if (pending == null) {
            return CompletableFuture.<Void>completedFuture(null).minimalCompletionStage();
        }
        return pending.future().thenApply(_ -> (Void) null).minimalCompletionStage();
    }

    @Nullable
    public synchronized CompletedStep pollCompletedStep() {
        pollPendingStep();
        CompletedStep completed = completedStep;
        completedStep = null;
        return completed;
    }

    @Override
    public void close() {
        PendingStep pending;
        synchronized (this) {
            closed = true;
            pending = pendingStep;
        }
        if (pending != null) {
            try {
                pending.future().join();
            } catch (CompletionException ignored) {
                // The next store tick will publish the failure if shutdown did not consume it first.
            }
        }
        ownerLane.shutdown();
    }

    @Nonnull
    @Override
    public PhysicsStepSchedulerResource clone() {
        return new PhysicsStepSchedulerResource();
    }

    @Nonnull
    public static ResourceType<PhysicsStore, PhysicsStepSchedulerResource> getResourceType() {
        return PhysicsResourceTypes.stepSchedulerResourceType();
    }

    private StepInput accumulatePendingDt(float dtSeconds,
        @Nonnull PhysicsStepSchedulingMode mode,
        float maxSubmittedDtSeconds) {
        float inputDtSeconds = safeDt(dtSeconds);
        if (inputDtSeconds <= 0.0f) {
            return new StepInput(0.0f, 0.0f, backlogDtSeconds, 0.0f, false);
        }
        if (mode != PhysicsStepSchedulingMode.ACCUMULATE_PENDING_DT) {
            return new StepInput(inputDtSeconds, 0.0f, backlogDtSeconds, inputDtSeconds, false);
        }
        SubmittedDt submittedDt = capSubmittedDt(backlogDtSeconds + inputDtSeconds,
            maxSubmittedDtSeconds);
        backlogDtSeconds = submittedDt.submittedDtSeconds();
        return new StepInput(inputDtSeconds,
            0.0f,
            backlogDtSeconds,
            submittedDt.droppedDtSeconds(),
            submittedDt.dtCapHit());
    }

    private void pollPendingStep() {
        PendingStep pending = pendingStep;
        if (pending == null || !pending.future().isDone()) {
            return;
        }
        try {
            completedStep = pending.future().join();
        } catch (CompletionException exception) {
            completedStep = CompletedStep.failed(pending.input(),
                exception.getCause() != null ? exception.getCause() : exception);
        }
        pendingStep = null;
    }

    private long pendingAgeNanos(long nowNanos) {
        PendingStep pending = pendingStep;
        return pending == null ? 0L : Math.max(0L, nowNanos - pending.startedNanos());
    }

    private static float safeDt(float dtSeconds) {
        return Float.isFinite(dtSeconds) ? Math.max(0.0f, dtSeconds) : 0.0f;
    }

    private static SubmittedDt capSubmittedDt(float dtSeconds, float maxSubmittedDtSeconds) {
        float safeDtSeconds = safeDt(dtSeconds);
        float safeMax = Float.isFinite(maxSubmittedDtSeconds) && maxSubmittedDtSeconds > 0.0f
            ? maxSubmittedDtSeconds
            : safeDtSeconds;
        if (safeDtSeconds <= safeMax) {
            return new SubmittedDt(safeDtSeconds, 0.0f, false);
        }
        return new SubmittedDt(safeMax, safeDtSeconds - safeMax, true);
    }

    @FunctionalInterface
    public interface StepTask {

        @Nonnull
        CompletedStep run();
    }

    public record StepInput(float inputDtSeconds,
                            float submittedDtSeconds,
                            float backlogDtSeconds,
                            float droppedBacklogDtSeconds,
                            boolean dtCapHit) {

        public StepInput {
            inputDtSeconds = safeDt(inputDtSeconds);
            submittedDtSeconds = safeDt(submittedDtSeconds);
            backlogDtSeconds = safeDt(backlogDtSeconds);
            droppedBacklogDtSeconds = safeDt(droppedBacklogDtSeconds);
        }
    }

    public record TickDecision(boolean shouldTick,
                               long pendingStepAgeNanos,
                               float inputDtSeconds,
                               float submittedDtSeconds,
                               float backlogDtSeconds,
                               float droppedBacklogDtSeconds,
                               boolean dtCapHit) {

        private static TickDecision tick() {
            return new TickDecision(true, 0L, 0.0f, 0.0f, 0.0f, 0.0f, false);
        }

        private static TickDecision skip(long pendingStepAgeNanos, @Nonnull StepInput input) {
            return new TickDecision(false,
                Math.max(0L, pendingStepAgeNanos),
                input.inputDtSeconds(),
                input.submittedDtSeconds(),
                input.backlogDtSeconds(),
                input.droppedBacklogDtSeconds(),
                input.dtCapHit());
        }
    }

    public record CompletedStep(@Nullable StepInput input,
                                int spaces,
                                int substeps,
                                long stepSubmitNanos,
                                long snapshotNanos,
                                @Nonnull PhysicsStepPhaseStats nativePhaseStats,
                                @Nonnull List<PhysicsBodySnapshot> bodySnapshots,
                                @Nullable Throwable failure) {

        public CompletedStep(int spaces,
            int substeps,
            long stepSubmitNanos,
            @Nonnull PhysicsStepPhaseStats nativePhaseStats) {
            this(spaces, substeps, stepSubmitNanos, 0L, nativePhaseStats, List.of());
        }

        public CompletedStep(int spaces,
            int substeps,
            long stepSubmitNanos,
            long snapshotNanos,
            @Nonnull PhysicsStepPhaseStats nativePhaseStats,
            @Nonnull List<PhysicsBodySnapshot> bodySnapshots) {
            this(null,
                spaces,
                substeps,
                stepSubmitNanos,
                snapshotNanos,
                nativePhaseStats,
                bodySnapshots,
                null);
        }

        public CompletedStep {
            spaces = Math.max(0, spaces);
            substeps = Math.max(0, substeps);
            stepSubmitNanos = Math.max(0L, stepSubmitNanos);
            snapshotNanos = Math.max(0L, snapshotNanos);
            Objects.requireNonNull(nativePhaseStats, "nativePhaseStats");
            bodySnapshots = List.copyOf(Objects.requireNonNull(bodySnapshots,
                "bodySnapshots"));
        }

        @Nonnull
        private CompletedStep withInput(@Nonnull StepInput input) {
            return new CompletedStep(input,
                spaces,
                substeps,
                stepSubmitNanos,
                snapshotNanos,
                nativePhaseStats,
                bodySnapshots,
                failure);
        }

        private static CompletedStep failed(@Nonnull StepInput input,
            @Nonnull Throwable failure) {
            return new CompletedStep(input,
                0,
                0,
                0L,
                0L,
                PhysicsStepPhaseStats.unavailable(),
                List.of(),
                Objects.requireNonNull(failure, "failure"));
        }

        public boolean failed() {
            return failure != null;
        }
    }

    private record PendingStep(@Nonnull StepInput input,
                               @Nonnull CompletableFuture<CompletedStep> future,
                               long startedNanos) {
    }

    private record SubmittedDt(float submittedDtSeconds,
                               float droppedDtSeconds,
                               boolean dtCapHit) {
    }

    private static final class OwnerLaneThreadFactory implements ThreadFactory {

        @Override
        public Thread newThread(@Nonnull Runnable runnable) {
            Thread thread = new Thread(runnable,
                "Impulse PhysicsStore owner lane-" + THREAD_COUNTER.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    }
}
