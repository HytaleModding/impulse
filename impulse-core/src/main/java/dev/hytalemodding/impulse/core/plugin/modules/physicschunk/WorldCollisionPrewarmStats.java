package dev.hytalemodding.impulse.core.plugin.modules.physicschunk;

/**
 * Statistics from ensuring PhysicsChunk terrain around multiple target positions.
 */
public record WorldCollisionPrewarmStats(int sectionTargets,
                                         WorldCollisionBuildStats buildStats) {
}
