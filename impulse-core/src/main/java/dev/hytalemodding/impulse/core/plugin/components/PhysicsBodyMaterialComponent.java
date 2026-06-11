package dev.hytalemodding.impulse.core.plugin.components;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.core.ImpulsePlugin;
import javax.annotation.Nonnull;
import lombok.Getter;
import lombok.Setter;

/**
 * Durable ECS material data for an entity-authored physics body.
 */
public class PhysicsBodyMaterialComponent implements Component<EntityStore> {

    @Nonnull
    public static final BuilderCodec<PhysicsBodyMaterialComponent> CODEC = BuilderCodec.builder(
            PhysicsBodyMaterialComponent.class,
            PhysicsBodyMaterialComponent::new)
        .append(new KeyedCodec<>("Friction", Codec.FLOAT, false),
            (component, value) -> component.friction = value != null ? value : 0.5f,
            PhysicsBodyMaterialComponent::getFriction)
        .add()
        .append(new KeyedCodec<>("Restitution", Codec.FLOAT, false),
            (component, value) -> component.restitution = value != null ? value : 0.0f,
            PhysicsBodyMaterialComponent::getRestitution)
        .add()
        .build();

    @Setter
    @Getter
    private float friction = 0.5f;
    @Setter
    @Getter
    private float restitution;

    public PhysicsBodyMaterialComponent() {
    }

    public PhysicsBodyMaterialComponent(float friction, float restitution) {
        this.friction = friction;
        this.restitution = restitution;
    }

    public static ComponentType<EntityStore, PhysicsBodyMaterialComponent> getComponentType() {
        return ImpulsePlugin.get().getPhysicsBodyMaterialComponentType();
    }

    @Nonnull
    @Override
    public PhysicsBodyMaterialComponent clone() {
        return new PhysicsBodyMaterialComponent(friction, restitution);
    }
}
