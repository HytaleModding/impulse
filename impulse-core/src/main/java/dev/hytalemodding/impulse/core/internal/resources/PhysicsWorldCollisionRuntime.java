package dev.hytalemodding.impulse.core.internal.resources;

import com.hypixel.hytale.server.core.universe.world.World;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.internal.voxel.WorldVoxelCollisionCache;
import dev.hytalemodding.impulse.core.plugin.collision.WorldCollisionBuildStats;
import dev.hytalemodding.impulse.core.plugin.collision.WorldCollisionPrewarmStats;
import dev.hytalemodding.impulse.core.plugin.collision.WorldCollisionStats;
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

    @Nonnull
    public WorldVoxelCollisionCache worldVoxelCollisionCache() {
        return worldVoxelCollisionCache;
    }

    @Nonnull
    public WorldCollisionBuildStats rebuildAround(@Nonnull World world,
        @Nonnull PhysicsSpaceBinding space,
        @Nonnull Vector3d center,
        int radius) {
        return worldCollisionStats(worldVoxelCollisionCache.rebuildAround(world,
            space,
            center,
            radius));
    }

    @Nonnull
    public WorldCollisionPrewarmStats ensureAround(@Nonnull World world,
        @Nonnull PhysicsSpaceBinding space,
        @Nonnull Iterable<Vector3d> centers,
        int radius,
        long tick) {
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
                visitedSections));
        }
        return new WorldCollisionPrewarmStats(visitedSections.size(),
            worldCollisionStats(total));
    }

    public int clear(@Nonnull PhysicsSpaceBinding space) {
        return worldVoxelCollisionCache.clear(space);
    }

    public void clear(@Nonnull SpaceId spaceId, @Nullable PhysicsSpaceBinding space) {
        worldVoxelCollisionCache.clear(spaceId, space);
    }

    public void clearAll() {
        worldVoxelCollisionCache.copyFrom(new WorldVoxelCollisionCache());
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
