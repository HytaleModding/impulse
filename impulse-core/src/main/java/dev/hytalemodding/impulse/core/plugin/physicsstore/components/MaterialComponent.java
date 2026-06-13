package dev.hytalemodding.impulse.core.plugin.physicsstore.components;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.core.plugin.physicsstore.PhysicsStoreTypes;
import javax.annotation.Nonnull;

/**
 * Physical material row referenced by colliders.
 */
public final class MaterialComponent implements Component<PhysicsStore> {

    @Nonnull
    public static final BuilderCodec<MaterialComponent> CODEC = BuilderCodec.builder(
            MaterialComponent.class,
            MaterialComponent::new)
        .append(new KeyedCodec<>("Friction", Codec.FLOAT, false),
            (component, value) -> component.friction = value != null ? value : 0.5f,
            MaterialComponent::getFriction)
        .add()
        .append(new KeyedCodec<>("Restitution", Codec.FLOAT, false),
            (component, value) -> component.restitution = value != null ? value : 0.0f,
            MaterialComponent::getRestitution)
        .add()
        .build();

    private float friction = 0.5f;
    private float restitution;

    public MaterialComponent() {
    }

    public MaterialComponent(float friction, float restitution) {
        this.friction = friction;
        this.restitution = restitution;
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

    @Nonnull
    public static ComponentType<PhysicsStore, MaterialComponent> getComponentType() {
        return PhysicsStoreTypes.materialComponentType();
    }

    @Nonnull
    @Override
    public MaterialComponent clone() {
        return new MaterialComponent(friction, restitution);
    }
}
