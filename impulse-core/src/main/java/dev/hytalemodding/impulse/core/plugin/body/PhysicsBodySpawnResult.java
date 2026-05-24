package dev.hytalemodding.impulse.core.plugin.body;

import dev.hytalemodding.impulse.api.SpaceId;
import javax.annotation.Nonnull;

/**
 * Body-id-first result returned by {@link PhysicsBodies#spawn}.
 */
public record PhysicsBodySpawnResult(@Nonnull PhysicsBodyId bodyId,
                                     @Nonnull SpaceId spaceId,
                                     @Nonnull PhysicsBodyKind kind,
                                     @Nonnull PhysicsBodyPersistenceMode persistenceMode) {
}
