package dev.hytalemodding.impulse.core.plugin.components;

import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.EnumCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.core.ImpulsePlugin;
import javax.annotation.Nonnull;

/**
 * Motion mode intent for an ECS rigid body.
 */
public class RigidBodyTypeComponent implements Component<EntityStore> {

    @Nonnull
    public static final BuilderCodec<RigidBodyTypeComponent> CODEC = BuilderCodec.builder(
            RigidBodyTypeComponent.class,
            RigidBodyTypeComponent::new)
        .append(new KeyedCodec<>("BodyType", new EnumCodec<>(PhysicsBodyType.class), false),
            (component, value) -> component.bodyType = value != null
                ? value
                : PhysicsBodyType.DYNAMIC,
            RigidBodyTypeComponent::getBodyType)
        .add()
        .build();

    @Nonnull
    private PhysicsBodyType bodyType = PhysicsBodyType.DYNAMIC;

    public RigidBodyTypeComponent() {
    }

    public RigidBodyTypeComponent(@Nonnull PhysicsBodyType bodyType) {
        this.bodyType = bodyType;
    }

    @Nonnull
    public PhysicsBodyType getBodyType() {
        return bodyType;
    }

    public void setBodyType(@Nonnull PhysicsBodyType bodyType) {
        this.bodyType = bodyType;
    }

    public static ComponentType<EntityStore, RigidBodyTypeComponent> getComponentType() {
        return ImpulsePlugin.get().getRigidBodyTypeComponentType();
    }

    @Nonnull
    @Override
    public RigidBodyTypeComponent clone() {
        return new RigidBodyTypeComponent(bodyType);
    }
}
