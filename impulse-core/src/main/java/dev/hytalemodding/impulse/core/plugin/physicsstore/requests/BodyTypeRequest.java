package dev.hytalemodding.impulse.core.plugin.physicsstore.requests;

import dev.hytalemodding.impulse.api.PhysicsBodyType;
import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nonnull;

/**
 * Copied request that changes a PhysicsStore body's canonical motion type.
 */
public record BodyTypeRequest(@Nonnull UUID requestUuid,
                              @Nonnull UUID bodyUuid,
                              @Nonnull PhysicsBodyType bodyType,
                              boolean activate) implements PhysicsStoreRequest {

    public BodyTypeRequest {
        Objects.requireNonNull(requestUuid, "requestUuid");
        Objects.requireNonNull(bodyUuid, "bodyUuid");
        Objects.requireNonNull(bodyType, "bodyType");
    }

    @Nonnull
    public static BodyTypeRequest of(@Nonnull UUID bodyUuid,
        @Nonnull PhysicsBodyType bodyType,
        boolean activate) {
        return new BodyTypeRequest(UUID.randomUUID(), bodyUuid, bodyType, activate);
    }
}
