package dev.hytalemodding.impulse.core.plugin.events;

import java.util.List;
import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Immutable value frame of physics-owner events.
 *
 * <p>Event frames describe owner-thread outcomes. They are distinct from command
 * contexts, backend handles, and published body snapshots.</p>
 *
 * <p>The runtime keeps only the latest frame. This is useful for diagnostics, latency
 * correlation, and status overlays, but it is not a durable replay stream or event bus.</p>
 *
 * @param frameSequence event-frame publication sequence
 * @param worldEpoch physics world epoch observed when the event frame was published
 * @param latestSnapshotFrameEpoch latest published snapshot frame visible when the event frame was
 *     published
 * @param latestSnapshotStepSequence step-scheduler sequence carried by that latest snapshot frame
 * @param latestSnapshotServerTick server tick carried by that latest snapshot frame
 * @param latestSnapshotCommandBatchSequenceWatermark command-batch watermark on that latest
 *     snapshot frame
 * @param commandBatches copied command-batch outcome events in this frame
 * @param steps copied physics step snapshot-capture events in this frame
 * @param snapshotPublications copied reader-side snapshot-publication events in this frame
 */
public record PhysicsEventFrame(long frameSequence,
                                long worldEpoch,
                                long latestSnapshotFrameEpoch,
                                long latestSnapshotStepSequence,
                                long latestSnapshotServerTick,
                                long latestSnapshotCommandBatchSequenceWatermark,
                                @Nonnull List<PhysicsCommandBatchEvent> commandBatches,
                                @Nonnull List<PhysicsStepEvent> steps,
                                @Nonnull List<PhysicsSnapshotPublicationEvent> snapshotPublications) {

    public PhysicsEventFrame {
        frameSequence = Math.max(0L, frameSequence);
        worldEpoch = Math.max(0L, worldEpoch);
        latestSnapshotFrameEpoch = Math.max(0L, latestSnapshotFrameEpoch);
        latestSnapshotStepSequence = Math.max(0L, latestSnapshotStepSequence);
        latestSnapshotServerTick = Math.max(0L, latestSnapshotServerTick);
        latestSnapshotCommandBatchSequenceWatermark =
            Math.max(0L, latestSnapshotCommandBatchSequenceWatermark);
        commandBatches = List.copyOf(Objects.requireNonNull(commandBatches, "commandBatches"));
        steps = List.copyOf(Objects.requireNonNull(steps, "steps"));
        snapshotPublications =
            List.copyOf(Objects.requireNonNull(snapshotPublications, "snapshotPublications"));
    }

    public PhysicsEventFrame(long frameSequence,
        long worldEpoch,
        long latestSnapshotFrameEpoch,
        long latestSnapshotCommandBatchSequenceWatermark,
        @Nonnull List<PhysicsCommandBatchEvent> commandBatches,
        @Nonnull List<PhysicsStepEvent> steps,
        @Nonnull List<PhysicsSnapshotPublicationEvent> snapshotPublications) {
        this(frameSequence,
            worldEpoch,
            latestSnapshotFrameEpoch,
            0L,
            0L,
            latestSnapshotCommandBatchSequenceWatermark,
            commandBatches,
            steps,
            snapshotPublications);
    }

    public PhysicsEventFrame(long frameSequence,
        long worldEpoch,
        long latestSnapshotFrameEpoch,
        long latestSnapshotCommandBatchSequenceWatermark,
        @Nonnull List<PhysicsCommandBatchEvent> commandBatches) {
        this(frameSequence,
            worldEpoch,
            latestSnapshotFrameEpoch,
            0L,
            0L,
            latestSnapshotCommandBatchSequenceWatermark,
            commandBatches,
            List.of(),
            List.of());
    }

    @Nonnull
    public static PhysicsEventFrame empty(long worldEpoch) {
        return new PhysicsEventFrame(0L, worldEpoch, 0L, 0L, 0L, 0L, List.of(), List.of(), List.of());
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

    public int eventCount() {
        return commandBatchCount() + stepCount() + snapshotPublicationCount();
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

    public boolean latestSnapshotIncludes(@Nonnull PhysicsCommandBatchEvent event) {
        return latestSnapshotIncludesCommandBatch(
            Objects.requireNonNull(event, "event").commandBatchSequence());
    }

    public boolean latestSnapshotIncludesCommandBatch(long commandBatchSequence) {
        return commandBatchSequence > 0L
            && latestSnapshotCommandBatchSequenceWatermark >= commandBatchSequence;
    }

    public long visibleSnapshotServerTickLatency(@Nonnull PhysicsCommandBatchEvent event) {
        if (!latestSnapshotIncludes(event)) {
            return 0L;
        }
        return latestSnapshotServerTickLatencyFromSubmittedTick(event.submittedServerTick());
    }

    public long latestSnapshotServerTickLatencyFromSubmittedTick(long submittedServerTick) {
        long submitted = Math.max(0L, submittedServerTick);
        if (latestSnapshotServerTick <= 0L || latestSnapshotServerTick < submitted) {
            return 0L;
        }
        return latestSnapshotServerTick - submitted;
    }

    public long latestSnapshotFrameLatencyFrom(long snapshotFrameEpoch) {
        long frameEpoch = Math.max(0L, snapshotFrameEpoch);
        if (latestSnapshotFrameEpoch <= frameEpoch) {
            return 0L;
        }
        return latestSnapshotFrameEpoch - frameEpoch;
    }
}
