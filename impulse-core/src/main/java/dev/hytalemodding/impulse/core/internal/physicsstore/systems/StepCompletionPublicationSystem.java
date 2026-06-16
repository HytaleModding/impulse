package dev.hytalemodding.impulse.core.internal.physicsstore.systems;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsProfilingResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsRestoreStatusResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsStepSchedulerResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsStepSchedulerResource.CompletedStep;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsStepSchedulerResource.StepInput;
import javax.annotation.Nonnull;

/**
 * Publishes completed owner-lane step profiling back onto the PhysicsStore world thread.
 */
public final class StepCompletionPublicationSystem extends TickingSystem<PhysicsStore> {

    @Override
    public void tick(float dt, int systemIndex, @Nonnull Store<PhysicsStore> store) {
        CompletedStep completed = store.getResource(PhysicsStepSchedulerResource.getResourceType())
            .pollCompletedStep();
        if (completed == null) {
            return;
        }
        if (completed.failed()) {
            Throwable failure = completed.failure();
            String message = failure != null ? failure.getMessage() : null;
            store.getResource(PhysicsRestoreStatusResource.getResourceType())
                .markFailed(message != null ? message : "PhysicsStore owner-lane step failed");
            throw new IllegalStateException("PhysicsStore owner-lane step failed", failure);
        }
        PhysicsProfilingResource profiling = store.getResource(PhysicsProfilingResource.getResourceType());
        profiling.recordStep(completed.stepSubmitNanos(),
            completed.spaces(),
            completed.substeps(),
            completed.nativePhaseStats());
        StepInput input = completed.input();
        if (input != null) {
            profiling.recordStepScheduling(input.inputDtSeconds(),
                input.submittedDtSeconds(),
                input.backlogDtSeconds(),
                input.droppedBacklogDtSeconds(),
                input.dtCapHit());
        }
    }
}
