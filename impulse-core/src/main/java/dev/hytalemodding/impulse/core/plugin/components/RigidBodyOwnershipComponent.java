package dev.hytalemodding.impulse.core.plugin.components;

import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.EnumCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.core.ImpulsePlugin;
import javax.annotation.Nonnull;

/**
 * Ownership semantics for an ECS rigid body.
 */
public class RigidBodyOwnershipComponent implements Component<EntityStore> {

    @Nonnull
    public static final BuilderCodec<RigidBodyOwnershipComponent> CODEC = BuilderCodec.builder(
            RigidBodyOwnershipComponent.class,
            RigidBodyOwnershipComponent::new)
        .append(new KeyedCodec<>("Ownership", new EnumCodec<>(Ownership.class), false),
            (component, value) -> component.ownership = value != null
                ? value
                : Ownership.ENTITY_OWNED,
            RigidBodyOwnershipComponent::getOwnership)
        .add()
        .build();

    @Nonnull
    private Ownership ownership = Ownership.ENTITY_OWNED;

    public RigidBodyOwnershipComponent() {
    }

    public RigidBodyOwnershipComponent(@Nonnull Ownership ownership) {
        this.ownership = ownership;
    }

    @Nonnull
    public Ownership getOwnership() {
        return ownership;
    }

    public void setOwnership(@Nonnull Ownership ownership) {
        this.ownership = ownership;
    }

    public static ComponentType<EntityStore, RigidBodyOwnershipComponent> getComponentType() {
        return ImpulsePlugin.get().getRigidBodyOwnershipComponentType();
    }

    @Nonnull
    @Override
    public RigidBodyOwnershipComponent clone() {
        return new RigidBodyOwnershipComponent(ownership);
    }

    public enum Ownership {
        ENTITY_OWNED,
        DETACHED_VIEW,
        FULL_DETACHED
    }
}
