package dev.hytalemodding.impulse.core.plugin.events;

import java.util.List;
import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Immutable value frame of physics-owner events.
 *
 * <p>Event frames describe owner-lane outcomes. They are distinct from command
 * contexts, backend handles, and published body snapshots.</p>
 *
 * <p>The runtime keeps only the latest frame. This is useful for diagnostics, latency
 * correlation, and status overlays, but it is not a durable replay stream or event bus.</p>
 *
 * @param frameSequence event-frame publication sequence
 * @param worldEpoch physics world epoch observed when the event frame was published
 * @param latestCapturedSnapshotFrameEpoch latest captured snapshot frame known when the event frame was
 *     published
 * @param latestCapturedSnapshotStepSequence step-scheduler sequence carried by that latest captured snapshot
 *     frame
 * @param latestCapturedSnapshotServerTick server tick carried by that latest captured snapshot frame
 * @param latestCapturedSnapshotLastIncludedCommandBatchSequence latest command-batch sequence
 *     included by that captured snapshot frame
 * @param commandBatches copied command-batch outcome events in this frame
 * @param steps copied physics step snapshot-capture events in this frame
 * @param snapshotPublications copied reader-side snapshot-publication events in this frame
 * @param physicsEvents copied stable physics events in this frame
 * @param droppedBackendEventCount backend events dropped while bounded buffers were full
 */
public record PhysicsEventFrame(long frameSequence,
                                long worldEpoch,
                                long latestCapturedSnapshotFrameEpoch,
                                long latestCapturedSnapshotStepSequence,
                                long latestCapturedSnapshotServerTick,
                                long latestCapturedSnapshotLastIncludedCommandBatchSequence,
                                @Nonnull List<PhysicsCommandBatchEvent> commandBatches,
                                @Nonnull List<PhysicsStepEvent> steps,
                                @Nonnull List<PhysicsSnapshotPublicationEvent> snapshotPublications,
                                @Nonnull List<PhysicsFrameEvent> physicsEvents,
                                int droppedBackendEventCount) {

    public PhysicsEventFrame {
        frameSequence = Math.max(0L, frameSequence);
        worldEpoch = Math.max(0L, worldEpoch);
        latestCapturedSnapshotFrameEpoch = Math.max(0L, latestCapturedSnapshotFrameEpoch);
        latestCapturedSnapshotStepSequence = Math.max(0L, latestCapturedSnapshotStepSequence);
        latestCapturedSnapshotServerTick = Math.max(0L, latestCapturedSnapshotServerTick);
        latestCapturedSnapshotLastIncludedCommandBatchSequence =
            Math.max(0L, latestCapturedSnapshotLastIncludedCommandBatchSequence);
        commandBatches = List.copyOf(Objects.requireNonNull(commandBatches, "commandBatches"));
        steps = List.copyOf(Objects.requireNonNull(steps, "steps"));
        snapshotPublications =
            List.copyOf(Objects.requireNonNull(snapshotPublications, "snapshotPublications"));
        physicsEvents = List.copyOf(Objects.requireNonNull(physicsEvents, "physicsEvents"));
        droppedBackendEventCount = Math.max(0, droppedBackendEventCount);
    }

    public PhysicsEventFrame(long frameSequence,
        long worldEpoch,
        long latestCapturedSnapshotFrameEpoch,
        long latestCapturedSnapshotLastIncludedCommandBatchSequence,
        @Nonnull List<PhysicsCommandBatchEvent> commandBatches,
        @Nonnull List<PhysicsStepEvent> steps,
        @Nonnull List<PhysicsSnapshotPublicationEvent> snapshotPublications) {
        this(frameSequence,
            worldEpoch,
            latestCapturedSnapshotFrameEpoch,
            0L,
            0L,
            latestCapturedSnapshotLastIncludedCommandBatchSequence,
            commandBatches,
            steps,
            snapshotPublications,
            List.of(),
            0);
    }

    public PhysicsEventFrame(long frameSequence,
        long worldEpoch,
        long latestCapturedSnapshotFrameEpoch,
        long latestCapturedSnapshotLastIncludedCommandBatchSequence,
        @Nonnull List<PhysicsCommandBatchEvent> commandBatches) {
        this(frameSequence,
            worldEpoch,
            latestCapturedSnapshotFrameEpoch,
            0L,
            0L,
            latestCapturedSnapshotLastIncludedCommandBatchSequence,
            commandBatches,
            List.of(),
            List.of(),
            List.of(),
            0);
    }

    @Nonnull
    public static PhysicsEventFrame empty(long worldEpoch) {
        return new PhysicsEventFrame(0L,
            worldEpoch,
            0L,
            0L,
            0L,
            0L,
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            0);
    }

    public int commandBatchCount() {
        return commandBatches.size();
    }

    public int stepCount() {
        return steps.size();
    }

    public int snapshotPublicationCount() {
        return snapshotPublications.size();
    }

    public int physicsEventCount() {
        return physicsEvents.size();
    }

    public int eventCount() {
        return commandBatchCount() + stepCount() + snapshotPublicationCount() + physicsEventCount();
    }

    public boolean isEmpty() {
        return eventCount() == 0;
    }

    @Nullable
    public PhysicsCommandBatchEvent latestCommandBatch() {
        int count = commandBatches.size();
        return count == 0 ? null : commandBatches.get(count - 1);
    }

    @Nullable
    public PhysicsStepEvent latestStep() {
        int count = steps.size();
        return count == 0 ? null : steps.get(count - 1);
    }

    @Nullable
    public PhysicsSnapshotPublicationEvent latestSnapshotPublication() {
        int count = snapshotPublications.size();
        return count == 0 ? null : snapshotPublications.get(count - 1);
    }

    public boolean latestCapturedSnapshotIncludes(@Nonnull PhysicsCommandBatchEvent event) {
        return latestCapturedSnapshotIncludesCommandBatch(
            Objects.requireNonNull(event, "event").commandBatchSequence());
    }

    public boolean latestCapturedSnapshotIncludesCommandBatch(long commandBatchSequence) {
        return commandBatchSequence > 0L
            && latestCapturedSnapshotLastIncludedCommandBatchSequence >= commandBatchSequence;
    }

    public long capturedSnapshotServerTickLatency(@Nonnull PhysicsCommandBatchEvent event) {
        if (!latestCapturedSnapshotIncludes(event)) {
            return 0L;
        }
        return latestCapturedSnapshotServerTickLatencyFromSubmittedTick(event.submittedServerTick());
    }

    public long latestCapturedSnapshotServerTickLatencyFromSubmittedTick(long submittedServerTick) {
        long submitted = Math.max(0L, submittedServerTick);
        if (latestCapturedSnapshotServerTick <= 0L || latestCapturedSnapshotServerTick < submitted) {
            return 0L;
        }
        return latestCapturedSnapshotServerTick - submitted;
    }

    public long latestCapturedSnapshotFrameLatencyFrom(long snapshotFrameEpoch) {
        long frameEpoch = Math.max(0L, snapshotFrameEpoch);
        if (latestCapturedSnapshotFrameEpoch <= frameEpoch) {
            return 0L;
        }
        return latestCapturedSnapshotFrameEpoch - frameEpoch;
    }
}
