package dev.hytalemodding.impulse.core.internal.physicsstore.persistence;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.EnumCodec;
import com.hypixel.hytale.codec.validation.Validators;
import com.hypixel.hytale.math.vector.Vector3fUtil;
import dev.hytalemodding.impulse.core.plugin.simulation.JointType;
import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nonnull;
import org.joml.Vector3f;

public final class PersistentJointDto {

    private static final Vector3f ZERO = new Vector3f();

    @Nonnull
    public static final BuilderCodec<PersistentJointDto> CODEC =
        BuilderCodec.builder(PersistentJointDto.class, PersistentJointDto::new)
            .append(new KeyedCodec<>("JointUuid", Codec.UUID_BINARY),
                (dto, value) -> dto.jointUuid = value,
                PersistentJointDto::getJointUuid)
            .addValidator(Validators.nonNull())
            .add()
            .append(new KeyedCodec<>("SpaceUuid", Codec.UUID_BINARY),
                (dto, value) -> dto.spaceUuid = value,
                PersistentJointDto::getSpaceUuid)
            .addValidator(Validators.nonNull())
            .add()
            .append(new KeyedCodec<>("BodyAUuid", Codec.UUID_BINARY),
                (dto, value) -> dto.bodyAUuid = value,
                PersistentJointDto::getBodyAUuid)
            .addValidator(Validators.nonNull())
            .add()
            .append(new KeyedCodec<>("BodyBUuid", Codec.UUID_BINARY),
                (dto, value) -> dto.bodyBUuid = value,
                PersistentJointDto::getBodyBUuid)
            .addValidator(Validators.nonNull())
            .add()
            .append(new KeyedCodec<>("Type", new EnumCodec<>(JointType.class), false),
                (dto, value) -> dto.type = value != null ? value : JointType.FIXED,
                PersistentJointDto::getType)
            .addValidator(Validators.nonNull())
            .add()
            .append(new KeyedCodec<>("AnchorA", Vector3fUtil.CODEC, false),
                (dto, value) -> dto.anchorA.set(value != null ? value : ZERO),
                PersistentJointDto::getAnchorA)
            .addValidator(PhysicsStorePersistenceValidation.finiteVector(
                "Persisted joint anchor A must be finite"))
            .add()
            .append(new KeyedCodec<>("AnchorB", Vector3fUtil.CODEC, false),
                (dto, value) -> dto.anchorB.set(value != null ? value : ZERO),
                PersistentJointDto::getAnchorB)
            .addValidator(PhysicsStorePersistenceValidation.finiteVector(
                "Persisted joint anchor B must be finite"))
            .add()
            .append(new KeyedCodec<>("Axis", Vector3fUtil.CODEC, false),
                (dto, value) -> dto.axis.set(value != null ? value : ZERO),
                PersistentJointDto::getAxis)
            .addValidator(PhysicsStorePersistenceValidation.finiteVector(
                "Persisted joint axis must be finite"))
            .add()
            .append(new KeyedCodec<>("LowerLimit", Codec.FLOAT, false),
                (dto, value) -> dto.lowerLimit = value != null ? value : 0.0f,
                PersistentJointDto::getLowerLimit)
            .addValidator(PhysicsStorePersistenceValidation.finiteFloat(
                "Persisted joint lower limit must be finite"))
            .add()
            .append(new KeyedCodec<>("UpperLimit", Codec.FLOAT, false),
                (dto, value) -> dto.upperLimit = value != null ? value : 0.0f,
                PersistentJointDto::getUpperLimit)
            .addValidator(PhysicsStorePersistenceValidation.finiteFloat(
                "Persisted joint upper limit must be finite"))
            .add()
            .append(new KeyedCodec<>("Enabled", Codec.BOOLEAN, false),
                (dto, value) -> dto.enabled = value == null || value,
                PersistentJointDto::isEnabled)
            .add()
            .append(new KeyedCodec<>("MotorEnabled", Codec.BOOLEAN, false),
                (dto, value) -> dto.motorEnabled = value != null && value,
                PersistentJointDto::isMotorEnabled)
            .add()
            .append(new KeyedCodec<>("MotorTargetVelocity", Codec.FLOAT, false),
                (dto, value) -> dto.motorTargetVelocity = value != null ? value : 0.0f,
                PersistentJointDto::getMotorTargetVelocity)
            .addValidator(PhysicsStorePersistenceValidation.finiteFloat(
                "Persisted joint motor target velocity must be finite"))
            .add()
            .append(new KeyedCodec<>("MotorMaxForce", Codec.FLOAT, false),
                (dto, value) -> dto.motorMaxForce = value != null ? value : 0.0f,
                PersistentJointDto::getMotorMaxForce)
            .addValidator(PhysicsStorePersistenceValidation.nonNegativeFiniteFloat(
                "Persisted joint motor max force must be finite and >= 0"))
            .add()
            .append(new KeyedCodec<>("SpringRestLength", Codec.FLOAT, false),
                (dto, value) -> dto.springRestLength = value != null ? value : 0.0f,
                PersistentJointDto::getSpringRestLength)
            .addValidator(PhysicsStorePersistenceValidation.nonNegativeFiniteFloat(
                "Persisted joint spring rest length must be finite and >= 0"))
            .add()
            .append(new KeyedCodec<>("SpringStiffness", Codec.FLOAT, false),
                (dto, value) -> dto.springStiffness = value != null ? value : 0.0f,
                PersistentJointDto::getSpringStiffness)
            .addValidator(PhysicsStorePersistenceValidation.nonNegativeFiniteFloat(
                "Persisted joint spring stiffness must be finite and >= 0"))
            .add()
            .append(new KeyedCodec<>("SpringDamping", Codec.FLOAT, false),
                (dto, value) -> dto.springDamping = value != null ? value : 0.0f,
                PersistentJointDto::getSpringDamping)
            .addValidator(PhysicsStorePersistenceValidation.nonNegativeFiniteFloat(
                "Persisted joint spring damping must be finite and >= 0"))
            .add()
            .build();

    @Nonnull
    private UUID jointUuid = new UUID(0L, 0L);
    @Nonnull
    private UUID spaceUuid = new UUID(0L, 0L);
    @Nonnull
    private UUID bodyAUuid = new UUID(0L, 0L);
    @Nonnull
    private UUID bodyBUuid = new UUID(0L, 0L);
    @Nonnull
    private JointType type = JointType.FIXED;
    @Nonnull
    private final Vector3f anchorA = new Vector3f();
    @Nonnull
    private final Vector3f anchorB = new Vector3f();
    @Nonnull
    private final Vector3f axis = new Vector3f();
    private float lowerLimit;
    private float upperLimit;
    private boolean enabled = true;
    private boolean motorEnabled;
    private float motorTargetVelocity;
    private float motorMaxForce;
    private float springRestLength;
    private float springStiffness;
    private float springDamping;

    public PersistentJointDto() {
    }

    public PersistentJointDto(@Nonnull UUID jointUuid,
        @Nonnull UUID spaceUuid,
        @Nonnull UUID bodyAUuid,
        @Nonnull UUID bodyBUuid,
        @Nonnull JointType type,
        @Nonnull Vector3f anchorA,
        @Nonnull Vector3f anchorB,
        @Nonnull Vector3f axis,
        float lowerLimit,
        float upperLimit,
        boolean enabled,
        boolean motorEnabled,
        float motorTargetVelocity,
        float motorMaxForce,
        float springRestLength,
        float springStiffness,
        float springDamping) {
        this.jointUuid = Objects.requireNonNull(jointUuid, "jointUuid");
        this.spaceUuid = Objects.requireNonNull(spaceUuid, "spaceUuid");
        this.bodyAUuid = Objects.requireNonNull(bodyAUuid, "bodyAUuid");
        this.bodyBUuid = Objects.requireNonNull(bodyBUuid, "bodyBUuid");
        this.type = Objects.requireNonNull(type, "type");
        this.anchorA.set(Objects.requireNonNull(anchorA, "anchorA"));
        this.anchorB.set(Objects.requireNonNull(anchorB, "anchorB"));
        this.axis.set(Objects.requireNonNull(axis, "axis"));
        this.lowerLimit = lowerLimit;
        this.upperLimit = upperLimit;
        this.enabled = enabled;
        this.motorEnabled = motorEnabled;
        this.motorTargetVelocity = motorTargetVelocity;
        this.motorMaxForce = motorMaxForce;
        this.springRestLength = springRestLength;
        this.springStiffness = springStiffness;
        this.springDamping = springDamping;
    }

    @Nonnull
    public UUID getJointUuid() {
        return jointUuid;
    }

    @Nonnull
    public UUID getSpaceUuid() {
        return spaceUuid;
    }

    @Nonnull
    public UUID getBodyAUuid() {
        return bodyAUuid;
    }

    @Nonnull
    public UUID getBodyBUuid() {
        return bodyBUuid;
    }

    @Nonnull
    public JointType getType() {
        return type;
    }

    @Nonnull
    public Vector3f getAnchorA() {
        return new Vector3f(anchorA);
    }

    @Nonnull
    public Vector3f getAnchorB() {
        return new Vector3f(anchorB);
    }

    @Nonnull
    public Vector3f getAxis() {
        return new Vector3f(axis);
    }

    public float getLowerLimit() {
        return lowerLimit;
    }

    public float getUpperLimit() {
        return upperLimit;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isMotorEnabled() {
        return motorEnabled;
    }

    public float getMotorTargetVelocity() {
        return motorTargetVelocity;
    }

    public float getMotorMaxForce() {
        return motorMaxForce;
    }

    public float getSpringRestLength() {
        return springRestLength;
    }

    public float getSpringStiffness() {
        return springStiffness;
    }

    public float getSpringDamping() {
        return springDamping;
    }

    @Nonnull
    public PersistentJointDto copy() {
        PersistentJointDto copy = new PersistentJointDto();
        copy.jointUuid = Objects.requireNonNull(jointUuid, "jointUuid");
        copy.spaceUuid = Objects.requireNonNull(spaceUuid, "spaceUuid");
        copy.bodyAUuid = Objects.requireNonNull(bodyAUuid, "bodyAUuid");
        copy.bodyBUuid = Objects.requireNonNull(bodyBUuid, "bodyBUuid");
        copy.type = type;
        copy.anchorA.set(anchorA);
        copy.anchorB.set(anchorB);
        copy.axis.set(axis);
        copy.lowerLimit = lowerLimit;
        copy.upperLimit = upperLimit;
        copy.enabled = enabled;
        copy.motorEnabled = motorEnabled;
        copy.motorTargetVelocity = motorTargetVelocity;
        copy.motorMaxForce = motorMaxForce;
        copy.springRestLength = springRestLength;
        copy.springStiffness = springStiffness;
        copy.springDamping = springDamping;
        return copy;
    }
}
