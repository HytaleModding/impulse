package dev.hytalemodding.impulse.core.plugin.components;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.ImpulsePlugin;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Explicit physics space target for an ECS rigid body.
 */
public class RigidBodySpaceComponent implements Component<EntityStore> {

    @Nonnull
    public static final BuilderCodec<RigidBodySpaceComponent> CODEC = BuilderCodec.builder(
            RigidBodySpaceComponent.class,
            RigidBodySpaceComponent::new)
        .append(new KeyedCodec<>("SpaceId", Codec.INTEGER, false),
            (component, value) -> component.spaceId = value != null && value > 0
                ? new SpaceId(value)
                : null,
            RigidBodySpaceComponent::getSpaceIdValue)
        .add()
        .build();

    @Nullable
    private SpaceId spaceId;

    public RigidBodySpaceComponent() {
    }

    public RigidBodySpaceComponent(@Nullable SpaceId spaceId) {
        this.spaceId = spaceId;
    }

    @Nullable
    public SpaceId getSpaceId() {
        return spaceId;
    }

    public void setSpaceId(@Nullable SpaceId spaceId) {
        this.spaceId = spaceId;
    }

    public static ComponentType<EntityStore, RigidBodySpaceComponent> getComponentType() {
        return ImpulsePlugin.get().getRigidBodySpaceComponentType();
    }

    @Nullable
    private Integer getSpaceIdValue() {
        return spaceId != null ? spaceId.value() : null;
    }

    @Nonnull
    @Override
    public RigidBodySpaceComponent clone() {
        return new RigidBodySpaceComponent(spaceId);
    }
}
