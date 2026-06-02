package dev.hytalemodding.impulse.api;

import java.util.List;
import java.util.Objects;
import javax.annotation.Nonnull;

/**
 * Immutable post-step backend event batch drained by core after a backend step completes.
 */
@Deprecated(forRemoval = true)
public record PhysicsBackendEventBatch(@Nonnull List<PhysicsBackendEvent> events,
                                       int droppedEventCount) {

    private static final PhysicsBackendEventBatch EMPTY = new PhysicsBackendEventBatch(List.of(), 0);

    public PhysicsBackendEventBatch {
        events = List.copyOf(Objects.requireNonNull(events, "events"));
        droppedEventCount = Math.max(0, droppedEventCount);
    }

    @Nonnull
    public static PhysicsBackendEventBatch empty() {
        return EMPTY;
    }

    public int size() {
        return events.size();
    }

    public boolean isEmpty() {
        return events.isEmpty() && droppedEventCount == 0;
    }
}
