package dev.hytalemodding.impulse.core.plugin.snapshots;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.api.PhysicsBodyType;
import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Copied body snapshot published out of PhysicsStore for projection and queries.
 */
public final class PhysicsBodySnapshot {

    @Nullable
    private final Ref<PhysicsStore> bodyRef;
    @Nonnull
    private final UUID bodyUuid;
    @Nonnull
    private final UUID spaceUuid;
    @Nonnull
    private final PhysicsBodyType bodyType;
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
    private final float centerOfMassOffsetY;
    private final boolean sleeping;

    public PhysicsBodySnapshot(@Nonnull UUID bodyUuid,
                               @Nonnull UUID spaceUuid,
                               @Nonnull PhysicsBodyType bodyType,
                               @Nonnull Vector3f position,
                               @Nonnull Quaternionf rotation,
                               @Nonnull Vector3f linearVelocity,
                               @Nonnull Vector3f angularVelocity,
                               float centerOfMassOffsetY,
                               boolean sleeping) {
        this(null,
            bodyUuid,
            spaceUuid,
            bodyType,
            position,
            rotation,
            linearVelocity,
            angularVelocity,
            centerOfMassOffsetY,
            sleeping);
    }

    public PhysicsBodySnapshot(@Nullable Ref<PhysicsStore> bodyRef,
                               @Nonnull UUID bodyUuid,
                               @Nonnull UUID spaceUuid,
                               @Nonnull PhysicsBodyType bodyType,
                               @Nonnull Vector3f position,
                               @Nonnull Quaternionf rotation,
                               @Nonnull Vector3f linearVelocity,
                               @Nonnull Vector3f angularVelocity,
                               float centerOfMassOffsetY,
                               boolean sleeping) {
        this(bodyRef,
            bodyUuid,
            spaceUuid,
            bodyType,
            Objects.requireNonNull(position, "position").x,
            position.y,
            position.z,
            Objects.requireNonNull(rotation, "rotation").x,
            rotation.y,
            rotation.z,
            rotation.w,
            Objects.requireNonNull(linearVelocity, "linearVelocity").x,
            linearVelocity.y,
            linearVelocity.z,
            Objects.requireNonNull(angularVelocity, "angularVelocity").x,
            angularVelocity.y,
            angularVelocity.z,
            centerOfMassOffsetY,
            sleeping);
    }

    @Nonnull
    public static PhysicsBodySnapshot of(@Nonnull UUID bodyUuid,
        @Nonnull UUID spaceUuid,
        @Nonnull PhysicsBodyType bodyType,
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
        float centerOfMassOffsetY,
        boolean sleeping) {
        return of(null,
            bodyUuid,
            spaceUuid,
            bodyType,
            positionX,
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
            centerOfMassOffsetY,
            sleeping);
    }

    @Nonnull
    public static PhysicsBodySnapshot of(@Nullable Ref<PhysicsStore> bodyRef,
        @Nonnull UUID bodyUuid,
        @Nonnull UUID spaceUuid,
        @Nonnull PhysicsBodyType bodyType,
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
        float centerOfMassOffsetY,
        boolean sleeping) {
        return new PhysicsBodySnapshot(bodyRef,
            bodyUuid,
            spaceUuid,
            bodyType,
            positionX,
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
            centerOfMassOffsetY,
            sleeping);
    }

    private PhysicsBodySnapshot(@Nullable Ref<PhysicsStore> bodyRef,
        @Nonnull UUID bodyUuid,
        @Nonnull UUID spaceUuid,
        @Nonnull PhysicsBodyType bodyType,
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
        float centerOfMassOffsetY,
        boolean sleeping) {
        this.bodyRef = bodyRef;
        this.bodyUuid = Objects.requireNonNull(bodyUuid, "bodyUuid");
        this.spaceUuid = Objects.requireNonNull(spaceUuid, "spaceUuid");
        this.bodyType = Objects.requireNonNull(bodyType, "bodyType");
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
        this.centerOfMassOffsetY = centerOfMassOffsetY;
        this.sleeping = sleeping;
    }

    @Nullable
    public Ref<PhysicsStore> bodyRef() {
        return bodyRef;
    }

    @Nonnull
    public UUID bodyUuid() {
        return bodyUuid;
    }

    @Nonnull
    public UUID spaceUuid() {
        return spaceUuid;
    }

    @Nonnull
    public PhysicsBodyType bodyType() {
        return bodyType;
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

    public float centerOfMassOffsetY() {
        return centerOfMassOffsetY;
    }

    public boolean sleeping() {
        return sleeping;
    }

    @Nonnull
    public Vector3f position() {
        return copyPositionTo(new Vector3f());
    }

    @Nonnull
    public Quaternionf rotation() {
        return copyRotationTo(new Quaternionf());
    }

    @Nonnull
    public Vector3f linearVelocity() {
        return copyLinearVelocityTo(new Vector3f());
    }

    @Nonnull
    public Vector3f angularVelocity() {
        return copyAngularVelocityTo(new Vector3f());
    }

    @Nonnull
    public Vector3f copyPositionTo(@Nonnull Vector3f target) {
        return Objects.requireNonNull(target, "target").set(positionX, positionY, positionZ);
    }

    @Nonnull
    public Quaternionf copyRotationTo(@Nonnull Quaternionf target) {
        return Objects.requireNonNull(target, "target")
            .set(rotationX, rotationY, rotationZ, rotationW);
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
            && Float.compare(centerOfMassOffsetY, that.centerOfMassOffsetY) == 0
            && sleeping == that.sleeping
            && Objects.equals(bodyRef, that.bodyRef)
            && bodyUuid.equals(that.bodyUuid)
            && spaceUuid.equals(that.spaceUuid)
            && bodyType == that.bodyType;
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(bodyRef, bodyUuid, spaceUuid, bodyType, sleeping);
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
        result = 31 * result + Float.hashCode(centerOfMassOffsetY);
        return result;
    }

    @Nonnull
    @Override
    public String toString() {
        return "PhysicsBodySnapshot[bodyRef=" + bodyRef
            + ", bodyUuid=" + bodyUuid
            + ", spaceUuid=" + spaceUuid
            + ", bodyType=" + bodyType
            + ", position=(" + positionX + ", " + positionY + ", " + positionZ + ')'
            + ", rotation=(" + rotationX + ", " + rotationY + ", " + rotationZ + ", "
            + rotationW + ')'
            + ", linearVelocity=(" + linearVelocityX + ", " + linearVelocityY + ", "
            + linearVelocityZ + ')'
            + ", angularVelocity=(" + angularVelocityX + ", " + angularVelocityY + ", "
            + angularVelocityZ + ')'
            + ", centerOfMassOffsetY=" + centerOfMassOffsetY
            + ", sleeping=" + sleeping
            + ']';
    }
}
