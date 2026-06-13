package dev.hytalemodding.impulse.core.internal.physicsstore.persistence;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.validation.Validators;
import com.hypixel.hytale.math.vector.Vector3fUtil;
import dev.hytalemodding.impulse.core.plugin.codec.ImpulseCodecs;
import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nonnull;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class PersistentColliderDto {

    private static final Vector3f ZERO = new Vector3f();
    private static final Quaternionf IDENTITY = new Quaternionf();

    @Nonnull
    public static final BuilderCodec<PersistentColliderDto> CODEC =
        BuilderCodec.builder(PersistentColliderDto.class, PersistentColliderDto::new)
            .append(new KeyedCodec<>("ColliderUuid", Codec.UUID_BINARY),
                (dto, value) -> dto.colliderUuid = value,
                PersistentColliderDto::getColliderUuid)
            .addValidator(Validators.nonNull())
            .add()
            .append(new KeyedCodec<>("BodyUuid", Codec.UUID_BINARY),
                (dto, value) -> dto.bodyUuid = value,
                PersistentColliderDto::getBodyUuid)
            .addValidator(Validators.nonNull())
            .add()
            .append(new KeyedCodec<>("ShapeUuid", Codec.UUID_BINARY),
                (dto, value) -> dto.shapeUuid = value,
                PersistentColliderDto::getShapeUuid)
            .addValidator(Validators.nonNull())
            .add()
            .append(new KeyedCodec<>("MaterialUuid", Codec.UUID_BINARY),
                (dto, value) -> dto.materialUuid = value,
                PersistentColliderDto::getMaterialUuid)
            .addValidator(Validators.nonNull())
            .add()
            .append(new KeyedCodec<>("LocalPosition", Vector3fUtil.CODEC, false),
                (dto, value) -> dto.localPosition.set(value != null ? value : ZERO),
                PersistentColliderDto::getLocalPosition)
            .addValidator(PhysicsStorePersistenceValidation.finiteVector(
                "Persisted collider local position must be finite"))
            .add()
            .append(new KeyedCodec<>("LocalRotation", ImpulseCodecs.QUATERNIONF, false),
                (dto, value) -> dto.localRotation.set(value != null ? value : IDENTITY),
                PersistentColliderDto::getLocalRotation)
            .add()
            .append(new KeyedCodec<>("Sensor", Codec.BOOLEAN, false),
                (dto, value) -> dto.sensor = value != null && value,
                PersistentColliderDto::isSensor)
            .add()
            .append(new KeyedCodec<>("CollisionGroup", Codec.INTEGER, false),
                (dto, value) -> dto.collisionGroup = value != null ? value : 0,
                PersistentColliderDto::getCollisionGroup)
            .add()
            .append(new KeyedCodec<>("CollisionMask", Codec.INTEGER, false),
                (dto, value) -> dto.collisionMask = value != null ? value : -1,
                PersistentColliderDto::getCollisionMask)
            .add()
            .build();

    @Nonnull
    private UUID colliderUuid = new UUID(0L, 0L);
    @Nonnull
    private UUID bodyUuid = new UUID(0L, 0L);
    @Nonnull
    private UUID shapeUuid = new UUID(0L, 0L);
    @Nonnull
    private UUID materialUuid = new UUID(0L, 0L);
    @Nonnull
    private final Vector3f localPosition = new Vector3f();
    @Nonnull
    private final Quaternionf localRotation = new Quaternionf();
    private boolean sensor;
    private int collisionGroup;
    private int collisionMask = -1;

    public PersistentColliderDto() {
    }

    public PersistentColliderDto(@Nonnull UUID colliderUuid,
        @Nonnull UUID bodyUuid,
        @Nonnull UUID shapeUuid,
        @Nonnull UUID materialUuid,
        @Nonnull Vector3f localPosition,
        @Nonnull Quaternionf localRotation,
        boolean sensor,
        int collisionGroup,
        int collisionMask) {
        this.colliderUuid = Objects.requireNonNull(colliderUuid, "colliderUuid");
        this.bodyUuid = Objects.requireNonNull(bodyUuid, "bodyUuid");
        this.shapeUuid = Objects.requireNonNull(shapeUuid, "shapeUuid");
        this.materialUuid = Objects.requireNonNull(materialUuid, "materialUuid");
        this.localPosition.set(Objects.requireNonNull(localPosition, "localPosition"));
        this.localRotation.set(Objects.requireNonNull(localRotation, "localRotation"));
        this.sensor = sensor;
        this.collisionGroup = collisionGroup;
        this.collisionMask = collisionMask;
    }

    @Nonnull
    public UUID getColliderUuid() {
        return colliderUuid;
    }

    @Nonnull
    public UUID getBodyUuid() {
        return bodyUuid;
    }

    @Nonnull
    public UUID getShapeUuid() {
        return shapeUuid;
    }

    @Nonnull
    public UUID getMaterialUuid() {
        return materialUuid;
    }

    @Nonnull
    public Vector3f getLocalPosition() {
        return new Vector3f(localPosition);
    }

    @Nonnull
    public Quaternionf getLocalRotation() {
        return new Quaternionf(localRotation);
    }

    public boolean isSensor() {
        return sensor;
    }

    public int getCollisionGroup() {
        return collisionGroup;
    }

    public int getCollisionMask() {
        return collisionMask;
    }

    @Nonnull
    public PersistentColliderDto copy() {
        return new PersistentColliderDto(colliderUuid,
            bodyUuid,
            shapeUuid,
            materialUuid,
            localPosition,
            localRotation,
            sensor,
            collisionGroup,
            collisionMask);
    }
}
