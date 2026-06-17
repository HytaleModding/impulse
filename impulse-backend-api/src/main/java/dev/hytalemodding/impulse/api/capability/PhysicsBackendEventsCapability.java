package dev.hytalemodding.impulse.api.capability;

import dev.hytalemodding.impulse.api.PhysicsBackendEventKind;
import dev.hytalemodding.impulse.api.PhysicsContactPhase;
import java.util.Set;
import javax.annotation.Nonnull;

/**
 * Optional backend capability indicating which post-step backend events may be emitted.
 */
@Deprecated(forRemoval = true)
public interface PhysicsBackendEventsCapability extends PhysicsCapability {

    PhysicsCapabilityDescriptor DESCRIPTOR = new PhysicsCapabilityDescriptor(
        new PhysicsCapabilityId("impulse:backend_events"),
        "Backend events",
        "Reports backend event kinds emitted during physics steps");

    @Nonnull
    Set<PhysicsBackendEventKind> supportedEventKinds();

    default boolean supportsEventKind(@Nonnull PhysicsBackendEventKind kind) {
        return supportedEventKinds().contains(kind);
    }

    default boolean supportsContactPhase(@Nonnull PhysicsContactPhase phase) {
        return supportsEventKind(phase.eventKind());
    }
}
