package dev.hytalemodding.impulse.core.internal.systems.publication;

import com.hypixel.hytale.logger.HytaleLogger;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsWorldRuntimeResource;
import dev.hytalemodding.impulse.core.internal.resources.profiling.PhysicsRuntimeProfilingResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsWorldWorkerResource;
import dev.hytalemodding.impulse.core.internal.worker.PhysicsWorkerMutationCompletion;
import dev.hytalemodding.impulse.core.internal.worker.PhysicsWorkerResult;
import dev.hytalemodding.impulse.core.internal.worker.PhysicsWorkerSnapshot;
import dev.hytalemodding.impulse.core.internal.worker.PhysicsWorkerStepCompletion;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsWorldResource;
import dev.hytalemodding.impulse.core.plugin.snapshot.PublishedPhysicsSnapshotFrame;
import java.util.List;
import java.util.logging.Level;
import javax.annotation.Nonnull;

/**
 * Names the frame boundary between worker execution and reader-visible physics state.
 *
 * <p>The publication system uses this class to drain completed mutations, apply one completed
 * step snapshot, and write profiling data without blocking the entity-store tick.</p>
 */
public final class PhysicsPublicationPipeline {

    private static final HytaleLogger LOGGER = HytaleLogger.get("Impulse");
    private static final int MAX_MUTATION_COMPLETIONS_PER_TICK = 64;

    private PhysicsPublicationPipeline() {
    }

    public static int publishCompletedMutations(@Nonnull PhysicsWorldWorkerResource worker) {
        // Mutation completions report failures and clear handles; they do not publish snapshots.
        List<PhysicsWorkerMutationCompletion> completions =
            worker.pollCompletedMutations(MAX_MUTATION_COMPLETIONS_PER_TICK);
        for (PhysicsWorkerMutationCompletion completion : completions) {
            if (completion.executionFailure() != null) {
                LOGGER.at(Level.SEVERE).log(
                    "Async physics worker mutation failed while running %s: %s",
                    completion.operation(),
                    completion.executionFailure().getMessage());
            }
        }
        return completions.size();
    }

    public static void publishCompletedStep(@Nonnull PhysicsWorldWorkerResource worker,
        @Nonnull PhysicsWorldResource resource,
        @Nonnull PhysicsRuntimeProfilingResource profiling) {
        publishCompletedStep(worker, resource, profiling, 0L);
    }

    public static void publishCompletedStep(@Nonnull PhysicsWorldWorkerResource worker,
        @Nonnull PhysicsWorldResource resource,
        @Nonnull PhysicsRuntimeProfilingResource profiling,
        long publicationServerTick) {
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
            // Reader-side apply happens here; command handles may have completed earlier.
            PhysicsWorldRuntimeResource.require(resource)
                .applyPublishedSnapshotFrame(frame, publicationServerTick);
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
                result != null ? result.completedNanos() : 0L,
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
}
