package dev.hytalemodding.impulse.core.plugin.body;


/**
 * Classifies why a body exists in the runtime registry.
 */
public enum PhysicsBodyKind {
    BODY,
    TEMPORARY,
    TERRAIN;

    public boolean isTerrain() {
        return this == TERRAIN;
    }
}
