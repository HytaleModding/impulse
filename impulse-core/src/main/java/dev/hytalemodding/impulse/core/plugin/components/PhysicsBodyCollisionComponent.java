package dev.hytalemodding.impulse.core.plugin.components;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.api.PhysicsCollisionFilters;
import dev.hytalemodding.impulse.core.ImpulsePlugin;
import javax.annotation.Nonnull;
import lombok.Getter;
import lombok.Setter;

/**
 * Durable ECS collision filtering data for an entity-authored physics body.
 */
public class PhysicsBodyCollisionComponent implements Component<EntityStore> {

    @Nonnull
    public static final BuilderCodec<PhysicsBodyCollisionComponent> CODEC = BuilderCodec.builder(
            PhysicsBodyCollisionComponent.class,
            PhysicsBodyCollisionComponent::new)
        .append(new KeyedCodec<>("Sensor", Codec.BOOLEAN, false),
            (component, value) -> component.sensor = value != null && value,
            PhysicsBodyCollisionComponent::isSensor)
        .add()
        .append(new KeyedCodec<>("CollisionGroup", Codec.INTEGER, false),
            (component, value) -> component.collisionGroup = value != null
                ? value
                : PhysicsCollisionFilters.DYNAMIC_BODY,
            PhysicsBodyCollisionComponent::getCollisionGroup)
        .add()
        .append(new KeyedCodec<>("CollisionMask", Codec.INTEGER, false),
            (component, value) -> component.collisionMask = value != null
                ? value
                : PhysicsCollisionFilters.ALL,
            PhysicsBodyCollisionComponent::getCollisionMask)
        .add()
        .build();

    @Setter
    @Getter
    private boolean sensor;
    @Setter
    @Getter
    private int collisionGroup = PhysicsCollisionFilters.DYNAMIC_BODY;
    @Setter
    @Getter
    private int collisionMask = PhysicsCollisionFilters.ALL;

    public PhysicsBodyCollisionComponent() {
    }

    public PhysicsBodyCollisionComponent(boolean sensor, int collisionGroup, int collisionMask) {
        this.sensor = sensor;
        this.collisionGroup = collisionGroup;
        this.collisionMask = collisionMask;
    }

    public static ComponentType<EntityStore, PhysicsBodyCollisionComponent> getComponentType() {
        return ImpulsePlugin.get().getPhysicsBodyCollisionComponentType();
    }

    @Nonnull
    @Override
    public PhysicsBodyCollisionComponent clone() {
        return new PhysicsBodyCollisionComponent(sensor, collisionGroup, collisionMask);
    }
}
