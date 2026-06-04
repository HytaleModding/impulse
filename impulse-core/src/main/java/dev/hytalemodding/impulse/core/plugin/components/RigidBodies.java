package dev.hytalemodding.impulse.core.plugin.components;

import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.api.PhysicsAxis;
import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.PhysicsCollisionFilters;
import dev.hytalemodding.impulse.api.ShapeType;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyPersistenceMode;
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import javax.annotation.Nonnull;

/**
 * Stateless helpers for composing rigid-body ECS components.
 */
public final class RigidBodies {

    private RigidBodies() {
    }

    @Nonnull
    public static RigidBodyKey addEntityOwnedBox(@Nonnull Holder<EntityStore> holder,
        @Nonnull SpaceId spaceId,
        float halfX,
        float halfY,
        float halfZ,
        float mass) {
        RigidBodyKey bodyKey = RigidBodyKey.random();
        addEntityOwnedBox(holder, bodyKey, spaceId, halfX, halfY, halfZ, mass);
        return bodyKey;
    }

    public static void addEntityOwnedBox(@Nonnull Holder<EntityStore> holder,
        @Nonnull RigidBodyKey bodyKey,
        @Nonnull SpaceId spaceId,
        float halfX,
        float halfY,
        float halfZ,
        float mass) {
        holder.addComponent(RigidBodyComponent.getComponentType(),
            new RigidBodyComponent(bodyKey,
                spaceId,
                ShapeType.BOX,
                halfX,
                halfY,
                halfZ,
                0.0f,
                0.0f,
                PhysicsAxis.Y,
                0.0f,
                PhysicsBodyType.DYNAMIC,
                mass,
                0.5f,
                0.0f,
                0.0f,
                0.0f,
                false,
                PhysicsCollisionFilters.DYNAMIC_BODY,
                PhysicsCollisionFilters.ALL,
                PhysicsBodyPersistenceMode.RUNTIME_ONLY,
                RigidBodyComponent.Ownership.ENTITY_OWNED));
    }

    public static void putDetachedView(@Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull RigidBodyKey bodyKey,
        @Nonnull SpaceId spaceId) {
        RigidBodyComponent body = new RigidBodyComponent();
        body.setBodyKey(bodyKey);
        body.setSpaceId(spaceId);
        body.setOwnership(RigidBodyComponent.Ownership.DETACHED_VIEW);
        store.putComponent(ref, RigidBodyComponent.getComponentType(), body);
    }
}
