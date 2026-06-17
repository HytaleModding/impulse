package dev.hytalemodding.impulse.core.plugin.modules.physicschunk;

import javax.annotation.Nonnull;

/**
 * Controls PhysicsChunk terrain collider generation for a PhysicsStore space.
 */
public enum PhysicsChunkTerrainMode {
    /**
     * Terrain colliders are disabled.
     */
    NONE,

    /**
     * Terrain colliders are only built when explicitly requested.
     */
    MANUAL,

    /**
     * Terrain colliders stream around players and configured physics bodies.
     */
    STREAMING;

    @Nonnull
    @Deprecated(forRemoval = false)
    public WorldCollisionMode toWorldCollisionMode() {
        return WorldCollisionMode.valueOf(name());
    }

    @Nonnull
    @Deprecated(forRemoval = false)
    public static PhysicsChunkTerrainMode fromWorldCollisionMode(
        @Nonnull WorldCollisionMode mode) {
        return valueOf(mode.name());
    }
}
