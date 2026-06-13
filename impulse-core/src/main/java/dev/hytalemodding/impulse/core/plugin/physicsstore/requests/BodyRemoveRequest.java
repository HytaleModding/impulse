package dev.hytalemodding.impulse.core.plugin.physicsstore.requests;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nonnull;

/**
 * Copied request that removes one body graph from PhysicsStore.
 */
public record BodyRemoveRequest(@Nonnull UUID requestUuid,
                                @Nonnull UUID bodyUuid,
                                @Nonnull List<UUID> ownedRowUuids) implements PhysicsStoreRequest {

    public BodyRemoveRequest {
        Objects.requireNonNull(requestUuid, "requestUuid");
        Objects.requireNonNull(bodyUuid, "bodyUuid");
        ownedRowUuids = List.copyOf(Objects.requireNonNull(ownedRowUuids, "ownedRowUuids"));
    }

    @Nonnull
    public static BodyRemoveRequest of(@Nonnull UUID bodyUuid) {
        return owned(bodyUuid, List.of());
    }

    @Nonnull
    public static BodyRemoveRequest owned(@Nonnull UUID bodyUuid,
        @Nonnull List<UUID> ownedRowUuids) {
        return new BodyRemoveRequest(UUID.randomUUID(), bodyUuid, ownedRowUuids);
    }
}
