package dev.hytalemodding.impulse.core.internal.worker;

import java.util.Objects;
import javax.annotation.Nonnull;

/**
 * Completed worker command metadata plus its published step snapshot.
 */
public record PhysicsWorkerResult(long sequence,
                                  @Nonnull PhysicsWorkerSnapshot snapshot,
                                  long queuedNanos,
                                  long runNanos,
                                  long completedNanos) {

    public PhysicsWorkerResult {
        if (sequence < 1L) {
            throw new IllegalArgumentException("sequence must be positive");
        }
        Objects.requireNonNull(snapshot, "snapshot");
        queuedNanos = Math.max(0L, queuedNanos);
        runNanos = Math.max(0L, runNanos);
        completedNanos = Math.max(0L, completedNanos);
    }

    public PhysicsWorkerResult(long sequence,
        @Nonnull PhysicsWorkerSnapshot snapshot,
        long queuedNanos,
        long runNanos) {
        this(sequence, snapshot, queuedNanos, runNanos, 0L);
    }
}
