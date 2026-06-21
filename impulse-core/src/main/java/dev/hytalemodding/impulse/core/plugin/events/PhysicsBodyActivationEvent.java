package dev.hytalemodding.impulse.core.plugin.events;

import dev.hytalemodding.impulse.api.PhysicsBodyActivationPhase;
import dev.hytalemodding.impulse.api.SpaceId;
import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nonnull;

/**
 * Stable body activation event copied from a backend event batch.
 *
 * @deprecated Physics event plugin API is deprecated without replacement.
 */
@Deprecated(since = "0.1.0", forRemoval = false)
public record PhysicsBodyActivationEvent(@Nonnull SpaceId spaceId,
                                         @Nonnull PhysicsBodyActivationPhase phase,
                                         @Nonnull UUID bodyUuid) implements PhysicsFrameEvent {

    public PhysicsBodyActivationEvent {
        Objects.requireNonNull(spaceId, "spaceId");
        Objects.requireNonNull(phase, "phase");
        Objects.requireNonNull(bodyUuid, "bodyUuid");
    }

    @Nonnull
    @Override
    public PhysicsFrameEventKind kind() {
        return PhysicsFrameEventKind.BODY_ACTIVATION;
    }
}
