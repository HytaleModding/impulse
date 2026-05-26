package dev.hytalemodding.impulse.core.plugin.body;

import dev.hytalemodding.impulse.api.PhysicsBody;
import dev.hytalemodding.impulse.api.SpaceId;
import javax.annotation.Nonnull;

/**
 * Owner-thread registration for a live backend body.
 *
 * <p>This record carries a {@link PhysicsBody} handle and must not be retained or read by
 * world-thread systems when a physics worker is attached. Use {@link PhysicsBodyRegistrationView}
 * for public/off-owner metadata checks.</p>
 */
public record PhysicsBodyRegistration(@Nonnull PhysicsBodyId id,
    @Nonnull PhysicsBody body,
    @Nonnull SpaceId spaceId,
    @Nonnull PhysicsBodyKind kind,
    @Nonnull PhysicsBodyPersistenceMode persistenceMode) {
}
