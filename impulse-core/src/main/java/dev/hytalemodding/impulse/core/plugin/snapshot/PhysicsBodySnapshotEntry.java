package dev.hytalemodding.impulse.core.plugin.snapshot;

import dev.hytalemodding.impulse.api.PhysicsBodySnapshot;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyKind;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyPersistenceMode;
import javax.annotation.Nonnull;

/**
 * Snapshot query result carrying a body's stable key, latest snapshot, and registration metadata.
 */
public record PhysicsBodySnapshotEntry(@Nonnull RigidBodyKey bodyKey,
    @Nonnull PhysicsBodySnapshot snapshot,
    @Nonnull SpaceId spaceId,
    @Nonnull PhysicsBodyKind kind,
    @Nonnull PhysicsBodyPersistenceMode persistenceMode) {

}
