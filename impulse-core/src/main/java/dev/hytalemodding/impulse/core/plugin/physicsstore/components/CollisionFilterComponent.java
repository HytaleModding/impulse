package dev.hytalemodding.impulse.core.plugin.physicsstore.components;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.api.PhysicsCollisionFilters;
import dev.hytalemodding.impulse.core.plugin.physicsstore.PhysicsStoreTypes;
import javax.annotation.Nonnull;

/**
 * Collision group/mask row referenced by colliders.
 */
public final class CollisionFilterComponent implements Component<PhysicsStore> {

    @Nonnull
    public static final BuilderCodec<CollisionFilterComponent> CODEC = BuilderCodec.builder(
            CollisionFilterComponent.class,
            CollisionFilterComponent::new)
        .append(new KeyedCodec<>("CollisionGroup", Codec.INTEGER, false),
            (component, value) -> component.collisionGroup = value != null
                ? value
                : PhysicsCollisionFilters.DYNAMIC_BODY,
            CollisionFilterComponent::getCollisionGroup)
        .add()
        .append(new KeyedCodec<>("CollisionMask", Codec.INTEGER, false),
            (component, value) -> component.collisionMask = value != null
                ? value
                : PhysicsCollisionFilters.ALL,
            CollisionFilterComponent::getCollisionMask)
        .add()
        .build();

    private int collisionGroup = PhysicsCollisionFilters.DYNAMIC_BODY;
    private int collisionMask = PhysicsCollisionFilters.ALL;

    public CollisionFilterComponent() {
    }

    public CollisionFilterComponent(int collisionGroup, int collisionMask) {
        this.collisionGroup = collisionGroup;
        this.collisionMask = collisionMask;
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

    @Nonnull
    public static ComponentType<PhysicsStore, CollisionFilterComponent> getComponentType() {
        return PhysicsStoreTypes.collisionFilterComponentType();
    }

    @Nonnull
    @Override
    public CollisionFilterComponent clone() {
        return new CollisionFilterComponent(collisionGroup, collisionMask);
    }
}
