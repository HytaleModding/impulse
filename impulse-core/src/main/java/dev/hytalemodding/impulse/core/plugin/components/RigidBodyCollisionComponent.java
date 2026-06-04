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

/**
 * Collision filtering intent for an ECS rigid body.
 */
public class RigidBodyCollisionComponent implements Component<EntityStore> {

    @Nonnull
    public static final BuilderCodec<RigidBodyCollisionComponent> CODEC = BuilderCodec.builder(
            RigidBodyCollisionComponent.class,
            RigidBodyCollisionComponent::new)
        .append(new KeyedCodec<>("Sensor", Codec.BOOLEAN, false),
            (component, value) -> component.sensor = value != null && value,
            RigidBodyCollisionComponent::isSensor)
        .add()
        .append(new KeyedCodec<>("CollisionGroup", Codec.INTEGER, false),
            (component, value) -> component.collisionGroup = value != null
                ? value
                : PhysicsCollisionFilters.DYNAMIC_BODY,
            RigidBodyCollisionComponent::getCollisionGroup)
        .add()
        .append(new KeyedCodec<>("CollisionMask", Codec.INTEGER, false),
            (component, value) -> component.collisionMask = value != null
                ? value
                : PhysicsCollisionFilters.ALL,
            RigidBodyCollisionComponent::getCollisionMask)
        .add()
        .build();

    private boolean sensor;
    private int collisionGroup = PhysicsCollisionFilters.DYNAMIC_BODY;
    private int collisionMask = PhysicsCollisionFilters.ALL;

    public RigidBodyCollisionComponent() {
    }

    public RigidBodyCollisionComponent(boolean sensor,
        int collisionGroup,
        int collisionMask) {
        this.sensor = sensor;
        this.collisionGroup = collisionGroup;
        this.collisionMask = collisionMask;
    }

    public boolean isSensor() {
        return sensor;
    }

    public void setSensor(boolean sensor) {
        this.sensor = sensor;
    }

    public int getCollisionGroup() {
        return collisionGroup;
    }

    public void setCollisionGroup(int collisionGroup) {
        this.collisionGroup = collisionGroup;
    }

    public int getCollisionMask() {
        return collisionMask;
    }

    public void setCollisionMask(int collisionMask) {
        this.collisionMask = collisionMask;
    }

    public static ComponentType<EntityStore, RigidBodyCollisionComponent> getComponentType() {
        return ImpulsePlugin.get().getRigidBodyCollisionComponentType();
    }

    @Nonnull
    @Override
    public RigidBodyCollisionComponent clone() {
        return new RigidBodyCollisionComponent(sensor, collisionGroup, collisionMask);
    }
}
