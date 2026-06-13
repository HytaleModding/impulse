package dev.hytalemodding.impulse.core.plugin.physicsstore.components;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.math.vector.Vector3fUtil;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.core.plugin.codec.ImpulseCodecs;
import dev.hytalemodding.impulse.core.plugin.physicsstore.PhysicsStoreTypes;
import javax.annotation.Nonnull;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Kinematic target state applied by PhysicsStore systems to a bound body.
 */
public final class TargetComponent implements Component<PhysicsStore> {

    private static final Vector3f ZERO = new Vector3f();
    private static final Quaternionf IDENTITY = new Quaternionf();

    @Nonnull
    public static final BuilderCodec<TargetComponent> CODEC = BuilderCodec.builder(
            TargetComponent.class,
            TargetComponent::new)
        .append(new KeyedCodec<>("Active", Codec.BOOLEAN, false),
            (component, value) -> component.active = value != null && value,
            TargetComponent::isActive)
        .add()
        .append(new KeyedCodec<>("Position", Vector3fUtil.CODEC, false),
            (component, value) -> component.position.set(value != null ? value : ZERO),
            TargetComponent::getPosition)
        .add()
        .append(new KeyedCodec<>("Rotation", ImpulseCodecs.QUATERNIONF, false),
            (component, value) -> component.rotation.set(value != null ? value : IDENTITY),
            TargetComponent::getRotation)
        .add()
        .append(new KeyedCodec<>("LinearVelocity", Vector3fUtil.CODEC, false),
            (component, value) -> component.linearVelocity.set(value != null ? value : ZERO),
            TargetComponent::getLinearVelocity)
        .add()
        .append(new KeyedCodec<>("AngularVelocity", Vector3fUtil.CODEC, false),
            (component, value) -> component.angularVelocity.set(value != null ? value : ZERO),
            TargetComponent::getAngularVelocity)
        .add()
        .build();

    private boolean active;
    @Nonnull
    private final Vector3f position = new Vector3f();
    @Nonnull
    private final Quaternionf rotation = new Quaternionf();
    @Nonnull
    private final Vector3f linearVelocity = new Vector3f();
    @Nonnull
    private final Vector3f angularVelocity = new Vector3f();

    public TargetComponent() {
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    @Nonnull
    public Vector3f getPosition() {
        return new Vector3f(position);
    }

    public void setPosition(@Nonnull Vector3f position) {
        this.position.set(position);
    }

    @Nonnull
    public Quaternionf getRotation() {
        return new Quaternionf(rotation);
    }

    public void setRotation(@Nonnull Quaternionf rotation) {
        this.rotation.set(rotation);
    }

    @Nonnull
    public Vector3f getLinearVelocity() {
        return new Vector3f(linearVelocity);
    }

    public void setLinearVelocity(@Nonnull Vector3f linearVelocity) {
        this.linearVelocity.set(linearVelocity);
    }

    @Nonnull
    public Vector3f getAngularVelocity() {
        return new Vector3f(angularVelocity);
    }

    public void setAngularVelocity(@Nonnull Vector3f angularVelocity) {
        this.angularVelocity.set(angularVelocity);
    }

    @Nonnull
    public static ComponentType<PhysicsStore, TargetComponent> getComponentType() {
        return PhysicsStoreTypes.targetComponentType();
    }

    @Nonnull
    @Override
    public TargetComponent clone() {
        TargetComponent copy = new TargetComponent();
        copy.active = active;
        copy.position.set(position);
        copy.rotation.set(rotation);
        copy.linearVelocity.set(linearVelocity);
        copy.angularVelocity.set(angularVelocity);
        return copy;
    }
}
