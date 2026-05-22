package dev.hytalemodding.impulse.core.plugin.collision;

/**
 * Aggregate statistics from building or rebuilding streamed world-collision geometry.
 */
public record WorldCollisionBuildStats(int scannedBlocks,
                                       int solidBlocks,
                                       int culledInteriorBlocks,
                                       int fullCubeRuns,
                                       int detailBoxes,
                                       int colliderBodies,
                                       int removedBodies,
                                       int sectionsBuilt,
                                       int sectionsRebuilt,
                                       int voxelBodies) {
}
