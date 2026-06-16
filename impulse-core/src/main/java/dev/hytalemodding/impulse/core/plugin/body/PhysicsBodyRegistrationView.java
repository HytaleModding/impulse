package dev.hytalemodding.impulse.core.plugin.body;

import dev.hytalemodding.impulse.api.SpaceId;
import java.util.UUID;
import javax.annotation.Nonnull;

/**
 * Immutable body registration metadata safe for public and off-owner callers.
 */
public record PhysicsBodyRegistrationView(@Nonnull RigidBodyKey bodyKey,
    @Nonnull SpaceId spaceId,
    @Nonnull PhysicsBodyKind kind,
    @Nonnull PhysicsBodyPersistenceMode persistenceMode) {

    @Nonnull
    public UUID bodyUuid() {
        return bodyKey.value();
    }
}
