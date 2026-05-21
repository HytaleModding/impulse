package dev.hytalemodding.impulse.core.internal.resources;

import com.hypixel.hytale.component.Resource;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.core.ImpulsePlugin;
import dev.hytalemodding.impulse.core.internal.voxel.WorldVoxelCollisionCache.BuildStats;
import javax.annotation.Nonnull;
import lombok.Getter;
import lombok.Setter;

/**
 * Runtime-only profiling state for world collision streaming.
 *
 * <p>This resource collects targeted metrics for the streamed voxel-collision
 * path so performance work can be driven by section/build/prune behavior rather
 * than generic timing guesses. The data is operational state only and is not
 * part of persisted physics world state.</p>
 */
@Getter
public class WorldCollisionProfilingResource implements Resource<EntityStore> {

    @Setter
    private boolean enabled;

    private final Snapshot cumulative = new Snapshot();
    private final Snapshot latestTick = new Snapshot();
    private final Snapshot worstTick = new Snapshot();

    public WorldCollisionProfilingResource() {
    }

    @Nonnull
    public Snapshot beginTick() {
        Snapshot snapshot = new Snapshot();
        snapshot.recordTickSample();
        return snapshot;
    }

    public void finishTick(@Nonnull Snapshot snapshot) {
        latestTick.copyFrom(snapshot);
        cumulative.add(snapshot);
        if (snapshot.getTickNanos() >= worstTick.getTickNanos()) {
            worstTick.copyFrom(snapshot);
        }
    }

    public void reset() {
        cumulative.reset();
        latestTick.reset();
        worstTick.reset();
    }

    @Nonnull
    public Snapshot getCumulativeSnapshot() {
        return cumulative.copy();
    }

    @Nonnull
    public Snapshot getLatestTickSnapshot() {
        return latestTick.copy();
    }

    @Nonnull
    public Snapshot getWorstTickSnapshot() {
        return worstTick.copy();
    }

    @Nonnull
    @Override
    public WorldCollisionProfilingResource clone() {
        WorldCollisionProfilingResource copy = new WorldCollisionProfilingResource();
        copy.enabled = enabled;
        copy.cumulative.copyFrom(cumulative);
        copy.latestTick.copyFrom(latestTick);
        copy.worstTick.copyFrom(worstTick);
        return copy;
    }

    public static ResourceType<EntityStore, WorldCollisionProfilingResource> getResourceType() {
        return ImpulsePlugin.get().getWorldCollisionProfilingResourceType();
    }

    /**
     * Mutable metrics snapshot for one profiled tick or an aggregate of many ticks.
     */
    @Getter
    public static final class Snapshot {

        private int tickSamples;
        @Setter
        private int playerStreamingTargets;
        private int bodyStreamingCandidates;
        private int bodySpatialIndexCandidates;
        private int bodyStreamingTargets;
        private int bodyTargetDedupeSkips;
        private int playerSectionTargets;
        private int bodySectionTargets;
        private int streamingSpaces;
        private int ensureCalls;
        private int sectionRequests;
        private int sectionCacheHits;
        private int missingChunks;
        private int sectionsBuilt;
        private int sectionsRebuilt;
        private int voxelBodies;
        private int colliderBodiesAdded;
        private int bodiesRemovedFromRebuild;
        private int bodiesRemovedFromUnloadedPrune;
        private int bodiesRemovedFromTtlPrune;
        private int sectionsRemovedFromUnloadedPrune;
        private int sectionsRemovedFromTtlPrune;
        private int duplicateSkips;
        private int scannedBlocks;
        private int solidBlocks;
        private int culledInteriorBlocks;
        private int fullCubeRuns;
        private int detailBoxes;
        @Setter
        private long tickNanos;
        private long ensureAroundNanos;
        private long ensureSectionNanos;
        private long pruneUnloadedNanos;
        private long pruneUnusedNanos;

        public void recordTickSample() {
            tickSamples++;
        }

        public void incrementStreamingSpaces() {
            streamingSpaces++;
        }

        public void addBodyStreamingCandidates(int count) {
            bodyStreamingCandidates += count;
        }

        public void addBodySpatialIndexCandidates(int count) {
            bodySpatialIndexCandidates += count;
        }

        public void addBodyStreamingTargets(int count) {
            bodyStreamingTargets += count;
        }

        public void incrementBodyTargetDedupeSkips() {
            bodyTargetDedupeSkips++;
        }

        public void addPlayerSectionTargets(int count) {
            playerSectionTargets += count;
        }

        public void addBodySectionTargets(int count) {
            bodySectionTargets += count;
        }

        public void incrementEnsureCalls() {
            ensureCalls++;
        }

        public void incrementSectionRequests() {
            sectionRequests++;
        }

        public void incrementSectionCacheHits() {
            sectionCacheHits++;
        }

        public void incrementMissingChunks() {
            missingChunks++;
        }

        public void incrementDuplicateSkips() {
            duplicateSkips++;
        }

        public void addBuildStats(@Nonnull BuildStats stats) {
            scannedBlocks += stats.scannedBlocks();
            solidBlocks += stats.solidBlocks();
            culledInteriorBlocks += stats.culledInteriorBlocks();
            fullCubeRuns += stats.fullCubeRuns();
            detailBoxes += stats.detailBoxes();
            colliderBodiesAdded += stats.colliderBodies();
            bodiesRemovedFromRebuild += stats.removedBodies();
            sectionsBuilt += stats.sectionsBuilt();
            sectionsRebuilt += stats.sectionsRebuilt();
            voxelBodies += stats.voxelBodies();
        }

        public void addUnloadedPrune(int removedSections, int removedBodies) {
            sectionsRemovedFromUnloadedPrune += removedSections;
            bodiesRemovedFromUnloadedPrune += removedBodies;
        }

        public void addTtlPrune(int removedSections, int removedBodies) {
            sectionsRemovedFromTtlPrune += removedSections;
            bodiesRemovedFromTtlPrune += removedBodies;
        }

        public void addEnsureAroundNanos(long nanos) {
            ensureAroundNanos += nanos;
        }

        public void addEnsureSectionNanos(long nanos) {
            ensureSectionNanos += nanos;
        }

        public void addPruneUnloadedNanos(long nanos) {
            pruneUnloadedNanos += nanos;
        }

        public void addPruneUnusedNanos(long nanos) {
            pruneUnusedNanos += nanos;
        }

        @Nonnull
        public Snapshot copy() {
            Snapshot copy = new Snapshot();
            copy.copyFrom(this);
            return copy;
        }

        public void copyFrom(@Nonnull Snapshot other) {
            tickSamples = other.tickSamples;
            playerStreamingTargets = other.playerStreamingTargets;
            bodyStreamingCandidates = other.bodyStreamingCandidates;
            bodySpatialIndexCandidates = other.bodySpatialIndexCandidates;
            bodyStreamingTargets = other.bodyStreamingTargets;
            bodyTargetDedupeSkips = other.bodyTargetDedupeSkips;
            playerSectionTargets = other.playerSectionTargets;
            bodySectionTargets = other.bodySectionTargets;
            streamingSpaces = other.streamingSpaces;
            ensureCalls = other.ensureCalls;
            sectionRequests = other.sectionRequests;
            sectionCacheHits = other.sectionCacheHits;
            missingChunks = other.missingChunks;
            sectionsBuilt = other.sectionsBuilt;
            sectionsRebuilt = other.sectionsRebuilt;
            voxelBodies = other.voxelBodies;
            colliderBodiesAdded = other.colliderBodiesAdded;
            bodiesRemovedFromRebuild = other.bodiesRemovedFromRebuild;
            bodiesRemovedFromUnloadedPrune = other.bodiesRemovedFromUnloadedPrune;
            bodiesRemovedFromTtlPrune = other.bodiesRemovedFromTtlPrune;
            sectionsRemovedFromUnloadedPrune = other.sectionsRemovedFromUnloadedPrune;
            sectionsRemovedFromTtlPrune = other.sectionsRemovedFromTtlPrune;
            duplicateSkips = other.duplicateSkips;
            scannedBlocks = other.scannedBlocks;
            solidBlocks = other.solidBlocks;
            culledInteriorBlocks = other.culledInteriorBlocks;
            fullCubeRuns = other.fullCubeRuns;
            detailBoxes = other.detailBoxes;
            tickNanos = other.tickNanos;
            ensureAroundNanos = other.ensureAroundNanos;
            ensureSectionNanos = other.ensureSectionNanos;
            pruneUnloadedNanos = other.pruneUnloadedNanos;
            pruneUnusedNanos = other.pruneUnusedNanos;
        }

        public void add(@Nonnull Snapshot other) {
            tickSamples += other.tickSamples;
            playerStreamingTargets += other.playerStreamingTargets;
            bodyStreamingCandidates += other.bodyStreamingCandidates;
            bodySpatialIndexCandidates += other.bodySpatialIndexCandidates;
            bodyStreamingTargets += other.bodyStreamingTargets;
            bodyTargetDedupeSkips += other.bodyTargetDedupeSkips;
            playerSectionTargets += other.playerSectionTargets;
            bodySectionTargets += other.bodySectionTargets;
            streamingSpaces += other.streamingSpaces;
            ensureCalls += other.ensureCalls;
            sectionRequests += other.sectionRequests;
            sectionCacheHits += other.sectionCacheHits;
            missingChunks += other.missingChunks;
            sectionsBuilt += other.sectionsBuilt;
            sectionsRebuilt += other.sectionsRebuilt;
            voxelBodies += other.voxelBodies;
            colliderBodiesAdded += other.colliderBodiesAdded;
            bodiesRemovedFromRebuild += other.bodiesRemovedFromRebuild;
            bodiesRemovedFromUnloadedPrune += other.bodiesRemovedFromUnloadedPrune;
            bodiesRemovedFromTtlPrune += other.bodiesRemovedFromTtlPrune;
            sectionsRemovedFromUnloadedPrune += other.sectionsRemovedFromUnloadedPrune;
            sectionsRemovedFromTtlPrune += other.sectionsRemovedFromTtlPrune;
            duplicateSkips += other.duplicateSkips;
            scannedBlocks += other.scannedBlocks;
            solidBlocks += other.solidBlocks;
            culledInteriorBlocks += other.culledInteriorBlocks;
            fullCubeRuns += other.fullCubeRuns;
            detailBoxes += other.detailBoxes;
            tickNanos += other.tickNanos;
            ensureAroundNanos += other.ensureAroundNanos;
            ensureSectionNanos += other.ensureSectionNanos;
            pruneUnloadedNanos += other.pruneUnloadedNanos;
            pruneUnusedNanos += other.pruneUnusedNanos;
        }

        public void reset() {
            tickSamples = 0;
            playerStreamingTargets = 0;
            bodyStreamingCandidates = 0;
            bodySpatialIndexCandidates = 0;
            bodyStreamingTargets = 0;
            bodyTargetDedupeSkips = 0;
            playerSectionTargets = 0;
            bodySectionTargets = 0;
            streamingSpaces = 0;
            ensureCalls = 0;
            sectionRequests = 0;
            sectionCacheHits = 0;
            missingChunks = 0;
            sectionsBuilt = 0;
            sectionsRebuilt = 0;
            voxelBodies = 0;
            colliderBodiesAdded = 0;
            bodiesRemovedFromRebuild = 0;
            bodiesRemovedFromUnloadedPrune = 0;
            bodiesRemovedFromTtlPrune = 0;
            sectionsRemovedFromUnloadedPrune = 0;
            sectionsRemovedFromTtlPrune = 0;
            duplicateSkips = 0;
            scannedBlocks = 0;
            solidBlocks = 0;
            culledInteriorBlocks = 0;
            fullCubeRuns = 0;
            detailBoxes = 0;
            tickNanos = 0L;
            ensureAroundNanos = 0L;
            ensureSectionNanos = 0L;
            pruneUnloadedNanos = 0L;
            pruneUnusedNanos = 0L;
        }
    }
}
