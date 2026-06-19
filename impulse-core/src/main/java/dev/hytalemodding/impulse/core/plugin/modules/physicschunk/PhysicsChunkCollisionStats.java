package dev.hytalemodding.impulse.core.plugin.modules.physicschunk;

/**
 * Current size of the generated PhysicsChunk collision cache.
 */
public record PhysicsChunkCollisionStats(int spaces,
                                       int sections,
                                       int bodies,
                                       int shapeTemplates) {
}
