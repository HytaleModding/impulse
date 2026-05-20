package dev.hytalemodding.impulse.core.systems;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.core.components.PhysicsBodyAttachmentComponent;
import dev.hytalemodding.impulse.core.resources.PhysicsWorldResource;
import javax.annotation.Nonnull;

/**
 * Tracks entity attachments to body ids without making those entities body owners.
 */
public class PhysicsBodyAttachmentSystem extends RefSystem<EntityStore> {

    @Override
    public void onEntityAdded(@Nonnull Ref<EntityStore> ref,
        @Nonnull AddReason reason,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        PhysicsBodyAttachmentComponent component = commandBuffer.getComponent(ref,
            PhysicsBodyAttachmentComponent.getComponentType());
        if (component == null) {
            return;
        }

        commandBuffer.getResource(PhysicsWorldResource.getResourceType())
            .registerBodyAttachment(component.getBodyId(), ref);
    }

    @Override
    public void onEntityRemove(@Nonnull Ref<EntityStore> ref,
        @Nonnull RemoveReason reason,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        PhysicsBodyAttachmentComponent component = store.getComponent(ref,
            PhysicsBodyAttachmentComponent.getComponentType());
        if (component == null) {
            return;
        }

        PhysicsWorldResource resource = store.getResource(PhysicsWorldResource.getResourceType());
        resource.unregisterBodyAttachment(component.getBodyId(), ref);
        resource.clearBodySyncState(ref);
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return PhysicsBodyAttachmentComponent.getComponentType();
    }
}
