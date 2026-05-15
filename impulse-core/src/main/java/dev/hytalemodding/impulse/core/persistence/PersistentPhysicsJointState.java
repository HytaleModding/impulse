package dev.hytalemodding.impulse.core.persistence;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.EnumCodec;
import com.hypixel.hytale.math.vector.Vector3fUtil;
import dev.hytalemodding.impulse.api.PhysicsJoint;
import dev.hytalemodding.impulse.api.PhysicsJointType;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import lombok.Getter;
import lombok.Setter;
import org.joml.Vector3f;

/**
 * Codec-backed definition of one physics joint for the persistence layer.
 *
 * <p>Unlike the old snapshot-based joint format (which used integer body IDs
 * assigned during export), this class uses entity UUIDs to identify the two
 * endpoint bodies. When the hydration systems rebuild a joint, they look up
 * the live bodies by UUID through the entity store.</p>
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
        .append(new KeyedCodec<>("SpaceId", Codec.INTEGER), (state, value) -> state.spaceId = value,
            state -> state.spaceId)
        .add()
        .append(new KeyedCodec<>("BodyAUuid", Codec.UUID_BINARY), (state, value) -> state.bodyAUuid = value,
            state -> state.bodyAUuid)
        .add()
        .append(new KeyedCodec<>("BodyBUuid", Codec.UUID_BINARY), (state, value) -> state.bodyBUuid = value,
            state -> state.bodyBUuid)
        .add()
        .append(new KeyedCodec<>("Type", new EnumCodec<>(PhysicsJointType.class)),
            (state, value) -> state.type = value,
            state -> state.type)
        .add()
        .append(new KeyedCodec<>("AnchorA", Vector3fUtil.CODEC), (state, value) -> state.anchorA.set(value),
            state -> state.anchorA)
        .add()
        .append(new KeyedCodec<>("AnchorB", Vector3fUtil.CODEC), (state, value) -> state.anchorB.set(value),
            state -> state.anchorB)
        .add()
        .append(new KeyedCodec<>("Axis", Vector3fUtil.CODEC, true),
            (state, value) -> state.axis = value != null ? new Vector3f(value) : null,
            state -> state.axis)
        .add()
        .append(new KeyedCodec<>("LowerLimit", Codec.FLOAT), (state, value) -> state.lowerLimit = value,
            state -> state.lowerLimit)
        .add()
        .append(new KeyedCodec<>("UpperLimit", Codec.FLOAT), (state, value) -> state.upperLimit = value,
            state -> state.upperLimit)
        .add()
        .append(new KeyedCodec<>("Enabled", Codec.BOOLEAN), (state, value) -> state.enabled = value,
            state -> state.enabled)
        .add()
        .append(new KeyedCodec<>("MotorEnabled", Codec.BOOLEAN),
            (state, value) -> state.motorEnabled = value,
            state -> state.motorEnabled)
        .add()
        .append(new KeyedCodec<>("MotorTargetVelocity", Codec.FLOAT),
            (state, value) -> state.motorTargetVelocity = value,
            state -> state.motorTargetVelocity)
        .add()
        .append(new KeyedCodec<>("MotorMaxForce", Codec.FLOAT),
            (state, value) -> state.motorMaxForce = value,
            state -> state.motorMaxForce)
        .add()
        .append(new KeyedCodec<>("SpringRestLength", Codec.FLOAT),
            (state, value) -> state.springRestLength = value,
            state -> state.springRestLength)
        .add()
        .append(new KeyedCodec<>("SpringStiffness", Codec.FLOAT),
            (state, value) -> state.springStiffness = value,
            state -> state.springStiffness)
        .add()
        .append(new KeyedCodec<>("SpringDamping", Codec.FLOAT),
            (state, value) -> state.springDamping = value,
            state -> state.springDamping)
        .add()
        .build();

    @Setter
    @Getter
    private int spaceId;
    @Nullable
    private UUID bodyAUuid;
    @Nullable
    private UUID bodyBUuid;
    @Nonnull
    private PhysicsJointType type = PhysicsJointType.FIXED;
    @Nonnull
    private final Vector3f anchorA = new Vector3f();
    @Nonnull
    private final Vector3f anchorB = new Vector3f();
    @Nullable
    private Vector3f axis;
    @Setter
    @Getter
    private float lowerLimit;
    @Setter
    @Getter
    private float upperLimit;
    @Setter
    @Getter
    private boolean enabled = true;
    @Setter
    @Getter
    private boolean motorEnabled;
    @Getter
    @Setter
    private float motorTargetVelocity;
    @Getter
    @Setter
    private float motorMaxForce;
    @Getter
    @Setter
    private float springRestLength = Float.NaN;
    @Getter
    @Setter
    private float springStiffness = Float.NaN;
    @Getter
    @Setter
    private float springDamping = Float.NaN;

    public PersistentPhysicsJointState() {
    }

    @Nonnull
    public static PersistentPhysicsJointState from(int spaceId,
        @Nonnull UUID bodyAUuid,
        @Nonnull UUID bodyBUuid,
        @Nonnull PhysicsJoint joint) {
        PersistentPhysicsJointState state = new PersistentPhysicsJointState();
        state.spaceId = spaceId;
        state.bodyAUuid = bodyAUuid;
        state.bodyBUuid = bodyBUuid;
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
        state.springRestLength = joint.getSpringRestLength();
        state.springStiffness = joint.getSpringStiffness();
        state.springDamping = joint.getSpringDamping();
        return state;
    }

    @Nullable
    public UUID getBodyAUuid() {
        return bodyAUuid;
    }

    public void setBodyAUuid(@Nullable UUID bodyAUuid) {
        this.bodyAUuid = bodyAUuid;
    }

    @Nullable
    public UUID getBodyBUuid() {
        return bodyBUuid;
    }

    public void setBodyBUuid(@Nullable UUID bodyBUuid) {
        this.bodyBUuid = bodyBUuid;
    }

    @Nonnull
    public PhysicsJointType getType() {
        return type;
    }

    public void setType(@Nonnull PhysicsJointType type) {
        this.type = type;
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
            .append(bodyAUuid)
            .append('|')
            .append(bodyBUuid)
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

    @Nonnull
    public PersistentPhysicsJointState copy() {
        PersistentPhysicsJointState copy = new PersistentPhysicsJointState();
        copy.spaceId = spaceId;
        copy.bodyAUuid = bodyAUuid;
        copy.bodyBUuid = bodyBUuid;
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
