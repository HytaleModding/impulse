package dev.hytalemodding.impulse.core.internal.resources;

import dev.hytalemodding.impulse.core.plugin.events.PhysicsCommandBatchEvent;
import dev.hytalemodding.impulse.core.plugin.events.PhysicsEventFrame;
import dev.hytalemodding.impulse.core.plugin.events.PhysicsFrameEvent;
import dev.hytalemodding.impulse.core.plugin.events.PhysicsSnapshotPublicationEvent;
import dev.hytalemodding.impulse.core.plugin.events.PhysicsStepEvent;
import dev.hytalemodding.impulse.core.internal.simulation.RecordedPhysicsCommandBatch;
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import dev.hytalemodding.impulse.core.plugin.joint.JointKey;
import dev.hytalemodding.impulse.core.plugin.simulation.PhysicsCommandCompletion;
import dev.hytalemodding.impulse.core.plugin.simulation.PhysicsCommandMetadata;
import dev.hytalemodding.impulse.core.plugin.simulation.PhysicsCommandResult;
import dev.hytalemodding.impulse.core.plugin.snapshot.PublishedPhysicsSnapshotFrame;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Latest value-only event frame for physics-owner outcomes.
 *
 * <p>This state intentionally replaces the previous frame instead of queueing history. The public
 * event frame is a low-overhead diagnostic snapshot of the newest owner outcome and latest
 * captured snapshot inclusion state.</p>
 */
public final class PhysicsWorldEventState {

    private final AtomicLong eventFrameSequence = new AtomicLong();
    @Nonnull
    private final AtomicReference<PhysicsEventFrame> latestFrame =
        new AtomicReference<>(PhysicsEventFrame.empty(0L));

    @Nonnull
    public PhysicsEventFrame getLatestFrame() {
        return latestFrame.get();
    }

    @Nonnull
    public PhysicsEventFrame publishCommandCompletion(long worldEpoch,
        @Nonnull PublishedPhysicsSnapshotFrame latestCapturedSnapshotFrame,
        @Nonnull PhysicsCommandMetadata metadata,
        int commandCount,
        @Nonnull PhysicsCommandCompletion completion) {
        return publishCommandCompletion(worldEpoch,
            latestCapturedSnapshotFrame,
            metadata,
            commandCount,
            0,
            null,
            0,
            null,
            completion);
    }

    @Nonnull
    public PhysicsEventFrame publishCommandCompletion(long worldEpoch,
        @Nonnull PublishedPhysicsSnapshotFrame latestCapturedSnapshotFrame,
        @Nonnull RecordedPhysicsCommandBatch batch,
        @Nonnull PhysicsCommandCompletion completion) {
        Objects.requireNonNull(batch, "batch");
        return publishCommandCompletion(worldEpoch,
            latestCapturedSnapshotFrame,
            batch.metadata(),
            batch.publicBatch().commandCount(),
            batch.bodyKeyReferenceCount(),
            batch.firstBodyKey(),
            batch.jointKeyReferenceCount(),
            batch.firstJointKey(),
            completion);
    }

    @Nonnull
    private PhysicsEventFrame publishCommandCompletion(long worldEpoch,
        @Nonnull PublishedPhysicsSnapshotFrame latestCapturedSnapshotFrame,
        @Nonnull PhysicsCommandMetadata metadata,
        int commandCount,
        int bodyKeyReferenceCount,
        @Nullable RigidBodyKey firstBodyKey,
        int jointKeyReferenceCount,
        @Nullable JointKey firstJointKey,
        @Nonnull PhysicsCommandCompletion completion) {
        Objects.requireNonNull(latestCapturedSnapshotFrame, "latestCapturedSnapshotFrame");
        Objects.requireNonNull(metadata, "metadata");
        Objects.requireNonNull(completion, "completion");
        PhysicsCommandResult firstRejected = completion.firstRejected().orElse(null);
        PhysicsCommandBatchEvent commandEvent = new PhysicsCommandBatchEvent(metadata.commandBatchSequence(),
            metadata.submittedServerTick(),
            System.nanoTime(),
            commandCount,
            bodyKeyReferenceCount,
            firstBodyKey,
            jointKeyReferenceCount,
            firstJointKey,
            completion.allApplied(),
            firstRejected != null ? firstRejected.commandSequence() : 0L,
            firstRejected != null ? firstRejected.message() : null);
        return publishFrame(worldEpoch, latestCapturedSnapshotFrame, List.of(commandEvent), List.of(), List.of());
    }

    @Nonnull
    public PhysicsEventFrame publishStepCaptured(long worldEpoch,
        @Nonnull PublishedPhysicsSnapshotFrame snapshotFrame) {
        return publishStepCaptured(worldEpoch, snapshotFrame, List.of(), 0);
    }

    @Nonnull
    public PhysicsEventFrame publishStepCaptured(long worldEpoch,
        @Nonnull PublishedPhysicsSnapshotFrame snapshotFrame,
        @Nonnull List<PhysicsFrameEvent> physicsEvents,
        int droppedBackendEventCount) {
        Objects.requireNonNull(snapshotFrame, "snapshotFrame");
        PhysicsStepEvent stepEvent = new PhysicsStepEvent(snapshotFrame.stepSequence(),
            snapshotFrame.serverTick(),
            snapshotFrame.frameEpoch(),
            snapshotFrame.status(),
            snapshotFrame.lastIncludedCommandBatchSequence(),
            snapshotFrame.bodyCount(),
            snapshotFrame.stepNanos(),
            snapshotFrame.snapshotNanos());
        return publishFrame(worldEpoch,
            snapshotFrame,
            List.of(),
            List.of(stepEvent),
            List.of(),
            physicsEvents,
            droppedBackendEventCount);
    }

    @Nonnull
    public PhysicsEventFrame publishSnapshotPublication(long worldEpoch,
        @Nonnull PublishedPhysicsSnapshotFrame snapshotFrame,
        int appliedBodyCount) {
        return publishSnapshotPublication(worldEpoch, snapshotFrame, appliedBodyCount, 0L);
    }

    @Nonnull
    public PhysicsEventFrame publishSnapshotPublication(long worldEpoch,
        @Nonnull PublishedPhysicsSnapshotFrame snapshotFrame,
        int appliedBodyCount,
        long publicationServerTick) {
        Objects.requireNonNull(snapshotFrame, "snapshotFrame");
        PhysicsSnapshotPublicationEvent publicationEvent =
            new PhysicsSnapshotPublicationEvent(snapshotFrame.frameEpoch(),
                snapshotFrame.worldEpoch(),
                snapshotFrame.stepSequence(),
                snapshotFrame.serverTick(),
                snapshotFrame.lastIncludedCommandBatchSequence(),
                publicationServerTick,
                System.nanoTime(),
                appliedBodyCount);
        return publishFrame(worldEpoch, snapshotFrame, List.of(), List.of(), List.of(publicationEvent));
    }

    @Nonnull
    public PhysicsEventFrame publishEmpty(long worldEpoch,
        @Nonnull PublishedPhysicsSnapshotFrame latestCapturedSnapshotFrame) {
        return publishFrame(worldEpoch,
            Objects.requireNonNull(latestCapturedSnapshotFrame, "latestCapturedSnapshotFrame"),
            List.of(),
            List.of(),
            List.of());
    }

    @Nonnull
    private PhysicsEventFrame publishFrame(long worldEpoch,
        @Nonnull PublishedPhysicsSnapshotFrame latestCapturedSnapshotFrame,
        @Nonnull List<PhysicsCommandBatchEvent> commandEvents,
        @Nonnull List<PhysicsStepEvent> stepEvents,
        @Nonnull List<PhysicsSnapshotPublicationEvent> publicationEvents) {
        return publishFrame(worldEpoch,
            latestCapturedSnapshotFrame,
            commandEvents,
            stepEvents,
            publicationEvents,
            List.of(),
            0);
    }

    @Nonnull
    private PhysicsEventFrame publishFrame(long worldEpoch,
        @Nonnull PublishedPhysicsSnapshotFrame latestCapturedSnapshotFrame,
        @Nonnull List<PhysicsCommandBatchEvent> commandEvents,
        @Nonnull List<PhysicsStepEvent> stepEvents,
        @Nonnull List<PhysicsSnapshotPublicationEvent> publicationEvents,
        @Nonnull List<PhysicsFrameEvent> physicsEvents,
        int droppedBackendEventCount) {
        PhysicsEventFrame frame = new PhysicsEventFrame(eventFrameSequence.incrementAndGet(),
            worldEpoch,
            latestCapturedSnapshotFrame.frameEpoch(),
            latestCapturedSnapshotFrame.stepSequence(),
            latestCapturedSnapshotFrame.serverTick(),
            latestCapturedSnapshotFrame.lastIncludedCommandBatchSequence(),
            commandEvents,
            stepEvents,
            publicationEvents,
            physicsEvents,
            droppedBackendEventCount);
        latestFrame.set(frame);
        return frame;
    }
}
