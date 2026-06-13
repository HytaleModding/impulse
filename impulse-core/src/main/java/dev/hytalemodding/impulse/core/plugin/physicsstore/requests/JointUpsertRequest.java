package dev.hytalemodding.impulse.core.plugin.physicsstore.requests;

import dev.hytalemodding.impulse.core.plugin.physicsstore.components.JointComponent;
import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nonnull;

/**
 * Copied request that authors one joint row in PhysicsStore.
 */
public record JointUpsertRequest(@Nonnull UUID requestUuid,
                                 @Nonnull UUID jointUuid,
                                 @Nonnull JointComponent joint) implements PhysicsStoreRequest {

    public JointUpsertRequest {
        Objects.requireNonNull(requestUuid, "requestUuid");
        Objects.requireNonNull(jointUuid, "jointUuid");
        joint = Objects.requireNonNull(joint, "joint").clone();
    }

    @Nonnull
    public static JointUpsertRequest of(@Nonnull UUID jointUuid,
        @Nonnull JointComponent joint) {
        return new JointUpsertRequest(UUID.randomUUID(), jointUuid, joint);
    }
}
