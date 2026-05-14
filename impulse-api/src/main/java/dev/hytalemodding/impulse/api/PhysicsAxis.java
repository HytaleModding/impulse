package dev.hytalemodding.impulse.api;

/**
 * Axis selection for oriented shapes and axis constrained joints.
 */
public enum PhysicsAxis {
    X(0),
    Y(1),
    Z(2);

    private final int index;

    PhysicsAxis(int index) {
        this.index = index;
    }

    public int index() {
        return index;
    }

    public static PhysicsAxis fromIndex(int index) {
        return switch (index) {
            case 0 -> X;
            case 1 -> Y;
            case 2 -> Z;
            default -> throw new IllegalArgumentException("Unknown physics axis index: " + index);
        };
    }
}
