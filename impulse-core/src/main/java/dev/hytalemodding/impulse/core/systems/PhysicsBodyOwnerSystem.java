package dev.hytalemodding.impulse.core.systems;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.core.components.PhysicsBodyComponent;
import dev.hytalemodding.impulse.core.resources.PhysicsWorldResource;
import javax.annotation.Nonnull;

public class PhysicsBodyOwnerSystem extends RefSystem<EntityStore> {

    @Override
    public void onEntityAdded(@Nonnull Ref<EntityStore> ref,
        @Nonnull AddReason reason,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        PhysicsBodyComponent component = commandBuffer.getComponent(ref,
            PhysicsBodyComponent.getComponentType());
        if (component == null) {
            return;
        }

        commandBuffer.getResource(PhysicsWorldResource.getResourceType())
            .registerBodyOwner(component.getBody(), ref);
    }

    @Override
    public void onEntityRemove(@Nonnull Ref<EntityStore> ref,
        @Nonnull RemoveReason reason,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        PhysicsBodyComponent component = store.getComponent(ref,
            PhysicsBodyComponent.getComponentType());
        if (component == null) {
            return;
        }

        store.getResource(PhysicsWorldResource.getResourceType())
            .unregisterBodyOwner(component.getBody(), ref);
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return PhysicsBodyComponent.getComponentType();
    }
}
