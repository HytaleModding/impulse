package dev.hytalemodding.impulse.core.plugin.resources;

import java.util.List;
import java.util.Objects;
import javax.annotation.Nonnull;

/**
 * Immutable async snapshot frame for world-level physics readers.
 */
public record PublishedPhysicsSnapshotFrame(long frameEpoch,
                                            long worldEpoch,
                                            long stepSequence,
                                            long serverTick,
                                            @Nonnull Status status,
                                            int spatialIndexCellCount,
                                            long stepNanos,
                                            long snapshotNanos,
                                            @Nonnull List<PublishedPhysicsSpaceFrame> spaces) {

    public enum Status {
        EMPTY,
        COMPLETE,
        PARTIAL,
        FAILED
    }

    public PublishedPhysicsSnapshotFrame {
        requireNonNegativeEpoch(frameEpoch, "frameEpoch");
        requireNonNegativeEpoch(worldEpoch, "worldEpoch");
        requireNonNegativeEpoch(stepSequence, "stepSequence");
        requireNonNegativeEpoch(serverTick, "serverTick");
        Objects.requireNonNull(status, "status");
        if (spatialIndexCellCount < 0) {
            throw new IllegalArgumentException("spatialIndexCellCount cannot be negative");
        }
        requireNonNegativeEpoch(stepNanos, "stepNanos");
        requireNonNegativeEpoch(snapshotNanos, "snapshotNanos");
        spaces = List.copyOf(Objects.requireNonNull(spaces, "spaces"));
        for (PublishedPhysicsSpaceFrame space : spaces) {
            requireSpaceInFrame(frameEpoch, worldEpoch, space);
        }
    }

    @Nonnull
    public static PublishedPhysicsSnapshotFrame empty(long frameEpoch, long worldEpoch) {
        return new PublishedPhysicsSnapshotFrame(frameEpoch,
            worldEpoch,
            0L,
            0L,
            Status.EMPTY,
            0,
            0L,
            0L,
            List.of());
    }

    public int spaceCount() {
        return spaces.size();
    }

    public int bodyCount() {
        int count = 0;
        for (PublishedPhysicsSpaceFrame space : spaces) {
            count += space.bodyCount();
        }
        return count;
    }

    private static void requireSpaceInFrame(long frameEpoch,
        long worldEpoch,
        @Nonnull PublishedPhysicsSpaceFrame space) {
        if (space.frameEpoch() != frameEpoch) {
            throw new IllegalArgumentException("space frame epoch does not match snapshot frame");
        }
        if (space.worldEpoch() != worldEpoch) {
            throw new IllegalArgumentException("space world epoch does not match snapshot frame");
        }
    }

    private static void requireNonNegativeEpoch(long epoch, @Nonnull String label) {
        if (epoch < 0L) {
            throw new IllegalArgumentException(label + " cannot be negative");
        }
    }
}
