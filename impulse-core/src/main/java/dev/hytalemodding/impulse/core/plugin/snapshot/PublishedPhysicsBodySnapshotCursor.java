package dev.hytalemodding.impulse.core.plugin.snapshot;

import dev.hytalemodding.impulse.api.PhysicsAxis;
import dev.hytalemodding.impulse.api.PhysicsBodySnapshot;
import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.ShapeType;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyKind;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyPersistenceMode;
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import javax.annotation.Nonnull;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Allocation-light body view used while traversing a published snapshot frame.
 *
 * <p>Consumers must not retain cursor instances beyond the callback. Use
 * {@link #toBodySnapshot()} or {@link PublishedPhysicsBodySnapshot} when a
 * durable value object is needed.</p>
 */
public interface PublishedPhysicsBodySnapshotCursor {

    @Nonnull
    RigidBodyKey bodyKey();

    @Nonnull
    default RigidBodyKey bodyId() {
        return bodyKey();
    }

    @Nonnull
    SpaceId spaceId();

    long frameEpoch();

    long worldEpoch();

    long spaceEpoch();

    long registrationGeneration();

    @Nonnull
    PhysicsBodyKind kind();

    @Nonnull
    PhysicsBodyPersistenceMode persistenceMode();

    float positionX();

    float positionY();

    float positionZ();

    float rotationX();

    float rotationY();

    float rotationZ();

    float rotationW();

    float linearVelocityX();

    float linearVelocityY();

    float linearVelocityZ();

    float angularVelocityX();

    float angularVelocityY();

    float angularVelocityZ();

    @Nonnull
    PhysicsBodyType bodyType();

    boolean sleeping();

    boolean sensor();

    float centerOfMassOffsetY();

    @Nonnull
    ShapeType shapeType();

    boolean hasBoxHalfExtents();

    float boxHalfExtentX();

    float boxHalfExtentY();

    float boxHalfExtentZ();

    float sphereRadius();

    float halfHeight();

    @Nonnull
    PhysicsAxis shapeAxis();

    @Nonnull
    default Vector3f copyPositionTo(@Nonnull Vector3f target) {
        return target.set(positionX(), positionY(), positionZ());
    }

    @Nonnull
    default Quaternionf copyRotationTo(@Nonnull Quaternionf target) {
        return target.set(rotationX(), rotationY(), rotationZ(), rotationW());
    }

    @Nonnull
    default Vector3f copyLinearVelocityTo(@Nonnull Vector3f target) {
        return target.set(linearVelocityX(), linearVelocityY(), linearVelocityZ());
    }

    @Nonnull
    default Vector3f copyAngularVelocityTo(@Nonnull Vector3f target) {
        return target.set(angularVelocityX(), angularVelocityY(), angularVelocityZ());
    }

    @Nonnull
    default Vector3f copyBoxHalfExtentsTo(@Nonnull Vector3f target) {
        if (!hasBoxHalfExtents()) {
            return target.zero();
        }
        return target.set(boxHalfExtentX(), boxHalfExtentY(), boxHalfExtentZ());
    }

    @Nonnull
    default PhysicsBodySnapshot toBodySnapshot() {
        return PhysicsBodySnapshot.of(positionX(),
            positionY(),
            positionZ(),
            rotationX(),
            rotationY(),
            rotationZ(),
            rotationW(),
            linearVelocityX(),
            linearVelocityY(),
            linearVelocityZ(),
            angularVelocityX(),
            angularVelocityY(),
            angularVelocityZ(),
            bodyType(),
            sleeping(),
            sensor(),
            centerOfMassOffsetY(),
            shapeType(),
            hasBoxHalfExtents(),
            boxHalfExtentX(),
            boxHalfExtentY(),
            boxHalfExtentZ(),
            sphereRadius(),
            halfHeight(),
            shapeAxis());
    }

    default boolean matchesSnapshot(@Nonnull PhysicsBodySnapshot snapshot) {
        if (bodyType() != snapshot.bodyType()
            || sleeping() != snapshot.sleeping()
            || sensor() != snapshot.sensor()
            || Float.compare(centerOfMassOffsetY(), snapshot.centerOfMassOffsetY()) != 0
            || shapeType() != snapshot.shapeType()
            || Float.compare(sphereRadius(), snapshot.sphereRadius()) != 0
            || Float.compare(halfHeight(), snapshot.halfHeight()) != 0
            || shapeAxis() != snapshot.shapeAxis()
            || Float.compare(positionX(), snapshot.positionX()) != 0
            || Float.compare(positionY(), snapshot.positionY()) != 0
            || Float.compare(positionZ(), snapshot.positionZ()) != 0
            || Float.compare(linearVelocityX(), snapshot.linearVelocityX()) != 0
            || Float.compare(linearVelocityY(), snapshot.linearVelocityY()) != 0
            || Float.compare(linearVelocityZ(), snapshot.linearVelocityZ()) != 0
            || Float.compare(angularVelocityX(), snapshot.angularVelocityX()) != 0
            || Float.compare(angularVelocityY(), snapshot.angularVelocityY()) != 0
            || Float.compare(angularVelocityZ(), snapshot.angularVelocityZ()) != 0
            || Float.compare(rotationX(), snapshot.rotationX()) != 0
            || Float.compare(rotationY(), snapshot.rotationY()) != 0
            || Float.compare(rotationZ(), snapshot.rotationZ()) != 0
            || Float.compare(rotationW(), snapshot.rotationW()) != 0) {
            return false;
        }

        if (!hasBoxHalfExtents()) {
            return !snapshot.hasBoxHalfExtents();
        }
        return snapshot.hasBoxHalfExtents()
            && Float.compare(boxHalfExtentX(), snapshot.boxHalfExtentX()) == 0
            && Float.compare(boxHalfExtentY(), snapshot.boxHalfExtentY()) == 0
            && Float.compare(boxHalfExtentZ(), snapshot.boxHalfExtentZ()) == 0;
    }
}
