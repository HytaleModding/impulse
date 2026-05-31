package dev.hytalemodding.impulse.core.plugin.simulation;

import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Copied result for one physics command.
 *
 * @param commandBatchSequence owner FIFO batch sequence assigned when the command batch was submitted
 * @param submittedServerTick server tick supplied by the caller when the command context was recorded
 * @param includedSnapshotFrameEpoch published snapshot frame that is known to include this command,
 *                                  or {@code 0} when completion has not been correlated to a
 *                                  snapshot frame
 */
public record PhysicsCommandResult(@Nonnull Status status,
                                   long commandSequence,
                                   long commandBatchSequence,
                                   long submittedServerTick,
                                   long includedSnapshotFrameEpoch,
                                   @Nullable String message) {

    public PhysicsCommandResult {
        Objects.requireNonNull(status, "status");
        commandSequence = Math.max(0L, commandSequence);
        commandBatchSequence = Math.max(0L, commandBatchSequence);
        submittedServerTick = Math.max(0L, submittedServerTick);
        includedSnapshotFrameEpoch = Math.max(0L, includedSnapshotFrameEpoch);
    }

    @Nonnull
    public static PhysicsCommandResult applied(long commandSequence) {
        return new PhysicsCommandResult(Status.APPLIED, commandSequence, 0L, 0L, 0L, null);
    }

    @Nonnull
    public static PhysicsCommandResult applied(@Nonnull PhysicsCommandMetadata metadata,
        long commandSequence) {
        Objects.requireNonNull(metadata, "metadata");
        return new PhysicsCommandResult(Status.APPLIED,
            commandSequence,
            metadata.commandBatchSequence(),
            metadata.submittedServerTick(),
            0L,
            null);
    }

    @Nonnull
    public static PhysicsCommandResult rejected(long commandSequence, @Nonnull String message) {
        return new PhysicsCommandResult(Status.REJECTED,
            commandSequence,
            0L,
            0L,
            0L,
            Objects.requireNonNull(message, "message"));
    }

    @Nonnull
    public static PhysicsCommandResult rejected(@Nonnull PhysicsCommandMetadata metadata,
        long commandSequence,
        @Nonnull String message) {
        Objects.requireNonNull(metadata, "metadata");
        return new PhysicsCommandResult(Status.REJECTED,
            commandSequence,
            metadata.commandBatchSequence(),
            metadata.submittedServerTick(),
            0L,
            Objects.requireNonNull(message, "message"));
    }

    public enum Status {
        APPLIED,
        REJECTED
    }
}
