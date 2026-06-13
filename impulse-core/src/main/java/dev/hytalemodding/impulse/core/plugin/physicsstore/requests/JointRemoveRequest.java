package dev.hytalemodding.impulse.core.plugin.physicsstore.requests;

import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nonnull;

/**
 * Copied request that removes one joint row from PhysicsStore.
 */
public record JointRemoveRequest(@Nonnull UUID requestUuid,
                                 @Nonnull UUID jointUuid) implements PhysicsStoreRequest {

    public JointRemoveRequest {
        Objects.requireNonNull(requestUuid, "requestUuid");
        Objects.requireNonNull(jointUuid, "jointUuid");
    }

    @Nonnull
    public static JointRemoveRequest of(@Nonnull UUID jointUuid) {
        return new JointRemoveRequest(UUID.randomUUID(), jointUuid);
    }
}
