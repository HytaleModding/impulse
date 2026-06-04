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
 * Mass intent for an ECS rigid body.
 */
public class RigidBodyMassComponent implements Component<EntityStore> {

    @Nonnull
    public static final BuilderCodec<RigidBodyMassComponent> CODEC = BuilderCodec.builder(
            RigidBodyMassComponent.class,
            RigidBodyMassComponent::new)
        .append(new KeyedCodec<>("Mass", Codec.FLOAT, false),
            (component, value) -> component.mass = value != null ? value : 1.0f,
            RigidBodyMassComponent::getMass)
        .add()
        .build();

    private float mass = 1.0f;

    public RigidBodyMassComponent() {
    }

    public RigidBodyMassComponent(float mass) {
        this.mass = mass;
    }

    public float getMass() {
        return mass;
    }

    public void setMass(float mass) {
        this.mass = mass;
    }

    public static ComponentType<EntityStore, RigidBodyMassComponent> getComponentType() {
        return ImpulsePlugin.get().getRigidBodyMassComponentType();
    }

    @Nonnull
    @Override
    public RigidBodyMassComponent clone() {
        return new RigidBodyMassComponent(mass);
    }
}
