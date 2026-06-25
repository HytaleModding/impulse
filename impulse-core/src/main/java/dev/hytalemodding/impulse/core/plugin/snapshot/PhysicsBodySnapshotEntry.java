package dev.hytalemodding.impulse.core.plugin.snapshot;

import dev.hytalemodding.impulse.api.PhysicsBodySnapshot;
import dev.hytalemodding.impulse.api.SpaceId;
import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nonnull;

/**
 * Snapshot query result carrying a body's durable UUID, latest snapshot, and owning space.
 */
public record PhysicsBodySnapshotEntry(@Nonnull UUID bodyUuid,
    @Nonnull PhysicsBodySnapshot snapshot,
    @Nonnull SpaceId spaceId) {

    public PhysicsBodySnapshotEntry {
        Objects.requireNonNull(bodyUuid, "bodyUuid");
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(spaceId, "spaceId");
    }
}
