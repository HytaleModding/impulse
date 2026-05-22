package dev.hytalemodding.impulse.core.internal.worker;

import dev.hytalemodding.impulse.api.PhysicsStepPhaseStats;
import javax.annotation.Nonnull;

/**
 * Step output published by a physics worker command.
 */
public record PhysicsWorkerSnapshot(int spaces,
                                    int substeps,
                                    int bodySnapshots,
                                    int spatialIndexCells,
                                    long stepNanos,
                                    long snapshotNanos,
                                    @Nonnull PhysicsStepPhaseStats nativePhaseStats) {

    public PhysicsWorkerSnapshot {
        spaces = Math.max(0, spaces);
        substeps = Math.max(0, substeps);
        bodySnapshots = Math.max(0, bodySnapshots);
        spatialIndexCells = Math.max(0, spatialIndexCells);
        stepNanos = Math.max(0L, stepNanos);
        snapshotNanos = Math.max(0L, snapshotNanos);
        nativePhaseStats = nativePhaseStats == null
            ? PhysicsStepPhaseStats.unavailable()
            : nativePhaseStats;
    }

    public PhysicsWorkerSnapshot(int spaces,
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
    public static PhysicsWorkerSnapshot empty() {
        return new PhysicsWorkerSnapshot(0,
            0,
            0,
            0,
            0L,
            0L,
            PhysicsStepPhaseStats.unavailable());
    }
}
