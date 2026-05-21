package dev.hytalemodding.impulse.core.internal.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hytalemodding.impulse.core.internal.voxel.WorldVoxelCollisionCache.BuildStats;
import org.junit.jupiter.api.Test;

class WorldCollisionProfilingResourceTest {

    @Test
    void finishTickTracksLatestCumulativeAndWorstSnapshots() {
        WorldCollisionProfilingResource resource = new WorldCollisionProfilingResource();

        WorldCollisionProfilingResource.Snapshot first = resource.beginTick();
        first.setPlayerStreamingTargets(2);
        first.addBodyStreamingCandidates(5);
        first.addBodyStreamingTargets(3);
        first.incrementBodyTargetDedupeSkips();
        first.incrementStreamingSpaces();
        first.incrementEnsureCalls();
        first.incrementSectionRequests();
        first.incrementSectionCacheHits();
        first.incrementMissingChunks();
        first.incrementDuplicateSkips();
        first.addBuildStats(new BuildStats(10, 8, 2, 3, 4, 1, 1, 2, 1, 1));
        first.addUnloadedPrune(2, 3);
        first.addTtlPrune(1, 4);
        first.addEnsureAroundNanos(20L);
        first.addEnsureSectionNanos(30L);
        first.addPruneUnloadedNanos(40L);
        first.addPruneUnusedNanos(50L);
        first.setTickNanos(120L);
        resource.finishTick(first);

        WorldCollisionProfilingResource.Snapshot second = resource.beginTick();
        second.setPlayerStreamingTargets(1);
        second.addBodyStreamingCandidates(2);
        second.addBodyStreamingTargets(1);
        second.setTickNanos(60L);
        resource.finishTick(second);

        assertEquals(1, resource.getLatestTick().getTickSamples());
        assertEquals(1, resource.getLatestTick().getPlayerStreamingTargets());
        assertEquals(2, resource.getLatestTick().getBodyStreamingCandidates());
        assertEquals(60L, resource.getLatestTick().getTickNanos());

        assertEquals(2, resource.getCumulative().getTickSamples());
        assertEquals(3, resource.getCumulative().getPlayerStreamingTargets());
        assertEquals(7, resource.getCumulative().getBodyStreamingCandidates());
        assertEquals(4, resource.getCumulative().getBodyStreamingTargets());
        assertEquals(1, resource.getCumulative().getBodyTargetDedupeSkips());
        assertEquals(1, resource.getCumulative().getStreamingSpaces());
        assertEquals(1, resource.getCumulative().getEnsureCalls());
        assertEquals(1, resource.getCumulative().getSectionRequests());
        assertEquals(1, resource.getCumulative().getSectionCacheHits());
        assertEquals(1, resource.getCumulative().getMissingChunks());
        assertEquals(2, resource.getCumulative().getSectionsBuilt());
        assertEquals(1, resource.getCumulative().getSectionsRebuilt());
        assertEquals(1, resource.getCumulative().getVoxelBodies());
        assertEquals(1, resource.getCumulative().getColliderBodiesAdded());
        assertEquals(1, resource.getCumulative().getBodiesRemovedFromRebuild());
        assertEquals(3, resource.getCumulative().getBodiesRemovedFromUnloadedPrune());
        assertEquals(4, resource.getCumulative().getBodiesRemovedFromTtlPrune());
        assertEquals(2, resource.getCumulative().getSectionsRemovedFromUnloadedPrune());
        assertEquals(1, resource.getCumulative().getSectionsRemovedFromTtlPrune());
        assertEquals(1, resource.getCumulative().getDuplicateSkips());
        assertEquals(10, resource.getCumulative().getScannedBlocks());
        assertEquals(8, resource.getCumulative().getSolidBlocks());
        assertEquals(2, resource.getCumulative().getCulledInteriorBlocks());
        assertEquals(3, resource.getCumulative().getFullCubeRuns());
        assertEquals(4, resource.getCumulative().getDetailBoxes());
        assertEquals(180L, resource.getCumulative().getTickNanos());
        assertEquals(20L, resource.getCumulative().getEnsureAroundNanos());
        assertEquals(30L, resource.getCumulative().getEnsureSectionNanos());
        assertEquals(40L, resource.getCumulative().getPruneUnloadedNanos());
        assertEquals(50L, resource.getCumulative().getPruneUnusedNanos());
        assertEquals(120L, resource.getWorstTick().getTickNanos());
    }

    @Test
    void cloneAndResetPreserveExpectedMetrics() {
        WorldCollisionProfilingResource resource = new WorldCollisionProfilingResource();
        resource.setEnabled(true);
        WorldCollisionProfilingResource.Snapshot snapshot = resource.beginTick();
        snapshot.setTickNanos(25L);
        resource.finishTick(snapshot);

        WorldCollisionProfilingResource copy = resource.clone();
        assertTrue(copy.isEnabled());
        assertEquals(25L, copy.getLatestTick().getTickNanos());
        assertEquals(25L, copy.getWorstTick().getTickNanos());

        resource.reset();
        assertEquals(0, resource.getCumulative().getTickSamples());
        assertEquals(0L, resource.getLatestTick().getTickNanos());
        assertEquals(0L, resource.getWorstTick().getTickNanos());
    }
}
