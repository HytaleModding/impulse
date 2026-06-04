package dev.hytalemodding.impulse.core.plugin.components;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.core.ImpulsePlugin;
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import java.util.UUID;
import javax.annotation.Nonnull;

/**
 * Stable solver rigid-body key for ECS rigid body definitions.
 */
public class RigidBodyKeyComponent implements Component<EntityStore> {

    @Nonnull
    public static final BuilderCodec<RigidBodyKeyComponent> CODEC = BuilderCodec.builder(
            RigidBodyKeyComponent.class,
            RigidBodyKeyComponent::new)
        .append(new KeyedCodec<>("BodyId", Codec.UUID_BINARY, false),
            (component, value) -> component.bodyKey = value != null
                ? RigidBodyKey.of(value)
                : RigidBodyKey.random(),
            RigidBodyKeyComponent::getBodyKeyValue)
        .add()
        .build();

    @Nonnull
    private RigidBodyKey bodyKey = RigidBodyKey.random();

    public RigidBodyKeyComponent() {
    }

    public RigidBodyKeyComponent(@Nonnull RigidBodyKey bodyKey) {
        this.bodyKey = bodyKey;
    }

    @Nonnull
    public RigidBodyKey getBodyKey() {
        return bodyKey;
    }

    public void setBodyKey(@Nonnull RigidBodyKey bodyKey) {
        this.bodyKey = bodyKey;
    }

    public static ComponentType<EntityStore, RigidBodyKeyComponent> getComponentType() {
        return ImpulsePlugin.get().getRigidBodyKeyComponentType();
    }

    @Nonnull
    private UUID getBodyKeyValue() {
        return bodyKey.value();
    }

    @Nonnull
    @Override
    public RigidBodyKeyComponent clone() {
        return new RigidBodyKeyComponent(bodyKey);
    }
}
