package dev.hytalemodding.impulse.core.plugin.modules.physicschunk;

/**
 * Controls PhysicsChunk collision body generation for a PhysicsStore space.
 */
public enum PhysicsChunkCollisionMode {
    /**
     * Chunk-collision bodies are disabled.
     */
    NONE,

    /**
     * Chunk-collision bodies are only built when explicitly requested.
     */
    MANUAL,

    /**
     * Chunk-collision bodies stream around players and configured physics bodies.
     */
    STREAMING
}
