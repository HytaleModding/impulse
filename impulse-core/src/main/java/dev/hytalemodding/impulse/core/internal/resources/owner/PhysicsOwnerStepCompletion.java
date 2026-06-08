package dev.hytalemodding.impulse.core.internal.resources.owner;

import dev.hytalemodding.impulse.core.plugin.events.PhysicsEventFrame;
import dev.hytalemodding.impulse.core.plugin.snapshot.PublishedPhysicsSnapshotFrame;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Non-blocking step completion consumed by the main tick once the owner future
 * has already completed.
 */
public record PhysicsOwnerStepCompletion(@Nullable PhysicsOwnerResult result,
                                         @Nullable PublishedPhysicsSnapshotFrame frame,
                                         @Nullable PhysicsEventFrame eventFrame,
                                         @Nullable RuntimeException stepFailure,
                                         int preStepDrainedMutations,
                                         long preStepDrainRunNanos,
                                         int lateMutationBacklogAtStep,
                                         @Nullable Throwable executionFailure) {

    public PhysicsOwnerStepCompletion(@Nullable PhysicsOwnerResult result,
        @Nullable PublishedPhysicsSnapshotFrame frame,
        @Nullable PhysicsEventFrame eventFrame,
        @Nullable RuntimeException stepFailure,
        @Nullable Throwable executionFailure) {
        this(result, frame, eventFrame, stepFailure, 0, 0L, 0, executionFailure);
    }

    public PhysicsOwnerStepCompletion {
        preStepDrainedMutations = Math.max(0, preStepDrainedMutations);
        preStepDrainRunNanos = Math.max(0L, preStepDrainRunNanos);
        lateMutationBacklogAtStep = Math.max(0, lateMutationBacklogAtStep);
    }

    public boolean completedSuccessfully() {
        return result != null && executionFailure == null;
    }

    @Nonnull
    public PhysicsOwnerSnapshot snapshotOrEmpty() {
        return result != null ? result.snapshot() : PhysicsOwnerSnapshot.empty();
    }
}
