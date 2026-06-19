package dev.hytalemodding.impulse.core.plugin.modules.physicschunk;

import javax.annotation.Nonnull;

/**
 * Statistics from ensuring PhysicsChunk collision around multiple target positions.
 */
public record PhysicsChunkCollisionPrewarmStats(int sectionTargets,
                                              @Nonnull PhysicsChunkCollisionBuildStats buildStats) {
}
