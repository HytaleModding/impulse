package dev.hytalemodding.impulse.core.voxel;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import dev.hytalemodding.impulse.api.PhysicsBody;
import dev.hytalemodding.impulse.api.PhysicsSpace;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.resources.WorldCollisionProfilingResource.Snapshot;
import dev.hytalemodding.impulse.core.voxel.SectionCollisionGeometry.BoxCollider;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3f;

/**
 * Section-keyed cache that generates static physics collision from Hytale world blocks.
 *
 * <p>The cache is split per physics space so spaces can choose different world-collision
 * policies. Each cached section is rebuilt when Hytale's section change counter changes,
 * and removed when it falls out of the streaming radius or when its chunk unloads.</p>
 */
public final class WorldVoxelCollisionCache {

    private static final double SECTION_WAKE_MARGIN = 2.0;
    private static final Vector3f WAKE_POSITION_SCRATCH = new Vector3f();

    private final Int2ObjectMap<SpaceCollisionCache> spaces = new Int2ObjectOpenHashMap<>();
    private final ShapeTemplateCache shapeTemplates = new ShapeTemplateCache();
    private final SectionColliderBuilder sectionBuilder = new SectionColliderBuilder(shapeTemplates);

    public void copyFrom(@Nonnull WorldVoxelCollisionCache other) {
        spaces.clear();
        for (Int2ObjectMap.Entry<SpaceCollisionCache> entry : other.spaces.int2ObjectEntrySet()) {
            spaces.put(entry.getIntKey(), new SpaceCollisionCache(entry.getValue()));
        }
    }

    /**
     * Wipes cached sections for the space, then rebuilds everything in the given radius.
     */
    @Nonnull
    public BuildStats rebuildAround(@Nonnull World world,
        @Nonnull PhysicsSpace space,
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
        @Nonnull PhysicsSpace space,
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
        @Nonnull PhysicsSpace space,
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
        @Nonnull PhysicsSpace space,
        @Nonnull Vector3d center,
        int radius,
        long tick,
        @Nullable Snapshot profiling,
        @Nullable LongSet visitedSections) {
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
                        profiling));
                }
            }
        }
        if (profiling != null) {
            profiling.addEnsureAroundNanos(System.nanoTime() - start);
        }
        return total;
    }

    /**
     * Removes sections whose last use is older than the configured TTL.
     */
    public int pruneUnused(@Nonnull SpaceId spaceId,
        @Nonnull PhysicsSpace space,
        long currentTick,
        int ttlTicks) {
        return pruneUnused(spaceId, space, currentTick, ttlTicks, null);
    }

    /**
     * Removes sections whose last use is older than the configured TTL.
     */
    public int pruneUnused(@Nonnull SpaceId spaceId,
        @Nonnull PhysicsSpace space,
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
        if (cache.sections.isEmpty()) {
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
        @Nonnull PhysicsSpace space) {
        return pruneUnloaded(world, spaceId, space, null);
    }

    /**
     * Removes cached sections whose source chunk is no longer loaded.
     */
    public int pruneUnloaded(@Nonnull World world,
        @Nonnull SpaceId spaceId,
        @Nonnull PhysicsSpace space,
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
            CachedSection section = iterator.next().getValue();
            if (blockChunk(world, section.chunkX, section.chunkZ) != null) {
                continue;
            }

            removed += section.removeFrom(space);
            removedSections++;
            iterator.remove();
        }
        if (cache.sections.isEmpty()) {
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
    public int clear(@Nonnull SpaceId spaceId, @Nullable PhysicsSpace space) {
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
    public int clear(@Nonnull PhysicsSpace space) {
        return clear(space.getId(), space);
    }

    /**
     * Removes cached sections for one chunk from a single physics space.
     */
    public int clearChunk(@Nonnull SpaceId spaceId,
        @Nullable PhysicsSpace space,
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
        if (cache.sections.isEmpty()) {
            spaces.remove(spaceId.value());
        }
        return removed;
    }

    public int bodyCount() {
        int count = 0;
        for (SpaceCollisionCache cache : spaces.values()) {
            for (CachedSection section : cache.sections.values()) {
                count += section.bodies.size();
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

    public boolean containsBody(@Nonnull SpaceId spaceId, @Nonnull PhysicsBody body) {
        SpaceCollisionCache cache = spaces.get(spaceId.value());
        if (cache == null) {
            return false;
        }

        for (CachedSection section : cache.sections.values()) {
            if (section.bodies.contains(body)) {
                return true;
            }
        }
        return false;
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
        @Nonnull PhysicsSpace space,
        int chunkX,
        int sectionY,
        int chunkZ,
        long tick,
        @Nullable Snapshot profiling) {
        long start = profiling != null ? System.nanoTime() : 0L;
        if (profiling != null) {
            profiling.incrementSectionRequests();
        }

        BlockChunk blockChunk = blockChunk(world, chunkX, chunkZ);
        if (blockChunk == null) {
            if (profiling != null) {
                profiling.incrementMissingChunks();
                profiling.addEnsureSectionNanos(System.nanoTime() - start);
            }
            return BuildStats.empty();
        }

        BlockSection section = ChunkSectionAccess.blockSection(world, chunkX, sectionY, chunkZ);
        if (section == null) {
            if (profiling != null) {
                profiling.incrementMissingChunks();
                profiling.addEnsureSectionNanos(System.nanoTime() - start);
            }
            return BuildStats.empty();
        }
        SpaceCollisionCache cache = spaces.computeIfAbsent(space.getId().value(), ignored -> new SpaceCollisionCache());
        long key = packSectionKey(chunkX, sectionY, chunkZ);
        CachedSection cached = cache.sections.get(key);
        long neighborhoodSignature = sectionBuilder.neighborhoodSignature(world,
            section,
            chunkX,
            sectionY,
            chunkZ);
        if (cached != null && cached.neighborhoodSignature == neighborhoodSignature) {
            cached.lastUsedTick = tick;
            if (profiling != null) {
                profiling.incrementSectionCacheHits();
                profiling.addEnsureSectionNanos(System.nanoTime() - start);
            }
            return BuildStats.empty();
        }

        int removed = 0;
        boolean rebuilt = false;
        if (cached != null) {
            removed = cached.removeFrom(space);
            rebuilt = true;
        }

        SectionCollisionGeometry geometry = sectionBuilder.build(world, section, chunkX, sectionY, chunkZ);
        CachedSection built = new CachedSection(chunkX, sectionY, chunkZ, tick,
            neighborhoodSignature);
        addGeometryBodies(space, built, geometry, chunkX, sectionY, chunkZ);
        cache.sections.put(key, built);
        combineWithNeighbors(space, cache, built);
        if (rebuilt) {
            wakeDynamicBodiesNearSection(space, chunkX, sectionY, chunkZ);
        }

        BuildStats stats = BuildStats.from(geometry,
            built.bodies.size(),
            removed,
            rebuilt ? 0 : 1,
            rebuilt ? 1 : 0,
            space.supportsVoxelTerrain() && geometry.hasFullCubeVoxels() ? 1 : 0);
        if (profiling != null) {
            profiling.addBuildStats(stats);
            profiling.addEnsureSectionNanos(System.nanoTime() - start);
        }
        return stats;
    }

    private static void addGeometryBodies(@Nonnull PhysicsSpace space,
        @Nonnull CachedSection target,
        @Nonnull SectionCollisionGeometry geometry,
        int chunkX,
        int sectionY,
        int chunkZ) {
        target.fullCubeBoxes.addAll(geometry.mergedFullCubeBoxes());
        target.detailBoxes.addAll(geometry.detailBoxes());
        if (space.supportsVoxelTerrain() && geometry.hasFullCubeVoxels()) {
            target.voxelTerrain = true;
            PhysicsBody voxelBody = space.createVoxelTerrain(1.0f, 1.0f, 1.0f, geometry.fullCubeVoxels());
            voxelBody.setPosition(chunkX << ChunkUtil.BITS,
                sectionY << ChunkUtil.BITS,
                chunkZ << ChunkUtil.BITS);
            voxelBody.setFriction(0.75f);
            space.addBody(voxelBody);
            target.voxelBody = voxelBody;
            target.bodies.add(voxelBody);
        } else {
            for (BoxCollider box : geometry.mergedFullCubeBoxes()) {
                addStaticBox(space, target, box);
            }
        }

        for (BoxCollider box : geometry.detailBoxes()) {
            addStaticBox(space, target, box);
        }
    }

    private static void addStaticBox(@Nonnull PhysicsSpace space,
        @Nonnull CachedSection section,
        @Nonnull BoxCollider box) {
        if (box.halfX() <= 0.0 || box.halfY() <= 0.0 || box.halfZ() <= 0.0) {
            return;
        }

        PhysicsBody body = space.createBox((float) box.halfX(),
            (float) box.halfY(),
            (float) box.halfZ(),
            0.0f);
        body.setPosition((float) box.centerX(), (float) box.centerY(), (float) box.centerZ());
        body.setFriction(0.75f);
        space.addBody(body);
        section.bodies.add(body);
    }

    private static void combineWithNeighbors(@Nonnull PhysicsSpace space,
        @Nonnull SpaceCollisionCache cache,
        @Nonnull CachedSection section) {
        if (section.voxelBody == null) {
            return;
        }

        combineWithNeighbor(space, section,
            cache.section(section.chunkX + 1, section.sectionY, section.chunkZ),
            ChunkUtil.SIZE, 0, 0);
        combineWithNeighbor(space, section,
            cache.section(section.chunkX - 1, section.sectionY, section.chunkZ),
            -ChunkUtil.SIZE, 0, 0);
        combineWithNeighbor(space, section,
            cache.section(section.chunkX, section.sectionY + 1, section.chunkZ),
            0, ChunkUtil.SIZE, 0);
        combineWithNeighbor(space, section,
            cache.section(section.chunkX, section.sectionY - 1, section.chunkZ),
            0, -ChunkUtil.SIZE, 0);
        combineWithNeighbor(space, section,
            cache.section(section.chunkX, section.sectionY, section.chunkZ + 1),
            0, 0, ChunkUtil.SIZE);
        combineWithNeighbor(space, section,
            cache.section(section.chunkX, section.sectionY, section.chunkZ - 1),
            0, 0, -ChunkUtil.SIZE);
    }

    private static void combineWithNeighbor(@Nonnull PhysicsSpace space,
        @Nonnull CachedSection section,
        @Nullable CachedSection neighbor,
        int shiftX,
        int shiftY,
        int shiftZ) {
        if (neighbor == null || neighbor.voxelBody == null) {
            return;
        }

        space.combineVoxelTerrains(section.voxelBody, neighbor.voxelBody, shiftX, shiftY, shiftZ);
    }

    private static void wakeDynamicBodiesNearSection(@Nonnull PhysicsSpace space,
        int chunkX,
        int sectionY,
        int chunkZ) {
        double minX = (chunkX << ChunkUtil.BITS) - SECTION_WAKE_MARGIN;
        double minY = (sectionY << ChunkUtil.BITS) - SECTION_WAKE_MARGIN;
        double minZ = (chunkZ << ChunkUtil.BITS) - SECTION_WAKE_MARGIN;
        double maxX = (chunkX << ChunkUtil.BITS) + ChunkUtil.SIZE + SECTION_WAKE_MARGIN;
        double maxY = (sectionY << ChunkUtil.BITS) + ChunkUtil.SIZE + SECTION_WAKE_MARGIN;
        double maxZ = (chunkZ << ChunkUtil.BITS) + ChunkUtil.SIZE + SECTION_WAKE_MARGIN;

        space.forEachBody(body -> {
            if (!body.isDynamic()) {
                return;
            }
            body.getPosition(WAKE_POSITION_SCRATCH);
            double posX = WAKE_POSITION_SCRATCH.x;
            double posY = WAKE_POSITION_SCRATCH.y;
            double posZ = WAKE_POSITION_SCRATCH.z;
            if (posX < minX || posX > maxX
                || posY < minY || posY > maxY
                || posZ < minZ || posZ > maxZ) {
                return;
            }
            body.activate();
        });
    }

    @Nullable
    private static BlockChunk blockChunk(@Nonnull World world, int chunkX, int chunkZ) {
        Ref<ChunkStore> chunkRef = world.getChunkStore()
            .getChunkReference(ChunkUtil.indexChunk(chunkX, chunkZ));
        if (chunkRef == null || !chunkRef.isValid()) {
            return null;
        }
        Store<ChunkStore> store = world.getChunkStore().getStore();
        return store.getComponent(chunkRef, BlockChunk.getComponentType());
    }

    private static long packSectionKey(int chunkX, int sectionY, int chunkZ) {
        return ((long) chunkX & 0x3FF_FFFFL) << 38
            | ((long) chunkZ & 0x3FF_FFFFL) << 12
            | (sectionY & 0xFFFL);
    }

    /**
     * Per-space cache so pruning and clearing do not scan unrelated spaces.
     */
    private static final class SpaceCollisionCache {

        private final Long2ObjectMap<CachedSection> sections = new Long2ObjectOpenHashMap<>();

        private SpaceCollisionCache() {
        }

        private SpaceCollisionCache(@Nonnull SpaceCollisionCache other) {
            sections.putAll(other.sections);
        }

        @Nullable
        private CachedSection section(int chunkX, int sectionY, int chunkZ) {
            return sections.get(packSectionKey(chunkX, sectionY, chunkZ));
        }
    }

    /**
     * Generated static bodies for one chunk section.
     */
    private static final class CachedSection {

        private final int chunkX;
        private final int sectionY;
        private final int chunkZ;
        private final List<PhysicsBody> bodies = new ArrayList<>();
        private final List<BoxCollider> fullCubeBoxes = new ArrayList<>();
        private final List<BoxCollider> detailBoxes = new ArrayList<>();
        private final long neighborhoodSignature;
        @Nullable
        private PhysicsBody voxelBody;
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

        private int removeFrom(@Nonnull PhysicsSpace space) {
            int removed = bodies.size();
            for (PhysicsBody body : bodies) {
                space.removeBody(body);
            }
            bodies.clear();
            return removed;
        }

        @Nonnull
        private DebugSection debugSection() {
            return new DebugSection(chunkX,
                sectionY,
                chunkZ,
                bodies.size(),
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
        private static BuildStats empty() {
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
        private BuildStats plus(@Nonnull BuildStats stats) {
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
