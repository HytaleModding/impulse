package dev.hytalemodding.impulse.core.plugin.collision;

/**
 * Current size of the generated world-collision cache.
 */
public record WorldCollisionStats(int spaces,
                                  int sections,
                                  int bodies,
                                  int shapeTemplates) {
}
