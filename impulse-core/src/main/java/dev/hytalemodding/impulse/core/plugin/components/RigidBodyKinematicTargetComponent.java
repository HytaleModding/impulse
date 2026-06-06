package dev.hytalemodding.impulse.core.plugin.components;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.math.vector.Vector3fUtil;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.core.ImpulsePlugin;
import dev.hytalemodding.impulse.core.plugin.codec.ImpulseCodecs;
import javax.annotation.Nonnull;
import lombok.Getter;
import lombok.Setter;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Data-only kinematic target for an ECS rigid body.
 */
public class RigidBodyKinematicTargetComponent implements Component<EntityStore> {

    @Nonnull
    public static final BuilderCodec<RigidBodyKinematicTargetComponent> CODEC = BuilderCodec.builder(
            RigidBodyKinematicTargetComponent.class,
            RigidBodyKinematicTargetComponent::new)
        .append(new KeyedCodec<>("Position", Vector3fUtil.CODEC, false),
            (component, value) -> component.position.set(value != null ? value : new Vector3f()),
            RigidBodyKinematicTargetComponent::getPosition)
        .add()
        .append(new KeyedCodec<>("Rotation", ImpulseCodecs.QUATERNIONF, false),
            (component, value) -> component.rotation.set(value != null ? value : new Quaternionf()),
            component -> new Quaternionf(component.rotation))
        .add()
        .append(new KeyedCodec<>("LinearVelocity", Vector3fUtil.CODEC, false),
            (component, value) -> component.linearVelocity.set(value != null ? value : new Vector3f()),
            RigidBodyKinematicTargetComponent::getLinearVelocity)
        .add()
        .append(new KeyedCodec<>("AngularVelocity", Vector3fUtil.CODEC, false),
            (component, value) -> component.angularVelocity.set(value != null ? value : new Vector3f()),
            RigidBodyKinematicTargetComponent::getAngularVelocity)
        .add()
        .append(new KeyedCodec<>("TransformEnabled", Codec.BOOLEAN, false),
            (component, value) -> component.transformEnabled = value == null || value,
            RigidBodyKinematicTargetComponent::isTransformEnabled)
        .add()
        .append(new KeyedCodec<>("VelocityEnabled", Codec.BOOLEAN, false),
            (component, value) -> component.velocityEnabled = value != null && value,
            RigidBodyKinematicTargetComponent::isVelocityEnabled)
        .add()
        .append(new KeyedCodec<>("Activate", Codec.BOOLEAN, false),
            (component, value) -> component.activate = value == null || value,
            RigidBodyKinematicTargetComponent::isActivate)
        .add()
        .build();

    @Nonnull
    private final Vector3f position = new Vector3f();
    @Nonnull
    private final Quaternionf rotation = new Quaternionf();
    @Nonnull
    private final Vector3f linearVelocity = new Vector3f();
    @Nonnull
    private final Vector3f angularVelocity = new Vector3f();
    @Setter
    @Getter
    private boolean transformEnabled = true;
    @Setter
    @Getter
    private boolean velocityEnabled;
    @Setter
    @Getter
    private boolean activate = true;

    public RigidBodyKinematicTargetComponent() {
    }

    public RigidBodyKinematicTargetComponent(@Nonnull Vector3f position,
        @Nonnull Quaternionf rotation,
        @Nonnull Vector3f linearVelocity,
        @Nonnull Vector3f angularVelocity,
        boolean transformEnabled,
        boolean velocityEnabled,
        boolean activate) {
        this.position.set(position);
        this.rotation.set(rotation);
        this.linearVelocity.set(linearVelocity);
        this.angularVelocity.set(angularVelocity);
        this.transformEnabled = transformEnabled;
        this.velocityEnabled = velocityEnabled;
        this.activate = activate;
    }

    @Nonnull
    public Vector3f getPosition() {
        return position;
    }

    @Nonnull
    public Quaternionf getRotation() {
        return rotation;
    }

    @Nonnull
    public Vector3f getLinearVelocity() {
        return linearVelocity;
    }

    @Nonnull
    public Vector3f getAngularVelocity() {
        return angularVelocity;
    }

    public static ComponentType<EntityStore, RigidBodyKinematicTargetComponent> getComponentType() {
        return ImpulsePlugin.get().getRigidBodyKinematicTargetComponentType();
    }

    @Nonnull
    @Override
    public RigidBodyKinematicTargetComponent clone() {
        return new RigidBodyKinematicTargetComponent(position,
            rotation,
            linearVelocity,
            angularVelocity,
            transformEnabled,
            velocityEnabled,
            activate);
    }
}
