package dev.hytalemodding.impulse.core.internal.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class PhysicsRuntimeProfilingResourceTest {

    @Test
    void recordStepTracksLatestCumulativeAndWorstSamples() {
        PhysicsRuntimeProfilingResource resource = new PhysicsRuntimeProfilingResource();

        resource.recordStep(2, 6, 100L, 7, 8, 30L, 10L, 90L);
        resource.recordStep(1, 3, 40L);
        resource.recordStepSkippedPending(75L);

        assertEquals(1, resource.getLatestStep().getTickSamples());
        assertEquals(1, resource.getLatestStep().getSpaces());
        assertEquals(3, resource.getLatestStep().getSubsteps());
        assertEquals(40L, resource.getLatestStep().getTickNanos());
        assertEquals(1, resource.getLatestStep().getSkippedPendingSteps());
        assertEquals(75L, resource.getLatestStep().getPendingStepAgeNanos());

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

        assertEquals(100L, resource.getWorstStep().getTickNanos());
        assertEquals(75L, resource.getWorstStep().getMaxPendingStepAgeNanos());
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
        PhysicsRuntimeProfilingResource.SyncCollector collector = resource.beginSyncSample();
        collector.incrementBodiesSynced();
        resource.finishSyncSample(collector, 15L);

        PhysicsRuntimeProfilingResource copy = resource.clone();
        assertEquals(resource.getLatestStep().getTickNanos(), copy.getLatestStep().getTickNanos());
        assertEquals(resource.getLatestSync().getBodiesSynced(), copy.getLatestSync().getBodiesSynced());
        assertNull(copy.getActiveSyncCollector());

        resource.reset();
        assertEquals(0, resource.getCumulativeStep().getTickSamples());
        assertEquals(0, resource.getLatestStep().getTickNanos());
        assertEquals(0, resource.getWorstSync().getTickNanos());
        assertNull(resource.getActiveSyncCollector());
    }
}
