package dev.hytalemodding.impulse.core.internal.modules.physicschunk;

import javax.annotation.Nonnull;

/**
 * Aggregate statistics from a PhysicsChunk collision build or rebuild operation.
 */
public record PhysicsChunkBuildStats(int scannedBlocks,
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
    public static PhysicsChunkBuildStats empty() {
        return new PhysicsChunkBuildStats(0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    }

    @Nonnull
    static PhysicsChunkBuildStats from(@Nonnull SectionCollisionGeometry geometry,
        int colliderBodies,
        int removedBodies,
        int sectionsBuilt,
        int sectionsRebuilt,
        int voxelBodies) {
        return new PhysicsChunkBuildStats(geometry.scannedBlocks(),
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
    public PhysicsChunkBuildStats plus(@Nonnull PhysicsChunkBuildStats stats) {
        return new PhysicsChunkBuildStats(scannedBlocks + stats.scannedBlocks,
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
    PhysicsChunkBuildStats withRemovedBodies(int removedBodies) {
        return new PhysicsChunkBuildStats(scannedBlocks,
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
