package dev.hytalemodding.impulse.core.plugin.snapshot;

import dev.hytalemodding.impulse.api.PhysicsBodySnapshot;
import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.PhysicsAxis;
import dev.hytalemodding.impulse.api.ShapeType;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
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
 * <p>This type deliberately carries an Impulse body key instead of backend
 * body handles so published frames can be read away from the owner lane.</p>
 */
public final class PublishedPhysicsBodySnapshot implements PublishedPhysicsBodySnapshotCursor {

    @Nonnull
    private final RigidBodyKey bodyKey;
    @Nonnull
    private final SpaceId spaceId;
    private final long frameEpoch;
    private final long worldEpoch;
    private final long spaceEpoch;
    private final long registrationGeneration;
    @Nonnull
    private final PhysicsBodyKind kind;
    @Nonnull
    private final PhysicsBodyPersistenceMode persistenceMode;
    private final float positionX;
    private final float positionY;
    private final float positionZ;
    private final float rotationX;
    private final float rotationY;
    private final float rotationZ;
    private final float rotationW;
    private final float linearVelocityX;
    private final float linearVelocityY;
    private final float linearVelocityZ;
    private final float angularVelocityX;
    private final float angularVelocityY;
    private final float angularVelocityZ;
    @Nonnull
    private final PhysicsBodyType bodyType;
    private final boolean sleeping;
    private final boolean sensor;
    private final float centerOfMassOffsetY;
    @Nonnull
    private final ShapeType shapeType;
    private final boolean hasBoxHalfExtents;
    private final float boxHalfExtentX;
    private final float boxHalfExtentY;
    private final float boxHalfExtentZ;
    private final float sphereRadius;
    private final float halfHeight;
    @Nonnull
    private final PhysicsAxis shapeAxis;

    public PublishedPhysicsBodySnapshot(@Nonnull RigidBodyKey bodyKey,
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
        @Nonnull PhysicsAxis shapeAxis) {
        this.bodyKey = Objects.requireNonNull(bodyKey, "bodyKey");
        this.spaceId = Objects.requireNonNull(spaceId, "spaceId");
        requireNonNegativeEpoch(frameEpoch, "frameEpoch");
        requireNonNegativeEpoch(worldEpoch, "worldEpoch");
        requireNonNegativeEpoch(spaceEpoch, "spaceEpoch");
        requireNonNegativeEpoch(registrationGeneration, "registrationGeneration");
        this.frameEpoch = frameEpoch;
        this.worldEpoch = worldEpoch;
        this.spaceEpoch = spaceEpoch;
        this.registrationGeneration = registrationGeneration;
        this.kind = Objects.requireNonNull(kind, "kind");
        this.persistenceMode = Objects.requireNonNull(persistenceMode, "persistenceMode");
        Objects.requireNonNull(position, "position");
        this.positionX = position.x;
        this.positionY = position.y;
        this.positionZ = position.z;
        Objects.requireNonNull(rotation, "rotation");
        this.rotationX = rotation.x;
        this.rotationY = rotation.y;
        this.rotationZ = rotation.z;
        this.rotationW = rotation.w;
        Objects.requireNonNull(linearVelocity, "linearVelocity");
        this.linearVelocityX = linearVelocity.x;
        this.linearVelocityY = linearVelocity.y;
        this.linearVelocityZ = linearVelocity.z;
        Objects.requireNonNull(angularVelocity, "angularVelocity");
        this.angularVelocityX = angularVelocity.x;
        this.angularVelocityY = angularVelocity.y;
        this.angularVelocityZ = angularVelocity.z;
        this.bodyType = Objects.requireNonNull(bodyType, "bodyType");
        this.sleeping = sleeping;
        this.sensor = sensor;
        this.centerOfMassOffsetY = centerOfMassOffsetY;
        this.shapeType = Objects.requireNonNull(shapeType, "shapeType");
        if (boxHalfExtents != null) {
            hasBoxHalfExtents = true;
            boxHalfExtentX = boxHalfExtents.x;
            boxHalfExtentY = boxHalfExtents.y;
            boxHalfExtentZ = boxHalfExtents.z;
        } else {
            hasBoxHalfExtents = false;
            boxHalfExtentX = 0.0f;
            boxHalfExtentY = 0.0f;
            boxHalfExtentZ = 0.0f;
        }
        this.sphereRadius = sphereRadius;
        this.halfHeight = halfHeight;
        this.shapeAxis = Objects.requireNonNull(shapeAxis, "shapeAxis");
    }

    @Nonnull
    public static PublishedPhysicsBodySnapshot from(@Nonnull RigidBodyKey bodyKey,
        @Nonnull SpaceId spaceId,
        long frameEpoch,
        long worldEpoch,
        long spaceEpoch,
        long registrationGeneration,
        @Nonnull PhysicsBodyKind kind,
        @Nonnull PhysicsBodyPersistenceMode persistenceMode,
        @Nonnull PhysicsBodySnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        return new PublishedPhysicsBodySnapshot(bodyKey,
            spaceId,
            frameEpoch,
            worldEpoch,
            spaceEpoch,
            registrationGeneration,
            kind,
            persistenceMode,
            snapshot.positionX(),
            snapshot.positionY(),
            snapshot.positionZ(),
            snapshot.rotationX(),
            snapshot.rotationY(),
            snapshot.rotationZ(),
            snapshot.rotationW(),
            snapshot.linearVelocityX(),
            snapshot.linearVelocityY(),
            snapshot.linearVelocityZ(),
            snapshot.angularVelocityX(),
            snapshot.angularVelocityY(),
            snapshot.angularVelocityZ(),
            snapshot.bodyType(),
            snapshot.sleeping(),
            snapshot.sensor(),
            snapshot.centerOfMassOffsetY(),
            snapshot.shapeType(),
            snapshot.hasBoxHalfExtents(),
            snapshot.boxHalfExtentX(),
            snapshot.boxHalfExtentY(),
            snapshot.boxHalfExtentZ(),
            snapshot.sphereRadius(),
            snapshot.halfHeight(),
            snapshot.shapeAxis());
    }

    PublishedPhysicsBodySnapshot(@Nonnull RigidBodyKey bodyKey,
        @Nonnull SpaceId spaceId,
        long frameEpoch,
        long worldEpoch,
        long spaceEpoch,
        long registrationGeneration,
        @Nonnull PhysicsBodyKind kind,
        @Nonnull PhysicsBodyPersistenceMode persistenceMode,
        float positionX,
        float positionY,
        float positionZ,
        float rotationX,
        float rotationY,
        float rotationZ,
        float rotationW,
        float linearVelocityX,
        float linearVelocityY,
        float linearVelocityZ,
        float angularVelocityX,
        float angularVelocityY,
        float angularVelocityZ,
        @Nonnull PhysicsBodyType bodyType,
        boolean sleeping,
        boolean sensor,
        float centerOfMassOffsetY,
        @Nonnull ShapeType shapeType,
        boolean hasBoxHalfExtents,
        float boxHalfExtentX,
        float boxHalfExtentY,
        float boxHalfExtentZ,
        float sphereRadius,
        float halfHeight,
        @Nonnull PhysicsAxis shapeAxis) {
        this.bodyKey = Objects.requireNonNull(bodyKey, "bodyKey");
        this.spaceId = Objects.requireNonNull(spaceId, "spaceId");
        requireNonNegativeEpoch(frameEpoch, "frameEpoch");
        requireNonNegativeEpoch(worldEpoch, "worldEpoch");
        requireNonNegativeEpoch(spaceEpoch, "spaceEpoch");
        requireNonNegativeEpoch(registrationGeneration, "registrationGeneration");
        this.frameEpoch = frameEpoch;
        this.worldEpoch = worldEpoch;
        this.spaceEpoch = spaceEpoch;
        this.registrationGeneration = registrationGeneration;
        this.kind = Objects.requireNonNull(kind, "kind");
        this.persistenceMode = Objects.requireNonNull(persistenceMode, "persistenceMode");
        this.positionX = positionX;
        this.positionY = positionY;
        this.positionZ = positionZ;
        this.rotationX = rotationX;
        this.rotationY = rotationY;
        this.rotationZ = rotationZ;
        this.rotationW = rotationW;
        this.linearVelocityX = linearVelocityX;
        this.linearVelocityY = linearVelocityY;
        this.linearVelocityZ = linearVelocityZ;
        this.angularVelocityX = angularVelocityX;
        this.angularVelocityY = angularVelocityY;
        this.angularVelocityZ = angularVelocityZ;
        this.bodyType = Objects.requireNonNull(bodyType, "bodyType");
        this.sleeping = sleeping;
        this.sensor = sensor;
        this.centerOfMassOffsetY = centerOfMassOffsetY;
        this.shapeType = Objects.requireNonNull(shapeType, "shapeType");
        this.hasBoxHalfExtents = hasBoxHalfExtents;
        this.boxHalfExtentX = hasBoxHalfExtents ? boxHalfExtentX : 0.0f;
        this.boxHalfExtentY = hasBoxHalfExtents ? boxHalfExtentY : 0.0f;
        this.boxHalfExtentZ = hasBoxHalfExtents ? boxHalfExtentZ : 0.0f;
        this.sphereRadius = sphereRadius;
        this.halfHeight = halfHeight;
        this.shapeAxis = Objects.requireNonNull(shapeAxis, "shapeAxis");
    }

    @Nonnull
    public PhysicsBodySnapshot toBodySnapshot() {
        return PhysicsBodySnapshot.of(positionX,
            positionY,
            positionZ,
            rotationX,
            rotationY,
            rotationZ,
            rotationW,
            linearVelocityX,
            linearVelocityY,
            linearVelocityZ,
            angularVelocityX,
            angularVelocityY,
            angularVelocityZ,
            bodyType,
            sleeping,
            sensor,
            centerOfMassOffsetY,
            shapeType,
            hasBoxHalfExtents,
            boxHalfExtentX,
            boxHalfExtentY,
            boxHalfExtentZ,
            sphereRadius,
            halfHeight,
            shapeAxis);
    }

    public boolean matchesSnapshot(@Nonnull PhysicsBodySnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        if (bodyType != snapshot.bodyType()
            || sleeping != snapshot.sleeping()
            || sensor != snapshot.sensor()
            || Float.compare(centerOfMassOffsetY, snapshot.centerOfMassOffsetY()) != 0
            || shapeType != snapshot.shapeType()
            || Float.compare(sphereRadius, snapshot.sphereRadius()) != 0
            || Float.compare(halfHeight, snapshot.halfHeight()) != 0
            || shapeAxis != snapshot.shapeAxis()
            || Float.compare(positionX, snapshot.positionX()) != 0
            || Float.compare(positionY, snapshot.positionY()) != 0
            || Float.compare(positionZ, snapshot.positionZ()) != 0
            || Float.compare(linearVelocityX, snapshot.linearVelocityX()) != 0
            || Float.compare(linearVelocityY, snapshot.linearVelocityY()) != 0
            || Float.compare(linearVelocityZ, snapshot.linearVelocityZ()) != 0
            || Float.compare(angularVelocityX, snapshot.angularVelocityX()) != 0
            || Float.compare(angularVelocityY, snapshot.angularVelocityY()) != 0
            || Float.compare(angularVelocityZ, snapshot.angularVelocityZ()) != 0) {
            return false;
        }

        if (Float.compare(rotationX, snapshot.rotationX()) != 0
            || Float.compare(rotationY, snapshot.rotationY()) != 0
            || Float.compare(rotationZ, snapshot.rotationZ()) != 0
            || Float.compare(rotationW, snapshot.rotationW()) != 0) {
            return false;
        }

        if (!hasBoxHalfExtents) {
            return !snapshot.hasBoxHalfExtents();
        }
        return snapshot.hasBoxHalfExtents()
            && Float.compare(boxHalfExtentX, snapshot.boxHalfExtentX()) == 0
            && Float.compare(boxHalfExtentY, snapshot.boxHalfExtentY()) == 0
            && Float.compare(boxHalfExtentZ, snapshot.boxHalfExtentZ()) == 0;
    }

    @Nonnull
    public RigidBodyKey bodyKey() {
        return bodyKey;
    }

    @Nonnull
    public SpaceId spaceId() {
        return spaceId;
    }

    public long frameEpoch() {
        return frameEpoch;
    }

    public long worldEpoch() {
        return worldEpoch;
    }

    public long spaceEpoch() {
        return spaceEpoch;
    }

    public long registrationGeneration() {
        return registrationGeneration;
    }

    @Nonnull
    public PhysicsBodyKind kind() {
        return kind;
    }

    @Nonnull
    public PhysicsBodyPersistenceMode persistenceMode() {
        return persistenceMode;
    }

    @Nonnull
    public Vector3f position() {
        return new Vector3f(positionX, positionY, positionZ);
    }

    @Nonnull
    public Quaternionf rotation() {
        return new Quaternionf(rotationX, rotationY, rotationZ, rotationW);
    }

    @Nonnull
    public Vector3f linearVelocity() {
        return new Vector3f(linearVelocityX, linearVelocityY, linearVelocityZ);
    }

    @Nonnull
    public Vector3f angularVelocity() {
        return new Vector3f(angularVelocityX, angularVelocityY, angularVelocityZ);
    }

    @Nonnull
    public PhysicsBodyType bodyType() {
        return bodyType;
    }

    public boolean sleeping() {
        return sleeping;
    }

    public boolean sensor() {
        return sensor;
    }

    public float centerOfMassOffsetY() {
        return centerOfMassOffsetY;
    }

    @Nonnull
    public ShapeType shapeType() {
        return shapeType;
    }

    @Nullable
    public Vector3f boxHalfExtents() {
        return hasBoxHalfExtents ? new Vector3f(boxHalfExtentX, boxHalfExtentY, boxHalfExtentZ) : null;
    }

    public float sphereRadius() {
        return sphereRadius;
    }

    public float halfHeight() {
        return halfHeight;
    }

    @Nonnull
    public PhysicsAxis shapeAxis() {
        return shapeAxis;
    }

    public float positionX() {
        return positionX;
    }

    public float positionY() {
        return positionY;
    }

    public float positionZ() {
        return positionZ;
    }

    public float rotationX() {
        return rotationX;
    }

    public float rotationY() {
        return rotationY;
    }

    public float rotationZ() {
        return rotationZ;
    }

    public float rotationW() {
        return rotationW;
    }

    public float linearVelocityX() {
        return linearVelocityX;
    }

    public float linearVelocityY() {
        return linearVelocityY;
    }

    public float linearVelocityZ() {
        return linearVelocityZ;
    }

    public float angularVelocityX() {
        return angularVelocityX;
    }

    public float angularVelocityY() {
        return angularVelocityY;
    }

    public float angularVelocityZ() {
        return angularVelocityZ;
    }

    public boolean hasBoxHalfExtents() {
        return hasBoxHalfExtents;
    }

    public float boxHalfExtentX() {
        return boxHalfExtentX;
    }

    public float boxHalfExtentY() {
        return boxHalfExtentY;
    }

    public float boxHalfExtentZ() {
        return boxHalfExtentZ;
    }

    @Nonnull
    public Vector3f copyPositionTo(@Nonnull Vector3f target) {
        return Objects.requireNonNull(target, "target").set(positionX, positionY, positionZ);
    }

    @Nonnull
    public Quaternionf copyRotationTo(@Nonnull Quaternionf target) {
        return Objects.requireNonNull(target, "target").set(rotationX, rotationY, rotationZ, rotationW);
    }

    @Nonnull
    public Vector3f copyLinearVelocityTo(@Nonnull Vector3f target) {
        return Objects.requireNonNull(target, "target")
            .set(linearVelocityX, linearVelocityY, linearVelocityZ);
    }

    @Nonnull
    public Vector3f copyAngularVelocityTo(@Nonnull Vector3f target) {
        return Objects.requireNonNull(target, "target")
            .set(angularVelocityX, angularVelocityY, angularVelocityZ);
    }

    @Nonnull
    public Vector3f copyBoxHalfExtentsTo(@Nonnull Vector3f target) {
        Objects.requireNonNull(target, "target");
        if (!hasBoxHalfExtents) {
            return target.zero();
        }
        return target.set(boxHalfExtentX, boxHalfExtentY, boxHalfExtentZ);
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

    @Override
    public boolean equals(@Nullable Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof PublishedPhysicsBodySnapshot that)) {
            return false;
        }
        return frameEpoch == that.frameEpoch
            && worldEpoch == that.worldEpoch
            && spaceEpoch == that.spaceEpoch
            && registrationGeneration == that.registrationGeneration
            && Float.compare(positionX, that.positionX) == 0
            && Float.compare(positionY, that.positionY) == 0
            && Float.compare(positionZ, that.positionZ) == 0
            && Float.compare(rotationX, that.rotationX) == 0
            && Float.compare(rotationY, that.rotationY) == 0
            && Float.compare(rotationZ, that.rotationZ) == 0
            && Float.compare(rotationW, that.rotationW) == 0
            && Float.compare(linearVelocityX, that.linearVelocityX) == 0
            && Float.compare(linearVelocityY, that.linearVelocityY) == 0
            && Float.compare(linearVelocityZ, that.linearVelocityZ) == 0
            && Float.compare(angularVelocityX, that.angularVelocityX) == 0
            && Float.compare(angularVelocityY, that.angularVelocityY) == 0
            && Float.compare(angularVelocityZ, that.angularVelocityZ) == 0
            && sleeping == that.sleeping
            && sensor == that.sensor
            && Float.compare(centerOfMassOffsetY, that.centerOfMassOffsetY) == 0
            && hasBoxHalfExtents == that.hasBoxHalfExtents
            && Float.compare(boxHalfExtentX, that.boxHalfExtentX) == 0
            && Float.compare(boxHalfExtentY, that.boxHalfExtentY) == 0
            && Float.compare(boxHalfExtentZ, that.boxHalfExtentZ) == 0
            && Float.compare(sphereRadius, that.sphereRadius) == 0
            && Float.compare(halfHeight, that.halfHeight) == 0
            && bodyKey.equals(that.bodyKey)
            && spaceId.equals(that.spaceId)
            && kind == that.kind
            && persistenceMode == that.persistenceMode
            && bodyType == that.bodyType
            && shapeType == that.shapeType
            && shapeAxis == that.shapeAxis;
    }

    @Override
    public int hashCode() {
        int result = bodyKey.hashCode();
        result = 31 * result + spaceId.hashCode();
        result = 31 * result + Long.hashCode(frameEpoch);
        result = 31 * result + Long.hashCode(worldEpoch);
        result = 31 * result + Long.hashCode(spaceEpoch);
        result = 31 * result + Long.hashCode(registrationGeneration);
        result = 31 * result + kind.hashCode();
        result = 31 * result + persistenceMode.hashCode();
        result = 31 * result + Float.hashCode(positionX);
        result = 31 * result + Float.hashCode(positionY);
        result = 31 * result + Float.hashCode(positionZ);
        result = 31 * result + Float.hashCode(rotationX);
        result = 31 * result + Float.hashCode(rotationY);
        result = 31 * result + Float.hashCode(rotationZ);
        result = 31 * result + Float.hashCode(rotationW);
        result = 31 * result + Float.hashCode(linearVelocityX);
        result = 31 * result + Float.hashCode(linearVelocityY);
        result = 31 * result + Float.hashCode(linearVelocityZ);
        result = 31 * result + Float.hashCode(angularVelocityX);
        result = 31 * result + Float.hashCode(angularVelocityY);
        result = 31 * result + Float.hashCode(angularVelocityZ);
        result = 31 * result + bodyType.hashCode();
        result = 31 * result + Boolean.hashCode(sleeping);
        result = 31 * result + Boolean.hashCode(sensor);
        result = 31 * result + Float.hashCode(centerOfMassOffsetY);
        result = 31 * result + shapeType.hashCode();
        result = 31 * result + Boolean.hashCode(hasBoxHalfExtents);
        result = 31 * result + Float.hashCode(boxHalfExtentX);
        result = 31 * result + Float.hashCode(boxHalfExtentY);
        result = 31 * result + Float.hashCode(boxHalfExtentZ);
        result = 31 * result + Float.hashCode(sphereRadius);
        result = 31 * result + Float.hashCode(halfHeight);
        result = 31 * result + shapeAxis.hashCode();
        return result;
    }

    @Nonnull
    @Override
    public String toString() {
        return "PublishedPhysicsBodySnapshot["
            + "bodyKey=" + bodyKey
            + ", spaceId=" + spaceId
            + ", frameEpoch=" + frameEpoch
            + ", worldEpoch=" + worldEpoch
            + ", spaceEpoch=" + spaceEpoch
            + ", registrationGeneration=" + registrationGeneration
            + ", kind=" + kind
            + ", persistenceMode=" + persistenceMode
            + ", position=(" + positionX + ", " + positionY + ", " + positionZ + ')'
            + ", rotation=(" + rotationX + ", " + rotationY + ", " + rotationZ + ", " + rotationW + ')'
            + ", linearVelocity=(" + linearVelocityX + ", " + linearVelocityY + ", " + linearVelocityZ + ')'
            + ", angularVelocity=(" + angularVelocityX + ", " + angularVelocityY + ", " + angularVelocityZ + ')'
            + ", bodyType=" + bodyType
            + ", sleeping=" + sleeping
            + ", sensor=" + sensor
            + ", centerOfMassOffsetY=" + centerOfMassOffsetY
            + ", shapeType=" + shapeType
            + ", hasBoxHalfExtents=" + hasBoxHalfExtents
            + ", boxHalfExtents=(" + boxHalfExtentX + ", " + boxHalfExtentY + ", " + boxHalfExtentZ + ')'
            + ", sphereRadius=" + sphereRadius
            + ", halfHeight=" + halfHeight
            + ", shapeAxis=" + shapeAxis
            + ']';
    }

    private static void requireNonNegativeEpoch(long epoch, @Nonnull String label) {
        if (epoch < 0L) {
            throw new IllegalArgumentException(label + " cannot be negative");
        }
    }

}
