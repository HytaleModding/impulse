package dev.hytalemodding.impulse.core.internal.physicsstore.persistence;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.validation.Validators;
import com.hypixel.hytale.math.vector.Vector3fUtil;
import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nonnull;
import org.joml.Vector3f;

public final class PersistentSpaceDto {

    @Nonnull
    public static final BuilderCodec<PersistentSpaceDto> CODEC =
        BuilderCodec.builder(PersistentSpaceDto.class, PersistentSpaceDto::new)
            .append(new KeyedCodec<>("SpaceUuid", Codec.UUID_BINARY),
                (dto, value) -> dto.spaceUuid = value,
                PersistentSpaceDto::getSpaceUuid)
            .addValidator(Validators.nonNull())
            .add()
            .append(new KeyedCodec<>("BackendId", Codec.STRING),
                (dto, value) -> dto.backendId = value,
                PersistentSpaceDto::getBackendId)
            .addValidator(Validators.nonNull())
            .add()
            .append(new KeyedCodec<>("Gravity", Vector3fUtil.CODEC),
                (dto, value) -> dto.gravity.set(value),
                PersistentSpaceDto::getGravity)
            .addValidator(Validators.nonNull())
            .addValidator(PhysicsStorePersistenceValidation.finiteVector(
                "Persisted PhysicsStore space gravity must be finite"))
            .add()
            .build();

    @Nonnull
    private UUID spaceUuid = new UUID(0L, 0L);
    @Nonnull
    private String backendId = "";
    @Nonnull
    private final Vector3f gravity = new Vector3f(0.0f, -9.81f, 0.0f);

    public PersistentSpaceDto() {
    }

    public PersistentSpaceDto(@Nonnull UUID spaceUuid,
        @Nonnull String backendId,
        @Nonnull Vector3f gravity) {
        this.spaceUuid = Objects.requireNonNull(spaceUuid, "spaceUuid");
        this.backendId = Objects.requireNonNull(backendId, "backendId");
        this.gravity.set(Objects.requireNonNull(gravity, "gravity"));
    }

    @Nonnull
    public UUID getSpaceUuid() {
        return spaceUuid;
    }

    @Nonnull
    public String getBackendId() {
        return backendId;
    }

    @Nonnull
    public Vector3f getGravity() {
        return new Vector3f(gravity);
    }

    @Nonnull
    public PersistentSpaceDto copy() {
        return new PersistentSpaceDto(spaceUuid, backendId, gravity);
    }
}
