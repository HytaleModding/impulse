package dev.hytalemodding.impulse.core.plugin.execution;

import dev.hytalemodding.impulse.api.PhysicsBody;
import dev.hytalemodding.impulse.api.PhysicsSpace;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyId;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyKind;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyPersistenceMode;
import java.util.Collection;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Owner-scoped resolver for live backend physics objects.
 *
 * <p>Instances are passed into physics-owner callbacks and should not be retained. Each method
 * still validates owner access, so leaking this resolver does not grant world-thread access to live
 * backend state.</p>
 */
public interface PhysicsOwnerAccess {

    @Nullable
    PhysicsSpace getSpace(@Nonnull SpaceId spaceId);

    @Nonnull
    PhysicsSpace requireSpace(@Nonnull SpaceId spaceId);

    @Nonnull
    Collection<PhysicsSpace> getSpaces();

    @Nullable
    PhysicsBody getBody(@Nonnull PhysicsBodyId bodyId);

    @Nonnull
    PhysicsBody requireBody(@Nonnull PhysicsBodyId bodyId);

    @Nullable
    PhysicsBodyId getBodyId(@Nonnull PhysicsBody body);

    @Nonnull
    PhysicsBodyId addBody(@Nonnull SpaceId spaceId,
        @Nonnull PhysicsBody body,
        @Nonnull PhysicsBodyKind kind,
        @Nonnull PhysicsBodyPersistenceMode persistenceMode);

    @Nonnull
    PhysicsBodyId addBody(@Nonnull PhysicsBodyId bodyId,
        @Nonnull SpaceId spaceId,
        @Nonnull PhysicsBody body,
        @Nonnull PhysicsBodyKind kind,
        @Nonnull PhysicsBodyPersistenceMode persistenceMode);
}
