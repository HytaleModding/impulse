package dev.hytalemodding.impulse.core.plugin.modules.physicschunk;

/**
 * Controls PhysicsChunk collision body generation for a PhysicsStore space.
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
    STREAMING
}
