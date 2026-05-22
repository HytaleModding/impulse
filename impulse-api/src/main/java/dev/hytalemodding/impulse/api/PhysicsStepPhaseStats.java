package dev.hytalemodding.impulse.api;

import javax.annotation.Nonnull;

/**
 * Optional backend-native phase timings for one or more physics steps.
 *
 * <p>Backends may expose different internal phase boundaries. These values are
 * diagnostic counters for profiling and should not be used for simulation behavior.</p>
 */
public record PhysicsStepPhaseStats(boolean available,
                                    long stepNanos,
                                    long broadPhaseNanos,
                                    long narrowPhaseNanos,
                                    long solverNanos,
                                    long ccdNanos,
                                    long snapshotNanos) {

    private static final PhysicsStepPhaseStats UNAVAILABLE = new PhysicsStepPhaseStats(false,
        0L,
        0L,
        0L,
        0L,
        0L,
        0L);

    public PhysicsStepPhaseStats {
        if (!available) {
            stepNanos = 0L;
            broadPhaseNanos = 0L;
            narrowPhaseNanos = 0L;
            solverNanos = 0L;
            ccdNanos = 0L;
            snapshotNanos = 0L;
        } else {
            stepNanos = Math.max(0L, stepNanos);
            broadPhaseNanos = Math.max(0L, broadPhaseNanos);
            narrowPhaseNanos = Math.max(0L, narrowPhaseNanos);
            solverNanos = Math.max(0L, solverNanos);
            ccdNanos = Math.max(0L, ccdNanos);
            snapshotNanos = Math.max(0L, snapshotNanos);
        }
    }

    @Nonnull
    public static PhysicsStepPhaseStats available(long stepNanos,
        long broadPhaseNanos,
        long narrowPhaseNanos,
        long solverNanos,
        long ccdNanos,
        long snapshotNanos) {
        return new PhysicsStepPhaseStats(true,
            stepNanos,
            broadPhaseNanos,
            narrowPhaseNanos,
            solverNanos,
            ccdNanos,
            snapshotNanos);
    }

    @Nonnull
    public static PhysicsStepPhaseStats unavailable() {
        return UNAVAILABLE;
    }

    @Nonnull
    public PhysicsStepPhaseStats add(@Nonnull PhysicsStepPhaseStats other) {
        if (!available && !other.available) {
            return UNAVAILABLE;
        }
        return available(safeAdd(stepNanos, other.stepNanos),
            safeAdd(broadPhaseNanos, other.broadPhaseNanos),
            safeAdd(narrowPhaseNanos, other.narrowPhaseNanos),
            safeAdd(solverNanos, other.solverNanos),
            safeAdd(ccdNanos, other.ccdNanos),
            safeAdd(snapshotNanos, other.snapshotNanos));
    }

    private static long safeAdd(long left, long right) {
        if (Long.MAX_VALUE - left < right) {
            return Long.MAX_VALUE;
        }
        return left + right;
    }
}
