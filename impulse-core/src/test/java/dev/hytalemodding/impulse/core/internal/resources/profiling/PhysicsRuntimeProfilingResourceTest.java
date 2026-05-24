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

        assertEquals(1, resource.getLatestStep().getTickSamples());
        assertEquals(1, resource.getLatestStep().getSpaces());
        assertEquals(3, resource.getLatestStep().getSubsteps());
        assertEquals(40L, resource.getLatestStep().getTickNanos());
        assertEquals(1, resource.getLatestStep().getSkippedPendingSteps());
        assertEquals(75L, resource.getLatestStep().getPendingStepAgeNanos());
        assertEquals(0, resource.getLatestStep().getNativePhaseSamples());

        assertEquals(2, resource.getCumulativeStep().getTickSamples());
        assertEquals(3, resource.getCumulativeStep().getSpaces());
        assertEquals(9, resource.getCumulativeStep().getSubsteps());
        assertEquals(140L, resource.getCumulativeStep().getTickNanos());
        assertEquals(30L, resource.getCumulativeStep().getSnapshotNanos());
        assertEquals(10L, resource.getCumulativeStep().getWorkerQueuedNanos());
        assertEquals(90L, resource.getCumulativeStep().getWorkerRunNanos());
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
    void finishSyncSampleCapturesCollectorMetricsAndClearsActiveCollector() {
        PhysicsRuntimeProfilingResource resource = new PhysicsRuntimeProfilingResource();

        PhysicsRuntimeProfilingResource.SyncCollector first = resource.beginSyncSample();
        first.incrementBodiesInspected();
        first.incrementBodiesInspected();
        first.incrementBodiesSynced();
        first.incrementTransitionSyncs();
        first.incrementSkippedVisualDeadzone();
        resource.finishSyncSample(first, 80L);

        assertNull(resource.getActiveSyncCollector());
        assertEquals(1, resource.getLatestSync().getTickSamples());
        assertEquals(2, resource.getLatestSync().getBodiesInspected());
        assertEquals(1, resource.getLatestSync().getBodiesSynced());
        assertEquals(1, resource.getLatestSync().getTransitionSyncs());
        assertEquals(1, resource.getLatestSync().getSkippedVisualDeadzone());
        assertEquals(80L, resource.getLatestSync().getTickNanos());

        PhysicsRuntimeProfilingResource.SyncCollector second = resource.beginSyncSample();
        second.incrementBodiesInspected();
        second.incrementKeepaliveSyncs();
        resource.finishSyncSample(second, 40L);

        assertEquals(2, resource.getCumulativeSync().getTickSamples());
        assertEquals(3, resource.getCumulativeSync().getBodiesInspected());
        assertEquals(1, resource.getCumulativeSync().getBodiesSynced());
        assertEquals(1, resource.getCumulativeSync().getTransitionSyncs());
        assertEquals(1, resource.getCumulativeSync().getKeepaliveSyncs());
        assertEquals(80L, resource.getWorstSync().getTickNanos());
    }

    @Test
    void workerStepRateUsesCompletedWorkerStepIntervals() {
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
        assertEquals(0, resource.getLatestStep().getWorkerStepRateSamples());
        assertEquals(0L, resource.getLatestStep().getWorkerStepIntervalNanos());

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

        assertEquals(1, resource.getLatestStep().getWorkerStepRateSamples());
        assertEquals(100_000_000L, resource.getLatestStep().getWorkerStepIntervalNanos());
        assertEquals(2, resource.getCumulativeStep().getWorkerStepRateSamples());
        assertEquals(150_000_000L, resource.getCumulativeStep().getWorkerStepIntervalNanos());
        assertEquals(100_000_000L, resource.getCumulativeStep().getMaxWorkerStepIntervalNanos());

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

        assertEquals(0, resource.getLatestStep().getWorkerStepRateSamples());
        assertEquals(0L, resource.getLatestStep().getWorkerStepIntervalNanos());
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
        resource.recordStepScheduling(0.125f, 0.125f, 0.0f, 0.0f, false);
        PhysicsRuntimeProfilingResource.SyncCollector collector = resource.beginSyncSample();
        collector.incrementBodiesSynced();
        resource.finishSyncSample(collector, 15L);

        PhysicsRuntimeProfilingResource copy = resource.clone();
        assertEquals(resource.getLatestStep().getTickNanos(), copy.getLatestStep().getTickNanos());
        assertEquals(resource.getLatestStep().getSchedulerSubmittedDtNanos(),
            copy.getLatestStep().getSchedulerSubmittedDtNanos());
        assertEquals(resource.getLatestSync().getBodiesSynced(), copy.getLatestSync().getBodiesSynced());
        assertNull(copy.getActiveSyncCollector());

        resource.reset();
        assertEquals(0, resource.getCumulativeStep().getTickSamples());
        assertEquals(0, resource.getCumulativeStep().getSchedulerSamples());
        assertEquals(0, resource.getLatestStep().getTickNanos());
        assertEquals(0L, resource.getLatestStep().getSchedulerSubmittedDtNanos());
        assertEquals(0, resource.getWorstSync().getTickNanos());
        assertNull(resource.getActiveSyncCollector());
    }
}
