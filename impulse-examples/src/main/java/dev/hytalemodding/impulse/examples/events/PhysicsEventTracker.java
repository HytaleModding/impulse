package dev.hytalemodding.impulse.examples.events;

import dev.hytalemodding.impulse.core.plugin.events.PhysicsEventFrame;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.Nonnull;

public final class PhysicsEventTracker {

    private static final AtomicLong TRACKED_FRAMES = new AtomicLong();
    private static final AtomicLong TRACKED_PHYSICS_EVENTS = new AtomicLong();
    private static final AtomicLong TRACKED_CONTACTS = new AtomicLong();
    private static final AtomicLong TRACKED_BODY_ACTIVATIONS = new AtomicLong();
    private static final AtomicLong TRACKED_JOINT_BREAKS = new AtomicLong();
    private static final AtomicLong TRACKED_DROPPED_BACKEND_EVENTS = new AtomicLong();
    private static final AtomicReference<PhysicsEventFrame> LATEST_FRAME = new AtomicReference<>();

    private PhysicsEventTracker() {
    }

    public static void reset() {
        TRACKED_FRAMES.set(0L);
        TRACKED_PHYSICS_EVENTS.set(0L);
        TRACKED_CONTACTS.set(0L);
        TRACKED_BODY_ACTIVATIONS.set(0L);
        TRACKED_JOINT_BREAKS.set(0L);
        TRACKED_DROPPED_BACKEND_EVENTS.set(0L);
        LATEST_FRAME.set(null);
    }

    public static void record(@Nonnull PhysicsEventFrame frame) {
        PhysicsEventFrame eventFrame = Objects.requireNonNull(frame, "frame");
        PhysicsEventSummary.EventCounts counts = PhysicsEventSummary.countEvents(eventFrame);
        TRACKED_FRAMES.incrementAndGet();
        TRACKED_PHYSICS_EVENTS.addAndGet(eventFrame.physicsEventCount());
        TRACKED_CONTACTS.addAndGet(counts.contacts());
        TRACKED_BODY_ACTIVATIONS.addAndGet(counts.bodyActivations());
        TRACKED_JOINT_BREAKS.addAndGet(counts.jointBreaks());
        TRACKED_DROPPED_BACKEND_EVENTS.addAndGet(eventFrame.droppedBackendEventCount());
        LATEST_FRAME.set(eventFrame);
    }

    @Nonnull
    public static String snapshot() {
        StringBuilder builder = new StringBuilder("ECS tracked physics events: trackedFrames=")
            .append(TRACKED_FRAMES.get())
            .append(" trackedPhysicsEvents=")
            .append(TRACKED_PHYSICS_EVENTS.get())
            .append(" trackedContacts=")
            .append(TRACKED_CONTACTS.get())
            .append(" trackedBodyActivations=")
            .append(TRACKED_BODY_ACTIVATIONS.get())
            .append(" trackedJointBreaks=")
            .append(TRACKED_JOINT_BREAKS.get())
            .append(" totalDroppedBackendEvents=")
            .append(TRACKED_DROPPED_BACKEND_EVENTS.get());
        PhysicsEventFrame frame = LATEST_FRAME.get();
        if (frame == null) {
            return builder.append(" latest=none").toString();
        }
        return builder.append(" latest=")
            .append(PhysicsEventSummary.format(frame))
            .toString();
    }
}
