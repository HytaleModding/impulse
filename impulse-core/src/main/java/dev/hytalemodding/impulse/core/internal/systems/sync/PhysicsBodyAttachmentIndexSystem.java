package dev.hytalemodding.impulse.core.internal.systems.sync;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefChangeSystem;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsWorldRuntimeResource;
import dev.hytalemodding.impulse.core.plugin.components.PhysicsBodyAttachmentComponent;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsWorldResource;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Keeps the body-key to entity-ref attachment index in sync with ECS component changes.
 */
public class PhysicsBodyAttachmentIndexSystem
    extends RefChangeSystem<EntityStore, PhysicsBodyAttachmentComponent> {

    private static final ComponentType<EntityStore, PhysicsBodyAttachmentComponent> ATTACHMENT_TYPE =
        PhysicsBodyAttachmentComponent.getComponentType();
    private static final Query<EntityStore> QUERY = ATTACHMENT_TYPE;

    @Override
    public void onComponentAdded(@Nonnull Ref<EntityStore> ref,
        @Nonnull PhysicsBodyAttachmentComponent component,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        if (!component.usesLegacyBodyKey()) {
            return;
        }
        PhysicsWorldRuntimeResource.require(
                commandBuffer.getResource(PhysicsWorldResource.getResourceType()))
            .registerBodyAttachment(component.getBodyKey(), ref);
    }

    @Override
    public void onComponentSet(@Nonnull Ref<EntityStore> ref,
        @Nullable PhysicsBodyAttachmentComponent oldComponent,
        @Nonnull PhysicsBodyAttachmentComponent newComponent,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        assert oldComponent != null;
        boolean oldLegacy = oldComponent.usesLegacyBodyKey();
        boolean newLegacy = newComponent.usesLegacyBodyKey();
        if (!oldLegacy && !newLegacy) {
            return;
        }
        PhysicsWorldRuntimeResource resource = PhysicsWorldRuntimeResource.require(
            commandBuffer.getResource(PhysicsWorldResource.getResourceType()));
        if (oldLegacy && (!newLegacy || !oldComponent.getBodyKey().equals(newComponent.getBodyKey()))) {
            resource.unregisterBodyAttachment(oldComponent.getBodyKey(), ref);
        }
        if (newLegacy && (!oldLegacy || !oldComponent.getBodyKey().equals(newComponent.getBodyKey()))) {
            resource.registerBodyAttachment(newComponent.getBodyKey(), ref);
        }
        resource.clearBodySyncState(ref);
    }

    @Override
    public void onComponentRemoved(@Nonnull Ref<EntityStore> ref,
        @Nonnull PhysicsBodyAttachmentComponent component,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        if (!component.usesLegacyBodyKey()) {
            return;
        }
        PhysicsWorldRuntimeResource resource = PhysicsWorldRuntimeResource.require(
            commandBuffer.getResource(PhysicsWorldResource.getResourceType()));
        resource.unregisterBodyAttachment(component.getBodyKey(), ref);
        resource.clearBodySyncState(ref);
    }

    @Nonnull
    @Override
    public ComponentType<EntityStore, PhysicsBodyAttachmentComponent> componentType() {
        return ATTACHMENT_TYPE;
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return QUERY;
    }
}
