package dev.hytalemodding.impulse.core.internal.modules.physicschunk.profiling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hypixel.hytale.component.ComponentRegistry;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.core.internal.modules.physicschunk.VoxelTerrainCollisionCache.BuildStats;
import dev.hytalemodding.impulse.core.internal.modules.physicschunk.profiling.PhysicsChunkProfilingResource.MissingSectionReason;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class PhysicsChunkProfilingResourceTest {

    @AfterEach
    void clearRegistration() {
        PhysicsChunkProfilingResource.clearResourceType();
    }

    @Test
    void resourceTypeIsOwnedByPhysicsChunkModuleRegistration() {
        assertFalse(PhysicsChunkProfilingResource.isResourceTypeRegistered());
        assertThrows(IllegalStateException.class, PhysicsChunkProfilingResource::getResourceType);

        ComponentRegistry<EntityStore> registry = new ComponentRegistry<>();
        ResourceType<EntityStore, PhysicsChunkProfilingResource> type =
            registry.registerResource(PhysicsChunkProfilingResource.class,
                PhysicsChunkProfilingResource::new);

        PhysicsChunkProfilingResource.setResourceType(type);

        assertTrue(PhysicsChunkProfilingResource.isResourceTypeRegistered());
        assertSame(type, PhysicsChunkProfilingResource.getResourceType());

        PhysicsChunkProfilingResource.clearResourceType();

        assertFalse(PhysicsChunkProfilingResource.isResourceTypeRegistered());
    }

    @Test
    void finishTickTracksLatestCumulativeAndWorstSnapshots() {
        PhysicsChunkProfilingResource resource = new PhysicsChunkProfilingResource();

        PhysicsChunkProfilingResource.Snapshot first = resource.beginTick();
        first.setPlayerStreamingTargets(2);
        first.addBodyStreamingCandidates(5);
        first.addBodyStreamingTargets(3);
        first.incrementBodyTargetDedupeSkips();
        first.incrementBodyTargetCacheHits();
        first.incrementBodyTargetFirstSeen();
        first.incrementBodyTargetBoundsChanged();
        first.incrementBodyTargetActiveRefreshes();
        first.incrementBodyTargetSleepingRefreshes();
        first.incrementBodyTargetActiveStableSkips();
        first.incrementBodyTargetSleepingStableSkips();
        first.addBodyTargetsPruned(2);
        first.incrementStreamingSpaces();
        first.incrementTerrainApplyQueued();
        first.incrementTerrainApplySkippedPending();
        first.incrementEnsureCalls();
        first.incrementSectionRequests();
        first.incrementSectionCacheHits();
        first.incrementMissingChunks();
        first.incrementMissingBackoffSkip(MissingSectionReason.BLOCK_CHUNK);
        first.incrementMissingBackoffSkip(MissingSectionReason.BLOCK_SECTION);
        first.recordMissingSection(MissingSectionReason.BLOCK_CHUNK, 1, 2, 3, null);
        first.recordMissingSection(MissingSectionReason.BLOCK_SECTION, 1, 2, 3, null);
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

        PhysicsChunkProfilingResource.Snapshot second = resource.beginTick();
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
        assertEquals(1, resource.getCumulative().getBodyTargetCacheHits());
        assertEquals(1, resource.getCumulative().getBodyTargetFirstSeen());
        assertEquals(1, resource.getCumulative().getBodyTargetBoundsChanged());
        assertEquals(1, resource.getCumulative().getBodyTargetActiveRefreshes());
        assertEquals(1, resource.getCumulative().getBodyTargetSleepingRefreshes());
        assertEquals(1, resource.getCumulative().getBodyTargetActiveStableSkips());
        assertEquals(1, resource.getCumulative().getBodyTargetSleepingStableSkips());
        assertEquals(2, resource.getCumulative().getBodyTargetsPruned());
        assertEquals(1, resource.getCumulative().getStreamingSpaces());
        assertEquals(1, resource.getCumulative().getTerrainApplyQueued());
        assertEquals(1, resource.getCumulative().getTerrainApplySkippedPending());
        assertEquals(1, resource.getCumulative().getEnsureCalls());
        assertEquals(1, resource.getCumulative().getSectionRequests());
        assertEquals(1, resource.getCumulative().getSectionCacheHits());
        assertEquals(3, resource.getCumulative().getMissingChunks());
        assertEquals(1, resource.getCumulative().getMissingBlockChunks());
        assertEquals(1, resource.getCumulative().getMissingBlockSections());
        assertEquals(1, resource.getCumulative().getMissingReasonUnknown());
        assertEquals(2, resource.getCumulative().getMissingBackoffSkips());
        assertEquals(1, resource.getCumulative().getMissingBlockChunkBackoffSkips());
        assertEquals(1, resource.getCumulative().getMissingBlockSectionBackoffSkips());
        assertEquals(1, resource.getCumulative().getUniqueMissingSections());
        assertEquals(2, resource.getCumulative().getMissingUnconfiguredRetainedEnvelope());
        assertEquals(2, resource.getCumulative().getMissingSectionSamples().size());
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
        PhysicsChunkProfilingResource resource = new PhysicsChunkProfilingResource();
        resource.setEnabled(true);
        PhysicsChunkProfilingResource.Snapshot snapshot = resource.beginTick();
        snapshot.setTickNanos(25L);
        resource.finishTick(snapshot);

        PhysicsChunkProfilingResource copy = resource.clone();
        assertTrue(copy.isEnabled());
        assertEquals(25L, copy.getLatestTick().getTickNanos());
        assertEquals(25L, copy.getWorstTick().getTickNanos());

        resource.reset();
        assertEquals(0, resource.getCumulative().getTickSamples());
        assertEquals(0L, resource.getLatestTick().getTickNanos());
        assertEquals(0L, resource.getWorstTick().getTickNanos());
    }

    @Test
    void missingSectionDiagnosticsTrackRetainedEnvelopeStatus() {
        PhysicsChunkProfilingResource resource = new PhysicsChunkProfilingResource();
        LongOpenHashSet retained = new LongOpenHashSet();
        retained.add(PhysicsChunkProfilingResource.packDiagnosticSectionKey(1, 2, 3));
        resource.setDiagnosticRetainedSections(retained);

        PhysicsChunkProfilingResource.Snapshot snapshot = resource.beginTick();
        snapshot.recordMissingSection(MissingSectionReason.BLOCK_CHUNK, 1, 2, 3, null);
        snapshot.recordMissingSection(MissingSectionReason.BLOCK_SECTION, 4, 5, 6, null);
        resource.finishTick(snapshot);

        PhysicsChunkProfilingResource.Snapshot cumulative = resource.getCumulativeSnapshot();
        assertEquals(2, cumulative.getMissingChunks());
        assertEquals(1, cumulative.getMissingBlockChunks());
        assertEquals(1, cumulative.getMissingBlockSections());
        assertEquals(2, cumulative.getUniqueMissingSections());
        assertEquals(1, cumulative.getMissingInsideRetainedEnvelope());
        assertEquals(1, cumulative.getMissingOutsideRetainedEnvelope());
        assertEquals(0, cumulative.getMissingUnconfiguredRetainedEnvelope());
        assertEquals(2, cumulative.getMissingSectionSamples().size());
        assertEquals(PhysicsChunkProfilingResource.RetainedEnvelopeStatus.INSIDE,
            cumulative.getMissingSectionSamples().get(0).retainedEnvelopeStatus());
        assertEquals(PhysicsChunkProfilingResource.RetainedEnvelopeStatus.OUTSIDE,
            cumulative.getMissingSectionSamples().get(1).retainedEnvelopeStatus());
    }
}
