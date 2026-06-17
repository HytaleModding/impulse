package dev.hytalemodding.impulse.core.plugin.modules.physicsentity;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.core.internal.resources.profiling.PhysicsRuntimeProfilingResource;
import java.util.Objects;
import javax.annotation.Nonnull;

/**
 * Public value views for EntityStore-side physics runtime profiling.
 */
public final class PhysicsRuntimeProfiling {

    private PhysicsRuntimeProfiling() {
    }

    @Nonnull
    public static Snapshots snapshots(@Nonnull Store<EntityStore> store) {
        PhysicsRuntimeProfilingResource profiling = store.getResource(
            PhysicsRuntimeProfilingResource.getResourceType());
        return new Snapshots(profiling.isEnabled(),
            new StepSnapshotView(profiling.getCumulativeStep()),
            new StepSnapshotView(profiling.getLatestStep()),
            new StepSnapshotView(profiling.getLatestCompletedStep()),
            new StepSnapshotView(profiling.getWorstStep()),
            new SyncSnapshotView(profiling.getCumulativeSync()),
            new SyncSnapshotView(profiling.getLatestSync()),
            new SyncSnapshotView(profiling.getWorstSync()),
            new VisualSnapshotView(profiling.getCumulativeVisual()),
            new VisualSnapshotView(profiling.getLatestVisual()),
            new VisualSnapshotView(profiling.getWorstVisual()));
    }

    public record Snapshots(boolean enabled,
                            @Nonnull StepSnapshotView cumulativeStep,
                            @Nonnull StepSnapshotView latestStep,
                            @Nonnull StepSnapshotView latestCompletedStep,
                            @Nonnull StepSnapshotView worstStep,
                            @Nonnull SyncSnapshotView cumulativeSync,
                            @Nonnull SyncSnapshotView latestSync,
                            @Nonnull SyncSnapshotView worstSync,
                            @Nonnull VisualSnapshotView cumulativeVisual,
                            @Nonnull VisualSnapshotView latestVisual,
                            @Nonnull VisualSnapshotView worstVisual) {
    }

    public interface StepDrainSnapshotView {

        int getTickSamples();

        int getPreStepDrainedMutations();

        int getMaxPreStepDrainedMutations();

        long getPreStepDrainRunNanos();

        int getLateMutationBacklogAtStep();

        int getMaxLateMutationBacklogAtStep();
    }

    public static final class StepSnapshotView implements StepDrainSnapshotView {

        @Nonnull
        private final PhysicsRuntimeProfilingResource.StepSnapshot snapshot;

        private StepSnapshotView(
            @Nonnull PhysicsRuntimeProfilingResource.StepSnapshot snapshot) {
            this.snapshot = Objects.requireNonNull(snapshot, "snapshot");
        }

        public int getTickSamples() {
            return snapshot.getTickSamples();
        }

        public int getSpaces() {
            return snapshot.getSpaces();
        }

        public int getSubsteps() {
            return snapshot.getSubsteps();
        }

        public int getBodySnapshots() {
            return snapshot.getBodySnapshots();
        }

        public int getSpatialIndexCells() {
            return snapshot.getSpatialIndexCells();
        }

        public long getTickNanos() {
            return snapshot.getTickNanos();
        }

        public long getSnapshotNanos() {
            return snapshot.getSnapshotNanos();
        }

        public long getStoreTickQueuedNanos() {
            return snapshot.getStoreTickQueuedNanos();
        }

        public long getStoreTickRunNanos() {
            return snapshot.getStoreTickRunNanos();
        }

        public int getPreStepDrainedMutations() {
            return snapshot.getPreStepDrainedMutations();
        }

        public int getMaxPreStepDrainedMutations() {
            return snapshot.getMaxPreStepDrainedMutations();
        }

        public long getPreStepDrainRunNanos() {
            return snapshot.getPreStepDrainRunNanos();
        }

        public int getLateMutationBacklogAtStep() {
            return snapshot.getLateMutationBacklogAtStep();
        }

        public int getMaxLateMutationBacklogAtStep() {
            return snapshot.getMaxLateMutationBacklogAtStep();
        }

        public int getStoreTickStepRateSamples() {
            return snapshot.getStoreTickStepRateSamples();
        }

        public long getStoreTickStepIntervalNanos() {
            return snapshot.getStoreTickStepIntervalNanos();
        }

        public long getMaxStoreTickStepIntervalNanos() {
            return snapshot.getMaxStoreTickStepIntervalNanos();
        }

        public int getSkippedPendingSteps() {
            return snapshot.getSkippedPendingSteps();
        }

        public long getPendingStepAgeNanos() {
            return snapshot.getPendingStepAgeNanos();
        }

        public long getMaxPendingStepAgeNanos() {
            return snapshot.getMaxPendingStepAgeNanos();
        }

        public int getSchedulerSamples() {
            return snapshot.getSchedulerSamples();
        }

        public long getSchedulerInputDtNanos() {
            return snapshot.getSchedulerInputDtNanos();
        }

        public long getSchedulerSubmittedDtNanos() {
            return snapshot.getSchedulerSubmittedDtNanos();
        }

        public long getSchedulerBacklogDtNanos() {
            return snapshot.getSchedulerBacklogDtNanos();
        }

        public long getMaxSchedulerBacklogDtNanos() {
            return snapshot.getMaxSchedulerBacklogDtNanos();
        }

        public long getDroppedBacklogDtNanos() {
            return snapshot.getDroppedBacklogDtNanos();
        }

        public int getDroppedBacklogTicks() {
            return snapshot.getDroppedBacklogTicks();
        }

        public int getDtCapHits() {
            return snapshot.getDtCapHits();
        }

        public int getNativePhaseSamples() {
            return snapshot.getNativePhaseSamples();
        }

        public long getNativeStepNanos() {
            return snapshot.getNativeStepNanos();
        }

        public long getNativeBroadPhaseNanos() {
            return snapshot.getNativeBroadPhaseNanos();
        }

        public long getNativeNarrowPhaseNanos() {
            return snapshot.getNativeNarrowPhaseNanos();
        }

        public long getNativeSolverNanos() {
            return snapshot.getNativeSolverNanos();
        }

        public long getNativeCcdNanos() {
            return snapshot.getNativeCcdNanos();
        }

        public long getNativeSnapshotNanos() {
            return snapshot.getNativeSnapshotNanos();
        }
    }

    public static final class SyncSnapshotView {

        @Nonnull
        private final PhysicsRuntimeProfilingResource.SyncSnapshot snapshot;

        private SyncSnapshotView(
            @Nonnull PhysicsRuntimeProfilingResource.SyncSnapshot snapshot) {
            this.snapshot = Objects.requireNonNull(snapshot, "snapshot");
        }

        public int getTickSamples() {
            return snapshot.getTickSamples();
        }

        public int getBodiesInspected() {
            return snapshot.getBodiesInspected();
        }

        public int getBodiesSynced() {
            return snapshot.getBodiesSynced();
        }

        public int getTransitionSyncs() {
            return snapshot.getTransitionSyncs();
        }

        public int getKeepaliveSyncs() {
            return snapshot.getKeepaliveSyncs();
        }

        public int getSkippedSleeping() {
            return snapshot.getSkippedSleeping();
        }

        public int getSkippedThreshold() {
            return snapshot.getSkippedThreshold();
        }

        public int getSkippedVisualDeadzone() {
            return snapshot.getSkippedVisualDeadzone();
        }

        public int getSkippedVisualRange() {
            return snapshot.getSkippedVisualRange();
        }

        public int getSkippedStatic() {
            return snapshot.getSkippedStatic();
        }

        public int getSkippedMissingSpace() {
            return snapshot.getSkippedMissingSpace();
        }

        public long getTickNanos() {
            return snapshot.getTickNanos();
        }

        public int getBodySnapshotMotionSamples() {
            return snapshot.getBodySnapshotMotionSamples();
        }

        public double getBodySnapshotMotionDistance() {
            return snapshot.getBodySnapshotMotionDistance();
        }

        public double getMaxBodySnapshotMotionDistance() {
            return snapshot.getMaxBodySnapshotMotionDistance();
        }

        public int getVisualCorrectionSamples() {
            return snapshot.getVisualCorrectionSamples();
        }

        public double getVisualCorrectionDistance() {
            return snapshot.getVisualCorrectionDistance();
        }

        public double getMaxVisualCorrectionDistance() {
            return snapshot.getMaxVisualCorrectionDistance();
        }
    }

    public static final class VisualSnapshotView {

        @Nonnull
        private final PhysicsRuntimeProfilingResource.VisualSnapshot snapshot;

        private VisualSnapshotView(
            @Nonnull PhysicsRuntimeProfilingResource.VisualSnapshot snapshot) {
            this.snapshot = Objects.requireNonNull(snapshot, "snapshot");
        }

        public int getTickSamples() {
            return snapshot.getTickSamples();
        }

        public int getInterests() {
            return snapshot.getInterests();
        }

        public int getMaterialized() {
            return snapshot.getMaterialized();
        }

        public int getCandidates() {
            return snapshot.getCandidates();
        }

        public int getSpawned() {
            return snapshot.getSpawned();
        }

        public int getDematerialized() {
            return snapshot.getDematerialized();
        }

        public int getNearQueries() {
            return snapshot.getNearQueries();
        }

        public int getNearQueryCandidates() {
            return snapshot.getNearQueryCandidates();
        }

        public int getRaycasts() {
            return snapshot.getRaycasts();
        }

        public int getRaycastCacheHits() {
            return snapshot.getRaycastCacheHits();
        }

        public int getCandidateRefreshes() {
            return snapshot.getCandidateRefreshes();
        }

        public int getCandidateCacheUses() {
            return snapshot.getCandidateCacheUses();
        }

        public int getVisibilityChecks() {
            return snapshot.getVisibilityChecks();
        }

        public int getVisibilityCheckSkips() {
            return snapshot.getVisibilityCheckSkips();
        }

        public long getTickNanos() {
            return snapshot.getTickNanos();
        }
    }
}
