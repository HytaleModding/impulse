package dev.hytalemodding.impulse.core.internal.systems.sync;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefChangeSystem;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsProjectionIndexResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsWorldRuntimeResource;
import dev.hytalemodding.impulse.core.plugin.components.PhysicsBodyAttachmentComponent;
import dev.hytalemodding.impulse.core.plugin.components.PhysicsBodyAttachmentComponent.AttachmentLifecycle;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsWorldResource;
import java.util.UUID;
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
            registerPhysicsStoreAttachment(ref, component, commandBuffer);
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
            updatePhysicsStoreAttachment(ref, oldComponent, newComponent, commandBuffer);
            return;
        }
        if (!oldLegacy) {
            unregisterPhysicsStoreAttachment(ref, oldComponent, commandBuffer);
        }
        PhysicsWorldRuntimeResource resource = PhysicsWorldRuntimeResource.require(
            commandBuffer.getResource(PhysicsWorldResource.getResourceType()));
        if (oldLegacy && (!newLegacy || !oldComponent.getBodyKey().equals(newComponent.getBodyKey()))) {
            resource.unregisterBodyAttachment(oldComponent.getBodyKey(), ref);
        }
        if (newLegacy && (!oldLegacy || !oldComponent.getBodyKey().equals(newComponent.getBodyKey()))) {
            resource.registerBodyAttachment(newComponent.getBodyKey(), ref);
        }
        if (!newLegacy) {
            registerPhysicsStoreAttachment(ref, newComponent, commandBuffer);
        }
        resource.clearBodySyncState(ref);
    }

    @Override
    public void onComponentRemoved(@Nonnull Ref<EntityStore> ref,
        @Nonnull PhysicsBodyAttachmentComponent component,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        if (!component.usesLegacyBodyKey()) {
            unregisterPhysicsStoreAttachment(ref, component, commandBuffer);
            return;
        }
        PhysicsWorldRuntimeResource resource = PhysicsWorldRuntimeResource.require(
            commandBuffer.getResource(PhysicsWorldResource.getResourceType()));
        resource.unregisterBodyAttachment(component.getBodyKey(), ref);
        resource.clearBodySyncState(ref);
    }

    private static void updatePhysicsStoreAttachment(@Nonnull Ref<EntityStore> ref,
        @Nonnull PhysicsBodyAttachmentComponent oldComponent,
        @Nonnull PhysicsBodyAttachmentComponent newComponent,
        @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        UUID oldUuid = oldComponent.getPhysicsBodyUuid();
        UUID newUuid = newComponent.getPhysicsBodyUuid();
        if (oldUuid == null || newUuid == null) {
            return;
        }
        boolean sameUuid = oldUuid.equals(newUuid);
        boolean oldGeneratedProxy = oldComponent.getLifecycle() == AttachmentLifecycle.GENERATED_PROXY;
        boolean newGeneratedProxy = newComponent.getLifecycle() == AttachmentLifecycle.GENERATED_PROXY;
        if (sameUuid && oldGeneratedProxy == newGeneratedProxy) {
            return;
        }
        PhysicsProjectionIndexResource resource = commandBuffer.getResource(
            PhysicsProjectionIndexResource.getResourceType());
        if (!sameUuid) {
            resource.unregisterAttachment(oldUuid, ref);
            resource.registerAttachment(newUuid, ref);
        }
        if (!sameUuid || oldGeneratedProxy != newGeneratedProxy) {
            if (oldGeneratedProxy) {
                resource.clearGeneratedVisualProxy(oldUuid, ref);
            }
            if (newGeneratedProxy) {
                resource.setGeneratedVisualProxy(newUuid, ref);
            }
        }
    }

    private static void registerPhysicsStoreAttachment(@Nonnull Ref<EntityStore> ref,
        @Nonnull PhysicsBodyAttachmentComponent component,
        @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        UUID bodyUuid = component.getPhysicsBodyUuid();
        if (bodyUuid == null) {
            return;
        }
        PhysicsProjectionIndexResource resource = commandBuffer.getResource(
            PhysicsProjectionIndexResource.getResourceType());
        resource.registerAttachment(bodyUuid, ref);
        if (component.getLifecycle() == AttachmentLifecycle.GENERATED_PROXY) {
            resource.setGeneratedVisualProxy(bodyUuid, ref);
        }
    }

    private static void unregisterPhysicsStoreAttachment(@Nonnull Ref<EntityStore> ref,
        @Nonnull PhysicsBodyAttachmentComponent component,
        @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        UUID bodyUuid = component.getPhysicsBodyUuid();
        if (bodyUuid == null) {
            return;
        }
        PhysicsProjectionIndexResource resource = commandBuffer.getResource(
            PhysicsProjectionIndexResource.getResourceType());
        resource.unregisterAttachment(bodyUuid, ref);
        if (component.getLifecycle() == AttachmentLifecycle.GENERATED_PROXY) {
            resource.clearGeneratedVisualProxy(bodyUuid, ref);
        }
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
