package dev.hytalemodding.impulse.core.plugin.snapshot;

import dev.hytalemodding.impulse.api.PhysicsBodySnapshot;
import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.PhysicsAxis;
import dev.hytalemodding.impulse.api.ShapeType;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyId;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyKind;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyPersistenceMode;
import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Immutable body state published as part of an async snapshot frame.
 *
 * <p>This type deliberately carries Impulse body identity instead of backend
 * body handles so published frames can be read away from the owner thread.</p>
 */
public record PublishedPhysicsBodySnapshot(@Nonnull PhysicsBodyId bodyId,
                                           @Nonnull SpaceId spaceId,
                                           long frameEpoch,
                                           long worldEpoch,
                                           long spaceEpoch,
                                           long registrationGeneration,
                                           @Nonnull PhysicsBodyKind kind,
                                           @Nonnull PhysicsBodyPersistenceMode persistenceMode,
                                           @Nonnull Vector3f position,
                                           @Nonnull Quaternionf rotation,
                                           @Nonnull Vector3f linearVelocity,
                                           @Nonnull Vector3f angularVelocity,
                                           @Nonnull PhysicsBodyType bodyType,
                                           boolean sleeping,
                                           boolean sensor,
                                           float centerOfMassOffsetY,
                                           @Nonnull ShapeType shapeType,
                                           @Nullable Vector3f boxHalfExtents,
                                           float sphereRadius,
                                           float halfHeight,
                                           @Nonnull PhysicsAxis shapeAxis,
                                           float planeGroundY) {

    public PublishedPhysicsBodySnapshot {
        Objects.requireNonNull(bodyId, "bodyId");
        Objects.requireNonNull(spaceId, "spaceId");
        requireNonNegativeEpoch(frameEpoch, "frameEpoch");
        requireNonNegativeEpoch(worldEpoch, "worldEpoch");
        requireNonNegativeEpoch(spaceEpoch, "spaceEpoch");
        requireNonNegativeEpoch(registrationGeneration, "registrationGeneration");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(persistenceMode, "persistenceMode");
        position = new Vector3f(Objects.requireNonNull(position, "position"));
        rotation = new Quaternionf(Objects.requireNonNull(rotation, "rotation"));
        linearVelocity = new Vector3f(Objects.requireNonNull(linearVelocity, "linearVelocity"));
        angularVelocity = new Vector3f(Objects.requireNonNull(angularVelocity, "angularVelocity"));
        Objects.requireNonNull(bodyType, "bodyType");
        Objects.requireNonNull(shapeType, "shapeType");
        boxHalfExtents = boxHalfExtents != null ? new Vector3f(boxHalfExtents) : null;
        Objects.requireNonNull(shapeAxis, "shapeAxis");
    }

    @Nonnull
    public static PublishedPhysicsBodySnapshot from(@Nonnull PhysicsBodyId bodyId,
        @Nonnull SpaceId spaceId,
        long frameEpoch,
        long worldEpoch,
        long spaceEpoch,
        long registrationGeneration,
        @Nonnull PhysicsBodyKind kind,
        @Nonnull PhysicsBodyPersistenceMode persistenceMode,
        @Nonnull PhysicsBodySnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        return new PublishedPhysicsBodySnapshot(bodyId,
            spaceId,
            frameEpoch,
            worldEpoch,
            spaceEpoch,
            registrationGeneration,
            kind,
            persistenceMode,
            snapshot.position(),
            snapshot.rotation(),
            snapshot.linearVelocity(),
            snapshot.angularVelocity(),
            snapshot.bodyType(),
            snapshot.sleeping(),
            snapshot.sensor(),
            snapshot.centerOfMassOffsetY(),
            snapshot.shapeType(),
            snapshot.boxHalfExtents(),
            snapshot.sphereRadius(),
            snapshot.halfHeight(),
            snapshot.shapeAxis(),
            snapshot.planeGroundY());
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

    @Nullable
    @Override
    public Vector3f boxHalfExtents() {
        return boxHalfExtents != null ? new Vector3f(boxHalfExtents) : null;
    }

    public boolean isStatic() {
        return bodyType == PhysicsBodyType.STATIC;
    }

    public boolean isDynamic() {
        return bodyType == PhysicsBodyType.DYNAMIC;
    }

    public boolean isKinematic() {
        return bodyType == PhysicsBodyType.KINEMATIC;
    }

    private static void requireNonNegativeEpoch(long epoch, @Nonnull String label) {
        if (epoch < 0L) {
            throw new IllegalArgumentException(label + " cannot be negative");
        }
    }
}
