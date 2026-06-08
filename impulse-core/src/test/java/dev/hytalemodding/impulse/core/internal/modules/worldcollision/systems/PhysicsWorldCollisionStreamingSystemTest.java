package dev.hytalemodding.impulse.core.internal.modules.worldcollision.systems;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hytalemodding.impulse.core.internal.modules.worldcollision.WorldVoxelCollisionCache;
import dev.hytalemodding.impulse.core.internal.modules.worldcollision.profiling.WorldCollisionProfilingResource;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsMutationHandle;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class PhysicsWorldCollisionStreamingSystemTest {

    @Test
    void rejectedStreamingApplyClearsPendingGateAndRecordsSkip() {
        WorldVoxelCollisionCache cache = new WorldVoxelCollisionCache();
        WorldCollisionProfilingResource.Snapshot snapshot =
            new WorldCollisionProfilingResource.Snapshot();

        assertTrue(cache.tryBeginStreamingApply());

        PhysicsWorldCollisionStreamingSystem.finishRejectedStreamingApply(cache, snapshot);

        assertFalse(cache.isStreamingApplyPending());
        assertEquals(1, snapshot.getTerrainApplySkippedPending());
        assertEquals(0, snapshot.getTerrainApplyQueued());
    }

    @Test
    void rejectedQueuedStreamingApplyClearsPendingGateAndKeepsSnapshotOpen() {
        WorldVoxelCollisionCache cache = new WorldVoxelCollisionCache();
        WorldCollisionProfilingResource.Snapshot snapshot =
            new WorldCollisionProfilingResource.Snapshot();

        assertTrue(cache.tryBeginStreamingApply());

        boolean queued = PhysicsWorldCollisionStreamingSystem.tryQueueStreamingApply(cache,
            new WorldCollisionProfilingResource(),
            snapshot,
            System.nanoTime(),
            _ -> {
                throw new RejectedExecutionException("forced enqueue rejection");
            });

        assertFalse(queued);
        assertFalse(cache.isStreamingApplyPending());
        assertEquals(0, snapshot.getTerrainApplyQueued());
        assertEquals(1, snapshot.getTerrainApplySkippedPending());
    }

    @Test
    void failedHandleStreamingApplyClearsPendingGateAndRecordsSkip() {
        WorldVoxelCollisionCache cache = new WorldVoxelCollisionCache();
        WorldCollisionProfilingResource.Snapshot snapshot =
            new WorldCollisionProfilingResource.Snapshot();

        assertTrue(cache.tryBeginStreamingApply());

        boolean queued = PhysicsWorldCollisionStreamingSystem.tryQueueStreamingApply(cache,
            new WorldCollisionProfilingResource(),
            snapshot,
            System.nanoTime(),
            _ -> PhysicsMutationHandle.failed("stream world collision terrain apply",
                null,
                new RejectedExecutionException("forced handle rejection")));

        assertFalse(queued);
        assertFalse(cache.isStreamingApplyPending());
        assertEquals(0, snapshot.getTerrainApplyQueued());
        assertEquals(1, snapshot.getTerrainApplySkippedPending());
    }

    @Test
    void queuedStreamingApplyFinishesGateAndProfilingOnce() {
        WorldVoxelCollisionCache cache = new WorldVoxelCollisionCache();
        WorldCollisionProfilingResource profiling = new WorldCollisionProfilingResource();
        WorldCollisionProfilingResource.Snapshot snapshot = profiling.beginTick();
        AtomicBoolean finished = new AtomicBoolean();

        assertTrue(cache.tryBeginStreamingApply());

        PhysicsWorldCollisionStreamingSystem.finishQueuedStreamingApply(cache,
            profiling,
            snapshot,
            System.nanoTime(),
            finished);
        PhysicsWorldCollisionStreamingSystem.finishQueuedStreamingApply(cache,
            profiling,
            snapshot,
            System.nanoTime(),
            finished);

        assertFalse(cache.isStreamingApplyPending());
        assertEquals(1, profiling.getCumulativeSnapshot().getTickSamples());
    }
}
