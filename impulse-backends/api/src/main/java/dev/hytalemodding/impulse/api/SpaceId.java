package dev.hytalemodding.impulse.api;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Physics space identifier.
 *
 * @param value backend-local numeric space id
 */
public record SpaceId(int value) {

    /*
     * PhysicsStore space helpers to avoid a global static counter.
     */
    private static final AtomicInteger COUNTER = new AtomicInteger(0);

    public static SpaceId next() {
        return new SpaceId(COUNTER.incrementAndGet());
    }

    public static void reserveAtLeast(int value) {
        COUNTER.updateAndGet(current -> Math.max(current, value));
    }
}
