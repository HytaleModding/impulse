package dev.hytalemodding.impulse.core.plugin.body;

import dev.hytalemodding.impulse.api.SpaceId;
import javax.annotation.Nonnull;

/**
 * Immutable body registration metadata safe for public and off-owner callers.
 */
public record PhysicsBodyRegistrationView(@Nonnull PhysicsBodyId id,
    @Nonnull SpaceId spaceId,
    @Nonnull PhysicsBodyKind kind,
    @Nonnull PhysicsBodyPersistenceMode persistenceMode) {
}
