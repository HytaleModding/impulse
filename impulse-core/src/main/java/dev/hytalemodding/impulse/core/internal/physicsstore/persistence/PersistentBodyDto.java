package dev.hytalemodding.impulse.core.internal.physicsstore.persistence;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.EnumCodec;
import com.hypixel.hytale.codec.codecs.array.ArrayCodec;
import com.hypixel.hytale.codec.validation.Validators;
import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyKind;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyPersistenceMode;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nonnull;

public final class PersistentBodyDto {

    private static final UUID[] EMPTY_UUIDS = new UUID[0];

    @Nonnull
    public static final BuilderCodec<PersistentBodyDto> CODEC =
        BuilderCodec.builder(PersistentBodyDto.class, PersistentBodyDto::new)
            .append(new KeyedCodec<>("BodyUuid", Codec.UUID_BINARY),
                (dto, value) -> dto.bodyUuid = value,
                PersistentBodyDto::getBodyUuid)
            .addValidator(Validators.nonNull())
            .add()
            .append(new KeyedCodec<>("SpaceUuid", Codec.UUID_BINARY),
                (dto, value) -> dto.spaceUuid = value,
                PersistentBodyDto::getSpaceUuid)
            .addValidator(Validators.nonNull())
            .add()
            .append(new KeyedCodec<>("Kind", new EnumCodec<>(PhysicsBodyKind.class), false),
                (dto, value) -> dto.kind = value != null ? value : PhysicsBodyKind.BODY,
                PersistentBodyDto::getKind)
            .addValidator(Validators.nonNull())
            .add()
            .append(new KeyedCodec<>("PersistenceMode",
                    new EnumCodec<>(PhysicsBodyPersistenceMode.class),
                    false),
                (dto, value) -> dto.persistenceMode = value != null
                    ? value
                    : PhysicsBodyPersistenceMode.RUNTIME_ONLY,
                PersistentBodyDto::getPersistenceMode)
            .addValidator(Validators.nonNull())
            .add()
            .append(new KeyedCodec<>("BodyType", new EnumCodec<>(PhysicsBodyType.class), false),
                (dto, value) -> dto.bodyType = value != null ? value : PhysicsBodyType.DYNAMIC,
                PersistentBodyDto::getBodyType)
            .addValidator(Validators.nonNull())
            .add()
            .append(new KeyedCodec<>("Mass", Codec.FLOAT, false),
                (dto, value) -> dto.mass = value != null ? value : 1.0f,
                PersistentBodyDto::getMass)
            .addValidator(PhysicsStorePersistenceValidation.nonNegativeFiniteFloat(
                "Persisted body mass must be finite and >= 0"))
            .add()
            .append(new KeyedCodec<>("LinearDamping", Codec.FLOAT, false),
                (dto, value) -> dto.linearDamping = value != null ? value : 0.0f,
                PersistentBodyDto::getLinearDamping)
            .addValidator(PhysicsStorePersistenceValidation.nonNegativeFiniteFloat(
                "Persisted body linear damping must be finite and >= 0"))
            .add()
            .append(new KeyedCodec<>("AngularDamping", Codec.FLOAT, false),
                (dto, value) -> dto.angularDamping = value != null ? value : 0.0f,
                PersistentBodyDto::getAngularDamping)
            .addValidator(PhysicsStorePersistenceValidation.nonNegativeFiniteFloat(
                "Persisted body angular damping must be finite and >= 0"))
            .add()
            .append(new KeyedCodec<>("ContinuousCollision", Codec.BOOLEAN, false),
                (dto, value) -> dto.continuousCollisionEnabled = value != null && value,
                PersistentBodyDto::isContinuousCollisionEnabled)
            .add()
            .append(new KeyedCodec<>("ColliderUuids",
                    new ArrayCodec<>(Codec.UUID_BINARY, UUID[]::new),
                    false),
                (dto, value) -> dto.colliderUuids = copyUuids(value),
                PersistentBodyDto::getColliderUuids)
            .addValidator(Validators.nonNull())
            .addValidator(Validators.nonNullArrayElements())
            .add()
            .append(new KeyedCodec<>("RuntimeState", PersistentBodyRuntimeStateDto.CODEC, false),
                (dto, value) -> dto.runtimeState = value != null
                    ? value.copy()
                    : new PersistentBodyRuntimeStateDto(),
                PersistentBodyDto::getRuntimeState)
            .addValidator(Validators.nonNull())
            .add()
            .build();

    @Nonnull
    private UUID bodyUuid = new UUID(0L, 0L);
    @Nonnull
    private UUID spaceUuid = new UUID(0L, 0L);
    @Nonnull
    private PhysicsBodyKind kind = PhysicsBodyKind.BODY;
    @Nonnull
    private PhysicsBodyPersistenceMode persistenceMode = PhysicsBodyPersistenceMode.RUNTIME_ONLY;
    @Nonnull
    private PhysicsBodyType bodyType = PhysicsBodyType.DYNAMIC;
    private float mass = 1.0f;
    private float linearDamping;
    private float angularDamping;
    private boolean continuousCollisionEnabled;
    @Nonnull
    private UUID[] colliderUuids = EMPTY_UUIDS;
    @Nonnull
    private PersistentBodyRuntimeStateDto runtimeState = new PersistentBodyRuntimeStateDto();

    public PersistentBodyDto() {
    }

    public PersistentBodyDto(@Nonnull UUID bodyUuid,
        @Nonnull UUID spaceUuid,
        @Nonnull PhysicsBodyKind kind,
        @Nonnull PhysicsBodyPersistenceMode persistenceMode,
        @Nonnull PhysicsBodyType bodyType,
        float mass,
        float linearDamping,
        float angularDamping,
        boolean continuousCollisionEnabled,
        @Nonnull UUID[] colliderUuids,
        @Nonnull PersistentBodyRuntimeStateDto runtimeState) {
        this.bodyUuid = Objects.requireNonNull(bodyUuid, "bodyUuid");
        this.spaceUuid = Objects.requireNonNull(spaceUuid, "spaceUuid");
        this.kind = Objects.requireNonNull(kind, "kind");
        this.persistenceMode = Objects.requireNonNull(persistenceMode, "persistenceMode");
        this.bodyType = Objects.requireNonNull(bodyType, "bodyType");
        this.mass = mass;
        this.linearDamping = linearDamping;
        this.angularDamping = angularDamping;
        this.continuousCollisionEnabled = continuousCollisionEnabled;
        this.colliderUuids = copyUuids(colliderUuids);
        this.runtimeState = Objects.requireNonNull(runtimeState, "runtimeState").copy();
    }

    @Nonnull
    public UUID getBodyUuid() {
        return bodyUuid;
    }

    @Nonnull
    public UUID getSpaceUuid() {
        return spaceUuid;
    }

    @Nonnull
    public PhysicsBodyKind getKind() {
        return kind;
    }

    @Nonnull
    public PhysicsBodyPersistenceMode getPersistenceMode() {
        return persistenceMode;
    }

    @Nonnull
    public PhysicsBodyType getBodyType() {
        return bodyType;
    }

    public float getMass() {
        return mass;
    }

    public float getLinearDamping() {
        return linearDamping;
    }

    public float getAngularDamping() {
        return angularDamping;
    }

    public boolean isContinuousCollisionEnabled() {
        return continuousCollisionEnabled;
    }

    @Nonnull
    public UUID[] getColliderUuids() {
        return copyUuids(colliderUuids);
    }

    @Nonnull
    public PersistentBodyRuntimeStateDto getRuntimeState() {
        return runtimeState.copy();
    }

    @Nonnull
    public PersistentBodyDto copy() {
        return new PersistentBodyDto(bodyUuid,
            spaceUuid,
            kind,
            persistenceMode,
            bodyType,
            mass,
            linearDamping,
            angularDamping,
            continuousCollisionEnabled,
            colliderUuids,
            runtimeState);
    }

    @Nonnull
    private static UUID[] copyUuids(UUID[] values) {
        if (values == null || values.length == 0) {
            return EMPTY_UUIDS;
        }
        return Arrays.copyOf(values, values.length);
    }
}
