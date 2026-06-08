package dev.hytalemodding.impulse.core.internal.resources.profiling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import dev.hytalemodding.impulse.api.PhysicsStepPhaseStats;
import org.junit.jupiter.api.Test;

class PhysicsRuntimeProfilingResourceTest {

    @Test
    void recordStepTracksLatestCumulativeAndWorstSamples() {
        PhysicsRuntimeProfilingResource resource = new PhysicsRuntimeProfilingResource();

        resource.recordStep(2,
            6,
            100L,
            7,
            8,
            30L,
            10L,
            90L,
            PhysicsStepPhaseStats.available(91L, 11L, 22L, 33L, 4L, 5L));
        resource.recordStep(1, 3, 40L);
        resource.recordStepSkippedPending(75L);

        assertEquals(0, resource.getLatestStep().getTickSamples());
        assertEquals(0, resource.getLatestStep().getSpaces());
        assertEquals(0, resource.getLatestStep().getSubsteps());
        assertEquals(0L, resource.getLatestStep().getTickNanos());
        assertEquals(1, resource.getLatestStep().getSkippedPendingSteps());
        assertEquals(75L, resource.getLatestStep().getPendingStepAgeNanos());
        assertEquals(0, resource.getLatestStep().getNativePhaseSamples());
        assertEquals(0, resource.getLatestStep().getPreStepDrainedMutations());
        assertEquals(0, resource.getLatestStep().getLateMutationBacklogAtStep());
        assertEquals(40L, resource.getLatestCompletedStep().getTickNanos());
        assertEquals(1, resource.getLatestCompletedStep().getSpaces());
        assertEquals(0, resource.getLatestCompletedStep().getPreStepDrainedMutations());

        assertEquals(2, resource.getCumulativeStep().getTickSamples());
        assertEquals(3, resource.getCumulativeStep().getSpaces());
        assertEquals(9, resource.getCumulativeStep().getSubsteps());
        assertEquals(140L, resource.getCumulativeStep().getTickNanos());
        assertEquals(30L, resource.getCumulativeStep().getSnapshotNanos());
        assertEquals(10L, resource.getCumulativeStep().getOwnerQueuedNanos());
        assertEquals(90L, resource.getCumulativeStep().getOwnerRunNanos());
        assertEquals(1, resource.getCumulativeStep().getSkippedPendingSteps());
        assertEquals(75L, resource.getCumulativeStep().getPendingStepAgeNanos());
        assertEquals(75L, resource.getCumulativeStep().getMaxPendingStepAgeNanos());
        assertEquals(1, resource.getCumulativeStep().getNativePhaseSamples());
        assertEquals(91L, resource.getCumulativeStep().getNativeStepNanos());
        assertEquals(11L, resource.getCumulativeStep().getNativeBroadPhaseNanos());
        assertEquals(22L, resource.getCumulativeStep().getNativeNarrowPhaseNanos());
        assertEquals(33L, resource.getCumulativeStep().getNativeSolverNanos());
        assertEquals(4L, resource.getCumulativeStep().getNativeCcdNanos());
        assertEquals(5L, resource.getCumulativeStep().getNativeSnapshotNanos());

        assertEquals(100L, resource.getWorstStep().getTickNanos());
        assertEquals(1, resource.getWorstStep().getNativePhaseSamples());
        assertEquals(91L, resource.getWorstStep().getNativeStepNanos());
        assertEquals(75L, resource.getWorstStep().getMaxPendingStepAgeNanos());
    }

    @Test
    void recordStepSchedulingTracksBacklogAndDroppedDt() {
        PhysicsRuntimeProfilingResource resource = new PhysicsRuntimeProfilingResource();

        resource.recordStepScheduling(0.125f, 0.0f, 0.125f, 0.0f, false);
        resource.recordStepScheduling(0.25f, 0.25f, 0.0f, 0.125f, true);

        assertEquals(1, resource.getLatestStep().getSchedulerSamples());
        assertEquals(250_000_000L, resource.getLatestStep().getSchedulerInputDtNanos());
        assertEquals(250_000_000L, resource.getLatestStep().getSchedulerSubmittedDtNanos());
        assertEquals(0L, resource.getLatestStep().getSchedulerBacklogDtNanos());
        assertEquals(125_000_000L, resource.getLatestStep().getDroppedBacklogDtNanos());
        assertEquals(1, resource.getLatestStep().getDroppedBacklogTicks());
        assertEquals(1, resource.getLatestStep().getDtCapHits());

        assertEquals(2, resource.getCumulativeStep().getSchedulerSamples());
        assertEquals(375_000_000L, resource.getCumulativeStep().getSchedulerInputDtNanos());
        assertEquals(250_000_000L, resource.getCumulativeStep().getSchedulerSubmittedDtNanos());
        assertEquals(125_000_000L, resource.getCumulativeStep().getSchedulerBacklogDtNanos());
        assertEquals(125_000_000L, resource.getCumulativeStep().getMaxSchedulerBacklogDtNanos());
        assertEquals(125_000_000L, resource.getCumulativeStep().getDroppedBacklogDtNanos());
        assertEquals(1, resource.getCumulativeStep().getDroppedBacklogTicks());
        assertEquals(1, resource.getCumulativeStep().getDtCapHits());
        assertEquals(125_000_000L, resource.getWorstStep().getMaxSchedulerBacklogDtNanos());
    }

    @Test
    void recordStepTracksPreStepDrainBackpressure() {
        PhysicsRuntimeProfilingResource resource = new PhysicsRuntimeProfilingResource();

        resource.recordStep(1,
            2,
            100L,
            3,
            4,
            5L,
            6L,
            7L,
            1_000_000_000L,
            PhysicsStepPhaseStats.unavailable(),
            2,
            30L,
            1);
        resource.recordStep(1,
            2,
            80L,
            3,
            4,
            5L,
            6L,
            7L,
            1_010_000_000L,
            PhysicsStepPhaseStats.unavailable(),
            4,
            50L,
            3);

        assertEquals(4, resource.getLatestStep().getPreStepDrainedMutations());
        assertEquals(50L, resource.getLatestStep().getPreStepDrainRunNanos());
        assertEquals(3, resource.getLatestStep().getLateMutationBacklogAtStep());
        assertEquals(6, resource.getCumulativeStep().getPreStepDrainedMutations());
        assertEquals(4, resource.getCumulativeStep().getMaxPreStepDrainedMutations());
        assertEquals(80L, resource.getCumulativeStep().getPreStepDrainRunNanos());
        assertEquals(4, resource.getCumulativeStep().getLateMutationBacklogAtStep());
        assertEquals(3, resource.getCumulativeStep().getMaxLateMutationBacklogAtStep());
        assertEquals(4, resource.getWorstStep().getMaxPreStepDrainedMutations());
        assertEquals(3, resource.getWorstStep().getMaxLateMutationBacklogAtStep());
    }

    @Test
    void finishSyncSampleCapturesCollectorMetricsAndClearsActiveCollector() {
        PhysicsRuntimeProfilingResource resource = new PhysicsRuntimeProfilingResource();

        PhysicsRuntimeProfilingResource.SyncCollector first = resource.beginSyncSample();
        first.incrementBodiesInspected();
        first.incrementBodiesInspected();
        first.incrementBodiesSynced();
        first.incrementTransitionSyncs();
        first.incrementSkippedVisualDeadzone();
        first.recordBodySnapshotMotion(Float.NaN);
        first.recordBodySnapshotMotion(0.125f);
        first.recordBodySnapshotMotion(0.25f);
        first.recordVisualCorrection(-1.0f);
        first.recordVisualCorrection(0.5f);
        resource.finishSyncSample(first, 80L);

        assertNull(resource.getActiveSyncCollector());
        assertEquals(1, resource.getLatestSync().getTickSamples());
        assertEquals(2, resource.getLatestSync().getBodiesInspected());
        assertEquals(1, resource.getLatestSync().getBodiesSynced());
        assertEquals(1, resource.getLatestSync().getTransitionSyncs());
        assertEquals(1, resource.getLatestSync().getSkippedVisualDeadzone());
        assertEquals(2, resource.getLatestSync().getBodySnapshotMotionSamples());
        assertEquals(0.375, resource.getLatestSync().getBodySnapshotMotionDistance(), 0.0001);
        assertEquals(0.25, resource.getLatestSync().getMaxBodySnapshotMotionDistance(), 0.0001);
        assertEquals(1, resource.getLatestSync().getVisualCorrectionSamples());
        assertEquals(0.5, resource.getLatestSync().getVisualCorrectionDistance(), 0.0001);
        assertEquals(0.5, resource.getLatestSync().getMaxVisualCorrectionDistance(), 0.0001);
        assertEquals(80L, resource.getLatestSync().getTickNanos());

        PhysicsRuntimeProfilingResource.SyncCollector second = resource.beginSyncSample();
        second.incrementBodiesInspected();
        second.incrementKeepaliveSyncs();
        second.recordBodySnapshotMotion(0.5f);
        second.recordVisualCorrection(0.25f);
        resource.finishSyncSample(second, 40L);

        assertEquals(2, resource.getCumulativeSync().getTickSamples());
        assertEquals(3, resource.getCumulativeSync().getBodiesInspected());
        assertEquals(1, resource.getCumulativeSync().getBodiesSynced());
        assertEquals(1, resource.getCumulativeSync().getTransitionSyncs());
        assertEquals(1, resource.getCumulativeSync().getKeepaliveSyncs());
        assertEquals(3, resource.getCumulativeSync().getBodySnapshotMotionSamples());
        assertEquals(0.875, resource.getCumulativeSync().getBodySnapshotMotionDistance(), 0.0001);
        assertEquals(0.5, resource.getCumulativeSync().getMaxBodySnapshotMotionDistance(), 0.0001);
        assertEquals(2, resource.getCumulativeSync().getVisualCorrectionSamples());
        assertEquals(0.75, resource.getCumulativeSync().getVisualCorrectionDistance(), 0.0001);
        assertEquals(0.5, resource.getCumulativeSync().getMaxVisualCorrectionDistance(), 0.0001);
        assertEquals(80L, resource.getWorstSync().getTickNanos());
    }

    @Test
    void ownerStepRateUsesCompletedOwnerStepIntervals() {
        PhysicsRuntimeProfilingResource resource = new PhysicsRuntimeProfilingResource();

        resource.recordStep(1,
            2,
            10L,
            3,
            4,
            5L,
            6L,
            7L,
            1_000_000_000L,
            PhysicsStepPhaseStats.unavailable());
        assertEquals(0, resource.getLatestStep().getOwnerStepRateSamples());
        assertEquals(0L, resource.getLatestStep().getOwnerStepIntervalNanos());

        resource.recordStep(1,
            2,
            10L,
            3,
            4,
            5L,
            6L,
            7L,
            1_050_000_000L,
            PhysicsStepPhaseStats.unavailable());
        resource.recordStep(1,
            2,
            10L,
            3,
            4,
            5L,
            6L,
            7L,
            1_150_000_000L,
            PhysicsStepPhaseStats.unavailable());

        assertEquals(1, resource.getLatestStep().getOwnerStepRateSamples());
        assertEquals(100_000_000L, resource.getLatestStep().getOwnerStepIntervalNanos());
        assertEquals(2, resource.getCumulativeStep().getOwnerStepRateSamples());
        assertEquals(150_000_000L, resource.getCumulativeStep().getOwnerStepIntervalNanos());
        assertEquals(100_000_000L, resource.getCumulativeStep().getMaxOwnerStepIntervalNanos());

        resource.reset();
        resource.recordStep(1,
            2,
            10L,
            3,
            4,
            5L,
            6L,
            7L,
            2_000_000_000L,
            PhysicsStepPhaseStats.unavailable());

        assertEquals(0, resource.getLatestStep().getOwnerStepRateSamples());
        assertEquals(0L, resource.getLatestStep().getOwnerStepIntervalNanos());
    }

    @Test
    void finishVisualSampleCapturesQueryAndCacheMetrics() {
        PhysicsRuntimeProfilingResource resource = new PhysicsRuntimeProfilingResource();

        PhysicsRuntimeProfilingResource.VisualCollector collector = resource.beginVisualSample();
        collector.setInterests(2);
        collector.setMaterialized(5);
        collector.setCandidates(7);
        collector.incrementSpawned();
        collector.incrementDematerialized();
        collector.incrementNearQueries();
        collector.addNearQueryCandidates(12);
        collector.incrementRaycasts();
        collector.incrementRaycastCacheHits();
        collector.incrementCandidateRefreshes();
        collector.incrementCandidateCacheUses();
        collector.incrementVisibilityChecks();
        collector.addVisibilityCheckSkips(4);
        resource.finishVisualSample(collector, 55L);

        assertEquals(1, resource.getLatestVisual().getTickSamples());
        assertEquals(2, resource.getLatestVisual().getInterests());
        assertEquals(5, resource.getLatestVisual().getMaterialized());
        assertEquals(7, resource.getLatestVisual().getCandidates());
        assertEquals(1, resource.getLatestVisual().getSpawned());
        assertEquals(1, resource.getLatestVisual().getDematerialized());
        assertEquals(1, resource.getLatestVisual().getNearQueries());
        assertEquals(12, resource.getLatestVisual().getNearQueryCandidates());
        assertEquals(1, resource.getLatestVisual().getRaycasts());
        assertEquals(1, resource.getLatestVisual().getRaycastCacheHits());
        assertEquals(1, resource.getLatestVisual().getCandidateRefreshes());
        assertEquals(1, resource.getLatestVisual().getCandidateCacheUses());
        assertEquals(1, resource.getLatestVisual().getVisibilityChecks());
        assertEquals(4, resource.getLatestVisual().getVisibilityCheckSkips());
        assertEquals(55L, resource.getLatestVisual().getTickNanos());
    }

    @Test
    void resetAndClonePreserveOnlyExpectedState() {
        PhysicsRuntimeProfilingResource resource = new PhysicsRuntimeProfilingResource();
        resource.setEnabled(true);
        resource.recordStep(1, 2, 30L);
        resource.recordStep(1,
            2,
            30L,
            3,
            4,
            5L,
            6L,
            7L,
            1_000_000_000L,
            PhysicsStepPhaseStats.unavailable(),
            2,
            11L,
            1);
        resource.recordStepScheduling(0.125f, 0.125f, 0.0f, 0.0f, false);
        PhysicsRuntimeProfilingResource.SyncCollector collector = resource.beginSyncSample();
        collector.incrementBodiesSynced();
        collector.recordBodySnapshotMotion(0.375f);
        collector.recordVisualCorrection(0.625f);
        resource.finishSyncSample(collector, 15L);

        PhysicsRuntimeProfilingResource copy = resource.clone();
        assertEquals(resource.getLatestStep().getTickNanos(), copy.getLatestStep().getTickNanos());
        assertEquals(resource.getLatestCompletedStep().getTickNanos(),
            copy.getLatestCompletedStep().getTickNanos());
        assertEquals(resource.getLatestStep().getSchedulerSubmittedDtNanos(),
            copy.getLatestStep().getSchedulerSubmittedDtNanos());
        assertEquals(resource.getLatestStep().getPreStepDrainedMutations(),
            copy.getLatestStep().getPreStepDrainedMutations());
        assertEquals(resource.getLatestStep().getLateMutationBacklogAtStep(),
            copy.getLatestStep().getLateMutationBacklogAtStep());
        assertEquals(resource.getLatestSync().getBodiesSynced(), copy.getLatestSync().getBodiesSynced());
        assertEquals(resource.getLatestSync().getBodySnapshotMotionDistance(),
            copy.getLatestSync().getBodySnapshotMotionDistance(), 0.0001);
        assertEquals(resource.getLatestSync().getVisualCorrectionDistance(),
            copy.getLatestSync().getVisualCorrectionDistance(), 0.0001);
        assertNull(copy.getActiveSyncCollector());

        resource.reset();
        assertEquals(0, resource.getCumulativeStep().getTickSamples());
        assertEquals(0, resource.getCumulativeStep().getSchedulerSamples());
        assertEquals(0, resource.getLatestStep().getTickNanos());
        assertEquals(0, resource.getLatestCompletedStep().getTickNanos());
        assertEquals(0L, resource.getLatestStep().getSchedulerSubmittedDtNanos());
        assertEquals(0, resource.getLatestStep().getPreStepDrainedMutations());
        assertEquals(0, resource.getLatestStep().getLateMutationBacklogAtStep());
        assertEquals(0, resource.getWorstSync().getTickNanos());
        assertEquals(0, resource.getCumulativeSync().getBodySnapshotMotionSamples());
        assertEquals(0.0, resource.getCumulativeSync().getVisualCorrectionDistance(), 0.0001);
        assertNull(resource.getActiveSyncCollector());
    }
}
