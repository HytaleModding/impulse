package dev.hytalemodding.impulse.core.internal.worker;

import javax.annotation.Nonnull;

/**
 * Step output published by a physics worker command.
 */
public record PhysicsWorkerSnapshot(int spaces,
                                    int substeps,
                                    int bodySnapshots,
                                    int spatialIndexCells,
                                    long stepNanos,
                                    long snapshotNanos) {

    public PhysicsWorkerSnapshot {
        spaces = Math.max(0, spaces);
        substeps = Math.max(0, substeps);
        bodySnapshots = Math.max(0, bodySnapshots);
        spatialIndexCells = Math.max(0, spatialIndexCells);
        stepNanos = Math.max(0L, stepNanos);
        snapshotNanos = Math.max(0L, snapshotNanos);
    }

    @Nonnull
    public static PhysicsWorkerSnapshot empty() {
        return new PhysicsWorkerSnapshot(0, 0, 0, 0, 0L, 0L);
    }
}
