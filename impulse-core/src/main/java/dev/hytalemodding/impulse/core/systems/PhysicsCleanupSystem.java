package dev.hytalemodding.impulse.core.systems;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.api.PhysicsBody;
import dev.hytalemodding.impulse.core.components.PhysicsBodyComponent;
import dev.hytalemodding.impulse.core.resources.PhysicsWorldResource;
import javax.annotation.Nonnull;

/**
 * Removes entity-owned physics bodies from the registry and backend space when entities despawn.
 */
public class PhysicsCleanupSystem extends RefSystem<EntityStore> {

    @Override
    public void onEntityAdded(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull AddReason reason,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {}

    @Override
    public void onEntityRemove(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull RemoveReason reason,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        PhysicsBodyComponent component = store.getComponent(ref,
            PhysicsBodyComponent.getComponentType());
        if (component == null) {
            return;
        }

        PhysicsBody body = component.getBody();
        PhysicsWorldResource resource = store.getResource(PhysicsWorldResource.getResourceType());
        PhysicsWorldResource.BodyRegistration registration = resource.getBodyRegistration(body);
        if (registration != null
            && (registration.ownerKind() != PhysicsWorldResource.BodyOwnerKind.ENTITY
            || !registration.removeWithOwner())) {
            return;
        }

        resource.unregisterBody(body, true);
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return PhysicsBodyComponent.getComponentType();
    }
}
