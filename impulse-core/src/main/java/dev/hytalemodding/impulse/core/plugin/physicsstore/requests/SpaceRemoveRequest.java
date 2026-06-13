package dev.hytalemodding.impulse.core.plugin.physicsstore.requests;

import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nonnull;

/**
 * Copied request that removes one empty PhysicsStore space row and backend binding.
 */
public record SpaceRemoveRequest(@Nonnull UUID requestUuid,
                                 @Nonnull UUID spaceUuid) implements PhysicsStoreRequest {

    public SpaceRemoveRequest {
        Objects.requireNonNull(requestUuid, "requestUuid");
        Objects.requireNonNull(spaceUuid, "spaceUuid");
    }

    @Nonnull
    public static SpaceRemoveRequest of(@Nonnull UUID spaceUuid) {
        return new SpaceRemoveRequest(UUID.randomUUID(), spaceUuid);
    }
}
