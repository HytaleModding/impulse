package dev.hytalemodding.impulse.core.plugin.events;

/**
 * Value-only event for applying a published snapshot frame to reader-side stores.
 *
 * @param snapshotFrameEpoch applied snapshot frame epoch
 * @param worldEpoch frame world epoch
 * @param stepSequence Impulse step-scheduler sequence carried by the frame
 * @param serverTick Hytale server tick carried by the frame
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
                                              long publicationServerTick,
                                              long publicationNanoTime,
                                              int appliedBodyCount) {

    public PhysicsSnapshotPublicationEvent(long snapshotFrameEpoch,
        long worldEpoch,
        long stepSequence,
        long serverTick,
        int appliedBodyCount) {
        this(snapshotFrameEpoch,
            worldEpoch,
            stepSequence,
            serverTick,
            0L,
            0L,
            appliedBodyCount);
    }

    public PhysicsSnapshotPublicationEvent {
        snapshotFrameEpoch = Math.max(0L, snapshotFrameEpoch);
        worldEpoch = Math.max(0L, worldEpoch);
        stepSequence = Math.max(0L, stepSequence);
        serverTick = Math.max(0L, serverTick);
        publicationServerTick = Math.max(0L, publicationServerTick);
        publicationNanoTime = Math.max(0L, publicationNanoTime);
        appliedBodyCount = Math.max(0, appliedBodyCount);
    }

    public long frameToPublicationServerTickLatency() {
        if (publicationServerTick <= 0L || publicationServerTick < serverTick) {
            return 0L;
        }
        return publicationServerTick - serverTick;
    }
}
