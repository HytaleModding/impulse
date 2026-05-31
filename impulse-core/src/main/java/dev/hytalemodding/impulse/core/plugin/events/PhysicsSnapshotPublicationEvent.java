package dev.hytalemodding.impulse.core.plugin.events;

import javax.annotation.Nonnull;

/**
 * Value-only event for applying a published snapshot frame to reader-side stores.
 *
 * @param snapshotFrameEpoch applied snapshot frame epoch
 * @param worldEpoch frame world epoch
 * @param stepSequence Impulse step-scheduler sequence carried by the frame
 * @param serverTick Hytale server tick carried by the frame
 * @param lastIncludedCommandBatchSequence latest completed command batch included by the frame
 * @param publicationServerTick Hytale server tick observed when this frame was applied to
 *     reader-side stores, or {@code 0} when unavailable
 * @param publicationNanoTime monotonic nano time sampled when this publication event was created,
 *     or {@code 0} when unavailable
 * @param appliedBodyCount number of body snapshots applied to reader-side stores
 */
public record PhysicsSnapshotPublicationEvent(long snapshotFrameEpoch,
                                              long worldEpoch,
                                              long stepSequence,
                                              long serverTick,
                                              long lastIncludedCommandBatchSequence,
                                              long publicationServerTick,
                                              long publicationNanoTime,
                                              int appliedBodyCount) {

    public PhysicsSnapshotPublicationEvent(long snapshotFrameEpoch,
        long worldEpoch,
        long stepSequence,
        long serverTick,
        long lastIncludedCommandBatchSequence,
        int appliedBodyCount) {
        this(snapshotFrameEpoch,
            worldEpoch,
            stepSequence,
            serverTick,
            lastIncludedCommandBatchSequence,
            0L,
            0L,
            appliedBodyCount);
    }

    public PhysicsSnapshotPublicationEvent {
        snapshotFrameEpoch = Math.max(0L, snapshotFrameEpoch);
        worldEpoch = Math.max(0L, worldEpoch);
        stepSequence = Math.max(0L, stepSequence);
        serverTick = Math.max(0L, serverTick);
        lastIncludedCommandBatchSequence = Math.max(0L, lastIncludedCommandBatchSequence);
        publicationServerTick = Math.max(0L, publicationServerTick);
        publicationNanoTime = Math.max(0L, publicationNanoTime);
        appliedBodyCount = Math.max(0, appliedBodyCount);
    }

    public boolean includesCommandBatch(long commandBatchSequence) {
        return commandBatchSequence > 0L
            && lastIncludedCommandBatchSequence >= commandBatchSequence;
    }

    public boolean includesCommandBatch(@Nonnull PhysicsCommandBatchEvent event) {
        return includesCommandBatch(event.commandBatchSequence());
    }

    public long frameToPublicationServerTickLatency() {
        if (publicationServerTick <= 0L || publicationServerTick < serverTick) {
            return 0L;
        }
        return publicationServerTick - serverTick;
    }

    public long commandToPublicationServerTickLatency(@Nonnull PhysicsCommandBatchEvent event) {
        if (!includesCommandBatch(event)) {
            return 0L;
        }
        return serverTickLatencyFrom(event.submittedServerTick());
    }

    private long serverTickLatencyFrom(long submittedServerTick) {
        long submitted = Math.max(0L, submittedServerTick);
        if (publicationServerTick <= 0L || publicationServerTick < submitted) {
            return 0L;
        }
        return publicationServerTick - submitted;
    }
}
