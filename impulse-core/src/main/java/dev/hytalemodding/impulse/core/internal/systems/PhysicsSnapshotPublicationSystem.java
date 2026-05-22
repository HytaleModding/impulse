package dev.hytalemodding.impulse.core.internal.systems;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsRuntimeProfilingResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsWorldWorkerResource;
import dev.hytalemodding.impulse.core.internal.resources.PublishedPhysicsSnapshotFrame;
import dev.hytalemodding.impulse.core.internal.worker.PhysicsWorkerMutationCompletion;
import dev.hytalemodding.impulse.core.internal.worker.PhysicsWorkerResult;
import dev.hytalemodding.impulse.core.internal.worker.PhysicsWorkerSnapshot;
import dev.hytalemodding.impulse.core.internal.worker.PhysicsWorkerStepCompletion;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsWorldResource;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import javax.annotation.Nonnull;

/**
 * Publishes completed worker snapshots to the main-thread snapshot store without
 * waiting for an in-flight physics step.
 */
public final class PhysicsSnapshotPublicationSystem extends TickingSystem<EntityStore> {

    private static final HytaleLogger LOGGER = HytaleLogger.get("Impulse");
    private static final int MAX_MUTATION_COMPLETIONS_PER_TICK = 64;

    private static final Set<Dependency<EntityStore>> DEPENDENCIES = Set.of(
        new SystemDependency<>(Order.BEFORE, PhysicsChunkBoundarySystem.class),
        new SystemDependency<>(Order.BEFORE, PhysicsWorldCollisionStreamingSystem.class),
        new SystemDependency<>(Order.BEFORE, PhysicsDetachedVisualMaterializationSystem.class),
        new SystemDependency<>(Order.BEFORE, PhysicsSyncSystem.class)
    );

    @Override
    public void tick(float dt, int systemIndex, @Nonnull Store<EntityStore> store) {
        PhysicsWorldWorkerResource worker = store.getResource(
            PhysicsWorldWorkerResource.getResourceType());
        if (worker.isClosed()) {
            return;
        }
        PhysicsWorldResource resource = store.getResource(PhysicsWorldResource.getResourceType());
        PhysicsRuntimeProfilingResource profiling = store.getResource(
            PhysicsRuntimeProfilingResource.getResourceType());
        publishCompletedMutations(worker);
        publishCompletedStep(worker, resource, profiling);
    }

    static int publishCompletedMutations(@Nonnull PhysicsWorldWorkerResource worker) {
        int completed = 0;
        while (true) {
            List<PhysicsWorkerMutationCompletion> completions =
                worker.pollCompletedMutations(MAX_MUTATION_COMPLETIONS_PER_TICK);
            if (completions.isEmpty()) {
                return completed;
            }
            completed += completions.size();
            for (PhysicsWorkerMutationCompletion completion : completions) {
                if (completion.executionFailure() != null) {
                    LOGGER.at(Level.SEVERE).log(
                        "Async physics worker mutation failed while running %s: %s",
                        completion.operation(),
                        completion.executionFailure().getMessage());
                }
            }
            if (completions.size() < MAX_MUTATION_COMPLETIONS_PER_TICK) {
                return completed;
            }
        }
    }

    static void publishCompletedStep(@Nonnull PhysicsWorldWorkerResource worker,
        @Nonnull PhysicsWorldResource resource,
        @Nonnull PhysicsRuntimeProfilingResource profiling) {
        PhysicsWorkerStepCompletion completion = worker.pollCompletedStep();
        if (completion == null) {
            return;
        }

        if (completion.executionFailure() != null) {
            LOGGER.at(Level.SEVERE).log("Async physics worker step failed: %s",
                completion.executionFailure().getMessage());
            return;
        }

        PublishedPhysicsSnapshotFrame frame = completion.frame();
        if (frame != null) {
            resource.applyPublishedSnapshotFrame(frame);
        }

        PhysicsWorkerSnapshot snapshot = completion.snapshotOrEmpty();
        if (profiling.isEnabled()) {
            PhysicsWorkerResult result = completion.result();
            profiling.recordStep(snapshot.spaces(),
                snapshot.substeps(),
                snapshot.stepNanos(),
                snapshot.bodySnapshots(),
                snapshot.spatialIndexCells(),
                snapshot.snapshotNanos(),
                result != null ? result.queuedNanos() : 0L,
                result != null ? result.runNanos() : 0L,
                snapshot.nativePhaseStats());
        }

        RuntimeException stepFailure = completion.stepFailure();
        if (stepFailure != null) {
            LOGGER.at(Level.SEVERE).log(
                "Async physics worker step failed after %s completed substeps; snapshots were published with status=%s: %s",
                snapshot.substeps(),
                frame != null ? frame.status() : "<missing>",
                stepFailure.getMessage());
        }
    }

    @Nonnull
    @Override
    public Set<Dependency<EntityStore>> getDependencies() {
        return DEPENDENCIES;
    }
}
