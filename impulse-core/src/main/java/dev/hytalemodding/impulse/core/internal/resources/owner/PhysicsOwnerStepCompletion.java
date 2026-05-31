package dev.hytalemodding.impulse.core.internal.resources.owner;

import dev.hytalemodding.impulse.core.plugin.snapshot.PublishedPhysicsSnapshotFrame;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Non-blocking step completion consumed by the main tick once the owner future
 * has already completed.
 */
public record PhysicsOwnerStepCompletion(@Nullable PhysicsOwnerResult result,
                                         @Nullable PublishedPhysicsSnapshotFrame frame,
                                         @Nullable RuntimeException stepFailure,
                                         @Nullable Throwable executionFailure) {

    public boolean completedSuccessfully() {
        return result != null && executionFailure == null;
    }

    @Nonnull
    public PhysicsOwnerSnapshot snapshotOrEmpty() {
        return result != null ? result.snapshot() : PhysicsOwnerSnapshot.empty();
    }
}
