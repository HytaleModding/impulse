package dev.hytalemodding.impulse.core.plugin.events;

import dev.hytalemodding.impulse.core.plugin.snapshot.PublishedPhysicsSnapshotFrame;
import java.util.Objects;
import javax.annotation.Nonnull;

/**
 * Value-only event for a physics owner step that captured a snapshot frame.
 *
 * @param stepSequence Impulse step-scheduler sequence copied into the captured snapshot
 * @param serverTick Hytale server tick copied into the captured snapshot
 * @param snapshotFrameEpoch captured snapshot frame epoch
 * @param snapshotStatus captured snapshot status
 * @param lastIncludedCommandBatchSequence latest completed command batch included by the capture
 * @param bodyCount number of body snapshots captured in the frame
 * @param stepNanos profiled step duration, or {@code 0} when profiling was disabled
 * @param snapshotNanos profiled snapshot capture duration, or {@code 0} when profiling was disabled
 */
public record PhysicsStepEvent(long stepSequence,
                               long serverTick,
                               long snapshotFrameEpoch,
                               @Nonnull PublishedPhysicsSnapshotFrame.Status snapshotStatus,
                               long lastIncludedCommandBatchSequence,
                               int bodyCount,
                               long stepNanos,
                               long snapshotNanos) {

    public PhysicsStepEvent {
        stepSequence = Math.max(0L, stepSequence);
        serverTick = Math.max(0L, serverTick);
        snapshotFrameEpoch = Math.max(0L, snapshotFrameEpoch);
        Objects.requireNonNull(snapshotStatus, "snapshotStatus");
        lastIncludedCommandBatchSequence = Math.max(0L, lastIncludedCommandBatchSequence);
        bodyCount = Math.max(0, bodyCount);
        stepNanos = Math.max(0L, stepNanos);
        snapshotNanos = Math.max(0L, snapshotNanos);
    }
}
