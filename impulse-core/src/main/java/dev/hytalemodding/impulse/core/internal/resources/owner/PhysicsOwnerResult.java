package dev.hytalemodding.impulse.core.internal.resources.owner;

import java.util.Objects;
import javax.annotation.Nonnull;

/**
 * Completed owner command metadata plus its published step snapshot.
 */
public record PhysicsOwnerResult(long sequence,
                                 @Nonnull PhysicsOwnerSnapshot snapshot,
                                 long queuedNanos,
                                 long runNanos,
                                 long completedNanos) {

    public PhysicsOwnerResult {
        if (sequence < 1L) {
            throw new IllegalArgumentException("sequence must be positive");
        }
        Objects.requireNonNull(snapshot, "snapshot");
        queuedNanos = Math.max(0L, queuedNanos);
        runNanos = Math.max(0L, runNanos);
        completedNanos = Math.max(0L, completedNanos);
    }

    public PhysicsOwnerResult(long sequence,
        @Nonnull PhysicsOwnerSnapshot snapshot,
        long queuedNanos,
        long runNanos) {
        this(sequence, snapshot, queuedNanos, runNanos, 0L);
    }
}
