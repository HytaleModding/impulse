package dev.hytalemodding.impulse.core.plugin.components;

import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.api.PhysicsBodyType;
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
        holder.addComponent(RigidBodyKeyComponent.getComponentType(), new RigidBodyKeyComponent(bodyKey));
        holder.addComponent(RigidBodySpaceComponent.getComponentType(), new RigidBodySpaceComponent(spaceId));
        holder.addComponent(RigidBodyShapeComponent.getComponentType(),
            new RigidBodyShapeComponent(ShapeType.BOX, halfX, halfY, halfZ, 0.0f, 0.0f,
                dev.hytalemodding.impulse.api.PhysicsAxis.Y, 0.0f));
        holder.addComponent(RigidBodyTypeComponent.getComponentType(),
            new RigidBodyTypeComponent(PhysicsBodyType.DYNAMIC));
        holder.addComponent(RigidBodyMassComponent.getComponentType(), new RigidBodyMassComponent(mass));
        holder.addComponent(RigidBodyOwnershipComponent.getComponentType(), new RigidBodyOwnershipComponent(
            RigidBodyOwnershipComponent.Ownership.ENTITY_OWNED));
        holder.addComponent(RigidBodyPersistenceComponent.getComponentType(),
            new RigidBodyPersistenceComponent(PhysicsBodyPersistenceMode.RUNTIME_ONLY));
    }

    public static void putDetachedView(@Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull RigidBodyKey bodyKey,
        @Nonnull SpaceId spaceId) {
        store.putComponent(ref, RigidBodyKeyComponent.getComponentType(),
            new RigidBodyKeyComponent(bodyKey));
        store.putComponent(ref, RigidBodySpaceComponent.getComponentType(),
            new RigidBodySpaceComponent(spaceId));
        store.putComponent(ref, RigidBodyOwnershipComponent.getComponentType(),
            new RigidBodyOwnershipComponent(RigidBodyOwnershipComponent.Ownership.DETACHED_VIEW));
    }
}
