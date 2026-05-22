package dev.hytalemodding.impulse.core.internal.worker;

import dev.hytalemodding.impulse.core.plugin.snapshot.PublishedPhysicsSnapshotFrame;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Non-blocking step completion consumed by the main tick once the worker future
 * has already completed.
 */
public record PhysicsWorkerStepCompletion(@Nullable PhysicsWorkerResult result,
                                          @Nullable PublishedPhysicsSnapshotFrame frame,
                                          @Nullable RuntimeException stepFailure,
                                          @Nullable Throwable executionFailure) {

    public boolean completedSuccessfully() {
        return result != null && executionFailure == null;
    }

    @Nonnull
    public PhysicsWorkerSnapshot snapshotOrEmpty() {
        return result != null ? result.snapshot() : PhysicsWorkerSnapshot.empty();
    }
}
