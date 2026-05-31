package dev.hytalemodding.impulse.core.plugin.simulation;

import dev.hytalemodding.impulse.core.plugin.events.PhysicsEventFrame;
import dev.hytalemodding.impulse.core.plugin.snapshot.PublishedPhysicsSnapshotFrame;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import javax.annotation.Nonnull;

/**
 * Completion handle for a submitted physics command batch.
 *
 * <p>The completion stage reports owner-lane execution of the batch. It does not mean the
 * latest published snapshot, ECS attachments, or debug readers have observed the resulting body
 * state yet. Use the snapshot-frame inclusion helpers when callers need to correlate a completion
 * to a captured physics snapshot frame.</p>
 */
public final class PhysicsCommandHandle {

    @Nonnull
    private final PhysicsCommandBatch batch;
    @Nonnull
    private final CompletableFuture<PhysicsCommandCompletion> completion;

    private PhysicsCommandHandle(@Nonnull PhysicsCommandBatch batch,
        @Nonnull CompletableFuture<PhysicsCommandCompletion> completion) {
        this.batch = Objects.requireNonNull(batch, "batch");
        this.completion = Objects.requireNonNull(completion, "completion");
    }

    @Nonnull
    public static PhysicsCommandHandle completed(@Nonnull PhysicsCommandBatch batch,
        @Nonnull List<PhysicsCommandResult> results) {
        return new PhysicsCommandHandle(batch,
            CompletableFuture.completedFuture(PhysicsCommandCompletion.of(results)));
    }

    @Nonnull
    public static PhysicsCommandHandle failed(@Nonnull PhysicsCommandBatch batch,
        @Nonnull Throwable failure) {
        CompletableFuture<PhysicsCommandCompletion> completion = new CompletableFuture<>();
        completion.completeExceptionally(Objects.requireNonNull(failure, "failure"));
        return new PhysicsCommandHandle(batch, completion);
    }

    @Nonnull
    public static PhysicsCommandHandle fromCompletion(@Nonnull PhysicsCommandBatch batch,
        @Nonnull CompletionStage<List<PhysicsCommandResult>> completion) {
        return new PhysicsCommandHandle(batch,
            Objects.requireNonNull(completion, "completion")
                .thenApply(PhysicsCommandCompletion::of)
                .toCompletableFuture());
    }

    @Nonnull
    public static PhysicsCommandHandle fromCompletionSummary(@Nonnull PhysicsCommandBatch batch,
        @Nonnull CompletionStage<PhysicsCommandCompletion> completion) {
        return new PhysicsCommandHandle(batch,
            Objects.requireNonNull(completion, "completion").toCompletableFuture());
    }

    @Nonnull
    public PhysicsCommandBatch batch() {
        return batch;
    }

    /**
     * Returns whether the latest captured snapshot in {@code frame} is known to include this
     * submitted command batch.
     */
    public boolean isIncludedInLatestCapturedSnapshot(@Nonnull PhysicsEventFrame frame) {
        return Objects.requireNonNull(frame, "frame")
            .latestCapturedSnapshotIncludesCommandBatch(batch.metadata().commandBatchSequence());
    }

    /**
     * Returns the submitted-server-tick distance from this command batch to the latest captured
     * snapshot in {@code frame}, or {@code 0} when that snapshot is not known to include this batch.
     */
    public long capturedSnapshotServerTickLatency(@Nonnull PhysicsEventFrame frame) {
        PhysicsEventFrame eventFrame = Objects.requireNonNull(frame, "frame");
        if (!eventFrame.latestCapturedSnapshotIncludesCommandBatch(batch.metadata().commandBatchSequence())) {
            return 0L;
        }
        return eventFrame.latestCapturedSnapshotServerTickLatencyFromSubmittedTick(
            batch.metadata().submittedServerTick());
    }

    /**
     * Returns whether {@code frame} is known to include this submitted command batch.
     */
    public boolean isIncludedInSnapshotFrame(@Nonnull PublishedPhysicsSnapshotFrame frame) {
        return Objects.requireNonNull(frame, "frame")
            .includesCommandBatch(batch.metadata().commandBatchSequence());
    }

    /**
     * Returns the submitted-server-tick distance from this command batch to {@code frame}, or
     * {@code 0} when the snapshot is not known to include this batch.
     */
    public long capturedSnapshotServerTickLatency(@Nonnull PublishedPhysicsSnapshotFrame frame) {
        PublishedPhysicsSnapshotFrame snapshotFrame = Objects.requireNonNull(frame, "frame");
        if (!snapshotFrame.includesCommandBatch(batch.metadata().commandBatchSequence())) {
            return 0L;
        }
        return snapshotFrame.serverTickLatencyFromSubmittedTick(
            batch.metadata().submittedServerTick());
    }

    @Nonnull
    public CompletionStage<List<PhysicsCommandResult>> completion() {
        return completion.thenApply(PhysicsCommandCompletion::results);
    }

    /**
     * Returns the owner-lane execution summary for this batch.
     *
     * <p>This stage completes before snapshot capture, reader-side snapshot application, and ECS
     * consumption. Use {@link #isIncludedInSnapshotFrame(PublishedPhysicsSnapshotFrame)} or
     * {@link #isIncludedInLatestCapturedSnapshot(PhysicsEventFrame)} when snapshot-frame inclusion
     * matters.</p>
     */
    @Nonnull
    public CompletionStage<PhysicsCommandCompletion> completionSummary() {
        return completion.minimalCompletionStage();
    }

    /**
     * Returns whether every recorded operation applied on the physics owner lane.
     */
    @Nonnull
    public CompletionStage<Boolean> allApplied() {
        return completion.thenApply(PhysicsCommandCompletion::allApplied);
    }

    /**
     * Returns the first owner-lane rejection, if any.
     */
    @Nonnull
    public CompletionStage<Optional<PhysicsCommandResult>> firstRejected() {
        return completion.thenApply(PhysicsCommandCompletion::firstRejected);
    }
}
