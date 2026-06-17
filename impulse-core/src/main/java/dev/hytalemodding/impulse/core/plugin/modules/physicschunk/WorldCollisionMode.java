package dev.hytalemodding.impulse.core.plugin.modules.physicschunk;

import javax.annotation.Nonnull;

/**
 * @deprecated Use {@link PhysicsChunkTerrainMode}.
 */
@Deprecated(forRemoval = false)
public enum WorldCollisionMode {
    NONE,
    MANUAL,
    STREAMING;

    @Nonnull
    public PhysicsChunkTerrainMode toPhysicsChunkTerrainMode() {
        return PhysicsChunkTerrainMode.valueOf(name());
    }

    @Nonnull
    public static WorldCollisionMode fromPhysicsChunkTerrainMode(
        @Nonnull PhysicsChunkTerrainMode mode) {
        return valueOf(mode.name());
    }
}
