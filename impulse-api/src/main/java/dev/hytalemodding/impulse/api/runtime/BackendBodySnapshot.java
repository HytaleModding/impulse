package dev.hytalemodding.impulse.api.runtime;

import dev.hytalemodding.impulse.api.PhysicsAxis;
import dev.hytalemodding.impulse.api.PhysicsBodySnapshot;
import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.ShapeType;
import java.util.Objects;
import javax.annotation.Nonnull;

/**
 * Backend-local body snapshot keyed by transient backend body id.
 */
public record BackendBodySnapshot(long bodyId,
                                  @Nonnull PhysicsBodySnapshot snapshot) {

    public BackendBodySnapshot {
        Objects.requireNonNull(snapshot, "snapshot");
    }

    @Nonnull
    public PhysicsBodySnapshot toPhysicsSnapshot() {
        return snapshot;
    }

    @Nonnull
    public PhysicsBodyType bodyType() {
        return snapshot.bodyType();
    }

    @Nonnull
    public ShapeType shapeType() {
        return snapshot.shapeType();
    }

    @Nonnull
    public PhysicsAxis shapeAxis() {
        return snapshot.shapeAxis();
    }
}
