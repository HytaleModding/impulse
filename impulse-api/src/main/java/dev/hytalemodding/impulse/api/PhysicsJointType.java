package dev.hytalemodding.impulse.api;

/**
 * Joint kinds supported by the shared API.
 */
public enum PhysicsJointType {

    /**
     * Locks two bodies together
     */
    FIXED,

    /**
     * Keeps one anchor between the bodies and allows free rotation
     */
    POINT,

    /**
     * Rotation around one axis
     */
    HINGE,

    /**
     * Movement along a direction/axis
     */
    SLIDER,

    /**
     * Spring behavior between two bodies
     */
    SPRING
}
