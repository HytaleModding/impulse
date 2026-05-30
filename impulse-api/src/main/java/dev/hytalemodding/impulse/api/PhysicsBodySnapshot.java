package dev.hytalemodding.impulse.api;

import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Immutable copy of body state captured on the physics owner.
 *
 * <p>Snapshots deliberately contain shape metadata instead of a live {@link PhysicsBody} handle so
 * they can be published to world-thread readers and debug systems without escaping backend
 * ownership.</p>
 */
public final class PhysicsBodySnapshot {

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

    public PhysicsBodySnapshot(@Nonnull Vector3f position,
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
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(rotation, "rotation");
        Objects.requireNonNull(linearVelocity, "linearVelocity");
        Objects.requireNonNull(angularVelocity, "angularVelocity");
        this.positionX = position.x;
        this.positionY = position.y;
        this.positionZ = position.z;
        this.rotationX = rotation.x;
        this.rotationY = rotation.y;
        this.rotationZ = rotation.z;
        this.rotationW = rotation.w;
        this.linearVelocityX = linearVelocity.x;
        this.linearVelocityY = linearVelocity.y;
        this.linearVelocityZ = linearVelocity.z;
        this.angularVelocityX = angularVelocity.x;
        this.angularVelocityY = angularVelocity.y;
        this.angularVelocityZ = angularVelocity.z;
        this.bodyType = Objects.requireNonNull(bodyType, "bodyType");
        this.sleeping = sleeping;
        this.sensor = sensor;
        this.centerOfMassOffsetY = centerOfMassOffsetY;
        this.shapeType = Objects.requireNonNull(shapeType, "shapeType");
        this.hasBoxHalfExtents = boxHalfExtents != null;
        this.boxHalfExtentX = boxHalfExtents != null ? boxHalfExtents.x : 0.0f;
        this.boxHalfExtentY = boxHalfExtents != null ? boxHalfExtents.y : 0.0f;
        this.boxHalfExtentZ = boxHalfExtents != null ? boxHalfExtents.z : 0.0f;
        this.sphereRadius = sphereRadius;
        this.halfHeight = halfHeight;
        this.shapeAxis = Objects.requireNonNull(shapeAxis, "shapeAxis");
    }

    private PhysicsBodySnapshot(float positionX,
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
    public static PhysicsBodySnapshot of(float positionX,
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
        return new PhysicsBodySnapshot(positionX,
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

    @Nonnull
    public static PhysicsBodySnapshot from(@Nonnull PhysicsBody body) {
        return from(body, null);
    }

    @Nonnull
    public static PhysicsBodySnapshot from(@Nonnull PhysicsBody body,
        @Nullable PhysicsBodySnapshot previous) {
        Objects.requireNonNull(body, "body");
        boolean sleeping = body.isSleeping();
        if (sleeping && previous != null && previous.sleeping()) {
            return previous;
        }

        Vector3f position = new Vector3f();
        Quaternionf rotation = new Quaternionf();
        Vector3f linearVelocity = new Vector3f();
        Vector3f angularVelocity = new Vector3f();
        body.getPosition(position);
        body.getRotation(rotation);
        PhysicsBodyType bodyType = body.getBodyType();
        if (!sleeping && bodyType != PhysicsBodyType.STATIC) {
            body.getLinearVelocity(linearVelocity);
            body.getAngularVelocity(angularVelocity);
        }
        Vector3f boxHalfExtents = body.getBoxHalfExtents();
        return new PhysicsBodySnapshot(position.x,
            position.y,
            position.z,
            rotation.x,
            rotation.y,
            rotation.z,
            rotation.w,
            linearVelocity.x,
            linearVelocity.y,
            linearVelocity.z,
            angularVelocity.x,
            angularVelocity.y,
            angularVelocity.z,
            bodyType,
            sleeping,
            body.isSensor(),
            body.getCenterOfMassOffsetY(),
            body.getShapeType(),
            boxHalfExtents != null,
            boxHalfExtents != null ? boxHalfExtents.x : 0.0f,
            boxHalfExtents != null ? boxHalfExtents.y : 0.0f,
            boxHalfExtents != null ? boxHalfExtents.z : 0.0f,
            body.getSphereRadius(),
            body.getHalfHeight(),
            body.getShapeAxis());
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
        return Objects.requireNonNull(target, "target").set(linearVelocityX, linearVelocityY, linearVelocityZ);
    }

    @Nonnull
    public Vector3f copyAngularVelocityTo(@Nonnull Vector3f target) {
        return Objects.requireNonNull(target, "target").set(angularVelocityX, angularVelocityY, angularVelocityZ);
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
        if (!(other instanceof PhysicsBodySnapshot that)) {
            return false;
        }
        return Float.compare(positionX, that.positionX) == 0
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
            && bodyType == that.bodyType
            && shapeType == that.shapeType
            && shapeAxis == that.shapeAxis;
    }

    @Override
    public int hashCode() {
        int result = Float.hashCode(positionX);
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

    @Override
    public String toString() {
        return "PhysicsBodySnapshot["
            + "position=(" + positionX + ", " + positionY + ", " + positionZ + ')'
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
}
