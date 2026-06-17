package dev.hytalemodding.impulse.core.plugin.modules.physicschunk;

/**
 * @deprecated Use {@link PhysicsChunkTerrainBuildStats}.
 */
@Deprecated(forRemoval = false)
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
