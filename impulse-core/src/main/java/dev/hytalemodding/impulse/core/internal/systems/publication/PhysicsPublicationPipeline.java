package dev.hytalemodding.impulse.core.internal.systems.publication;

import com.hypixel.hytale.logger.HytaleLogger;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsWorldRuntimeResource;
import dev.hytalemodding.impulse.core.internal.resources.profiling.PhysicsRuntimeProfilingResource;
import dev.hytalemodding.impulse.core.internal.resources.owner.PhysicsOwnerResource;
import dev.hytalemodding.impulse.core.internal.resources.owner.PhysicsOwnerMutationCompletion;
import dev.hytalemodding.impulse.core.internal.resources.owner.PhysicsOwnerResult;
import dev.hytalemodding.impulse.core.internal.resources.owner.PhysicsOwnerSnapshot;
import dev.hytalemodding.impulse.core.internal.resources.owner.PhysicsOwnerStepCompletion;
import dev.hytalemodding.impulse.core.plugin.events.PhysicsEventFrame;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsWorldResource;
import dev.hytalemodding.impulse.core.plugin.snapshot.PublishedPhysicsSnapshotFrame;
import java.util.List;
import java.util.logging.Level;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Names the frame boundary between owner execution and reader-visible physics state.
 *
 * <p>The publication system uses this class to drain completed mutations, apply one completed
 * step snapshot, and write profiling data without blocking the entity-store tick.</p>
 */
public final class PhysicsPublicationPipeline {

    private static final HytaleLogger LOGGER = HytaleLogger.get("Impulse");
    private static final int MAX_MUTATION_COMPLETIONS_PER_TICK = 64;

    private PhysicsPublicationPipeline() {
    }

    public static int publishCompletedMutations(@Nonnull PhysicsOwnerResource owner) {
        // Mutation completions report failures and clear handles; they do not publish snapshots.
        List<PhysicsOwnerMutationCompletion> completions =
            owner.pollCompletedMutations(MAX_MUTATION_COMPLETIONS_PER_TICK);
        for (PhysicsOwnerMutationCompletion completion : completions) {
            if (completion.executionFailure() != null) {
                LOGGER.at(Level.SEVERE).log(
                    "Async physics owner-lane mutation failed while running %s: %s",
                    completion.operation(),
                    completion.executionFailure().getMessage());
            }
        }
        return completions.size();
    }

    @Nullable
    public static PhysicsEventFrame publishCompletedStep(@Nonnull PhysicsOwnerResource owner,
        @Nonnull PhysicsWorldResource resource,
        @Nonnull PhysicsRuntimeProfilingResource profiling) {
        return publishCompletedStep(owner, resource, profiling, 0L);
    }

    @Nullable
    public static PhysicsEventFrame publishCompletedStep(@Nonnull PhysicsOwnerResource owner,
        @Nonnull PhysicsWorldResource resource,
        @Nonnull PhysicsRuntimeProfilingResource profiling,
        long publicationServerTick) {
        PhysicsOwnerStepCompletion completion = owner.pollCompletedStep();
        if (completion == null) {
            return null;
        }

        if (completion.executionFailure() != null) {
            LOGGER.at(Level.SEVERE).log("Async physics owner-lane step failed: %s",
                completion.executionFailure().getMessage());
            return null;
        }

        boolean currentFrame = true;
        PublishedPhysicsSnapshotFrame frame = completion.frame();
        if (frame != null) {
            // Reader-side apply happens here; command handles may have completed earlier.
            PhysicsWorldRuntimeResource runtime = PhysicsWorldRuntimeResource.require(resource);
            runtime.applyPublishedSnapshotFrame(frame, publicationServerTick);
            if (frame.worldEpoch() != runtime.worldEpoch()) {
                currentFrame = false;
            }
        }

        PhysicsOwnerSnapshot snapshot = completion.snapshotOrEmpty();
        if (profiling.isEnabled()) {
            PhysicsOwnerResult result = completion.result();
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
                "Async physics owner-lane step failed after %s completed substeps; snapshots were published with status=%s: %s",
                snapshot.substeps(),
                frame != null ? frame.status() : "<missing>",
                stepFailure.getMessage());
        }
        return currentFrame ? completion.eventFrame() : null;
    }
}
