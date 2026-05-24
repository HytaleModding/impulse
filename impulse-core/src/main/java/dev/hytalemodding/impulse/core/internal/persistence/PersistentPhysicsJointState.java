package dev.hytalemodding.impulse.core.internal.persistence;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.EnumCodec;
import com.hypixel.hytale.codec.validation.ValidationResults;
import com.hypixel.hytale.codec.validation.Validators;
import com.hypixel.hytale.math.vector.Vector3fUtil;
import dev.hytalemodding.impulse.api.PhysicsJoint;
import dev.hytalemodding.impulse.api.PhysicsJointType;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyId;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import lombok.Getter;
import lombok.Setter;
import org.joml.Vector3f;

/**
 * Codec-backed definition of one physics joint for the persistence layer.
 *
 * This class uses stable physics body ids to identify the two endpoint bodies.
 *
 * <p>The {@link #key()} method produces a deterministic string from all fields
 * (using {@link Float#floatToIntBits(float)} to avoid floating-point drift).
 * The joint hydration system uses this to detect whether a given joint already
 * exists in the runtime space and avoid duplicating it on repeated ticks.</p>
 */
public class PersistentPhysicsJointState {

    @Nonnull
    public static final BuilderCodec<PersistentPhysicsJointState> CODEC = BuilderCodec.builder(
            PersistentPhysicsJointState.class,
            PersistentPhysicsJointState::new)
        .append(new KeyedCodec<>("SpaceId", Codec.INTEGER),
            (state, value) -> state.spaceId = value,
            PersistentPhysicsJointState::getSpaceId)
        .addValidator(Validators.nonNull())
        .addValidator(Validators.range(1, Integer.MAX_VALUE))
        .add()
        .append(new KeyedCodec<>("BodyAId", Codec.UUID_BINARY),
            (state, value) -> state.bodyAId = value,
            PersistentPhysicsJointState::getBodyAIdValue)
        .addValidator(Validators.nonNull())
        .add()
        .append(new KeyedCodec<>("BodyBId", Codec.UUID_BINARY),
            (state, value) -> state.bodyBId = value,
            PersistentPhysicsJointState::getBodyBIdValue)
        .addValidator(Validators.nonNull())
        .add()
        .append(new KeyedCodec<>("Type", new EnumCodec<>(PhysicsJointType.class)),
            (state, value) -> state.type = value,
            PersistentPhysicsJointState::getType)
        .addValidator(Validators.nonNull())
        .add()
        .append(new KeyedCodec<>("AnchorA", Vector3fUtil.CODEC),
            (state, value) -> state.anchorA.set(value),
            PersistentPhysicsJointState::getAnchorA)
        .addValidator(Validators.nonNull())
        .addValidator(PersistentPhysicsValidation.finiteVector(
            "Persisted joint anchor A must be finite"))
        .add()
        .append(new KeyedCodec<>("AnchorB", Vector3fUtil.CODEC),
            (state, value) -> state.anchorB.set(value),
            PersistentPhysicsJointState::getAnchorB)
        .addValidator(Validators.nonNull())
        .addValidator(PersistentPhysicsValidation.finiteVector(
            "Persisted joint anchor B must be finite"))
        .add()
        .append(new KeyedCodec<>("Axis", Vector3fUtil.CODEC, true),
            (state, value) -> state.axis = value != null ? new Vector3f(value) : null,
            PersistentPhysicsJointState::getAxis)
        .addValidator(PersistentPhysicsValidation.finiteVector(
            "Persisted joint axis must be finite"))
        .add()
        .append(new KeyedCodec<>("LowerLimit", Codec.FLOAT),
            (state, value) -> state.lowerLimit = value,
            PersistentPhysicsJointState::getLowerLimit)
        .addValidator(PersistentPhysicsValidation.finiteFloat(
            "Persisted joint lower limit must be finite"))
        .add()
        .append(new KeyedCodec<>("UpperLimit", Codec.FLOAT),
            (state, value) -> state.upperLimit = value,
            PersistentPhysicsJointState::getUpperLimit)
        .addValidator(PersistentPhysicsValidation.finiteFloat(
            "Persisted joint upper limit must be finite"))
        .add()
        .append(new KeyedCodec<>("Enabled", Codec.BOOLEAN),
            (state, value) -> state.enabled = value,
            PersistentPhysicsJointState::isEnabled)
        .add()
        .append(new KeyedCodec<>("MotorEnabled", Codec.BOOLEAN),
            (state, value) -> state.motorEnabled = value,
            PersistentPhysicsJointState::isMotorEnabled)
        .add()
        .append(new KeyedCodec<>("MotorTargetVelocity", Codec.FLOAT),
            (state, value) -> state.motorTargetVelocity = value,
            PersistentPhysicsJointState::getMotorTargetVelocity)
        .addValidator(PersistentPhysicsValidation.finiteFloat(
            "Persisted joint motor target velocity must be finite"))
        .add()
        .append(new KeyedCodec<>("MotorMaxForce", Codec.FLOAT),
            (state, value) -> state.motorMaxForce = value,
            PersistentPhysicsJointState::getMotorMaxForce)
        .addValidator(PersistentPhysicsValidation.nonNegativeFiniteFloat(
            "Persisted joint motor max force must be finite and >= 0"))
        .add()
        .append(new KeyedCodec<>("SpringRestLength", Codec.FLOAT, false),
            (state, value) -> state.springRestLength = value,
            PersistentPhysicsJointState::getSpringRestLength)
        .addValidator(PersistentPhysicsValidation.nonNegativeFiniteFloat(
            "Persisted joint spring rest length must be finite and >= 0"))
        .add()
        .append(new KeyedCodec<>("SpringStiffness", Codec.FLOAT, false),
            (state, value) -> state.springStiffness = value,
            PersistentPhysicsJointState::getSpringStiffness)
        .addValidator(PersistentPhysicsValidation.nonNegativeFiniteFloat(
            "Persisted joint spring stiffness must be finite and >= 0"))
        .add()
        .append(new KeyedCodec<>("SpringDamping", Codec.FLOAT, false),
            (state, value) -> state.springDamping = value,
            PersistentPhysicsJointState::getSpringDamping)
        .addValidator(PersistentPhysicsValidation.nonNegativeFiniteFloat(
            "Persisted joint spring damping must be finite and >= 0"))
        .add()
        .afterDecode(PersistentPhysicsJointState::validateAfterDecode)
        .build();

    @Getter
    @Setter
    private int spaceId;
    @Nullable
    private UUID bodyAId;
    @Nullable
    private UUID bodyBId;
    @Nonnull
    @Setter
    private PhysicsJointType type = PhysicsJointType.FIXED;
    @Nonnull
    private final Vector3f anchorA = new Vector3f();
    @Nonnull
    private final Vector3f anchorB = new Vector3f();
    @Nullable
    private Vector3f axis;
    @Getter
    @Setter
    private float lowerLimit;
    @Getter
    @Setter
    private float upperLimit;
    @Getter
    @Setter
    private boolean enabled = true;
    @Getter
    @Setter
    private boolean motorEnabled;
    @Getter
    @Setter
    private float motorTargetVelocity;
    @Getter
    @Setter
    private float motorMaxForce;
    @Getter
    @Setter
    private float springRestLength = 0f;
    @Getter
    @Setter
    private float springStiffness = 0f;
    @Getter
    @Setter
    private float springDamping = 0f;

    public PersistentPhysicsJointState() {
    }

    @Nonnull
    public PhysicsJointType getType() {
        return type;
    }

    @Nonnull
    public Vector3f getAnchorA() {
        return anchorA;
    }

    @Nonnull
    public Vector3f getAnchorB() {
        return anchorB;
    }

    @Nullable
    public Vector3f getAxis() {
        return axis;
    }

    @Nonnull
    public static PersistentPhysicsJointState from(int spaceId,
        @Nonnull PhysicsBodyId bodyAId,
        @Nonnull PhysicsBodyId bodyBId,
        @Nonnull PhysicsJoint joint) {
        PersistentPhysicsJointState state = new PersistentPhysicsJointState();
        state.spaceId = spaceId;
        state.bodyAId = bodyAId.value();
        state.bodyBId = bodyBId.value();
        state.type = joint.getType();
        state.anchorA.set(joint.getAnchorA());
        state.anchorB.set(joint.getAnchorB());
        Vector3f jointAxis = joint.getAxis();
        state.axis = jointAxis != null ? new Vector3f(jointAxis) : null;
        state.lowerLimit = joint.getLowerLimit();
        state.upperLimit = joint.getUpperLimit();
        state.enabled = joint.isEnabled();
        state.motorEnabled = joint.isMotorEnabled();
        state.motorTargetVelocity = joint.getMotorTargetVelocity();
        state.motorMaxForce = joint.getMotorMaxForce();
        state.springRestLength = nanToZero(joint.getSpringRestLength());
        state.springStiffness = nanToZero(joint.getSpringStiffness());
        state.springDamping = nanToZero(joint.getSpringDamping());
        return state;
    }

    @Nullable
    public PhysicsBodyId getBodyAId() {
        return bodyAId != null ? PhysicsBodyId.of(bodyAId) : null;
    }

    @Nullable
    public UUID getBodyAIdValue() {
        return bodyAId;
    }

    public void setBodyAId(@Nullable PhysicsBodyId bodyAId) {
        this.bodyAId = bodyAId != null ? bodyAId.value() : null;
    }

    @Nullable
    public PhysicsBodyId getBodyBId() {
        return bodyBId != null ? PhysicsBodyId.of(bodyBId) : null;
    }

    @Nullable
    public UUID getBodyBIdValue() {
        return bodyBId;
    }

    public void setBodyBId(@Nullable PhysicsBodyId bodyBId) {
        this.bodyBId = bodyBId != null ? bodyBId.value() : null;
    }

    public void setAxis(@Nullable Vector3f axis) {
        this.axis = axis != null ? new Vector3f(axis) : null;
    }

    @Nonnull
    public String key() {
        return String.valueOf(spaceId)
            + '|'
            + type.name()
            + '|'
            + bodyAId
            + '|'
            + bodyBId
            + '|'
            + bits(anchorA.x)
            + '|'
            + bits(anchorA.y)
            + '|'
            + bits(anchorA.z)
            + '|'
            + bits(anchorB.x)
            + '|'
            + bits(anchorB.y)
            + '|'
            + bits(anchorB.z)
            + '|'
            + (axis != null ? bits(axis.x) : "na")
            + '|'
            + (axis != null ? bits(axis.y) : "na")
            + '|'
            + (axis != null ? bits(axis.z) : "na")
            + '|'
            + bits(lowerLimit)
            + '|'
            + bits(upperLimit)
            + '|'
            + enabled
            + '|'
            + motorEnabled
            + '|'
            + bits(motorTargetVelocity)
            + '|'
            + bits(motorMaxForce)
            + '|'
            + bits(springRestLength)
            + '|'
            + bits(springStiffness)
            + '|'
            + bits(springDamping);
    }

    private static int bits(float value) {
        return Float.floatToIntBits(value);
    }

    private static float nanToZero(float value) {
        return Float.isNaN(value) ? 0f : value;
    }

    @Nonnull
    public PersistentPhysicsJointState copy() {
        PersistentPhysicsJointState copy = new PersistentPhysicsJointState();
        copy.spaceId = spaceId;
        copy.bodyAId = bodyAId;
        copy.bodyBId = bodyBId;
        copy.type = type;
        copy.anchorA.set(anchorA);
        copy.anchorB.set(anchorB);
        copy.axis = axis != null ? new Vector3f(axis) : null;
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

    private static void validateAfterDecode(@Nonnull PersistentPhysicsJointState state,
        @Nonnull ExtraInfo extraInfo) {
        ValidationResults results = extraInfo.getValidationResults();
        if ((state.type == PhysicsJointType.HINGE || state.type == PhysicsJointType.SLIDER)
            && state.axis == null) {
            results.fail("Persisted " + state.type + " joint requires an axis");
        }
        if (state.lowerLimit > state.upperLimit) {
            results.fail("Persisted joint lower limit cannot exceed upper limit");
        }
        results._processValidationResults();
    }
}
