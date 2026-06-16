package dev.hytalemodding.impulse.core.plugin.snapshot;

import dev.hytalemodding.impulse.api.PhysicsBodySnapshot;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyKind;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyPersistenceMode;
import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nonnull;

/**
 * Snapshot query result carrying a body's durable UUID, latest snapshot, and registration metadata.
 */
public record PhysicsBodySnapshotEntry(@Nonnull UUID bodyUuid,
    @Nonnull PhysicsBodySnapshot snapshot,
    @Nonnull SpaceId spaceId,
    @Nonnull PhysicsBodyKind kind,
    @Nonnull PhysicsBodyPersistenceMode persistenceMode) {

    public PhysicsBodySnapshotEntry {
        Objects.requireNonNull(bodyUuid, "bodyUuid");
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(spaceId, "spaceId");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(persistenceMode, "persistenceMode");
    }

    public PhysicsBodySnapshotEntry(@Nonnull RigidBodyKey bodyKey,
        @Nonnull PhysicsBodySnapshot snapshot,
        @Nonnull SpaceId spaceId,
        @Nonnull PhysicsBodyKind kind,
        @Nonnull PhysicsBodyPersistenceMode persistenceMode) {
        this(Objects.requireNonNull(bodyKey, "bodyKey").value(),
            snapshot,
            spaceId,
            kind,
            persistenceMode);
    }

    @Nonnull
    public RigidBodyKey bodyKey() {
        return RigidBodyKey.of(bodyUuid);
    }
}
