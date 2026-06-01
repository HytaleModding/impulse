package dev.hytalemodding.impulse.examples.events;

import dev.hytalemodding.impulse.core.plugin.events.PhysicsBodyActivationEvent;
import dev.hytalemodding.impulse.core.plugin.events.PhysicsContactEvent;
import dev.hytalemodding.impulse.core.plugin.events.PhysicsEventFrame;
import dev.hytalemodding.impulse.core.plugin.events.PhysicsFrameEvent;
import dev.hytalemodding.impulse.core.plugin.events.PhysicsJointBreakEvent;
import java.util.Locale;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class PhysicsEventSummary {

    private PhysicsEventSummary() {
    }

    @Nonnull
    public static String format(@Nonnull PhysicsEventFrame frame) {
        EventCounts counts = countEvents(frame);
        StringBuilder builder = new StringBuilder("Latest physics event frame ")
            .append(frame.frameSequence())
            .append(": physicsEvents=")
            .append(frame.physicsEventCount())
            .append(" contacts=")
            .append(counts.contacts())
            .append(" bodyActivations=")
            .append(counts.bodyActivations())
            .append(" jointBreaks=")
            .append(counts.jointBreaks())
            .append(" droppedBackendEvents=")
            .append(frame.droppedBackendEventCount());
        PhysicsContactEvent firstContact = counts.firstContact();
        if (firstContact != null) {
            builder.append(" firstContact=")
                .append(firstContact.phase().name().toLowerCase(Locale.ROOT))
                .append(" space=")
                .append(firstContact.spaceId().value())
                .append(" bodyA=")
                .append(firstContact.bodyAKey())
                .append(" bodyB=")
                .append(firstContact.bodyBKey())
                .append(" distance=")
                .append(formatFloat(firstContact.distance()))
                .append(" impulse=")
                .append(formatFloat(firstContact.impulse()));
        }
        return builder.toString();
    }

    @Nonnull
    static EventCounts countEvents(@Nonnull PhysicsEventFrame frame) {
        int contacts = 0;
        int bodyActivations = 0;
        int jointBreaks = 0;
        PhysicsContactEvent firstContact = null;
        for (PhysicsFrameEvent event : frame.physicsEvents()) {
            if (event instanceof PhysicsContactEvent contactEvent) {
                contacts++;
                if (firstContact == null) {
                    firstContact = contactEvent;
                }
            } else if (event instanceof PhysicsBodyActivationEvent) {
                bodyActivations++;
            } else if (event instanceof PhysicsJointBreakEvent) {
                jointBreaks++;
            }
        }
        return new EventCounts(contacts, bodyActivations, jointBreaks, firstContact);
    }

    @Nonnull
    private static String formatFloat(float value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }

    record EventCounts(int contacts,
        int bodyActivations,
        int jointBreaks,
        @Nullable PhysicsContactEvent firstContact) {
    }
}
