package dev.hytalemodding.impulse.core.internal.modules.worldcollision;

import com.hypixel.hytale.server.core.universe.world.World;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsSpaceBinding;
import dev.hytalemodding.impulse.core.plugin.modules.worldcollision.WorldCollisionBuildStats;
import dev.hytalemodding.impulse.core.plugin.modules.worldcollision.WorldCollisionPrewarmStats;
import dev.hytalemodding.impulse.core.plugin.modules.worldcollision.WorldCollisionStats;
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
 * World-collision runtime state for one physics world.
 */
public final class PhysicsWorldCollisionRuntime {

    private final WorldVoxelCollisionCache worldVoxelCollisionCache = new WorldVoxelCollisionCache();
    private final Int2LongMap streamingRevisions = new Int2LongOpenHashMap();

    @Nonnull
    public WorldVoxelCollisionCache worldVoxelCollisionCache() {
        return worldVoxelCollisionCache;
    }

    public void registerSpace(@Nonnull SpaceId spaceId) {
        streamingRevisions.putIfAbsent(spaceId.value(), 1L);
    }

    public void unregisterSpace(@Nonnull SpaceId spaceId) {
        streamingRevisions.remove(spaceId.value());
    }

    public long streamingRevision(@Nonnull SpaceId spaceId) {
        return streamingRevisions.getOrDefault(spaceId.value(), 0L);
    }

    public long incrementStreamingRevision(@Nonnull SpaceId spaceId) {
        long revision = streamingRevision(spaceId) + 1L;
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
            WorldCollisionBuildOptions.fromNativeVoxelTerrainEnabled(
                PhysicsWorldCollisionSettings.DEFAULT_NATIVE_VOXEL_TERRAIN_ENABLED));
    }

    @Nonnull
    public WorldCollisionBuildStats rebuildAround(@Nonnull World world,
        @Nonnull PhysicsSpaceBinding space,
        @Nonnull Vector3d center,
        int radius,
        @Nonnull WorldCollisionBuildOptions buildOptions) {
        return worldCollisionStats(worldVoxelCollisionCache.rebuildAround(world,
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
        @Nonnull WorldCollisionBuildOptions buildOptions) {
        WorldVoxelCollisionCache.BuildStats stats = worldVoxelCollisionCache.refreshAround(world,
            space,
            center,
            radius,
            buildOptions);
        if (stats.removedBodies() > 0) {
            incrementStreamingRevision(space.spaceId());
        }
        return worldCollisionStats(stats);
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
            WorldCollisionBuildOptions.fromNativeVoxelTerrainEnabled(
                PhysicsWorldCollisionSettings.DEFAULT_NATIVE_VOXEL_TERRAIN_ENABLED));
    }

    @Nonnull
    public WorldCollisionPrewarmStats ensureAround(@Nonnull World world,
        @Nonnull PhysicsSpaceBinding space,
        @Nonnull Iterable<Vector3d> centers,
        int radius,
        long tick,
        @Nonnull WorldCollisionBuildOptions buildOptions) {
        Objects.requireNonNull(centers, "centers");
        LongSet visitedSections = new LongOpenHashSet();
        WorldVoxelCollisionCache.BuildStats total = WorldVoxelCollisionCache.BuildStats.empty();
        for (Vector3d center : centers) {
            total = total.plus(worldVoxelCollisionCache.ensureAround(world,
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
            worldCollisionStats(total));
    }

    public int clear(@Nonnull PhysicsSpaceBinding space) {
        incrementStreamingRevision(space.spaceId());
        return worldVoxelCollisionCache.clear(space);
    }

    public void clear(@Nonnull SpaceId spaceId, @Nullable PhysicsSpaceBinding space) {
        worldVoxelCollisionCache.clear(spaceId, space);
        unregisterSpace(spaceId);
    }

    public void clearAll() {
        worldVoxelCollisionCache.copyFrom(new WorldVoxelCollisionCache());
        for (int spaceId : streamingRevisions.keySet().toIntArray()) {
            streamingRevisions.put(spaceId, streamingRevisions.get(spaceId) + 1L);
        }
    }

    public void clearRetainedTerrain(@Nonnull Iterable<PhysicsSpaceBinding> spaces) {
        for (PhysicsSpaceBinding space : spaces) {
            clear(space);
        }
        worldVoxelCollisionCache.finishStreamingApply();
    }

    public void clearAllAndUnregisterSpaces() {
        worldVoxelCollisionCache.copyFrom(new WorldVoxelCollisionCache());
        streamingRevisions.clear();
    }

    @Nonnull
    public WorldCollisionStats getStats() {
        return new WorldCollisionStats(worldVoxelCollisionCache.spaceCount(),
            worldVoxelCollisionCache.sectionCount(),
            worldVoxelCollisionCache.bodyCount(),
            worldVoxelCollisionCache.shapeTemplateCount());
    }

    @Nonnull
    private static WorldCollisionBuildStats worldCollisionStats(
        @Nonnull WorldVoxelCollisionCache.BuildStats stats) {
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
