package dev.hytalemodding.impulse.core.plugin.physicsstore.requests;

import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nonnull;

/**
 * Runtime-only completion summary for one queued PhysicsStore request batch.
 */
public record PhysicsStoreRequestFenceResult(@Nonnull UUID fenceUuid,
                                             long submittedServerTick,
                                             long consumedServerTick,
                                             int acceptedCount,
                                             int appliedCount,
                                             int softSkippedCount,
                                             int rejectedCount,
                                             int failedCount) {

    public PhysicsStoreRequestFenceResult {
        Objects.requireNonNull(fenceUuid, "fenceUuid");
        submittedServerTick = Math.max(0L, submittedServerTick);
        consumedServerTick = Math.max(0L, consumedServerTick);
        acceptedCount = Math.max(0, acceptedCount);
        appliedCount = Math.max(0, appliedCount);
        softSkippedCount = Math.max(0, softSkippedCount);
        rejectedCount = Math.max(0, rejectedCount);
        failedCount = Math.max(0, failedCount);
    }

    public boolean allApplied() {
        return acceptedCount == appliedCount
            && softSkippedCount == 0
            && rejectedCount == 0
            && failedCount == 0;
    }

    public boolean hasProblems() {
        return softSkippedCount > 0
            || rejectedCount > 0
            || failedCount > 0
            || acceptedCount != appliedCount + softSkippedCount + rejectedCount + failedCount;
    }

    public long consumedServerTickLatency() {
        return Math.max(0L, consumedServerTick - submittedServerTick);
    }
}
