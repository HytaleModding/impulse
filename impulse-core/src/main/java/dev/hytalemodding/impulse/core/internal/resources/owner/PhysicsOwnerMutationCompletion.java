package dev.hytalemodding.impulse.core.internal.resources.owner;

import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Completed non-step owner mutation consumed by the main tick after the future
 * is already complete.
 */
public record PhysicsOwnerMutationCompletion(@Nonnull String operation,
                                             @Nullable PhysicsOwnerResult result,
                                              @Nullable Throwable executionFailure) {

    public PhysicsOwnerMutationCompletion {
        Objects.requireNonNull(operation, "operation");
    }

    public boolean completedSuccessfully() {
        return result != null && executionFailure == null;
    }

    @Nonnull
    public PhysicsOwnerSnapshot snapshotOrEmpty() {
        return result != null ? result.snapshot() : PhysicsOwnerSnapshot.empty();
    }
}
