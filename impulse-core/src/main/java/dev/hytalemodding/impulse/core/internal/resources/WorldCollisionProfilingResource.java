package dev.hytalemodding.impulse.core.internal.resources;

import com.hypixel.hytale.component.Resource;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.core.ImpulsePlugin;
import dev.hytalemodding.impulse.core.internal.voxel.WorldVoxelCollisionCache.BuildStats;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsBodyId;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.joml.Vector3d;
import org.joml.Vector3f;

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
    @Getter(AccessLevel.NONE)
    @Nullable
    private RetainedSectionEnvelope diagnosticRetainedEnvelope;

    public WorldCollisionProfilingResource() {
    }

    @Nonnull
    public synchronized Snapshot beginTick() {
        Snapshot snapshot = new Snapshot();
        snapshot.setDiagnosticRetainedEnvelope(diagnosticRetainedEnvelope);
        snapshot.recordTickSample();
        return snapshot;
    }

    public synchronized void finishTick(@Nonnull Snapshot snapshot) {
        latestTick.copyFrom(snapshot);
        cumulative.add(snapshot);
        if (snapshot.getTickNanos() >= worstTick.getTickNanos()) {
            worstTick.copyFrom(snapshot);
        }
    }

    public synchronized void reset() {
        cumulative.reset();
        latestTick.reset();
        worstTick.reset();
    }

    public synchronized void setDiagnosticRetainedSections(@Nonnull LongSet sectionKeys) {
        diagnosticRetainedEnvelope = new RetainedSectionEnvelope(sectionKeys);
    }

    public synchronized void clearDiagnosticRetainedSections() {
        diagnosticRetainedEnvelope = null;
    }

    public static long packDiagnosticSectionKey(int chunkX, int sectionY, int chunkZ) {
        return ((long) chunkX & 0x3FF_FFFFL) << 38
            | ((long) chunkZ & 0x3FF_FFFFL) << 12
            | (sectionY & 0xFFFL);
    }

    @Nonnull
    public synchronized Snapshot getCumulativeSnapshot() {
        return cumulative.copy();
    }

    @Nonnull
    public synchronized Snapshot getLatestTickSnapshot() {
        return latestTick.copy();
    }

    @Nonnull
    public synchronized Snapshot getWorstTickSnapshot() {
        return worstTick.copy();
    }

    @Nonnull
    @Override
    public synchronized WorldCollisionProfilingResource clone() {
        WorldCollisionProfilingResource copy = new WorldCollisionProfilingResource();
        copy.enabled = enabled;
        copy.cumulative.copyFrom(cumulative);
        copy.latestTick.copyFrom(latestTick);
        copy.worstTick.copyFrom(worstTick);
        copy.diagnosticRetainedEnvelope = diagnosticRetainedEnvelope;
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
        private int bodyTargetCacheHits;
        private int bodyTargetFirstSeen;
        private int bodyTargetBoundsChanged;
        private int bodyTargetActiveRefreshes;
        private int bodyTargetSleepingRefreshes;
        private int bodyTargetActiveStableSkips;
        private int bodyTargetSleepingStableSkips;
        private int bodyTargetsPruned;
        private int playerSectionTargets;
        private int bodySectionTargets;
        private int streamingSpaces;
        private int terrainApplyQueued;
        private int terrainApplySkippedPending;
        private int ensureCalls;
        private int sectionRequests;
        private int sectionCacheHits;
        private int missingChunks;
        private int missingBlockChunks;
        private int missingBlockSections;
        private int missingReasonUnknown;
        private int missingBackoffSkips;
        private int missingBlockChunkBackoffSkips;
        private int missingBlockSectionBackoffSkips;
        private int missingInsideRetainedEnvelope;
        private int missingOutsideRetainedEnvelope;
        private int missingUnconfiguredRetainedEnvelope;
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
        @Getter(AccessLevel.NONE)
        @Nullable
        private RetainedSectionEnvelope diagnosticRetainedEnvelope;
        private final LongSet uniqueMissingSectionKeys = new LongOpenHashSet();
        private final List<MissingSectionSample> missingSectionSamples = new ArrayList<>();

        private static final int MAX_MISSING_SECTION_SAMPLES = 8;

        public void recordTickSample() {
            tickSamples++;
        }

        public void incrementStreamingSpaces() {
            streamingSpaces++;
        }

        public void incrementTerrainApplyQueued() {
            terrainApplyQueued++;
        }

        public void incrementTerrainApplySkippedPending() {
            terrainApplySkippedPending++;
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

        public void incrementBodyTargetCacheHits() {
            bodyTargetCacheHits++;
        }

        public void incrementBodyTargetFirstSeen() {
            bodyTargetFirstSeen++;
        }

        public void incrementBodyTargetBoundsChanged() {
            bodyTargetBoundsChanged++;
        }

        public void incrementBodyTargetActiveRefreshes() {
            bodyTargetActiveRefreshes++;
        }

        public void incrementBodyTargetSleepingRefreshes() {
            bodyTargetSleepingRefreshes++;
        }

        public void incrementBodyTargetActiveStableSkips() {
            bodyTargetActiveStableSkips++;
        }

        public void incrementBodyTargetSleepingStableSkips() {
            bodyTargetSleepingStableSkips++;
        }

        public void addBodyTargetsPruned(int count) {
            bodyTargetsPruned += count;
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
            missingReasonUnknown++;
        }

        public void incrementMissingBackoffSkip(@Nonnull MissingSectionReason reason) {
            missingBackoffSkips++;
            switch (reason) {
                case BLOCK_CHUNK -> missingBlockChunkBackoffSkips++;
                case BLOCK_SECTION -> missingBlockSectionBackoffSkips++;
                case UNKNOWN -> {
                }
            }
        }

        public void recordMissingSection(@Nonnull MissingSectionReason reason,
            int chunkX,
            int sectionY,
            int chunkZ,
            @Nullable StreamingTargetDiagnostic target) {
            missingChunks++;
            switch (reason) {
                case BLOCK_CHUNK -> missingBlockChunks++;
                case BLOCK_SECTION -> missingBlockSections++;
                case UNKNOWN -> missingReasonUnknown++;
            }

            long key = packDiagnosticSectionKey(chunkX, sectionY, chunkZ);
            uniqueMissingSectionKeys.add(key);
            RetainedEnvelopeStatus retainedStatus = retainedStatus(key);
            switch (retainedStatus) {
                case INSIDE -> missingInsideRetainedEnvelope++;
                case OUTSIDE -> missingOutsideRetainedEnvelope++;
                case UNCONFIGURED -> missingUnconfiguredRetainedEnvelope++;
            }
            if (missingSectionSamples.size() < MAX_MISSING_SECTION_SAMPLES) {
                missingSectionSamples.add(new MissingSectionSample(chunkX,
                    sectionY,
                    chunkZ,
                    reason,
                    retainedStatus,
                    target != null ? target : StreamingTargetDiagnostic.unknown()));
            }
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

        public int getUniqueMissingSections() {
            return uniqueMissingSectionKeys.size();
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
            bodyTargetCacheHits = other.bodyTargetCacheHits;
            bodyTargetFirstSeen = other.bodyTargetFirstSeen;
            bodyTargetBoundsChanged = other.bodyTargetBoundsChanged;
            bodyTargetActiveRefreshes = other.bodyTargetActiveRefreshes;
            bodyTargetSleepingRefreshes = other.bodyTargetSleepingRefreshes;
            bodyTargetActiveStableSkips = other.bodyTargetActiveStableSkips;
            bodyTargetSleepingStableSkips = other.bodyTargetSleepingStableSkips;
            bodyTargetsPruned = other.bodyTargetsPruned;
            playerSectionTargets = other.playerSectionTargets;
            bodySectionTargets = other.bodySectionTargets;
            streamingSpaces = other.streamingSpaces;
            terrainApplyQueued = other.terrainApplyQueued;
            terrainApplySkippedPending = other.terrainApplySkippedPending;
            ensureCalls = other.ensureCalls;
            sectionRequests = other.sectionRequests;
            sectionCacheHits = other.sectionCacheHits;
            missingChunks = other.missingChunks;
            missingBlockChunks = other.missingBlockChunks;
            missingBlockSections = other.missingBlockSections;
            missingReasonUnknown = other.missingReasonUnknown;
            missingBackoffSkips = other.missingBackoffSkips;
            missingBlockChunkBackoffSkips = other.missingBlockChunkBackoffSkips;
            missingBlockSectionBackoffSkips = other.missingBlockSectionBackoffSkips;
            missingInsideRetainedEnvelope = other.missingInsideRetainedEnvelope;
            missingOutsideRetainedEnvelope = other.missingOutsideRetainedEnvelope;
            missingUnconfiguredRetainedEnvelope = other.missingUnconfiguredRetainedEnvelope;
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
            uniqueMissingSectionKeys.clear();
            uniqueMissingSectionKeys.addAll(other.uniqueMissingSectionKeys);
            missingSectionSamples.clear();
            missingSectionSamples.addAll(other.missingSectionSamples);
        }

        public void add(@Nonnull Snapshot other) {
            tickSamples += other.tickSamples;
            playerStreamingTargets += other.playerStreamingTargets;
            bodyStreamingCandidates += other.bodyStreamingCandidates;
            bodySpatialIndexCandidates += other.bodySpatialIndexCandidates;
            bodyStreamingTargets += other.bodyStreamingTargets;
            bodyTargetDedupeSkips += other.bodyTargetDedupeSkips;
            bodyTargetCacheHits += other.bodyTargetCacheHits;
            bodyTargetFirstSeen += other.bodyTargetFirstSeen;
            bodyTargetBoundsChanged += other.bodyTargetBoundsChanged;
            bodyTargetActiveRefreshes += other.bodyTargetActiveRefreshes;
            bodyTargetSleepingRefreshes += other.bodyTargetSleepingRefreshes;
            bodyTargetActiveStableSkips += other.bodyTargetActiveStableSkips;
            bodyTargetSleepingStableSkips += other.bodyTargetSleepingStableSkips;
            bodyTargetsPruned += other.bodyTargetsPruned;
            playerSectionTargets += other.playerSectionTargets;
            bodySectionTargets += other.bodySectionTargets;
            streamingSpaces += other.streamingSpaces;
            terrainApplyQueued += other.terrainApplyQueued;
            terrainApplySkippedPending += other.terrainApplySkippedPending;
            ensureCalls += other.ensureCalls;
            sectionRequests += other.sectionRequests;
            sectionCacheHits += other.sectionCacheHits;
            missingChunks += other.missingChunks;
            missingBlockChunks += other.missingBlockChunks;
            missingBlockSections += other.missingBlockSections;
            missingReasonUnknown += other.missingReasonUnknown;
            missingBackoffSkips += other.missingBackoffSkips;
            missingBlockChunkBackoffSkips += other.missingBlockChunkBackoffSkips;
            missingBlockSectionBackoffSkips += other.missingBlockSectionBackoffSkips;
            missingInsideRetainedEnvelope += other.missingInsideRetainedEnvelope;
            missingOutsideRetainedEnvelope += other.missingOutsideRetainedEnvelope;
            missingUnconfiguredRetainedEnvelope += other.missingUnconfiguredRetainedEnvelope;
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
            uniqueMissingSectionKeys.addAll(other.uniqueMissingSectionKeys);
            appendMissingSamples(other.missingSectionSamples);
        }

        public void reset() {
            tickSamples = 0;
            playerStreamingTargets = 0;
            bodyStreamingCandidates = 0;
            bodySpatialIndexCandidates = 0;
            bodyStreamingTargets = 0;
            bodyTargetDedupeSkips = 0;
            bodyTargetCacheHits = 0;
            bodyTargetFirstSeen = 0;
            bodyTargetBoundsChanged = 0;
            bodyTargetActiveRefreshes = 0;
            bodyTargetSleepingRefreshes = 0;
            bodyTargetActiveStableSkips = 0;
            bodyTargetSleepingStableSkips = 0;
            bodyTargetsPruned = 0;
            playerSectionTargets = 0;
            bodySectionTargets = 0;
            streamingSpaces = 0;
            terrainApplyQueued = 0;
            terrainApplySkippedPending = 0;
            ensureCalls = 0;
            sectionRequests = 0;
            sectionCacheHits = 0;
            missingChunks = 0;
            missingBlockChunks = 0;
            missingBlockSections = 0;
            missingReasonUnknown = 0;
            missingBackoffSkips = 0;
            missingBlockChunkBackoffSkips = 0;
            missingBlockSectionBackoffSkips = 0;
            missingInsideRetainedEnvelope = 0;
            missingOutsideRetainedEnvelope = 0;
            missingUnconfiguredRetainedEnvelope = 0;
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
            uniqueMissingSectionKeys.clear();
            missingSectionSamples.clear();
        }

        private void setDiagnosticRetainedEnvelope(@Nullable RetainedSectionEnvelope envelope) {
            diagnosticRetainedEnvelope = envelope;
        }

        @Nonnull
        private RetainedEnvelopeStatus retainedStatus(long sectionKey) {
            if (diagnosticRetainedEnvelope == null) {
                return RetainedEnvelopeStatus.UNCONFIGURED;
            }
            return diagnosticRetainedEnvelope.contains(sectionKey)
                ? RetainedEnvelopeStatus.INSIDE
                : RetainedEnvelopeStatus.OUTSIDE;
        }

        private void appendMissingSamples(@Nonnull List<MissingSectionSample> samples) {
            for (MissingSectionSample sample : samples) {
                if (missingSectionSamples.size() >= MAX_MISSING_SECTION_SAMPLES) {
                    return;
                }
                missingSectionSamples.add(sample);
            }
        }
    }

    private static final class RetainedSectionEnvelope {

        private final LongSet sectionKeys;

        private RetainedSectionEnvelope(@Nonnull LongSet sectionKeys) {
            this.sectionKeys = new LongOpenHashSet(sectionKeys);
        }

        private boolean contains(long sectionKey) {
            return sectionKeys.contains(sectionKey);
        }
    }

    public enum MissingSectionReason {
        BLOCK_CHUNK,
        BLOCK_SECTION,
        UNKNOWN
    }

    public enum RetainedEnvelopeStatus {
        INSIDE,
        OUTSIDE,
        UNCONFIGURED
    }

    public enum StreamingTargetType {
        PLAYER,
        BODY,
        UNKNOWN
    }

    public record DiagnosticPosition(double x, double y, double z) {

        @Nonnull
        public static DiagnosticPosition from(@Nonnull Vector3d position) {
            return new DiagnosticPosition(position.x, position.y, position.z);
        }

        @Nonnull
        public static DiagnosticPosition from(@Nonnull Vector3f position) {
            return new DiagnosticPosition(position.x, position.y, position.z);
        }

        @Nonnull
        public String compact() {
            return String.format(java.util.Locale.ROOT, "%.3f,%.3f,%.3f", x, y, z);
        }
    }

    public record StreamingTargetDiagnostic(@Nonnull StreamingTargetType targetType,
                                            @Nullable PhysicsBodyId bodyId,
                                            @Nullable DiagnosticPosition snapshotPosition,
                                            @Nullable DiagnosticPosition livePosition) {

        @Nonnull
        public static StreamingTargetDiagnostic player(@Nonnull Vector3d position) {
            DiagnosticPosition diagnosticPosition = DiagnosticPosition.from(position);
            return new StreamingTargetDiagnostic(StreamingTargetType.PLAYER,
                null,
                diagnosticPosition,
                diagnosticPosition);
        }

        @Nonnull
        public static StreamingTargetDiagnostic body(@Nonnull PhysicsBodyId bodyId,
            @Nonnull Vector3f snapshotPosition,
            @Nonnull Vector3f livePosition) {
            return new StreamingTargetDiagnostic(StreamingTargetType.BODY,
                bodyId,
                DiagnosticPosition.from(snapshotPosition),
                DiagnosticPosition.from(livePosition));
        }

        @Nonnull
        public static StreamingTargetDiagnostic unknown() {
            return new StreamingTargetDiagnostic(StreamingTargetType.UNKNOWN, null, null, null);
        }
    }

    public record MissingSectionSample(int chunkX,
                                       int sectionY,
                                       int chunkZ,
                                       @Nonnull MissingSectionReason reason,
                                       @Nonnull RetainedEnvelopeStatus retainedEnvelopeStatus,
                                       @Nonnull StreamingTargetDiagnostic target) {
    }
}
