package dev.hytalemodding.impulse.core.internal.physicsstore.persistence;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.validation.Validators;
import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nonnull;

public final class PersistentMaterialDto {

    @Nonnull
    public static final BuilderCodec<PersistentMaterialDto> CODEC =
        BuilderCodec.builder(PersistentMaterialDto.class, PersistentMaterialDto::new)
            .append(new KeyedCodec<>("MaterialUuid", Codec.UUID_BINARY),
                (dto, value) -> dto.materialUuid = value,
                PersistentMaterialDto::getMaterialUuid)
            .addValidator(Validators.nonNull())
            .add()
            .append(new KeyedCodec<>("Friction", Codec.FLOAT, false),
                (dto, value) -> dto.friction = value != null ? value : 0.5f,
                PersistentMaterialDto::getFriction)
            .addValidator(PhysicsStorePersistenceValidation.nonNegativeFiniteFloat(
                "Persisted material friction must be finite and >= 0"))
            .add()
            .append(new KeyedCodec<>("Restitution", Codec.FLOAT, false),
                (dto, value) -> dto.restitution = value != null ? value : 0.0f,
                PersistentMaterialDto::getRestitution)
            .addValidator(PhysicsStorePersistenceValidation.nonNegativeFiniteFloat(
                "Persisted material restitution must be finite and >= 0"))
            .add()
            .build();

    @Nonnull
    private UUID materialUuid = new UUID(0L, 0L);
    private float friction = 0.5f;
    private float restitution;

    public PersistentMaterialDto() {
    }

    public PersistentMaterialDto(@Nonnull UUID materialUuid, float friction, float restitution) {
        this.materialUuid = Objects.requireNonNull(materialUuid, "materialUuid");
        this.friction = friction;
        this.restitution = restitution;
    }

    @Nonnull
    public UUID getMaterialUuid() {
        return materialUuid;
    }

    public float getFriction() {
        return friction;
    }

    public float getRestitution() {
        return restitution;
    }

    @Nonnull
    public PersistentMaterialDto copy() {
        return new PersistentMaterialDto(materialUuid, friction, restitution);
    }
}
