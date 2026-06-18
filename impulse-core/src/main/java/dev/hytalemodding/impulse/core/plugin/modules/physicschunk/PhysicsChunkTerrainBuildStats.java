package dev.hytalemodding.impulse.core.plugin.modules.physicschunk;

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
}
