package dev.hytalemodding.impulse.core.plugin.collision;

/**
 * Statistics from ensuring world collision around multiple target positions.
 */
public record WorldCollisionPrewarmStats(int sectionTargets,
                                         WorldCollisionBuildStats buildStats) {
}
