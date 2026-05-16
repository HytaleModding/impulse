package dev.hytalemodding.impulse.core.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class PhysicsRuntimeProfilingResourceTest {

    @Test
    void recordStepTracksLatestCumulativeAndWorstSamples() {
        PhysicsRuntimeProfilingResource resource = new PhysicsRuntimeProfilingResource();

        resource.recordStep(2, 6, 100L);
        resource.recordStep(1, 3, 40L);

        assertEquals(1, resource.getLatestStep().getTickSamples());
        assertEquals(1, resource.getLatestStep().getSpaces());
        assertEquals(3, resource.getLatestStep().getSubsteps());
        assertEquals(40L, resource.getLatestStep().getTickNanos());

        assertEquals(2, resource.getCumulativeStep().getTickSamples());
        assertEquals(3, resource.getCumulativeStep().getSpaces());
        assertEquals(9, resource.getCumulativeStep().getSubsteps());
        assertEquals(140L, resource.getCumulativeStep().getTickNanos());

        assertEquals(100L, resource.getWorstStep().getTickNanos());
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
