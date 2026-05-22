package dev.hytalemodding.impulse.core.internal.worker;

import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Completed non-step worker mutation consumed by the main tick after the future
 * is already complete.
 */
public record PhysicsWorkerMutationCompletion(@Nonnull String operation,
                                              @Nullable PhysicsWorkerResult result,
                                              @Nullable Throwable executionFailure) {

    public PhysicsWorkerMutationCompletion {
        Objects.requireNonNull(operation, "operation");
    }

    public boolean completedSuccessfully() {
        return result != null && executionFailure == null;
    }

    @Nonnull
    public PhysicsWorkerSnapshot snapshotOrEmpty() {
        return result != null ? result.snapshot() : PhysicsWorkerSnapshot.empty();
    }
}
