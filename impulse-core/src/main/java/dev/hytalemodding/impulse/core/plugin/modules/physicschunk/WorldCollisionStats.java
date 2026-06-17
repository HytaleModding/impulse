package dev.hytalemodding.impulse.core.plugin.modules.physicschunk;

/**
 * @deprecated Use {@link PhysicsChunkTerrainStats}.
 */
@Deprecated(forRemoval = false)
public record WorldCollisionStats(int spaces,
                                  int sections,
                                  int bodies,
                                  int shapeTemplates) {
}
