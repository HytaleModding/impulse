package dev.hytalemodding.impulse.core.plugin.physicsstore.requests;

import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nonnull;

/**
 * Copied request that explicitly wakes or sleeps a bound PhysicsStore body.
 */
public record BodyActivationRequest(@Nonnull UUID requestUuid,
                                    @Nonnull UUID bodyUuid,
                                    @Nonnull Action action) implements PhysicsStoreRequest {

    public BodyActivationRequest {
        Objects.requireNonNull(requestUuid, "requestUuid");
        Objects.requireNonNull(bodyUuid, "bodyUuid");
        Objects.requireNonNull(action, "action");
    }

    @Nonnull
    public static BodyActivationRequest wake(@Nonnull UUID bodyUuid) {
        return new BodyActivationRequest(UUID.randomUUID(), bodyUuid, Action.WAKE);
    }

    @Nonnull
    public static BodyActivationRequest sleep(@Nonnull UUID bodyUuid) {
        return new BodyActivationRequest(UUID.randomUUID(), bodyUuid, Action.SLEEP);
    }

    public enum Action {
        WAKE,
        SLEEP
    }
}
