package dev.hytalemodding.impulse.core.plugin.modules.physicschunk;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.core.internal.modules.physicschunk.profiling.PhysicsChunkProfilingResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsProfilingResource;
import dev.hytalemodding.impulse.core.internal.resources.profiling.PhysicsRuntimeProfilingResource;
import dev.hytalemodding.impulse.core.plugin.physicsstore.PhysicsThreading;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Public PhysicsChunk profiling helpers for command and diagnostics surfaces.
 */
public final class PhysicsChunkCollisionProfiling {

    private PhysicsChunkCollisionProfiling() {
    }

    public static boolean isRuntimeProfilingEnabled(@Nonnull Store<EntityStore> store) {
        PhysicsRuntimeProfilingResource runtimeProfiling = runtimeProfiling(store);
        PhysicsChunkProfilingResource collisionProfiling = collisionProfiling(store);
        return runtimeProfiling.isEnabled() && collisionProfiling.isEnabled();
    }

    public static void setRuntimeProfilingEnabled(@Nonnull World world,
        @Nonnull Store<EntityStore> store,
        boolean enabled) {
        runtimeProfiling(store).setEnabled(enabled);
        collisionProfiling(store).setEnabled(enabled);
        Store<PhysicsStore> physicsStore = physicsStoreOrNull(world);
        if (physicsStore != null) {
            physicsStore.getResource(PhysicsProfilingResource.getResourceType())
                .setEnabled(enabled);
        }
    }

    public static void resetRuntimeProfiling(@Nonnull World world,
        @Nonnull Store<EntityStore> store) {
        runtimeProfiling(store).reset();
        collisionProfiling(store).reset();
        Store<PhysicsStore> physicsStore = physicsStoreOrNull(world);
        if (physicsStore != null) {
            physicsStore.getResource(PhysicsProfilingResource.getResourceType()).reset();
        }
    }

    @Nonnull
    public static Snapshots snapshots(@Nonnull Store<EntityStore> store) {
        PhysicsChunkProfilingResource profiling = collisionProfiling(store);
        return new Snapshots(profiling.getCumulativeSnapshot(),
            profiling.getLatestTickSnapshot(),
            profiling.getWorstTickSnapshot(),
            profiling.isEnabled());
    }

    @Nonnull
    public static List<MissingSectionSampleView> missingSectionSamples(
        @Nonnull SnapshotView snapshot) {
        return snapshot.snapshot.getMissingSectionSamples()
            .stream()
            .map(PhysicsChunkCollisionProfiling::view)
            .toList();
    }

    @Nonnull
    private static MissingSectionSampleView view(
        @Nonnull PhysicsChunkProfilingResource.MissingSectionSample sample) {
        PhysicsChunkProfilingResource.StreamingTargetDiagnostic target = sample.target();
        return new MissingSectionSampleView(sample.chunkX(),
            sample.sectionY(),
            sample.chunkZ(),
            sample.reason().name().toLowerCase(Locale.ROOT),
            sample.retainedEnvelopeStatus().name().toLowerCase(Locale.ROOT),
            target.targetType().name().toLowerCase(Locale.ROOT),
            target.bodyUuid(),
            target.snapshotPosition() != null ? target.snapshotPosition().compact() : null,
            target.livePosition() != null ? target.livePosition().compact() : null);
    }

    @Nonnull
    private static PhysicsRuntimeProfilingResource runtimeProfiling(
        @Nonnull Store<EntityStore> store) {
        assert PhysicsRuntimeProfilingResource.getResourceType() != null;
        return store.getResource(PhysicsRuntimeProfilingResource.getResourceType());
    }

    @Nonnull
    private static PhysicsChunkProfilingResource collisionProfiling(
        @Nonnull Store<EntityStore> store) {
        return store.getResource(PhysicsChunkProfilingResource.getResourceType());
    }

    @Nullable
    private static Store<PhysicsStore> physicsStoreOrNull(@Nonnull World world) {
        return PhysicsThreading.storeOrNull(world);
    }

    public record Snapshots(@Nonnull SnapshotView cumulative,
                            @Nonnull SnapshotView latest,
                            @Nonnull SnapshotView worst,
                            boolean enabled) {

        private Snapshots(@Nonnull PhysicsChunkProfilingResource.Snapshot cumulative,
            @Nonnull PhysicsChunkProfilingResource.Snapshot latest,
            @Nonnull PhysicsChunkProfilingResource.Snapshot worst,
            boolean enabled) {
            this(new SnapshotView(cumulative), new SnapshotView(latest), new SnapshotView(worst),
                enabled);
        }
    }

    public static class SnapshotView {

        @Nonnull
        private final PhysicsChunkProfilingResource.Snapshot snapshot;

        SnapshotView(@Nonnull PhysicsChunkProfilingResource.Snapshot snapshot) {
            this.snapshot = snapshot;
        }

        @Nonnull
        final PhysicsChunkProfilingResource.Snapshot rawSnapshot() {
            return snapshot;
        }

        public int getTickSamples() {
            return snapshot.getTickSamples();
        }

        public int getPlayerStreamingTargets() {
            return snapshot.getPlayerStreamingTargets();
        }

        public int getBodyStreamingCandidates() {
            return snapshot.getBodyStreamingCandidates();
        }

        public int getBodySpatialIndexCandidates() {
            return snapshot.getBodySpatialIndexCandidates();
        }

        public int getBodyStreamingTargets() {
            return snapshot.getBodyStreamingTargets();
        }

        public int getBodyTargetDedupeSkips() {
            return snapshot.getBodyTargetDedupeSkips();
        }

        public int getBodyTargetCacheHits() {
            return snapshot.getBodyTargetCacheHits();
        }

        public int getBodyTargetFirstSeen() {
            return snapshot.getBodyTargetFirstSeen();
        }

        public int getBodyTargetBoundsChanged() {
            return snapshot.getBodyTargetBoundsChanged();
        }

        public int getBodyTargetActiveRefreshes() {
            return snapshot.getBodyTargetActiveRefreshes();
        }

        public int getBodyTargetSleepingRefreshes() {
            return snapshot.getBodyTargetSleepingRefreshes();
        }

        public int getBodyTargetActiveStableSkips() {
            return snapshot.getBodyTargetActiveStableSkips();
        }

        public int getBodyTargetSleepingStableSkips() {
            return snapshot.getBodyTargetSleepingStableSkips();
        }

        public int getBodyTargetsPruned() {
            return snapshot.getBodyTargetsPruned();
        }

        public int getPlayerSectionTargets() {
            return snapshot.getPlayerSectionTargets();
        }

        public int getBodySectionTargets() {
            return snapshot.getBodySectionTargets();
        }

        public int getStreamingSpaces() {
            return snapshot.getStreamingSpaces();
        }

        public int getCollisionApplyQueued() {
            return snapshot.getCollisionApplyQueued();
        }

        public int getCollisionApplySkippedPending() {
            return snapshot.getCollisionApplySkippedPending();
        }

        public int getEnsureCalls() {
            return snapshot.getEnsureCalls();
        }

        public int getSectionRequests() {
            return snapshot.getSectionRequests();
        }

        public int getSectionCacheHits() {
            return snapshot.getSectionCacheHits();
        }

        public int getMissingChunks() {
            return snapshot.getMissingChunks();
        }

        public int getMissingBlockChunks() {
            return snapshot.getMissingBlockChunks();
        }

        public int getMissingBlockSections() {
            return snapshot.getMissingBlockSections();
        }

        public int getMissingReasonUnknown() {
            return snapshot.getMissingReasonUnknown();
        }

        public int getMissingBackoffSkips() {
            return snapshot.getMissingBackoffSkips();
        }

        public int getMissingBlockChunkBackoffSkips() {
            return snapshot.getMissingBlockChunkBackoffSkips();
        }

        public int getMissingBlockSectionBackoffSkips() {
            return snapshot.getMissingBlockSectionBackoffSkips();
        }

        public int getMissingInsideRetainedEnvelope() {
            return snapshot.getMissingInsideRetainedEnvelope();
        }

        public int getMissingOutsideRetainedEnvelope() {
            return snapshot.getMissingOutsideRetainedEnvelope();
        }

        public int getMissingUnconfiguredRetainedEnvelope() {
            return snapshot.getMissingUnconfiguredRetainedEnvelope();
        }

        public int getSectionsBuilt() {
            return snapshot.getSectionsBuilt();
        }

        public int getSectionsRebuilt() {
            return snapshot.getSectionsRebuilt();
        }

        public int getVoxelBodies() {
            return snapshot.getVoxelBodies();
        }

        public int getColliderBodiesAdded() {
            return snapshot.getColliderBodiesAdded();
        }

        public int getBodiesRemovedFromRebuild() {
            return snapshot.getBodiesRemovedFromRebuild();
        }

        public int getBodiesRemovedFromUnloadedPrune() {
            return snapshot.getBodiesRemovedFromUnloadedPrune();
        }

        public int getBodiesRemovedFromTtlPrune() {
            return snapshot.getBodiesRemovedFromTtlPrune();
        }

        public int getSectionsRemovedFromUnloadedPrune() {
            return snapshot.getSectionsRemovedFromUnloadedPrune();
        }

        public int getSectionsRemovedFromTtlPrune() {
            return snapshot.getSectionsRemovedFromTtlPrune();
        }

        public int getDuplicateSkips() {
            return snapshot.getDuplicateSkips();
        }

        public int getScannedBlocks() {
            return snapshot.getScannedBlocks();
        }

        public int getSolidBlocks() {
            return snapshot.getSolidBlocks();
        }

        public int getCulledInteriorBlocks() {
            return snapshot.getCulledInteriorBlocks();
        }

        public int getFullCubeRuns() {
            return snapshot.getFullCubeRuns();
        }

        public int getDetailBoxes() {
            return snapshot.getDetailBoxes();
        }

        public int getUniqueMissingSections() {
            return snapshot.getUniqueMissingSections();
        }

        public long getTickNanos() {
            return snapshot.getTickNanos();
        }

        public long getEnsureAroundNanos() {
            return snapshot.getEnsureAroundNanos();
        }

        public long getEnsureSectionNanos() {
            return snapshot.getEnsureSectionNanos();
        }

        public long getPruneUnloadedNanos() {
            return snapshot.getPruneUnloadedNanos();
        }

        public long getPruneUnusedNanos() {
            return snapshot.getPruneUnusedNanos();
        }
    }

    public record MissingSectionSampleView(int chunkX,
                                           int sectionY,
                                           int chunkZ,
                                           @Nonnull String reason,
                                           @Nonnull String retainedEnvelopeStatus,
                                           @Nonnull String targetType,
                                           @Nullable UUID bodyUuid,
                                           @Nullable String snapshotPosition,
                                           @Nullable String livePosition) {
    }
}
