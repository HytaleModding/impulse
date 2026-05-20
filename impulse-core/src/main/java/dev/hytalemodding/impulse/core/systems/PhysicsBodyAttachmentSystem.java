package dev.hytalemodding.impulse.core.systems;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefChangeSystem;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.core.components.PhysicsBodyAttachmentComponent;
import dev.hytalemodding.impulse.core.resources.PhysicsWorldResource;
import javax.annotation.Nonnull;

/**
 * Tracks entity attachments to body ids without making those entities body owners.
 */
public class PhysicsBodyAttachmentSystem
    extends RefChangeSystem<EntityStore, PhysicsBodyAttachmentComponent> {

    @Override
    public ComponentType<EntityStore, PhysicsBodyAttachmentComponent> componentType() {
        return PhysicsBodyAttachmentComponent.getComponentType();
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return componentType();
    }

    @Override
    public void onComponentAdded(@Nonnull Ref<EntityStore> ref,
        @Nonnull PhysicsBodyAttachmentComponent component,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        commandBuffer.getResource(PhysicsWorldResource.getResourceType())
            .registerBodyAttachment(component.getBodyId(), ref);
    }

    @Override
    public void onComponentSet(@Nonnull Ref<EntityStore> ref,
        @Nonnull PhysicsBodyAttachmentComponent oldComponent,
        @Nonnull PhysicsBodyAttachmentComponent newComponent,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        PhysicsWorldResource resource = commandBuffer.getResource(PhysicsWorldResource.getResourceType());
        if (!oldComponent.getBodyId().equals(newComponent.getBodyId())) {
            resource.unregisterBodyAttachment(oldComponent.getBodyId(), ref);
            resource.registerBodyAttachment(newComponent.getBodyId(), ref);
        }
        resource.clearBodySyncState(ref);
    }

    @Override
    public void onComponentRemoved(@Nonnull Ref<EntityStore> ref,
        @Nonnull PhysicsBodyAttachmentComponent component,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        PhysicsWorldResource resource = commandBuffer.getResource(PhysicsWorldResource.getResourceType());
        resource.unregisterBodyAttachment(component.getBodyId(), ref);
        resource.clearBodySyncState(ref);
    }
}
