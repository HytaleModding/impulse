package dev.hytalemodding.impulse.api;

/**
 * Shared collision category bits used by Impulse examples and core-generated terrain.
 */
public final class PhysicsCollisionFilters {

    public static final int DEFAULT = 1;
    public static final int TERRAIN = 1;
    public static final int DYNAMIC_BODY = 1 << 1;
    public static final int ALL = -1;

    private PhysicsCollisionFilters() {
    }
}
