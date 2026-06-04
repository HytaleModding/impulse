package dev.hytalemodding.impulse.core.plugin.components;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.core.ImpulsePlugin;
import javax.annotation.Nonnull;

/**
 * Material and damping intent for an ECS rigid body.
 */
public class RigidBodyMaterialComponent implements Component<EntityStore> {

    @Nonnull
    public static final BuilderCodec<RigidBodyMaterialComponent> CODEC = BuilderCodec.builder(
            RigidBodyMaterialComponent.class,
            RigidBodyMaterialComponent::new)
        .append(new KeyedCodec<>("Friction", Codec.FLOAT, false),
            (component, value) -> component.friction = value != null ? value : 0.5f,
            RigidBodyMaterialComponent::getFriction)
        .add()
        .append(new KeyedCodec<>("Restitution", Codec.FLOAT, false),
            (component, value) -> component.restitution = value != null ? value : 0.0f,
            RigidBodyMaterialComponent::getRestitution)
        .add()
        .append(new KeyedCodec<>("LinearDamping", Codec.FLOAT, false),
            (component, value) -> component.linearDamping = value != null ? value : 0.0f,
            RigidBodyMaterialComponent::getLinearDamping)
        .add()
        .append(new KeyedCodec<>("AngularDamping", Codec.FLOAT, false),
            (component, value) -> component.angularDamping = value != null ? value : 0.0f,
            RigidBodyMaterialComponent::getAngularDamping)
        .add()
        .build();

    private float friction = 0.5f;
    private float restitution;
    private float linearDamping;
    private float angularDamping;

    public RigidBodyMaterialComponent() {
    }

    public RigidBodyMaterialComponent(float friction,
        float restitution,
        float linearDamping,
        float angularDamping) {
        this.friction = friction;
        this.restitution = restitution;
        this.linearDamping = linearDamping;
        this.angularDamping = angularDamping;
    }

    public float getFriction() {
        return friction;
    }

    public void setFriction(float friction) {
        this.friction = friction;
    }

    public float getRestitution() {
        return restitution;
    }

    public void setRestitution(float restitution) {
        this.restitution = restitution;
    }

    public float getLinearDamping() {
        return linearDamping;
    }

    public void setLinearDamping(float linearDamping) {
        this.linearDamping = linearDamping;
    }

    public float getAngularDamping() {
        return angularDamping;
    }

    public void setAngularDamping(float angularDamping) {
        this.angularDamping = angularDamping;
    }

    public static ComponentType<EntityStore, RigidBodyMaterialComponent> getComponentType() {
        return ImpulsePlugin.get().getRigidBodyMaterialComponentType();
    }

    @Nonnull
    @Override
    public RigidBodyMaterialComponent clone() {
        return new RigidBodyMaterialComponent(friction,
            restitution,
            linearDamping,
            angularDamping);
    }
}
