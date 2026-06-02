package dev.hytalemodding.impulse.api.runtime;

/**
 * Primitive step-phase timing callback.
 */
@FunctionalInterface
public interface BackendStepPhaseStatsSink {

    void accept(long stepNanos,
        long broadPhaseNanos,
        long narrowPhaseNanos,
        long solverNanos,
        long continuousCollisionNanos,
        long snapshotNanos,
        boolean available);
}
