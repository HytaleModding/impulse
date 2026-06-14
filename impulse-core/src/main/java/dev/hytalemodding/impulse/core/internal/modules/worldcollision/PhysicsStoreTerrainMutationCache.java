package dev.hytalemodding.impulse.core.internal.modules.worldcollision;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import dev.hytalemodding.impulse.core.internal.modules.worldcollision.profiling.WorldCollisionProfilingResource.MissingSectionReason;
import dev.hytalemodding.impulse.core.internal.modules.worldcollision.profiling.WorldCollisionProfilingResource.Snapshot;
import dev.hytalemodding.impulse.core.internal.modules.worldcollision.profiling.WorldCollisionProfilingResource.StreamingTargetDiagnostic;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsTerrainMutationQueueResource;
import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.util.Iterator;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;

/**
 * Section cache for PhysicsStore terrain mutation producers.
 */
public final class PhysicsStoreTerrainMutationCache {

    private static final int ACTIVE_BODY_STREAMING_INTERVAL_TICKS = 4;
    private static final int SLEEPING_BODY_STREAMING_INTERVAL_TICKS = 20;
    private static final int MISSING_BLOCK_CHUNK_RETRY_TICKS = 10;
    private static final int MISSING_BLOCK_SECTION_RETRY_TICKS = 5;
    private static final long BODY_TARGET_REFRESH_PENDING = Long.MIN_VALUE;

    @Nonnull
    private final Object2ObjectMap<UUID, SpaceCollisionCache> spaces =
        new Object2ObjectOpenHashMap<>();
    @Nonnull
    private final ShapeTemplateCache shapeTemplates = new ShapeTemplateCache();
    @Nonnull
    private final SectionColliderBuilder sectionBuilder = new SectionColliderBuilder(shapeTemplates);

    @Nonnull
    public synchronized WorldVoxelCollisionCache.BuildStats ensureAround(@Nonnull World world,
        @Nonnull UUID spaceUuid,
        @Nonnull PhysicsTerrainMutationQueueResource queue,
        @Nonnull Vector3d center,
        int radius,
        long tick,
        @Nullable Snapshot profiling,
        @Nullable LongSet visitedSections,
        @Nullable StreamingTargetDiagnostic targetDiagnostic,
        @Nonnull WorldCollisionBuildOptions buildOptions) {
        long start = profiling != null ? System.nanoTime() : 0L;
        if (profiling != null) {
            profiling.incrementEnsureCalls();
        }

        int minX = (int) Math.floor(center.x) - radius;
        int maxX = (int) Math.floor(center.x) + radius;
        int minY = Math.max(0, (int) Math.floor(center.y) - radius);
        int maxY = Math.min(ChunkUtil.HEIGHT_MINUS_1, (int) Math.floor(center.y) + radius);
        int minZ = (int) Math.floor(center.z) - radius;
        int maxZ = (int) Math.floor(center.z) + radius;

        int minChunkX = ChunkUtil.chunkCoordinate(minX);
        int maxChunkX = ChunkUtil.chunkCoordinate(maxX);
        int minSectionY = ChunkUtil.indexSection(minY);
        int maxSectionY = ChunkUtil.indexSection(maxY);
        int minChunkZ = ChunkUtil.chunkCoordinate(minZ);
        int maxChunkZ = ChunkUtil.chunkCoordinate(maxZ);

        WorldVoxelCollisionCache.BuildStats total = WorldVoxelCollisionCache.BuildStats.empty();
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int sectionY = minSectionY; sectionY <= maxSectionY; sectionY++) {
                for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                    long key = packSectionKey(chunkX, sectionY, chunkZ);
                    if (visitedSections != null && !visitedSections.add(key)) {
                        if (profiling != null) {
                            profiling.incrementDuplicateSkips();
                        }
                        continue;
                    }
                    total = total.plus(ensureSection(world,
                        spaceUuid,
                        queue,
                        chunkX,
                        sectionY,
                        chunkZ,
                        tick,
                        profiling,
                        targetDiagnostic,
                        buildOptions));
                }
            }
        }
        if (profiling != null) {
            profiling.addEnsureAroundNanos(System.nanoTime() - start);
        }
        return total;
    }

    public synchronized int pruneUnused(@Nonnull UUID spaceUuid,
        @Nonnull PhysicsTerrainMutationQueueResource queue,
        long currentTick,
        int ttlTicks,
        @Nullable Snapshot profiling) {
        long start = profiling != null ? System.nanoTime() : 0L;
        SpaceCollisionCache cache = spaces.get(spaceUuid);
        if (cache == null) {
            return 0;
        }

        int removedBodies = 0;
        int removedSections = 0;
        Iterator<Long2ObjectMap.Entry<CachedSection>> iterator =
            cache.sections.long2ObjectEntrySet().iterator();
        while (iterator.hasNext()) {
            CachedSection section = iterator.next().getValue();
            if (currentTick - section.lastUsedTick <= ttlTicks) {
                continue;
            }
            removedBodies += removeSection(spaceUuid, queue, section);
            removedSections++;
            iterator.remove();
        }
        pruneExpiredMissingBackoffs(cache, currentTick);
        if (cache.isEmpty()) {
            spaces.remove(spaceUuid);
        }
        if (profiling != null) {
            profiling.addTtlPrune(removedSections, removedBodies);
            profiling.addPruneUnusedNanos(System.nanoTime() - start);
        }
        return removedBodies;
    }

    public synchronized int pruneUnloaded(@Nonnull World world,
        @Nonnull UUID spaceUuid,
        @Nonnull PhysicsTerrainMutationQueueResource queue,
        @Nullable Snapshot profiling) {
        long start = profiling != null ? System.nanoTime() : 0L;
        SpaceCollisionCache cache = spaces.get(spaceUuid);
        if (cache == null) {
            return 0;
        }

        int removedBodies = 0;
        int removedSections = 0;
        Iterator<Long2ObjectMap.Entry<CachedSection>> iterator =
            cache.sections.long2ObjectEntrySet().iterator();
        while (iterator.hasNext()) {
            CachedSection section = iterator.next().getValue();
            if (blockChunk(world, section.chunkX, section.chunkZ) != null) {
                continue;
            }
            removedBodies += removeSection(spaceUuid, queue, section);
            removedSections++;
            iterator.remove();
        }
        if (cache.isEmpty()) {
            spaces.remove(spaceUuid);
        }
        if (profiling != null) {
            profiling.addUnloadedPrune(removedSections, removedBodies);
            profiling.addPruneUnloadedNanos(System.nanoTime() - start);
        }
        return removedBodies;
    }

    public synchronized void retainSpaces(@Nonnull Set<UUID> retainedSpaces,
        @Nonnull PhysicsTerrainMutationQueueResource queue) {
        Iterator<Object2ObjectMap.Entry<UUID, SpaceCollisionCache>> iterator =
            spaces.object2ObjectEntrySet().iterator();
        while (iterator.hasNext()) {
            Object2ObjectMap.Entry<UUID, SpaceCollisionCache> entry = iterator.next();
            if (retainedSpaces.contains(entry.getKey())) {
                continue;
            }
            removeAllSections(entry.getKey(), queue, entry.getValue());
            iterator.remove();
        }
    }

    @Nonnull
    public synchronized TargetRefreshDecision shouldRefreshBodyTarget(@Nonnull UUID spaceUuid,
        @Nonnull UUID bodyUuid,
        @Nonnull WorldCollisionStreamingBounds bounds,
        boolean sleeping,
        long currentTick,
        int ttlTicks,
        @Nullable Snapshot profiling) {
        SpaceCollisionCache cache = spaces.computeIfAbsent(spaceUuid, _ -> new SpaceCollisionCache());
        CachedBodyStreamingTarget target = cache.bodyTargets.get(bodyUuid);
        if (target == null) {
            cache.bodyTargets.put(bodyUuid, new CachedBodyStreamingTarget(bounds,
                sleeping,
                currentTick,
                BODY_TARGET_REFRESH_PENDING));
            if (profiling != null) {
                profiling.incrementBodyTargetFirstSeen();
            }
            return TargetRefreshDecision.refresh(TargetRefreshReason.FIRST_SEEN);
        }

        target.lastSeenTick = currentTick;
        target.sleeping = sleeping;
        if (profiling != null) {
            profiling.incrementBodyTargetCacheHits();
        }

        if (!target.bounds.equals(bounds)) {
            target.bounds = bounds;
            if (profiling != null) {
                profiling.incrementBodyTargetBoundsChanged();
            }
            return TargetRefreshDecision.refresh(TargetRefreshReason.BOUNDS_CHANGED);
        }
        if (target.lastRefreshTick == BODY_TARGET_REFRESH_PENDING) {
            return TargetRefreshDecision.refresh(TargetRefreshReason.PENDING_APPLY);
        }

        int interval = sleeping ? sleepingBodyStreamingInterval(ttlTicks)
            : ACTIVE_BODY_STREAMING_INTERVAL_TICKS;
        if (currentTick == 1L || currentTick - target.lastRefreshTick >= interval) {
            if (profiling != null) {
                if (sleeping) {
                    profiling.incrementBodyTargetSleepingRefreshes();
                } else {
                    profiling.incrementBodyTargetActiveRefreshes();
                }
            }
            return TargetRefreshDecision.refresh(sleeping
                ? TargetRefreshReason.SLEEPING_INTERVAL
                : TargetRefreshReason.ACTIVE_INTERVAL);
        }

        if (profiling != null) {
            if (sleeping) {
                profiling.incrementBodyTargetSleepingStableSkips();
            } else {
                profiling.incrementBodyTargetActiveStableSkips();
            }
        }
        return TargetRefreshDecision.skip();
    }

    public synchronized void recordBodyTargetRefresh(@Nonnull UUID spaceUuid,
        @Nonnull UUID bodyUuid,
        @Nonnull WorldCollisionStreamingBounds bounds,
        boolean sleeping,
        long currentTick) {
        SpaceCollisionCache cache = spaces.computeIfAbsent(spaceUuid, _ -> new SpaceCollisionCache());
        CachedBodyStreamingTarget target = cache.bodyTargets.get(bodyUuid);
        if (target == null) {
            cache.bodyTargets.put(bodyUuid, new CachedBodyStreamingTarget(bounds,
                sleeping,
                currentTick,
                currentTick));
            return;
        }
        target.bounds = bounds;
        target.sleeping = sleeping;
        target.lastSeenTick = currentTick;
        target.lastRefreshTick = currentTick;
    }

    public synchronized int pruneBodyStreamingTargets(@Nonnull UUID spaceUuid,
        long currentTick,
        int ttlTicks,
        @Nullable Snapshot profiling) {
        SpaceCollisionCache cache = spaces.get(spaceUuid);
        if (cache == null) {
            return 0;
        }
        long maxAge = Math.max(1L, ttlTicks) * 2L;
        int removed = 0;
        Iterator<Object2ObjectMap.Entry<UUID, CachedBodyStreamingTarget>> iterator =
            cache.bodyTargets.object2ObjectEntrySet().iterator();
        while (iterator.hasNext()) {
            CachedBodyStreamingTarget target = iterator.next().getValue();
            if (currentTick - target.lastSeenTick <= maxAge) {
                continue;
            }
            iterator.remove();
            removed++;
        }
        pruneExpiredMissingBackoffs(cache, currentTick);
        if (cache.isEmpty()) {
            spaces.remove(spaceUuid);
        }
        if (removed > 0 && profiling != null) {
            profiling.addBodyTargetsPruned(removed);
        }
        return removed;
    }

    @Nonnull
    private WorldVoxelCollisionCache.BuildStats ensureSection(@Nonnull World world,
        @Nonnull UUID spaceUuid,
        @Nonnull PhysicsTerrainMutationQueueResource queue,
        int chunkX,
        int sectionY,
        int chunkZ,
        long tick,
        @Nullable Snapshot profiling,
        @Nullable StreamingTargetDiagnostic targetDiagnostic,
        @Nonnull WorldCollisionBuildOptions buildOptions) {
        long start = profiling != null ? System.nanoTime() : 0L;
        if (profiling != null) {
            profiling.incrementSectionRequests();
        }

        SpaceCollisionCache cache = spaces.computeIfAbsent(spaceUuid, _ -> new SpaceCollisionCache());
        long chunkKey = ChunkUtil.indexChunk(chunkX, chunkZ);
        long sectionKey = packSectionKey(chunkX, sectionY, chunkZ);
        if (isBackedOff(cache.missingBlockChunkBackoffs, chunkKey, tick)) {
            recordMissingBackoff(profiling,
                MissingSectionReason.BLOCK_CHUNK,
                chunkX,
                sectionY,
                chunkZ,
                targetDiagnostic,
                start);
            return WorldVoxelCollisionCache.BuildStats.empty();
        }
        if (blockChunk(world, chunkX, chunkZ) == null) {
            cache.missingBlockChunkBackoffs.put(chunkKey, tick + MISSING_BLOCK_CHUNK_RETRY_TICKS);
            recordMissing(profiling,
                MissingSectionReason.BLOCK_CHUNK,
                chunkX,
                sectionY,
                chunkZ,
                targetDiagnostic,
                start);
            return WorldVoxelCollisionCache.BuildStats.empty();
        }
        cache.missingBlockChunkBackoffs.remove(chunkKey);

        if (isBackedOff(cache.missingBlockSectionBackoffs, sectionKey, tick)) {
            recordMissingBackoff(profiling,
                MissingSectionReason.BLOCK_SECTION,
                chunkX,
                sectionY,
                chunkZ,
                targetDiagnostic,
                start);
            return WorldVoxelCollisionCache.BuildStats.empty();
        }
        BlockSection section = ChunkSectionAccess.blockSection(world, chunkX, sectionY, chunkZ);
        if (section == null) {
            cache.missingBlockSectionBackoffs.put(sectionKey,
                tick + MISSING_BLOCK_SECTION_RETRY_TICKS);
            recordMissing(profiling,
                MissingSectionReason.BLOCK_SECTION,
                chunkX,
                sectionY,
                chunkZ,
                targetDiagnostic,
                start);
            return WorldVoxelCollisionCache.BuildStats.empty();
        }
        cache.missingBlockSectionBackoffs.remove(sectionKey);

        long neighborhoodSignature = sectionBuilder.neighborhoodSignature(world,
            section,
            chunkX,
            sectionY,
            chunkZ);
        CachedSection cached = cache.sections.get(sectionKey);
        if (cached != null
            && cached.neighborhoodSignature == neighborhoodSignature
            && cached.buildOptions.equals(buildOptions)) {
            cached.lastUsedTick = tick;
            if (profiling != null) {
                profiling.incrementSectionCacheHits();
                profiling.addEnsureSectionNanos(System.nanoTime() - start);
            }
            return WorldVoxelCollisionCache.BuildStats.empty();
        }

        SectionCollisionGeometry geometry = sectionBuilder.build(world,
            section,
            chunkX,
            sectionY,
            chunkZ);
        CachedSection built = new CachedSection(chunkX,
            sectionY,
            chunkZ,
            tick,
            neighborhoodSignature,
            buildOptions,
            bodyCount(geometry, buildOptions),
            buildOptions.nativeVoxelTerrainEnabled() && geometry.hasFullCubeVoxels());
        int removedBodies = cached != null ? removeSection(spaceUuid, queue, cached) : 0;
        if (built.bodyCount > 0) {
            queue.enqueue(PhysicsStoreTerrainMutations.upsert(spaceUuid,
                chunkX,
                sectionY,
                chunkZ,
                neighborhoodSignature,
                geometry,
                buildOptions));
        }
        cache.sections.put(sectionKey, built);
        WorldVoxelCollisionCache.BuildStats stats = new WorldVoxelCollisionCache.BuildStats(
            geometry.scannedBlocks(),
            geometry.solidBlocks(),
            geometry.culledInteriorBlocks(),
            geometry.mergedFullCubeBoxes().size(),
            geometry.detailBoxCount(),
            built.bodyCount,
            removedBodies,
            cached == null ? 1 : 0,
            cached == null ? 0 : 1,
            built.voxelTerrain ? 1 : 0);
        if (profiling != null) {
            profiling.addBuildStats(stats);
            profiling.addEnsureSectionNanos(System.nanoTime() - start);
        }
        return stats;
    }

    private static int bodyCount(@Nonnull SectionCollisionGeometry geometry,
        @Nonnull WorldCollisionBuildOptions buildOptions) {
        int fullCubeBodyCount = buildOptions.nativeVoxelTerrainEnabled()
            && geometry.hasFullCubeVoxels()
            ? 1
            : geometry.mergedFullCubeBoxes().size();
        return fullCubeBodyCount + geometry.detailBoxes().size();
    }

    private static int removeSection(@Nonnull UUID spaceUuid,
        @Nonnull PhysicsTerrainMutationQueueResource queue,
        @Nonnull CachedSection section) {
        if (section.bodyCount <= 0) {
            return 0;
        }
        queue.enqueue(PhysicsStoreTerrainMutations.remove(spaceUuid,
            section.chunkX,
            section.sectionY,
            section.chunkZ));
        return section.bodyCount;
    }

    private static void removeAllSections(@Nonnull UUID spaceUuid,
        @Nonnull PhysicsTerrainMutationQueueResource queue,
        @Nonnull SpaceCollisionCache cache) {
        for (CachedSection section : cache.sections.values()) {
            removeSection(spaceUuid, queue, section);
        }
        cache.sections.clear();
    }

    private static void recordMissingBackoff(@Nullable Snapshot profiling,
        @Nonnull MissingSectionReason reason,
        int chunkX,
        int sectionY,
        int chunkZ,
        @Nullable StreamingTargetDiagnostic targetDiagnostic,
        long start) {
        if (profiling == null) {
            return;
        }
        profiling.incrementMissingBackoffSkip(reason);
        recordMissing(profiling, reason, chunkX, sectionY, chunkZ, targetDiagnostic, start);
    }

    private static void recordMissing(@Nullable Snapshot profiling,
        @Nonnull MissingSectionReason reason,
        int chunkX,
        int sectionY,
        int chunkZ,
        @Nullable StreamingTargetDiagnostic targetDiagnostic,
        long start) {
        if (profiling == null) {
            return;
        }
        profiling.recordMissingSection(reason, chunkX, sectionY, chunkZ, targetDiagnostic);
        profiling.addEnsureSectionNanos(System.nanoTime() - start);
    }

    private static boolean isBackedOff(@Nonnull Long2LongMap backoffs, long key, long tick) {
        return backoffs.containsKey(key) && backoffs.get(key) > tick;
    }

    private static void pruneExpiredMissingBackoffs(@Nonnull SpaceCollisionCache cache, long tick) {
        cache.missingBlockChunkBackoffs.long2LongEntrySet().removeIf(entry -> entry.getLongValue() <= tick);
        cache.missingBlockSectionBackoffs.long2LongEntrySet().removeIf(entry -> entry.getLongValue() <= tick);
    }

    private static int sleepingBodyStreamingInterval(int ttlTicks) {
        int ttlBound = Math.max(1, ttlTicks / 4);
        return Math.max(ACTIVE_BODY_STREAMING_INTERVAL_TICKS,
            Math.min(SLEEPING_BODY_STREAMING_INTERVAL_TICKS, ttlBound));
    }

    private static long packSectionKey(int chunkX, int sectionY, int chunkZ) {
        long x = ((long) chunkX & 0x3FFFFFL) << 42;
        long y = ((long) sectionY & 0x3FFL) << 32;
        long z = (long) chunkZ & 0xFFFFFFFFL;
        return x | y | z;
    }

    @Nullable
    private static BlockChunk blockChunk(@Nonnull World world, int chunkX, int chunkZ) {
        Ref<ChunkStore> chunkRef = world.getChunkStore()
            .getChunkReference(ChunkUtil.indexChunk(chunkX, chunkZ));
        if (chunkRef == null || !chunkRef.isValid()) {
            return null;
        }
        Store<ChunkStore> store = world.getChunkStore().getStore();
        return store.getComponentConcurrent(chunkRef, BlockChunk.getComponentType());
    }

    private static final class SpaceCollisionCache {

        private final Long2ObjectMap<CachedSection> sections = new Long2ObjectOpenHashMap<>();
        private final Long2LongMap missingBlockChunkBackoffs = new Long2LongOpenHashMap();
        private final Long2LongMap missingBlockSectionBackoffs = new Long2LongOpenHashMap();
        private final Object2ObjectMap<UUID, CachedBodyStreamingTarget> bodyTargets =
            new Object2ObjectOpenHashMap<>();

        private boolean isEmpty() {
            return sections.isEmpty()
                && missingBlockChunkBackoffs.isEmpty()
                && missingBlockSectionBackoffs.isEmpty()
                && bodyTargets.isEmpty();
        }
    }

    private static final class CachedSection {

        private final int chunkX;
        private final int sectionY;
        private final int chunkZ;
        private final long neighborhoodSignature;
        @Nonnull
        private final WorldCollisionBuildOptions buildOptions;
        private final int bodyCount;
        private final boolean voxelTerrain;
        private long lastUsedTick;

        private CachedSection(int chunkX,
            int sectionY,
            int chunkZ,
            long lastUsedTick,
            long neighborhoodSignature,
            @Nonnull WorldCollisionBuildOptions buildOptions,
            int bodyCount,
            boolean voxelTerrain) {
            this.chunkX = chunkX;
            this.sectionY = sectionY;
            this.chunkZ = chunkZ;
            this.lastUsedTick = lastUsedTick;
            this.neighborhoodSignature = neighborhoodSignature;
            this.buildOptions = buildOptions;
            this.bodyCount = bodyCount;
            this.voxelTerrain = voxelTerrain;
        }
    }

    private static final class CachedBodyStreamingTarget {

        @Nonnull
        private WorldCollisionStreamingBounds bounds;
        private boolean sleeping;
        private long lastSeenTick;
        private long lastRefreshTick;

        private CachedBodyStreamingTarget(@Nonnull WorldCollisionStreamingBounds bounds,
            boolean sleeping,
            long lastSeenTick,
            long lastRefreshTick) {
            this.bounds = bounds;
            this.sleeping = sleeping;
            this.lastSeenTick = lastSeenTick;
            this.lastRefreshTick = lastRefreshTick;
        }
    }

    public enum TargetRefreshReason {
        FIRST_SEEN,
        BOUNDS_CHANGED,
        PENDING_APPLY,
        ACTIVE_INTERVAL,
        SLEEPING_INTERVAL,
        STABLE_SKIP
    }

    public record TargetRefreshDecision(boolean refresh, @Nonnull TargetRefreshReason reason) {

        @Nonnull
        private static TargetRefreshDecision refresh(@Nonnull TargetRefreshReason reason) {
            return new TargetRefreshDecision(true, reason);
        }

        @Nonnull
        private static TargetRefreshDecision skip() {
            return new TargetRefreshDecision(false, TargetRefreshReason.STABLE_SKIP);
        }
    }
}
