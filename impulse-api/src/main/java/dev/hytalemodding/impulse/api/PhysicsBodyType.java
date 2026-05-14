package dev.hytalemodding.impulse.api;

/**
 * Body motion modes.
 */
public enum PhysicsBodyType {
    /**
     * BodyType that doesn't move from simulation.
     */
    STATIC,

    /**
     * BodyType that responds to forces and collisions.
     */
    DYNAMIC,

    /**
     * BodyType moved by code but that still collides.
     */
    KINEMATIC
}
