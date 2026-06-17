package dev.hytalemodding.impulse.core.internal.modules.physicschunk;

import com.hypixel.hytale.server.core.universe.world.World;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsSpaceBinding;
import dev.hytalemodding.impulse.core.plugin.modules.physicschunk.WorldCollisionBuildStats;
import dev.hytalemodding.impulse.core.plugin.modules.physicschunk.WorldCollisionPrewarmStats;
import dev.hytalemodding.impulse.core.plugin.modules.physicschunk.WorldCollisionStats;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsWorldCollisionSettings;
import it.unimi.dsi.fastutil.ints.Int2LongMap;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;

/**
 * PhysicsChunk terrain runtime state for one physics world.
 */
public final class PhysicsChunkTerrainRuntime {

    private final VoxelTerrainCollisionCache voxelTerrainCache = new VoxelTerrainCollisionCache();
    private final Int2LongMap streamingRevisions = new Int2LongOpenHashMap();

    @Nonnull
    public VoxelTerrainCollisionCache voxelTerrainCache() {
        return voxelTerrainCache;
    }

    public synchronized void registerSpace(@Nonnull SpaceId spaceId) {
        streamingRevisions.putIfAbsent(spaceId.value(), 1L);
    }

    public synchronized void unregisterSpace(@Nonnull SpaceId spaceId) {
        streamingRevisions.remove(spaceId.value());
    }

    public synchronized long streamingRevision(@Nonnull SpaceId spaceId) {
        return streamingRevisions.getOrDefault(spaceId.value(), 0L);
    }

    public synchronized long incrementStreamingRevision(@Nonnull SpaceId spaceId) {
        long revision = streamingRevisions.getOrDefault(spaceId.value(), 0L) + 1L;
        streamingRevisions.put(spaceId.value(), revision);
        return revision;
    }

    @Nonnull
    public WorldCollisionBuildStats rebuildAround(@Nonnull World world,
        @Nonnull PhysicsSpaceBinding space,
        @Nonnull Vector3d center,
        int radius) {
        return rebuildAround(world,
            space,
            center,
            radius,
            PhysicsChunkBuildOptions.fromNativeVoxelTerrainEnabled(
                PhysicsWorldCollisionSettings.DEFAULT_NATIVE_VOXEL_TERRAIN_ENABLED));
    }

    @Nonnull
    public WorldCollisionBuildStats rebuildAround(@Nonnull World world,
        @Nonnull PhysicsSpaceBinding space,
        @Nonnull Vector3d center,
        int radius,
        @Nonnull PhysicsChunkBuildOptions buildOptions) {
        return terrainStats(voxelTerrainCache.rebuildAround(world,
            space,
            center,
            radius,
            buildOptions));
    }

    @Nonnull
    public WorldCollisionBuildStats refreshAround(@Nonnull World world,
        @Nonnull PhysicsSpaceBinding space,
        @Nonnull Vector3d center,
        int radius,
        @Nonnull PhysicsChunkBuildOptions buildOptions) {
        VoxelTerrainCollisionCache.BuildStats stats = voxelTerrainCache.refreshAround(world,
            space,
            center,
            radius,
            buildOptions);
        if (stats.removedBodies() > 0) {
            incrementStreamingRevision(space.spaceId());
        }
        return terrainStats(stats);
    }

    @Nonnull
    public WorldCollisionPrewarmStats ensureAround(@Nonnull World world,
        @Nonnull PhysicsSpaceBinding space,
        @Nonnull Iterable<Vector3d> centers,
        int radius,
        long tick) {
        return ensureAround(world,
            space,
            centers,
            radius,
            tick,
            PhysicsChunkBuildOptions.fromNativeVoxelTerrainEnabled(
                PhysicsWorldCollisionSettings.DEFAULT_NATIVE_VOXEL_TERRAIN_ENABLED));
    }

    @Nonnull
    public WorldCollisionPrewarmStats ensureAround(@Nonnull World world,
        @Nonnull PhysicsSpaceBinding space,
        @Nonnull Iterable<Vector3d> centers,
        int radius,
        long tick,
        @Nonnull PhysicsChunkBuildOptions buildOptions) {
        Objects.requireNonNull(centers, "centers");
        LongSet visitedSections = new LongOpenHashSet();
        VoxelTerrainCollisionCache.BuildStats total = VoxelTerrainCollisionCache.BuildStats.empty();
        for (Vector3d center : centers) {
            total = total.plus(voxelTerrainCache.ensureAround(world,
                space,
                center,
                radius,
                tick,
                null,
                visitedSections,
                null,
                null,
                buildOptions));
        }
        return new WorldCollisionPrewarmStats(visitedSections.size(),
            terrainStats(total));
    }

    public int clear(@Nonnull PhysicsSpaceBinding space) {
        incrementStreamingRevision(space.spaceId());
        return voxelTerrainCache.clear(space);
    }

    public void clear(@Nonnull SpaceId spaceId, @Nullable PhysicsSpaceBinding space) {
        voxelTerrainCache.clear(spaceId, space);
        unregisterSpace(spaceId);
    }

    public synchronized void clearAll() {
        voxelTerrainCache.copyFrom(new VoxelTerrainCollisionCache());
        for (int spaceId : streamingRevisions.keySet().toIntArray()) {
            streamingRevisions.put(spaceId, streamingRevisions.get(spaceId) + 1L);
        }
    }

    public void clearRetainedTerrain(@Nonnull Iterable<PhysicsSpaceBinding> spaces) {
        for (PhysicsSpaceBinding space : spaces) {
            clear(space);
        }
        voxelTerrainCache.finishStreamingApply();
    }

    public synchronized void clearAllAndUnregisterSpaces() {
        voxelTerrainCache.copyFrom(new VoxelTerrainCollisionCache());
        streamingRevisions.clear();
    }

    @Nonnull
    public WorldCollisionStats getStats() {
        return new WorldCollisionStats(voxelTerrainCache.spaceCount(),
            voxelTerrainCache.sectionCount(),
            voxelTerrainCache.bodyCount(),
            voxelTerrainCache.shapeTemplateCount());
    }

    @Nonnull
    private static WorldCollisionBuildStats terrainStats(
        @Nonnull VoxelTerrainCollisionCache.BuildStats stats) {
        return new WorldCollisionBuildStats(stats.scannedBlocks(),
            stats.solidBlocks(),
            stats.culledInteriorBlocks(),
            stats.fullCubeRuns(),
            stats.detailBoxes(),
            stats.colliderBodies(),
            stats.removedBodies(),
            stats.sectionsBuilt(),
            stats.sectionsRebuilt(),
            stats.voxelBodies());
    }
}
