package dev.hytalemodding.impulse.core.internal.resources.owner;

import dev.hytalemodding.impulse.api.PhysicsStepPhaseStats;
import javax.annotation.Nonnull;

/**
 * Step output published by a physics owner command.
 */
public record PhysicsOwnerSnapshot(int spaces,
                                   int substeps,
                                   int bodySnapshots,
                                   int spatialIndexCells,
                                   long stepNanos,
                                   long snapshotNanos,
                                   @Nonnull PhysicsStepPhaseStats nativePhaseStats) {

    public PhysicsOwnerSnapshot {
        spaces = Math.max(0, spaces);
        substeps = Math.max(0, substeps);
        bodySnapshots = Math.max(0, bodySnapshots);
        spatialIndexCells = Math.max(0, spatialIndexCells);
        stepNanos = Math.max(0L, stepNanos);
        snapshotNanos = Math.max(0L, snapshotNanos);
    }

    public PhysicsOwnerSnapshot(int spaces,
        int substeps,
        int bodySnapshots,
        int spatialIndexCells,
        long stepNanos,
        long snapshotNanos) {
        this(spaces,
            substeps,
            bodySnapshots,
            spatialIndexCells,
            stepNanos,
            snapshotNanos,
            PhysicsStepPhaseStats.unavailable());
    }

    @Nonnull
    public static PhysicsOwnerSnapshot empty() {
        return new PhysicsOwnerSnapshot(0,
            0,
            0,
            0,
            0L,
            0L,
            PhysicsStepPhaseStats.unavailable());
    }
}
