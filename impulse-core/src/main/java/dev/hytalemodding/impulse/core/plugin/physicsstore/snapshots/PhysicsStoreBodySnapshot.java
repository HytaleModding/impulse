package dev.hytalemodding.impulse.core.plugin.physicsstore.snapshots;

import dev.hytalemodding.impulse.api.PhysicsBodyType;
import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nonnull;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Copied body snapshot published out of PhysicsStore for projection and queries.
 */
public record PhysicsStoreBodySnapshot(@Nonnull UUID bodyUuid,
                                       @Nonnull UUID spaceUuid,
                                       @Nonnull PhysicsBodyType bodyType,
                                       @Nonnull Vector3f position,
                                       @Nonnull Quaternionf rotation,
                                       @Nonnull Vector3f linearVelocity,
                                       @Nonnull Vector3f angularVelocity,
                                       boolean sleeping) {

    public PhysicsStoreBodySnapshot {
        Objects.requireNonNull(bodyUuid, "bodyUuid");
        Objects.requireNonNull(spaceUuid, "spaceUuid");
        Objects.requireNonNull(bodyType, "bodyType");
        position = new Vector3f(Objects.requireNonNull(position, "position"));
        rotation = new Quaternionf(Objects.requireNonNull(rotation, "rotation"));
        linearVelocity = new Vector3f(Objects.requireNonNull(linearVelocity, "linearVelocity"));
        angularVelocity = new Vector3f(Objects.requireNonNull(angularVelocity, "angularVelocity"));
    }

    @Nonnull
    @Override
    public Vector3f position() {
        return new Vector3f(position);
    }

    @Nonnull
    @Override
    public Quaternionf rotation() {
        return new Quaternionf(rotation);
    }

    @Nonnull
    @Override
    public Vector3f linearVelocity() {
        return new Vector3f(linearVelocity);
    }

    @Nonnull
    @Override
    public Vector3f angularVelocity() {
        return new Vector3f(angularVelocity);
    }
}
