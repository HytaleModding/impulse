package dev.hytalemodding.impulse.core.plugin.modules.physicschunk;

/**
 * @deprecated Use {@link PhysicsChunkTerrainPrewarmStats}.
 */
@Deprecated(forRemoval = false)
public record WorldCollisionPrewarmStats(int sectionTargets,
                                         WorldCollisionBuildStats buildStats) {
}
