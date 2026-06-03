package dev.hytalemodding.impulse.core.plugin.modules.worldcollision;

/**
 * Current size of the generated world-collision cache.
 */
public record WorldCollisionStats(int spaces,
                                  int sections,
                                  int bodies,
                                  int shapeTemplates) {
}
