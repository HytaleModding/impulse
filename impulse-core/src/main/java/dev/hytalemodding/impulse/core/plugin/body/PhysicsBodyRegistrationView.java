package dev.hytalemodding.impulse.core.plugin.body;

import dev.hytalemodding.impulse.api.SpaceId;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Immutable body registration metadata safe for public and off-owner callers.
 */
public record PhysicsBodyRegistrationView(@Nonnull PhysicsBodyId id,
    @Nonnull SpaceId spaceId,
    @Nonnull PhysicsBodyKind kind,
    @Nonnull PhysicsBodyPersistenceMode persistenceMode) {

    @Nullable
    public static PhysicsBodyRegistrationView from(@Nullable PhysicsBodyRegistration registration) {
        if (registration == null) {
            return null;
        }
        return new PhysicsBodyRegistrationView(registration.id(),
            registration.spaceId(),
            registration.kind(),
            registration.persistenceMode());
    }
}
