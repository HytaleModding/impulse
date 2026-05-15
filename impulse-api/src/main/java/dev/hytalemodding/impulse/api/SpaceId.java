package dev.hytalemodding.impulse.api;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * PhysicsSpace identifier
 *
 * @param value
 */
public record SpaceId(int value) {

    /*
     * TODO: If this appears in profiles, replace it with a per-world allocator in
     * PhysicsWorldResource to avoid a global static counter.
     */
    private static final AtomicInteger COUNTER = new AtomicInteger(0);

    public static SpaceId next() {
        return new SpaceId(COUNTER.incrementAndGet());
    }

    public static void reserveAtLeast(int value) {
        COUNTER.updateAndGet(current -> Math.max(current, value));
    }
}
