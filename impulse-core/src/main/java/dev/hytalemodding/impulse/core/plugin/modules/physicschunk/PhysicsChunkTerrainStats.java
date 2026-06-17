package dev.hytalemodding.impulse.core.plugin.modules.physicschunk;

import javax.annotation.Nonnull;

/**
 * Current size of the generated PhysicsChunk terrain cache.
 */
public record PhysicsChunkTerrainStats(int spaces,
                                       int sections,
                                       int bodies,
                                       int shapeTemplates) {

    @Nonnull
    @Deprecated(forRemoval = false)
    public static PhysicsChunkTerrainStats fromWorldCollisionStats(
        @Nonnull WorldCollisionStats stats) {
        return new PhysicsChunkTerrainStats(stats.spaces(),
            stats.sections(),
            stats.bodies(),
            stats.shapeTemplates());
    }

    @Nonnull
    @Deprecated(forRemoval = false)
    public WorldCollisionStats toWorldCollisionStats() {
        return new WorldCollisionStats(spaces,
            sections,
            bodies,
            shapeTemplates);
    }
}
