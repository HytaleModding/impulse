package dev.hytalemodding.impulse.core.plugin.body;


/**
 * Classifies why a body exists in the runtime registry.
 */
public enum PhysicsBodyKind {
    BODY,

    /**
     * @deprecated Use {@link #TERRAIN}.
     */
    @Deprecated(forRemoval = false)
    WORLD_COLLISION,
    TEMPORARY,
    TERRAIN;

    public boolean isTerrainCollider() {
        return this == TERRAIN || this == WORLD_COLLISION;
    }
}
