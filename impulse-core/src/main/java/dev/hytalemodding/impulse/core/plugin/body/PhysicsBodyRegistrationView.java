package dev.hytalemodding.impulse.core.plugin.body;

import dev.hytalemodding.impulse.api.SpaceId;
import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nonnull;

/**
 * Immutable body registration metadata safe for public and off-owner callers.
 */
public record PhysicsBodyRegistrationView(@Nonnull UUID bodyUuid,
    @Nonnull SpaceId spaceId,
    @Nonnull PhysicsBodyKind kind,
    @Nonnull PhysicsBodyPersistenceMode persistenceMode) {

    public PhysicsBodyRegistrationView {
        Objects.requireNonNull(bodyUuid, "bodyUuid");
        Objects.requireNonNull(spaceId, "spaceId");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(persistenceMode, "persistenceMode");
    }

    public PhysicsBodyRegistrationView(@Nonnull RigidBodyKey bodyKey,
        @Nonnull SpaceId spaceId,
        @Nonnull PhysicsBodyKind kind,
        @Nonnull PhysicsBodyPersistenceMode persistenceMode) {
        this(Objects.requireNonNull(bodyKey, "bodyKey").value(), spaceId, kind, persistenceMode);
    }

    @Nonnull
    public RigidBodyKey bodyKey() {
        return RigidBodyKey.of(bodyUuid);
    }
}
