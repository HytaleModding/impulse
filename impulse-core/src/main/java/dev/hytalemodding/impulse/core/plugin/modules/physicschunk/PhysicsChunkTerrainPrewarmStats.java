package dev.hytalemodding.impulse.core.plugin.modules.physicschunk;

import javax.annotation.Nonnull;

/**
 * Statistics from ensuring PhysicsChunk terrain around multiple target positions.
 */
public record PhysicsChunkTerrainPrewarmStats(int sectionTargets,
                                              @Nonnull PhysicsChunkTerrainBuildStats buildStats) {

    @Nonnull
    public static PhysicsChunkTerrainPrewarmStats fromWorldCollisionStats(
        @Nonnull WorldCollisionPrewarmStats stats) {
        return new PhysicsChunkTerrainPrewarmStats(stats.sectionTargets(),
            PhysicsChunkTerrainBuildStats.fromWorldCollisionStats(stats.buildStats()));
    }

    @Nonnull
    public WorldCollisionPrewarmStats toWorldCollisionStats() {
        return new WorldCollisionPrewarmStats(sectionTargets,
            buildStats.toWorldCollisionStats());
    }
}
