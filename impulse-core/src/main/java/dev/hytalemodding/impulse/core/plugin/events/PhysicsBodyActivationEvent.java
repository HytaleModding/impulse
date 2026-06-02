package dev.hytalemodding.impulse.core.plugin.events;

import dev.hytalemodding.impulse.api.PhysicsBodyActivationPhase;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import java.util.Objects;
import javax.annotation.Nonnull;

/**
 * Stable body activation event copied from a backend event batch.
 */
public record PhysicsBodyActivationEvent(@Nonnull SpaceId spaceId,
                                         @Nonnull PhysicsBodyActivationPhase phase,
                                         @Nonnull RigidBodyKey bodyKey) implements PhysicsFrameEvent {

    public PhysicsBodyActivationEvent {
        Objects.requireNonNull(spaceId, "spaceId");
        Objects.requireNonNull(phase, "phase");
        Objects.requireNonNull(bodyKey, "bodyKey");
    }

    @Nonnull
    @Override
    public PhysicsFrameEventKind kind() {
        return PhysicsFrameEventKind.BODY_ACTIVATION;
    }
}
