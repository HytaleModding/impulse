package dev.hytalemodding.impulse.api.runtime;

/**
 * Copied backend phase timing counters.
 */
public record BackendStepPhaseStats(long stepNanos,
                                    long broadPhaseNanos,
                                    long narrowPhaseNanos,
                                    long solverNanos,
                                    long continuousCollisionNanos,
                                    long snapshotNanos,
                                    boolean available) {

    public static BackendStepPhaseStats unavailable() {
        return new BackendStepPhaseStats(0L, 0L, 0L, 0L, 0L, 0L, false);
    }
}
