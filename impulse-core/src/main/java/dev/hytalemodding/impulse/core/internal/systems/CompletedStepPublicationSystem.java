package dev.hytalemodding.impulse.core.internal.systems;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.QuerySystem;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsEventResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsProfilingResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsRestoreStatusResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsSnapshotResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsStepSchedulerResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsStepSchedulerResource.CompletedStep;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsStepSchedulerResource.StepInput;
import dev.hytalemodding.impulse.core.internal.systems.binding.TargetBindingSystem;
import dev.hytalemodding.impulse.core.plugin.snapshots.PhysicsBodySnapshot;
import dev.hytalemodding.impulse.core.plugin.snapshots.PhysicsSnapshotFrame;
import java.util.List;
import java.util.Set;
import javax.annotation.Nonnull;

/**
 * Publishes the last completed backend state as a copied PhysicsStore snapshot frame.
 */
public final class CompletedStepPublicationSystem extends TickingSystem<PhysicsStore>
    implements QuerySystem<PhysicsStore> {

    private static final Set<Dependency<PhysicsStore>> DEPENDENCIES = Set.of(
        new SystemDependency<>(Order.AFTER, TargetBindingSystem.class)
    );

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
        PhysicsSnapshotResource snapshot = store.getResource(PhysicsSnapshotResource.getResourceType());
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
        List<PhysicsBodySnapshot> bodies = completed.bodySnapshots();
        long nextSequence = snapshot.latestSequence() + 1L;
        float frameDt = input != null ? input.submittedDtSeconds() : dt;
        PhysicsSnapshotFrame frame = new PhysicsSnapshotFrame(nextSequence,
            frameDt,
            bodies);
        snapshot.publish(frame);
        profiling.recordSnapshot(completed.snapshotNanos(), bodies.size());
        store.getResource(PhysicsEventResource.getResourceType())
            .publishStepFrame(frame.sequence(),
                Math.max(0L, store.getExternalData().getWorld().getTick()),
                bodies.size(),
                profiling.getStepSubmitNanos(),
                completed.snapshotNanos(),
                completed.physicsEvents(),
                completed.droppedBackendEventCount());
    }

    @Nonnull
    @Override
    public Set<Dependency<PhysicsStore>> getDependencies() {
        return DEPENDENCIES;
    }

    @Nonnull
    @Override
    public Query<PhysicsStore> getQuery() {
        return PhysicsStoreSystemSupport.uuidQuery();
    }
}
