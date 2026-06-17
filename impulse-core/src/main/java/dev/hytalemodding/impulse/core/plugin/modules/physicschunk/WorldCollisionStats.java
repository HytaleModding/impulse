package dev.hytalemodding.impulse.core.plugin.modules.physicschunk;

/**
 * Current size of the generated PhysicsChunk terrain cache.
 */
public record WorldCollisionStats(int spaces,
                                  int sections,
                                  int bodies,
                                  int shapeTemplates) {
}
