package dev.hytalemodding.impulse.core.plugin.modules.physicschunk;

import javax.annotation.Nonnull;

/**
 * Statistics from ensuring PhysicsChunk terrain around multiple target positions.
 */
public record PhysicsChunkTerrainPrewarmStats(int sectionTargets,
                                              @Nonnull PhysicsChunkTerrainBuildStats buildStats) {
}
