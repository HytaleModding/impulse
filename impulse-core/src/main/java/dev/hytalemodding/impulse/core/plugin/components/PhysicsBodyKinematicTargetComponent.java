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
import java.util.Objects;
import javax.annotation.Nonnull;
import lombok.Getter;
import lombok.Setter;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Data-only kinematic target for an entity-authored physics body.
 */
public class PhysicsBodyKinematicTargetComponent implements Component<EntityStore> {

    @Nonnull
    public static final BuilderCodec<PhysicsBodyKinematicTargetComponent> CODEC = BuilderCodec.builder(
            PhysicsBodyKinematicTargetComponent.class,
            PhysicsBodyKinematicTargetComponent::new)
        .append(new KeyedCodec<>("Position", Vector3fUtil.CODEC, false),
            (component, value) -> component.position.set(value != null ? value : new Vector3f()),
            PhysicsBodyKinematicTargetComponent::getPosition)
        .add()
        .append(new KeyedCodec<>("Rotation", ImpulseCodecs.QUATERNIONF, false),
            (component, value) -> component.rotation.set(value != null ? value : new Quaternionf()),
            component -> new Quaternionf(component.rotation))
        .add()
        .append(new KeyedCodec<>("LinearVelocity", Vector3fUtil.CODEC, false),
            (component, value) -> component.linearVelocity.set(value != null ? value : new Vector3f()),
            PhysicsBodyKinematicTargetComponent::getLinearVelocity)
        .add()
        .append(new KeyedCodec<>("AngularVelocity", Vector3fUtil.CODEC, false),
            (component, value) -> component.angularVelocity.set(value != null ? value : new Vector3f()),
            PhysicsBodyKinematicTargetComponent::getAngularVelocity)
        .add()
        .append(new KeyedCodec<>("TransformEnabled", Codec.BOOLEAN, false),
            (component, value) -> component.transformEnabled = value == null || value,
            PhysicsBodyKinematicTargetComponent::isTransformEnabled)
        .add()
        .append(new KeyedCodec<>("VelocityEnabled", Codec.BOOLEAN, false),
            (component, value) -> component.velocityEnabled = value != null && value,
            PhysicsBodyKinematicTargetComponent::isVelocityEnabled)
        .add()
        .append(new KeyedCodec<>("Activate", Codec.BOOLEAN, false),
            (component, value) -> component.activate = value == null || value,
            PhysicsBodyKinematicTargetComponent::isActivate)
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

    public PhysicsBodyKinematicTargetComponent() {
    }

    public PhysicsBodyKinematicTargetComponent(@Nonnull Vector3f position,
        @Nonnull Quaternionf rotation,
        @Nonnull Vector3f linearVelocity,
        @Nonnull Vector3f angularVelocity,
        boolean transformEnabled,
        boolean velocityEnabled,
        boolean activate) {
        this.position.set(Objects.requireNonNull(position, "position"));
        this.rotation.set(Objects.requireNonNull(rotation, "rotation"));
        this.linearVelocity.set(Objects.requireNonNull(linearVelocity, "linearVelocity"));
        this.angularVelocity.set(Objects.requireNonNull(angularVelocity, "angularVelocity"));
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

    public static ComponentType<EntityStore, PhysicsBodyKinematicTargetComponent> getComponentType() {
        return ImpulsePlugin.get().getPhysicsBodyKinematicTargetComponentType();
    }

    @Nonnull
    @Override
    public PhysicsBodyKinematicTargetComponent clone() {
        return new PhysicsBodyKinematicTargetComponent(position,
            rotation,
            linearVelocity,
            angularVelocity,
            transformEnabled,
            velocityEnabled,
            activate);
    }
}
