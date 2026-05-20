package dev.hytalemodding.impulse.core.persistence;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.EnumCodec;
import com.hypixel.hytale.math.vector.Vector3fUtil;
import dev.hytalemodding.impulse.api.PhysicsJoint;
import dev.hytalemodding.impulse.api.PhysicsJointType;
import dev.hytalemodding.impulse.core.resources.PhysicsBodyId;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
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
        .add()
        .append(new KeyedCodec<>("BodyAId", Codec.UUID_BINARY),
            (state, value) -> state.bodyAId = value,
            PersistentPhysicsJointState::getBodyAIdValue)
        .add()
        .append(new KeyedCodec<>("BodyBId", Codec.UUID_BINARY),
            (state, value) -> state.bodyBId = value,
            PersistentPhysicsJointState::getBodyBIdValue)
        .add()
        .append(new KeyedCodec<>("Type", new EnumCodec<>(PhysicsJointType.class)),
            (state, value) -> state.type = value,
            PersistentPhysicsJointState::getType)
        .add()
        .append(new KeyedCodec<>("AnchorA", Vector3fUtil.CODEC),
            (state, value) -> state.anchorA.set(value),
            PersistentPhysicsJointState::getAnchorA)
        .add()
        .append(new KeyedCodec<>("AnchorB", Vector3fUtil.CODEC),
            (state, value) -> state.anchorB.set(value),
            PersistentPhysicsJointState::getAnchorB)
        .add()
        .append(new KeyedCodec<>("Axis", Vector3fUtil.CODEC, true),
            (state, value) -> state.axis = value != null ? new Vector3f(value) : null,
            PersistentPhysicsJointState::getAxis)
        .add()
        .append(new KeyedCodec<>("LowerLimit", Codec.FLOAT),
            (state, value) -> state.lowerLimit = value,
            PersistentPhysicsJointState::getLowerLimit)
        .add()
        .append(new KeyedCodec<>("UpperLimit", Codec.FLOAT),
            (state, value) -> state.upperLimit = value,
            PersistentPhysicsJointState::getUpperLimit)
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
        .add()
        .append(new KeyedCodec<>("MotorMaxForce", Codec.FLOAT),
            (state, value) -> state.motorMaxForce = value,
            PersistentPhysicsJointState::getMotorMaxForce)
        .add()
        .append(new KeyedCodec<>("SpringRestLength", Codec.FLOAT, false),
            (state, value) -> state.springRestLength = nanToZero(value),
            PersistentPhysicsJointState::getSpringRestLength)
        .add()
        .append(new KeyedCodec<>("SpringStiffness", Codec.FLOAT, false),
            (state, value) -> state.springStiffness = nanToZero(value),
            PersistentPhysicsJointState::getSpringStiffness)
        .add()
        .append(new KeyedCodec<>("SpringDamping", Codec.FLOAT, false),
            (state, value) -> state.springDamping = nanToZero(value),
            PersistentPhysicsJointState::getSpringDamping)
        .add()
        .build();

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
    @Setter
    private float lowerLimit;
    @Setter
    private float upperLimit;
    @Setter
    private boolean enabled = true;
    @Setter
    private boolean motorEnabled;
    @Setter
    private float motorTargetVelocity;
    @Setter
    private float motorMaxForce;
    @Setter
    private float springRestLength = 0f;
    @Setter
    private float springStiffness = 0f;
    @Setter
    private float springDamping = 0f;

    public PersistentPhysicsJointState() {
    }

    public int getSpaceId() {
        return spaceId;
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
        StringBuilder builder = new StringBuilder();
        builder.append(spaceId)
            .append('|')
            .append(type.name())
            .append('|')
            .append(bodyAId)
            .append('|')
            .append(bodyBId)
            .append('|')
            .append(bits(anchorA.x))
            .append('|')
            .append(bits(anchorA.y))
            .append('|')
            .append(bits(anchorA.z))
            .append('|')
            .append(bits(anchorB.x))
            .append('|')
            .append(bits(anchorB.y))
            .append('|')
            .append(bits(anchorB.z))
            .append('|')
            .append(axis != null ? bits(axis.x) : "na")
            .append('|')
            .append(axis != null ? bits(axis.y) : "na")
            .append('|')
            .append(axis != null ? bits(axis.z) : "na")
            .append('|')
            .append(bits(lowerLimit))
            .append('|')
            .append(bits(upperLimit))
            .append('|')
            .append(enabled)
            .append('|')
            .append(motorEnabled)
            .append('|')
            .append(bits(motorTargetVelocity))
            .append('|')
            .append(bits(motorMaxForce))
            .append('|')
            .append(bits(springRestLength))
            .append('|')
            .append(bits(springStiffness))
            .append('|')
            .append(bits(springDamping));
        return builder.toString();
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
}
