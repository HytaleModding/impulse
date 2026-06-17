package dev.hytalemodding.impulse.core.plugin.modules.physicschunk;

import javax.annotation.Nonnull;

/**
 * Aggregate statistics from building or rebuilding streamed PhysicsChunk terrain geometry.
 */
public record PhysicsChunkTerrainBuildStats(int scannedBlocks,
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
    public static PhysicsChunkTerrainBuildStats fromWorldCollisionStats(
        @Nonnull WorldCollisionBuildStats stats) {
        return new PhysicsChunkTerrainBuildStats(stats.scannedBlocks(),
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

    @Nonnull
    public WorldCollisionBuildStats toWorldCollisionStats() {
        return new WorldCollisionBuildStats(scannedBlocks,
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
