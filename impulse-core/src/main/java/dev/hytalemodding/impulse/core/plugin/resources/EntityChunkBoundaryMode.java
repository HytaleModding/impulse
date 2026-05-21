package dev.hytalemodding.impulse.core.plugin.resources;


/**
 * Controls how entity-backed physics bodies behave when they reach a chunk that
 * is not currently loaded and ticking.
 */
public enum EntityChunkBoundaryMode {
    /**
     * Freeze the body at its last known safe pose until the destination chunk is available.
     * This avoids pulling extra chunks into ticking state, at the cost of temporary pauses.
     */
    PAUSE_UNTIL_LOADED,

    /**
     * Proactively load and tick the destination chunk so the body can continue moving.
     * This preserves motion continuity, but can increase chunk work.
     */
    LOAD_TICKING_CHUNK
}
