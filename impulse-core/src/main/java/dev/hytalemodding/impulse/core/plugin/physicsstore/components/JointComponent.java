package dev.hytalemodding.impulse.core.plugin.physicsstore.components;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.EnumCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.math.vector.Vector3fUtil;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.core.plugin.physicsstore.PhysicsStoreTypes;
import dev.hytalemodding.impulse.core.plugin.simulation.JointType;
import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nonnull;
import org.joml.Vector3f;

/**
 * Authored joint row keyed by durable endpoint body UUIDs.
 */
public final class JointComponent implements Component<PhysicsStore> {

    private static final Vector3f ZERO = new Vector3f();

    @Nonnull
    public static final BuilderCodec<JointComponent> CODEC = BuilderCodec.builder(
            JointComponent.class,
            JointComponent::new)
        .append(new KeyedCodec<>("SpaceUuid", Codec.UUID_BINARY, false),
            (component, value) -> component.spaceUuid = value,
            JointComponent::getSpaceUuid)
        .add()
        .append(new KeyedCodec<>("BodyAUuid", Codec.UUID_BINARY, false),
            (component, value) -> component.bodyAUuid = value,
            JointComponent::getBodyAUuid)
        .add()
        .append(new KeyedCodec<>("BodyBUuid", Codec.UUID_BINARY, false),
            (component, value) -> component.bodyBUuid = value,
            JointComponent::getBodyBUuid)
        .add()
        .append(new KeyedCodec<>("Type", new EnumCodec<>(JointType.class), false),
            (component, value) -> component.type = value != null ? value : JointType.FIXED,
            JointComponent::getType)
        .add()
        .append(new KeyedCodec<>("AnchorA", Vector3fUtil.CODEC, false),
            (component, value) -> component.anchorA.set(value != null ? value : ZERO),
            JointComponent::getAnchorA)
        .add()
        .append(new KeyedCodec<>("AnchorB", Vector3fUtil.CODEC, false),
            (component, value) -> component.anchorB.set(value != null ? value : ZERO),
            JointComponent::getAnchorB)
        .add()
        .append(new KeyedCodec<>("Axis", Vector3fUtil.CODEC, false),
            (component, value) -> component.axis.set(value != null ? value : ZERO),
            JointComponent::getAxis)
        .add()
        .append(new KeyedCodec<>("LowerLimit", Codec.FLOAT, false),
            (component, value) -> component.lowerLimit = value != null ? value : 0.0f,
            JointComponent::getLowerLimit)
        .add()
        .append(new KeyedCodec<>("UpperLimit", Codec.FLOAT, false),
            (component, value) -> component.upperLimit = value != null ? value : 0.0f,
            JointComponent::getUpperLimit)
        .add()
        .append(new KeyedCodec<>("Enabled", Codec.BOOLEAN, false),
            (component, value) -> component.enabled = value == null || value,
            JointComponent::isEnabled)
        .add()
        .append(new KeyedCodec<>("MotorEnabled", Codec.BOOLEAN, false),
            (component, value) -> component.motorEnabled = value != null && value,
            JointComponent::isMotorEnabled)
        .add()
        .append(new KeyedCodec<>("MotorTargetVelocity", Codec.FLOAT, false),
            (component, value) -> component.motorTargetVelocity = value != null ? value : 0.0f,
            JointComponent::getMotorTargetVelocity)
        .add()
        .append(new KeyedCodec<>("MotorMaxForce", Codec.FLOAT, false),
            (component, value) -> component.motorMaxForce = value != null ? value : 0.0f,
            JointComponent::getMotorMaxForce)
        .add()
        .append(new KeyedCodec<>("SpringRestLength", Codec.FLOAT, false),
            (component, value) -> component.springRestLength = value != null ? value : 0.0f,
            JointComponent::getSpringRestLength)
        .add()
        .append(new KeyedCodec<>("SpringStiffness", Codec.FLOAT, false),
            (component, value) -> component.springStiffness = value != null ? value : 0.0f,
            JointComponent::getSpringStiffness)
        .add()
        .append(new KeyedCodec<>("SpringDamping", Codec.FLOAT, false),
            (component, value) -> component.springDamping = value != null ? value : 0.0f,
            JointComponent::getSpringDamping)
        .add()
        .build();

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

    public JointComponent() {
    }

    @Nonnull
    public UUID getSpaceUuid() {
        return spaceUuid;
    }

    public void setSpaceUuid(@Nonnull UUID spaceUuid) {
        this.spaceUuid = Objects.requireNonNull(spaceUuid, "spaceUuid");
    }

    @Nonnull
    public UUID getBodyAUuid() {
        return bodyAUuid;
    }

    public void setBodyAUuid(@Nonnull UUID bodyAUuid) {
        this.bodyAUuid = Objects.requireNonNull(bodyAUuid, "bodyAUuid");
    }

    @Nonnull
    public UUID getBodyBUuid() {
        return bodyBUuid;
    }

    public void setBodyBUuid(@Nonnull UUID bodyBUuid) {
        this.bodyBUuid = Objects.requireNonNull(bodyBUuid, "bodyBUuid");
    }

    @Nonnull
    public JointType getType() {
        return type;
    }

    public void setType(@Nonnull JointType type) {
        this.type = Objects.requireNonNull(type, "type");
    }

    @Nonnull
    public Vector3f getAnchorA() {
        return new Vector3f(anchorA);
    }

    public void setAnchorA(@Nonnull Vector3f anchorA) {
        this.anchorA.set(Objects.requireNonNull(anchorA, "anchorA"));
    }

    @Nonnull
    public Vector3f getAnchorB() {
        return new Vector3f(anchorB);
    }

    public void setAnchorB(@Nonnull Vector3f anchorB) {
        this.anchorB.set(Objects.requireNonNull(anchorB, "anchorB"));
    }

    @Nonnull
    public Vector3f getAxis() {
        return new Vector3f(axis);
    }

    public void setAxis(@Nonnull Vector3f axis) {
        this.axis.set(Objects.requireNonNull(axis, "axis"));
    }

    public float getLowerLimit() {
        return lowerLimit;
    }

    public void setLowerLimit(float lowerLimit) {
        this.lowerLimit = lowerLimit;
    }

    public float getUpperLimit() {
        return upperLimit;
    }

    public void setUpperLimit(float upperLimit) {
        this.upperLimit = upperLimit;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isMotorEnabled() {
        return motorEnabled;
    }

    public void setMotorEnabled(boolean motorEnabled) {
        this.motorEnabled = motorEnabled;
    }

    public float getMotorTargetVelocity() {
        return motorTargetVelocity;
    }

    public void setMotorTargetVelocity(float motorTargetVelocity) {
        this.motorTargetVelocity = motorTargetVelocity;
    }

    public float getMotorMaxForce() {
        return motorMaxForce;
    }

    public void setMotorMaxForce(float motorMaxForce) {
        this.motorMaxForce = motorMaxForce;
    }

    public float getSpringRestLength() {
        return springRestLength;
    }

    public void setSpringRestLength(float springRestLength) {
        this.springRestLength = springRestLength;
    }

    public float getSpringStiffness() {
        return springStiffness;
    }

    public void setSpringStiffness(float springStiffness) {
        this.springStiffness = springStiffness;
    }

    public float getSpringDamping() {
        return springDamping;
    }

    public void setSpringDamping(float springDamping) {
        this.springDamping = springDamping;
    }

    @Nonnull
    public static ComponentType<PhysicsStore, JointComponent> getComponentType() {
        return PhysicsStoreTypes.jointComponentType();
    }

    @Nonnull
    @Override
    public JointComponent clone() {
        JointComponent copy = new JointComponent();
        copy.spaceUuid = spaceUuid;
        copy.bodyAUuid = bodyAUuid;
        copy.bodyBUuid = bodyBUuid;
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
