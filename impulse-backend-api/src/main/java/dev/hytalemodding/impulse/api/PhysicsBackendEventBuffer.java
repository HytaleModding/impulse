package dev.hytalemodding.impulse.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nonnull;

/**
 * Bounded in-memory backend event buffer for one or more completed backend steps.
 */
@Deprecated(forRemoval = true)
public final class PhysicsBackendEventBuffer implements PhysicsBackendEventSink {

    public static final int DEFAULT_CAPACITY = 1024;

    private final int capacity;
    private final ArrayList<PhysicsBackendEvent> events;
    private int droppedEventCount;

    public PhysicsBackendEventBuffer() {
        this(DEFAULT_CAPACITY);
    }

    public PhysicsBackendEventBuffer(int capacity) {
        this.capacity = Math.max(0, capacity);
        this.events = new ArrayList<>(Math.min(this.capacity, DEFAULT_CAPACITY));
    }

    @Override
    public int capacity() {
        return capacity;
    }

    @Override
    public int size() {
        return events.size();
    }

    @Override
    public int droppedEventCount() {
        return droppedEventCount;
    }

    @Override
    public boolean offer(@Nonnull PhysicsBackendEvent event) {
        Objects.requireNonNull(event, "event");
        if (events.size() >= capacity) {
            droppedEventCount++;
            return false;
        }
        events.add(event);
        return true;
    }

    @Nonnull
    public PhysicsBackendEventBatch drain() {
        if (events.isEmpty() && droppedEventCount == 0) {
            return PhysicsBackendEventBatch.empty();
        }
        PhysicsBackendEventBatch batch =
            new PhysicsBackendEventBatch(List.copyOf(events), droppedEventCount);
        clear();
        return batch;
    }

    public void clear() {
        events.clear();
        droppedEventCount = 0;
    }
}
