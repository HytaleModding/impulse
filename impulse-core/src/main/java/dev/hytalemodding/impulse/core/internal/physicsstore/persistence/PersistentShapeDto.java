package dev.hytalemodding.impulse.core.internal.physicsstore.persistence;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.EnumCodec;
import com.hypixel.hytale.codec.validation.Validators;
import dev.hytalemodding.impulse.api.PhysicsAxis;
import dev.hytalemodding.impulse.api.ShapeType;
import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nonnull;

public final class PersistentShapeDto {

    @Nonnull
    public static final BuilderCodec<PersistentShapeDto> CODEC =
        BuilderCodec.builder(PersistentShapeDto.class, PersistentShapeDto::new)
            .append(new KeyedCodec<>("ShapeUuid", Codec.UUID_BINARY),
                (dto, value) -> dto.shapeUuid = value,
                PersistentShapeDto::getShapeUuid)
            .addValidator(Validators.nonNull())
            .add()
            .append(new KeyedCodec<>("ShapeType", new EnumCodec<>(ShapeType.class)),
                (dto, value) -> dto.shapeType = value,
                PersistentShapeDto::getShapeType)
            .addValidator(Validators.nonNull())
            .add()
            .append(new KeyedCodec<>("HalfExtentX", Codec.FLOAT, false),
                (dto, value) -> dto.halfExtentX = value != null ? value : 0.0f,
                PersistentShapeDto::getHalfExtentX)
            .addValidator(PhysicsStorePersistenceValidation.nonNegativeFiniteFloat(
                "Persisted shape half extent X must be finite and >= 0"))
            .add()
            .append(new KeyedCodec<>("HalfExtentY", Codec.FLOAT, false),
                (dto, value) -> dto.halfExtentY = value != null ? value : 0.0f,
                PersistentShapeDto::getHalfExtentY)
            .addValidator(PhysicsStorePersistenceValidation.nonNegativeFiniteFloat(
                "Persisted shape half extent Y must be finite and >= 0"))
            .add()
            .append(new KeyedCodec<>("HalfExtentZ", Codec.FLOAT, false),
                (dto, value) -> dto.halfExtentZ = value != null ? value : 0.0f,
                PersistentShapeDto::getHalfExtentZ)
            .addValidator(PhysicsStorePersistenceValidation.nonNegativeFiniteFloat(
                "Persisted shape half extent Z must be finite and >= 0"))
            .add()
            .append(new KeyedCodec<>("Radius", Codec.FLOAT, false),
                (dto, value) -> dto.radius = value != null ? value : 0.0f,
                PersistentShapeDto::getRadius)
            .addValidator(PhysicsStorePersistenceValidation.nonNegativeFiniteFloat(
                "Persisted shape radius must be finite and >= 0"))
            .add()
            .append(new KeyedCodec<>("HalfHeight", Codec.FLOAT, false),
                (dto, value) -> dto.halfHeight = value != null ? value : 0.0f,
                PersistentShapeDto::getHalfHeight)
            .addValidator(PhysicsStorePersistenceValidation.nonNegativeFiniteFloat(
                "Persisted shape half height must be finite and >= 0"))
            .add()
            .append(new KeyedCodec<>("Axis", new EnumCodec<>(PhysicsAxis.class), false),
                (dto, value) -> dto.axis = value != null ? value : PhysicsAxis.Y,
                PersistentShapeDto::getAxis)
            .addValidator(Validators.nonNull())
            .add()
            .append(new KeyedCodec<>("GroundY", Codec.FLOAT, false),
                (dto, value) -> dto.groundY = value != null ? value : 0.0f,
                PersistentShapeDto::getGroundY)
            .addValidator(PhysicsStorePersistenceValidation.finiteFloat(
                "Persisted shape ground Y must be finite"))
            .add()
            .append(new KeyedCodec<>("ResourceKey", Codec.STRING, false),
                (dto, value) -> dto.resourceKey = value != null ? value : "",
                PersistentShapeDto::getResourceKey)
            .add()
            .build();

    @Nonnull
    private UUID shapeUuid = new UUID(0L, 0L);
    @Nonnull
    private ShapeType shapeType = ShapeType.BOX;
    private float halfExtentX;
    private float halfExtentY;
    private float halfExtentZ;
    private float radius;
    private float halfHeight;
    @Nonnull
    private PhysicsAxis axis = PhysicsAxis.Y;
    private float groundY;
    @Nonnull
    private String resourceKey = "";

    public PersistentShapeDto() {
    }

    public PersistentShapeDto(@Nonnull UUID shapeUuid,
        @Nonnull ShapeType shapeType,
        float halfExtentX,
        float halfExtentY,
        float halfExtentZ,
        float radius,
        float halfHeight,
        @Nonnull PhysicsAxis axis,
        float groundY,
        @Nonnull String resourceKey) {
        this.shapeUuid = Objects.requireNonNull(shapeUuid, "shapeUuid");
        this.shapeType = Objects.requireNonNull(shapeType, "shapeType");
        this.halfExtentX = halfExtentX;
        this.halfExtentY = halfExtentY;
        this.halfExtentZ = halfExtentZ;
        this.radius = radius;
        this.halfHeight = halfHeight;
        this.axis = Objects.requireNonNull(axis, "axis");
        this.groundY = groundY;
        this.resourceKey = Objects.requireNonNull(resourceKey, "resourceKey");
    }

    @Nonnull
    public UUID getShapeUuid() {
        return shapeUuid;
    }

    @Nonnull
    public ShapeType getShapeType() {
        return shapeType;
    }

    public float getHalfExtentX() {
        return halfExtentX;
    }

    public float getHalfExtentY() {
        return halfExtentY;
    }

    public float getHalfExtentZ() {
        return halfExtentZ;
    }

    public float getRadius() {
        return radius;
    }

    public float getHalfHeight() {
        return halfHeight;
    }

    @Nonnull
    public PhysicsAxis getAxis() {
        return axis;
    }

    public float getGroundY() {
        return groundY;
    }

    @Nonnull
    public String getResourceKey() {
        return resourceKey;
    }

    @Nonnull
    public PersistentShapeDto copy() {
        return new PersistentShapeDto(shapeUuid,
            shapeType,
            halfExtentX,
            halfExtentY,
            halfExtentZ,
            radius,
            halfHeight,
            axis,
            groundY,
            resourceKey);
    }
}
