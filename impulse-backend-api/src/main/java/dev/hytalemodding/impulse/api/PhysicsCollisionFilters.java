package dev.hytalemodding.impulse.api;

/**
 * Shared collision category and mask bits.
 */
public final class PhysicsCollisionFilters {

    public static final int TERRAIN = 0b0000_0001;

    public static final int DYNAMIC_BODY = 0b0000_0010;

    public static final int ALL = 0b1111_1111_1111_1111_1111_1111_1111_1111;

    private PhysicsCollisionFilters() {
    }
}
