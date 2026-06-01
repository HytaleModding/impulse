package dev.hytalemodding.impulse.core.internal.voxel;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.PhysicsCollisionFilters;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.api.runtime.BackendRuntimeCodes;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsSpaceBinding;
import dev.hytalemodding.impulse.core.internal.resources.profiling.WorldCollisionProfilingResource.MissingSectionReason;
import dev.hytalemodding.impulse.core.internal.resources.profiling.WorldCollisionProfilingResource.Snapshot;
import dev.hytalemodding.impulse.core.internal.resources.profiling.WorldCollisionProfilingResource.StreamingTargetDiagnostic;
import dev.hytalemodding.impulse.core.internal.voxel.SectionCollisionGeometry.BoxCollider;
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;

/**
 * Section-keyed cache that generates static physics collision from Hytale world blocks.
 *
 * <p>The cache is split per physics space so spaces can choose different world-collision
 * policies. Each cached section is rebuilt when Hytale's section change counter changes,
 * and removed when it falls out of the streaming radius or when its chunk unloads.</p>
 */
public final class WorldVoxelCollisionCache {

    private static final int ACTIVE_BODY_STREAMING_INTERVAL_TICKS = 4;
    private static final int SLEEPING_BODY_STREAMING_INTERVAL_TICKS = 20;
    private static final int MISSING_BLOCK_CHUNK_RETRY_TICKS = 10;
    private static final int MISSING_BLOCK_SECTION_RETRY_TICKS = 5;

    private final Int2ObjectMap<SpaceCollisionCache> spaces = new Int2ObjectOpenHashMap<>();
    private final ShapeTemplateCache shapeTemplates = new ShapeTemplateCache();
    private final SectionColliderBuilder sectionBuilder = new SectionColliderBuilder(shapeTemplates);
    private final AtomicBoolean streamingApplyPending = new AtomicBoolean();

    public void copyFrom(@Nonnull WorldVoxelCollisionCache other) {
        spaces.clear();
        for (Int2ObjectMap.Entry<SpaceCollisionCache> entry : other.spaces.int2ObjectEntrySet()) {
            spaces.put(entry.getIntKey(), new SpaceCollisionCache(entry.getValue()));
        }
        streamingApplyPending.set(false);
    }

    public boolean tryBeginStreamingApply() {
        return streamingApplyPending.compareAndSet(false, true);
    }

    public void finishStreamingApply() {
        streamingApplyPending.set(false);
    }

    public boolean isStreamingApplyPending() {
        return streamingApplyPending.get();
    }

    @Nonnull
    public SectionAccessCache newSectionAccessCache() {
        return new SectionAccessCache();
    }

    @Nonnull
    public TargetRefreshDecision shouldRefreshBodyTarget(@Nonnull SpaceId spaceId,
        @Nonnull RigidBodyKey bodyKey,
        @Nonnull WorldCollisionStreamingBounds bounds,
        boolean sleeping,
        long currentTick,
        int ttlTicks,
        @Nullable Snapshot profiling) {
        SpaceCollisionCache cache = spaces.computeIfAbsent(spaceId.value(), ignored -> new SpaceCollisionCache());
        CachedBodyStreamingTarget target = cache.bodyTargets.get(bodyKey);
        if (target == null) {
            cache.bodyTargets.put(bodyKey, new CachedBodyStreamingTarget(bounds,
                sleeping,
                currentTick,
                currentTick));
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
            target.lastRefreshTick = currentTick;
            if (profiling != null) {
                profiling.incrementBodyTargetBoundsChanged();
            }
            return TargetRefreshDecision.refresh(TargetRefreshReason.BOUNDS_CHANGED);
        }

        int interval = sleeping ? sleepingBodyStreamingInterval(ttlTicks) : ACTIVE_BODY_STREAMING_INTERVAL_TICKS;
        if (currentTick == 1L || currentTick - target.lastRefreshTick >= interval) {
            target.lastRefreshTick = currentTick;
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

    public int pruneBodyStreamingTargets(@Nonnull SpaceId spaceId,
        long currentTick,
        int ttlTicks,
        @Nullable Snapshot profiling) {
        SpaceCollisionCache cache = spaces.get(spaceId.value());
        if (cache == null) {
            return 0;
        }

        long maxAge = Math.max(1L, ttlTicks) * 2L;
        int removed = 0;
        Iterator<Object2ObjectMap.Entry<RigidBodyKey, CachedBodyStreamingTarget>> iterator =
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
            spaces.remove(spaceId.value());
        }
        if (removed > 0 && profiling != null) {
            profiling.addBodyTargetsPruned(removed);
        }
        return removed;
    }

    /**
     * Wipes cached sections for the space, then rebuilds everything in the given radius.
     */
    @Nonnull
    public BuildStats rebuildAround(@Nonnull World world,
        @Nonnull PhysicsSpaceBinding space,
        @Nonnull Vector3d center,
        int radius) {
        int removed = clear(space);
        BuildStats stats = ensureAround(world, space, center, radius, 0L);
        return stats.withRemovedBodies(stats.removedBodies() + removed);
    }

    /**
     * Ensures all chunk sections within the block radius around {@code center} are cached.
     */
    @Nonnull
    public BuildStats ensureAround(@Nonnull World world,
        @Nonnull PhysicsSpaceBinding space,
        @Nonnull Vector3d center,
        int radius,
        long tick) {
        return ensureAround(world, space, center, radius, tick, null, null);
    }

    /**
     * Ensures all chunk sections within the block radius around {@code center} are cached.
     */
    @Nonnull
    public BuildStats ensureAround(@Nonnull World world,
        @Nonnull PhysicsSpaceBinding space,
        @Nonnull Vector3d center,
        int radius,
        long tick,
        @Nullable Snapshot profiling) {
        return ensureAround(world, space, center, radius, tick, profiling, null);
    }

    /**
     * Ensures all chunk sections within the block radius around {@code center} are cached.
     */
    @Nonnull
    public BuildStats ensureAround(@Nonnull World world,
        @Nonnull PhysicsSpaceBinding space,
        @Nonnull Vector3d center,
        int radius,
        long tick,
        @Nullable Snapshot profiling,
        @Nullable LongSet visitedSections) {
        return ensureAround(world, space, center, radius, tick, profiling, visitedSections, null);
    }

    /**
     * Ensures all chunk sections within the block radius around {@code center} are cached.
     */
    @Nonnull
    public BuildStats ensureAround(@Nonnull World world,
        @Nonnull PhysicsSpaceBinding space,
        @Nonnull Vector3d center,
        int radius,
        long tick,
        @Nullable Snapshot profiling,
        @Nullable LongSet visitedSections,
        @Nullable StreamingTargetDiagnostic targetDiagnostic) {
        return ensureAround(world,
            space,
            center,
            radius,
            tick,
            profiling,
            visitedSections,
            targetDiagnostic,
            null);
    }

    /**
     * Ensures all chunk sections within the block radius around {@code center} are cached.
     */
    @Nonnull
    public BuildStats ensureAround(@Nonnull World world,
        @Nonnull PhysicsSpaceBinding space,
        @Nonnull Vector3d center,
        int radius,
        long tick,
        @Nullable Snapshot profiling,
        @Nullable LongSet visitedSections,
        @Nullable StreamingTargetDiagnostic targetDiagnostic,
        @Nullable SectionAccessCache accessCache) {
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

        BuildStats total = BuildStats.empty();
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int sectionY = minSectionY; sectionY <= maxSectionY; sectionY++) {
                for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                    long key = packSectionKey(chunkX, sectionY, chunkZ);
                    if (visitedSections != null) {
                        if (!visitedSections.add(key)) {
                            if (profiling != null) {
                                profiling.incrementDuplicateSkips();
                            }
                            continue;
                        }
                    }
                    total = total.plus(ensureSection(
                        world,
                        space,
                        chunkX,
                        sectionY,
                        chunkZ,
                        tick,
                        profiling,
                        targetDiagnostic,
                        accessCache));
                }
            }
        }
        if (profiling != null) {
            profiling.addEnsureAroundNanos(System.nanoTime() - start);
        }
        return total;
    }

    /**
     * Refreshes the TTL of already-cached sections around a sleeping body without building
     * missing collision or touching chunk storage.
     */
    public int touchAround(@Nonnull SpaceId spaceId,
        @Nonnull Vector3d center,
        int radius,
        long tick) {
        SpaceCollisionCache cache = spaces.get(spaceId.value());
        if (cache == null) {
            return 0;
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

        int touched = 0;
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int sectionY = minSectionY; sectionY <= maxSectionY; sectionY++) {
                for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                    CachedSection section = cache.section(chunkX, sectionY, chunkZ);
                    if (section == null || section.lastUsedTick >= tick) {
                        continue;
                    }
                    section.lastUsedTick = tick;
                    touched++;
                }
            }
        }
        return touched;
    }

    /**
     * Removes sections whose last use is older than the configured TTL.
     */
    public int pruneUnused(@Nonnull SpaceId spaceId,
        @Nonnull PhysicsSpaceBinding space,
        long currentTick,
        int ttlTicks) {
        return pruneUnused(spaceId, space, currentTick, ttlTicks, null);
    }

    /**
     * Removes sections whose last use is older than the configured TTL.
     */
    public int pruneUnused(@Nonnull SpaceId spaceId,
        @Nonnull PhysicsSpaceBinding space,
        long currentTick,
        int ttlTicks,
        @Nullable Snapshot profiling) {
        long start = profiling != null ? System.nanoTime() : 0L;
        SpaceCollisionCache cache = spaces.get(spaceId.value());
        if (cache == null) {
            return 0;
        }

        int removed = 0;
        int removedSections = 0;
        Iterator<Long2ObjectMap.Entry<CachedSection>> iterator = cache.sections.long2ObjectEntrySet().iterator();
        while (iterator.hasNext()) {
            Long2ObjectMap.Entry<CachedSection> entry = iterator.next();
            CachedSection section = entry.getValue();
            if (currentTick - section.lastUsedTick <= ttlTicks) {
                continue;
            }

            removed += section.removeFrom(space);
            removedSections++;
            iterator.remove();
        }
        pruneExpiredMissingBackoffs(cache, currentTick);
        if (cache.isEmpty()) {
            spaces.remove(spaceId.value());
        }
        if (profiling != null) {
            profiling.addTtlPrune(removedSections, removed);
            profiling.addPruneUnusedNanos(System.nanoTime() - start);
        }
        return removed;
    }

    /**
     * Removes cached sections whose source chunk is no longer loaded.
     */
    public int pruneUnloaded(@Nonnull World world,
        @Nonnull SpaceId spaceId,
        @Nonnull PhysicsSpaceBinding space) {
        return pruneUnloaded(world, spaceId, space, null);
    }

    /**
     * Removes cached sections whose source chunk is no longer loaded.
     */
    public int pruneUnloaded(@Nonnull World world,
        @Nonnull SpaceId spaceId,
        @Nonnull PhysicsSpaceBinding space,
        @Nullable Snapshot profiling) {
        return pruneUnloaded(world, spaceId, space, profiling, null);
    }

    /**
     * Removes cached sections whose source chunk is no longer loaded.
     */
    public int pruneUnloaded(@Nonnull World world,
        @Nonnull SpaceId spaceId,
        @Nonnull PhysicsSpaceBinding space,
        @Nullable Snapshot profiling,
        @Nullable SectionAccessCache accessCache) {
        long start = profiling != null ? System.nanoTime() : 0L;
        SpaceCollisionCache cache = spaces.get(spaceId.value());
        if (cache == null) {
            return 0;
        }

        int removed = 0;
        int removedSections = 0;
        Iterator<Long2ObjectMap.Entry<CachedSection>> iterator = cache.sections.long2ObjectEntrySet().iterator();
        while (iterator.hasNext()) {
            CachedSection section = iterator.next().getValue();
            if (blockChunk(world, section.chunkX, section.chunkZ, accessCache) != null) {
                continue;
            }

            removed += section.removeFrom(space);
            removedSections++;
            iterator.remove();
        }
        if (cache.isEmpty()) {
            spaces.remove(spaceId.value());
        }
        if (profiling != null) {
            profiling.addUnloadedPrune(removedSections, removed);
            profiling.addPruneUnloadedNanos(System.nanoTime() - start);
        }
        return removed;
    }

    /**
     * Removes all cached sections for the given space.
     */
    public int clear(@Nonnull SpaceId spaceId, @Nullable PhysicsSpaceBinding space) {
        SpaceCollisionCache cache = spaces.remove(spaceId.value());
        if (cache == null || space == null) {
            return 0;
        }

        int removed = 0;
        for (CachedSection section : cache.sections.values()) {
            removed += section.removeFrom(space);
        }
        return removed;
    }

    /**
     * Removes all cached sections for the given space.
     */
    public int clear(@Nonnull PhysicsSpaceBinding space) {
        return clear(space.spaceId(), space);
    }

    /**
     * Removes cached sections for one chunk from a single physics space.
     */
    public int clearChunk(@Nonnull SpaceId spaceId,
        @Nullable PhysicsSpaceBinding space,
        int chunkX,
        int chunkZ) {
        SpaceCollisionCache cache = spaces.get(spaceId.value());
        if (cache == null) {
            return 0;
        }

        int removed = 0;
        Iterator<Long2ObjectMap.Entry<CachedSection>> iterator = cache.sections.long2ObjectEntrySet().iterator();
        while (iterator.hasNext()) {
            CachedSection section = iterator.next().getValue();
            if (section.chunkX != chunkX || section.chunkZ != chunkZ) {
                continue;
            }
            if (space != null) {
                removed += section.removeFrom(space);
            }
            iterator.remove();
        }
        cache.missingBlockChunkBackoffs.remove(ChunkUtil.indexChunk(chunkX, chunkZ));
        removeSectionBackoffsForChunk(cache, chunkX, chunkZ);
        if (cache.isEmpty()) {
            spaces.remove(spaceId.value());
        }
        return removed;
    }

    public int bodyCount() {
        int count = 0;
        for (SpaceCollisionCache cache : spaces.values()) {
            for (CachedSection section : cache.sections.values()) {
                count += section.backendBodyIds.size();
            }
        }
        return count;
    }

    public int sectionCount() {
        int count = 0;
        for (SpaceCollisionCache cache : spaces.values()) {
            count += cache.sections.size();
        }
        return count;
    }

    public int spaceCount() {
        return spaces.size();
    }

    public int shapeTemplateCount() {
        return shapeTemplates.size();
    }

    public boolean containsBody(@Nonnull SpaceId spaceId, long backendBodyId) {
        SpaceCollisionCache cache = spaces.get(spaceId.value());
        if (cache == null) {
            return false;
        }

        for (CachedSection section : cache.sections.values()) {
            if (section.backendBodyIds.contains(backendBodyId)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Probes the highest cached world-collision surface under a body footprint.
     * <p>
     * This is intended for diagnostics, not simulation. It uses the collision geometry
     * already built for a physics space so benchmark health checks can compare bodies to
     * the streamed terrain actually present in the backend instead of a fixed Y plane.</p>
     */
    @Nonnull
    public GroundProbe probeGround(@Nonnull SpaceId spaceId,
        double x,
        double z,
        double horizontalHalfExtent) {
        SpaceCollisionCache cache = spaces.get(spaceId.value());
        if (cache == null) {
            return GroundProbe.missing();
        }

        double halfExtent = Math.max(0.0, horizontalHalfExtent);
        double minX = x - halfExtent;
        double maxX = x + halfExtent;
        double minZ = z - halfExtent;
        double maxZ = z + halfExtent;
        int minChunkX = ChunkUtil.chunkCoordinate((int) Math.floor(minX));
        int maxChunkX = ChunkUtil.chunkCoordinate((int) Math.floor(maxX));
        int minChunkZ = ChunkUtil.chunkCoordinate((int) Math.floor(minZ));
        int maxChunkZ = ChunkUtil.chunkCoordinate((int) Math.floor(maxZ));

        double groundTopY = Double.NEGATIVE_INFINITY;
        for (CachedSection section : cache.sections.values()) {
            if (section.chunkX < minChunkX || section.chunkX > maxChunkX
                || section.chunkZ < minChunkZ || section.chunkZ > maxChunkZ) {
                continue;
            }
            groundTopY = probeGroundTop(section.fullCubeBoxes, minX, maxX, minZ, maxZ, groundTopY);
            groundTopY = probeGroundTop(section.detailBoxes, minX, maxX, minZ, maxZ, groundTopY);
        }
        if (groundTopY == Double.NEGATIVE_INFINITY) {
            return GroundProbe.missing();
        }
        return new GroundProbe(true, groundTopY);
    }

    private static double probeGroundTop(@Nonnull List<BoxCollider> boxes,
        double minX,
        double maxX,
        double minZ,
        double maxZ,
        double groundTopY) {
        double topY = groundTopY;
        for (BoxCollider box : boxes) {
            if (!overlaps(minX, maxX, box.centerX() - box.halfX(), box.centerX() + box.halfX())
                || !overlaps(minZ, maxZ, box.centerZ() - box.halfZ(), box.centerZ() + box.halfZ())) {
                continue;
            }
            topY = Math.max(topY, box.centerY() + box.halfY());
        }
        return topY;
    }

    private static boolean overlaps(double minA, double maxA, double minB, double maxB) {
        return maxA >= minB && maxB >= minA;
    }

    public void forEachDebugSection(@Nonnull SpaceId spaceId,
        @Nonnull Consumer<DebugSection> consumer) {
        SpaceCollisionCache cache = spaces.get(spaceId.value());
        if (cache == null) {
            return;
        }

        for (CachedSection section : cache.sections.values()) {
            consumer.accept(section.debugSection());
        }
    }

    @Nonnull
    private BuildStats ensureSection(@Nonnull World world,
        @Nonnull PhysicsSpaceBinding space,
        int chunkX,
        int sectionY,
        int chunkZ,
        long tick,
        @Nullable Snapshot profiling,
        @Nullable StreamingTargetDiagnostic targetDiagnostic,
        @Nullable SectionAccessCache accessCache) {
        long start = profiling != null ? System.nanoTime() : 0L;
        if (profiling != null) {
            profiling.incrementSectionRequests();
        }

        SpaceCollisionCache cache =
            spaces.computeIfAbsent(space.spaceId().value(), ignored -> new SpaceCollisionCache());
        long chunkKey = ChunkUtil.indexChunk(chunkX, chunkZ);
        long sectionKey = packSectionKey(chunkX, sectionY, chunkZ);
        if (isBackedOff(cache.missingBlockChunkBackoffs, chunkKey, tick)) {
            if (profiling != null) {
                profiling.incrementMissingBackoffSkip(MissingSectionReason.BLOCK_CHUNK);
                profiling.recordMissingSection(MissingSectionReason.BLOCK_CHUNK,
                    chunkX,
                    sectionY,
                    chunkZ,
                    targetDiagnostic);
                profiling.addEnsureSectionNanos(System.nanoTime() - start);
            }
            return BuildStats.empty();
        }

        BlockChunk blockChunk = blockChunk(world, chunkX, chunkZ, accessCache);
        if (blockChunk == null) {
            cache.missingBlockChunkBackoffs.put(chunkKey, tick + MISSING_BLOCK_CHUNK_RETRY_TICKS);
            if (profiling != null) {
                profiling.recordMissingSection(MissingSectionReason.BLOCK_CHUNK,
                    chunkX,
                    sectionY,
                    chunkZ,
                    targetDiagnostic);
                profiling.addEnsureSectionNanos(System.nanoTime() - start);
            }
            return BuildStats.empty();
        }
        cache.missingBlockChunkBackoffs.remove(chunkKey);

        if (isBackedOff(cache.missingBlockSectionBackoffs, sectionKey, tick)) {
            if (profiling != null) {
                profiling.incrementMissingBackoffSkip(MissingSectionReason.BLOCK_SECTION);
                profiling.recordMissingSection(MissingSectionReason.BLOCK_SECTION,
                    chunkX,
                    sectionY,
                    chunkZ,
                    targetDiagnostic);
                profiling.addEnsureSectionNanos(System.nanoTime() - start);
            }
            return BuildStats.empty();
        }

        BlockSection section = accessCache != null
            ? accessCache.blockSection(world, chunkX, sectionY, chunkZ)
            : ChunkSectionAccess.blockSection(world, chunkX, sectionY, chunkZ);
        if (section == null) {
            cache.missingBlockSectionBackoffs.put(sectionKey, tick + MISSING_BLOCK_SECTION_RETRY_TICKS);
            if (profiling != null) {
                profiling.recordMissingSection(MissingSectionReason.BLOCK_SECTION,
                    chunkX,
                    sectionY,
                    chunkZ,
                    targetDiagnostic);
                profiling.addEnsureSectionNanos(System.nanoTime() - start);
            }
            return BuildStats.empty();
        }
        cache.missingBlockSectionBackoffs.remove(sectionKey);
        CachedSection cached = cache.sections.get(sectionKey);
        long neighborhoodSignature = sectionBuilder.neighborhoodSignature(world,
            section,
            chunkX,
            sectionY,
            chunkZ,
            accessCache);
        if (cached != null && cached.neighborhoodSignature == neighborhoodSignature) {
            cached.lastUsedTick = tick;
            if (profiling != null) {
                profiling.incrementSectionCacheHits();
                profiling.addEnsureSectionNanos(System.nanoTime() - start);
            }
            return BuildStats.empty();
        }

        SectionCollisionGeometry geometry = sectionBuilder.build(world,
            section,
            chunkX,
            sectionY,
            chunkZ,
            accessCache);
        CachedSection built = new CachedSection(chunkX, sectionY, chunkZ, tick,
            neighborhoodSignature);
        try {
            addGeometryBodies(space, built, geometry);
        } catch (RuntimeException exception) {
            removeBuiltSectionAfterFailure(space, built, exception);
            throw exception;
        }

        int removed = 0;
        boolean rebuilt = cached != null;
        try {
            if (cached != null) {
                removed = cached.removeFrom(space);
            }
        } catch (RuntimeException exception) {
            removeBuiltSectionAfterFailure(space, built, exception);
            throw exception;
        }
        cache.sections.put(sectionKey, built);

        BuildStats stats = BuildStats.from(geometry,
            built.backendBodyIds.size(),
            removed,
            rebuilt ? 0 : 1,
            rebuilt ? 1 : 0,
            0);
        if (profiling != null) {
            profiling.addBuildStats(stats);
            profiling.addEnsureSectionNanos(System.nanoTime() - start);
        }
        return stats;
    }

    private static void removeBuiltSectionAfterFailure(@Nonnull PhysicsSpaceBinding space,
        @Nonnull CachedSection built,
        @Nonnull RuntimeException failure) {
        try {
            built.removeFrom(space);
        } catch (RuntimeException cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
        }
    }

    private static void addGeometryBodies(@Nonnull PhysicsSpaceBinding space,
        @Nonnull CachedSection target,
        @Nonnull SectionCollisionGeometry geometry) {
        target.fullCubeBoxes.addAll(geometry.mergedFullCubeBoxes());
        target.detailBoxes.addAll(geometry.detailBoxes());
        for (BoxCollider box : geometry.mergedFullCubeBoxes()) {
            addStaticBox(space, target, box);
        }

        for (BoxCollider box : geometry.detailBoxes()) {
            addStaticBox(space, target, box);
        }
    }

    private static void addStaticBox(@Nonnull PhysicsSpaceBinding space,
        @Nonnull CachedSection section,
        @Nonnull BoxCollider box) {
        if (box.halfX() <= 0.0 || box.halfY() <= 0.0 || box.halfZ() <= 0.0) {
            return;
        }

        long backendBodyId = space.runtime().createBody(space.backendSpaceHandle().value(),
            BackendRuntimeCodes.SHAPE_BOX,
            (float) box.halfX(),
            (float) box.halfY(),
            (float) box.halfZ(),
            0.0f,
            0.0f,
            BackendRuntimeCodes.AXIS_Y,
            0.0f,
            0.0f,
            BackendRuntimeCodes.bodyTypeCode(PhysicsBodyType.STATIC),
            (float) box.centerX(),
            (float) box.centerY(),
            (float) box.centerZ(),
            0.0f,
            0.0f,
            0.0f,
            1.0f);
        space.runtime().setBodyFriction(space.backendSpaceHandle().value(), backendBodyId, 0.75f);
        space.runtime()
            .setBodyCollisionFilter(space.backendSpaceHandle().value(),
                backendBodyId,
                PhysicsCollisionFilters.TERRAIN,
                PhysicsCollisionFilters.ALL);
        section.backendBodyIds.add(backendBodyId);
    }

    @Nullable
    private static BlockChunk blockChunk(@Nonnull World world, int chunkX, int chunkZ) {
        return blockChunk(world, chunkX, chunkZ, null);
    }

    @Nullable
    private static BlockChunk blockChunk(@Nonnull World world,
        int chunkX,
        int chunkZ,
        @Nullable SectionAccessCache accessCache) {
        return accessCache != null
            ? accessCache.blockChunk(world, chunkX, chunkZ)
            : loadBlockChunk(world, chunkX, chunkZ);
    }

    @Nullable
    private static BlockChunk loadBlockChunk(@Nonnull World world, int chunkX, int chunkZ) {
        Ref<ChunkStore> chunkRef = world.getChunkStore()
            .getChunkReference(ChunkUtil.indexChunk(chunkX, chunkZ));
        if (chunkRef == null || !chunkRef.isValid()) {
            return null;
        }
        Store<ChunkStore> store = world.getChunkStore().getStore();
        return store.getComponentConcurrent(chunkRef, BlockChunk.getComponentType());
    }

    private static int sleepingBodyStreamingInterval(int ttlTicks) {
        return Math.clamp(ttlTicks / 2, 1, SLEEPING_BODY_STREAMING_INTERVAL_TICKS);
    }

    private static boolean isBackedOff(@Nonnull Long2LongMap backoffs, long key, long currentTick) {
        if (!backoffs.containsKey(key)) {
            return false;
        }

        long retryTick = backoffs.get(key);
        if (currentTick < retryTick) {
            return true;
        }

        backoffs.remove(key);
        return false;
    }

    private static void pruneExpiredMissingBackoffs(@Nonnull SpaceCollisionCache cache, long currentTick) {
        pruneExpiredMissingBackoffs(cache.missingBlockChunkBackoffs, currentTick);
        pruneExpiredMissingBackoffs(cache.missingBlockSectionBackoffs, currentTick);
    }

    private static void pruneExpiredMissingBackoffs(@Nonnull Long2LongMap backoffs, long currentTick) {
        backoffs.long2LongEntrySet().removeIf(entry -> currentTick >= entry.getLongValue());
    }

    private static void removeSectionBackoffsForChunk(@Nonnull SpaceCollisionCache cache,
        int chunkX,
        int chunkZ) {
        Iterator<Long2LongMap.Entry> iterator = cache.missingBlockSectionBackoffs.long2LongEntrySet().iterator();
        while (iterator.hasNext()) {
            long key = iterator.next().getLongKey();
            if (unpackChunkX(key) == chunkX && unpackChunkZ(key) == chunkZ) {
                iterator.remove();
            }
        }
    }

    private static long packSectionKey(int chunkX, int sectionY, int chunkZ) {
        return ((long) chunkX & 0x3FF_FFFFL) << 38
            | ((long) chunkZ & 0x3FF_FFFFL) << 12
            | (sectionY & 0xFFFL);
    }

    private static int unpackChunkX(long sectionKey) {
        return signExtend26((int) (sectionKey >>> 38));
    }

    private static int unpackChunkZ(long sectionKey) {
        return signExtend26((int) ((sectionKey >>> 12) & 0x3FF_FFFFL));
    }

    private static int signExtend26(int value) {
        int signBit = 1 << 25;
        return (value ^ signBit) - signBit;
    }

    /**
     * Per-space cache so pruning and clearing do not scan unrelated spaces.
     */
    private static final class SpaceCollisionCache {

        private final Long2ObjectMap<CachedSection> sections = new Long2ObjectOpenHashMap<>();
        private final Object2ObjectMap<RigidBodyKey, CachedBodyStreamingTarget> bodyTargets =
            new Object2ObjectOpenHashMap<>();
        private final Long2LongMap missingBlockChunkBackoffs = new Long2LongOpenHashMap();
        private final Long2LongMap missingBlockSectionBackoffs = new Long2LongOpenHashMap();

        private SpaceCollisionCache() {
        }

        private SpaceCollisionCache(@Nonnull SpaceCollisionCache other) {
            sections.putAll(other.sections);
            for (Object2ObjectMap.Entry<RigidBodyKey, CachedBodyStreamingTarget> entry
                : other.bodyTargets.object2ObjectEntrySet()) {
                bodyTargets.put(entry.getKey(), new CachedBodyStreamingTarget(entry.getValue()));
            }
            missingBlockChunkBackoffs.putAll(other.missingBlockChunkBackoffs);
            missingBlockSectionBackoffs.putAll(other.missingBlockSectionBackoffs);
        }

        @Nullable
        private CachedSection section(int chunkX, int sectionY, int chunkZ) {
            return sections.get(packSectionKey(chunkX, sectionY, chunkZ));
        }

        private boolean isEmpty() {
            return sections.isEmpty()
                && bodyTargets.isEmpty()
                && missingBlockChunkBackoffs.isEmpty()
                && missingBlockSectionBackoffs.isEmpty();
        }
    }

    private static final class CachedBodyStreamingTarget {

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

        private CachedBodyStreamingTarget(@Nonnull CachedBodyStreamingTarget other) {
            this(other.bounds, other.sleeping, other.lastSeenTick, other.lastRefreshTick);
        }
    }

    /**
     * Generated static backend bodies for one chunk section.
     */
    private static final class CachedSection {

        private final int chunkX;
        private final int sectionY;
        private final int chunkZ;
        private final List<Long> backendBodyIds = new ArrayList<>();
        private final List<BoxCollider> fullCubeBoxes = new ArrayList<>();
        private final List<BoxCollider> detailBoxes = new ArrayList<>();
        private final long neighborhoodSignature;
        @Nullable
        private boolean voxelTerrain;
        private long lastUsedTick;

        private CachedSection(int chunkX,
            int sectionY,
            int chunkZ,
            long lastUsedTick,
            long neighborhoodSignature) {
            this.chunkX = chunkX;
            this.sectionY = sectionY;
            this.chunkZ = chunkZ;
            this.lastUsedTick = lastUsedTick;
            this.neighborhoodSignature = neighborhoodSignature;
        }

        private int removeFrom(@Nonnull PhysicsSpaceBinding space) {
            int removed = backendBodyIds.size();
            for (long backendBodyId : backendBodyIds) {
                space.runtime().removeBody(space.backendSpaceHandle().value(), backendBodyId);
            }
            backendBodyIds.clear();
            return removed;
        }

        @Nonnull
        private DebugSection debugSection() {
            return new DebugSection(chunkX,
                sectionY,
                chunkZ,
                backendBodyIds.size(),
                voxelTerrain,
                List.copyOf(fullCubeBoxes),
                List.copyOf(detailBoxes));
        }
    }

    /**
     * Immutable debug snapshot for one cached world-collision section.
     */
    public record DebugSection(int chunkX,
                               int sectionY,
                               int chunkZ,
                               int bodyCount,
                               boolean voxelTerrain,
                               @Nonnull List<BoxCollider> fullCubeBoxes,
                               @Nonnull List<BoxCollider> detailBoxes) {
    }

    /**
     * Highest cached terrain surface found under a queried body footprint.
     */
    public record GroundProbe(boolean found, double topY) {

        @Nonnull
        private static GroundProbe missing() {
            return new GroundProbe(false, Double.NaN);
        }
    }

    /**
     * Per-streaming-tick source-section cache. It avoids repeated Hytale chunk
     * reference lookups while many bodies request overlapping neighborhoods.
     */
    public static final class SectionAccessCache {

        private final Long2ObjectMap<BlockChunk> blockChunks = new Long2ObjectOpenHashMap<>();
        private final Long2ObjectMap<BlockSection> blockSections = new Long2ObjectOpenHashMap<>();

        private SectionAccessCache() {
        }

        @Nullable
        BlockChunk blockChunk(@Nonnull World world, int chunkX, int chunkZ) {
            long key = ChunkUtil.indexChunk(chunkX, chunkZ);
            if (blockChunks.containsKey(key)) {
                return blockChunks.get(key);
            }

            BlockChunk chunk = loadBlockChunk(world, chunkX, chunkZ);
            blockChunks.put(key, chunk);
            return chunk;
        }

        @Nullable
        BlockSection blockSection(@Nonnull World world, int chunkX, int sectionY, int chunkZ) {
            long key = packSectionKey(chunkX, sectionY, chunkZ);
            if (blockSections.containsKey(key)) {
                return blockSections.get(key);
            }

            BlockSection section = ChunkSectionAccess.blockSection(world, chunkX, sectionY, chunkZ);
            blockSections.put(key, section);
            return section;
        }
    }

    public enum TargetRefreshReason {
        FIRST_SEEN,
        BOUNDS_CHANGED,
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

    /**
     * Aggregate statistics from a cache build or rebuild operation.
     */
    public record BuildStats(int scannedBlocks,
                             int solidBlocks,
                             int culledInteriorBlocks,
                             int fullCubeRuns,
                             int detailBoxes,
                             int colliderBodies,
                             int removedBodies,
                             int sectionsBuilt,
                             int sectionsRebuilt,
                             int voxelBodies) {

        @Nonnull
        public static BuildStats empty() {
            return new BuildStats(0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        }

        @Nonnull
        private static BuildStats from(@Nonnull SectionCollisionGeometry geometry,
            int colliderBodies,
            int removedBodies,
            int sectionsBuilt,
            int sectionsRebuilt,
            int voxelBodies) {
            return new BuildStats(geometry.scannedBlocks(),
                geometry.solidBlocks(),
                geometry.culledInteriorBlocks(),
                geometry.mergedFullCubeBoxes().size(),
                geometry.detailBoxCount(),
                colliderBodies,
                removedBodies,
                sectionsBuilt,
                sectionsRebuilt,
                voxelBodies);
        }

        @Nonnull
        public BuildStats plus(@Nonnull BuildStats stats) {
            return new BuildStats(scannedBlocks + stats.scannedBlocks,
                solidBlocks + stats.solidBlocks,
                culledInteriorBlocks + stats.culledInteriorBlocks,
                fullCubeRuns + stats.fullCubeRuns,
                detailBoxes + stats.detailBoxes,
                colliderBodies + stats.colliderBodies,
                removedBodies + stats.removedBodies,
                sectionsBuilt + stats.sectionsBuilt,
                sectionsRebuilt + stats.sectionsRebuilt,
                voxelBodies + stats.voxelBodies);
        }

        @Nonnull
        private BuildStats withRemovedBodies(int removedBodies) {
            return new BuildStats(scannedBlocks,
                solidBlocks,
                culledInteriorBlocks,
                fullCubeRuns,
                detailBoxes,
                colliderBodies,
                removedBodies,
                sectionsBuilt,
                sectionsRebuilt,
                voxelBodies);
        }
    }
}
