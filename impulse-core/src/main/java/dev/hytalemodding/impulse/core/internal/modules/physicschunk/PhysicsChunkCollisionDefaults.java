package dev.hytalemodding.impulse.core.internal.modules.physicschunk;

import dev.hytalemodding.impulse.api.PhysicsCollisionFilters;

/**
 * Internal defaults for generated PhysicsChunk collision bodies.
 */
public final class PhysicsChunkCollisionDefaults {

    public static final float FRICTION = 0.75f;
    public static final float RESTITUTION = 0.0f;
    public static final int COLLISION_GROUP = PhysicsCollisionFilters.TERRAIN;
    public static final int COLLISION_MASK = PhysicsCollisionFilters.ALL;

    private PhysicsChunkCollisionDefaults() {
    }
}
