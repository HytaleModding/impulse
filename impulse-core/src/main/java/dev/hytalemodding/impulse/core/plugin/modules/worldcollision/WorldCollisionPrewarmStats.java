package dev.hytalemodding.impulse.core.plugin.modules.worldcollision;

/**
 * Statistics from ensuring world collision around multiple target positions.
 */
public record WorldCollisionPrewarmStats(int sectionTargets,
                                         WorldCollisionBuildStats buildStats) {
}
