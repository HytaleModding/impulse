package dev.hytalemodding.impulse.core.internal.systems.visual;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.core.plugin.physicsstore.projection.BodyAttachmentComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.projection.BodyAttachmentComponent.AttachmentLifecycle;
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsWorldRuntimeResource;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Shared cleanup policy for generated visual proxies and missing body attachments.
 */
public final class GeneratedProxyLifecycle {

    private static final ComponentType<EntityStore, BodyAttachmentComponent> ATTACHMENT_TYPE =
        BodyAttachmentComponent.getComponentType();

    private GeneratedProxyLifecycle() {
    }

    static void removeProxy(@Nonnull ComponentAccessor<EntityStore> accessor,
        @Nonnull PhysicsWorldRuntimeResource resource,
        @Nonnull RigidBodyKey bodyKey) {
        Ref<EntityStore> proxy = resource.getGeneratedVisualProxy(bodyKey);
        removeProxy(accessor, resource, bodyKey, proxy);
    }

    static void removeProxy(@Nonnull ComponentAccessor<EntityStore> accessor,
        @Nonnull PhysicsWorldRuntimeResource resource,
        @Nonnull RigidBodyKey bodyKey,
        @Nullable Ref<EntityStore> proxy) {
        if (proxy == null) {
            resource.clearGeneratedVisualProxy(bodyKey);
        } else {
            resource.clearGeneratedVisualProxy(bodyKey, proxy);
        }
        removeEntity(accessor, proxy);
    }

    public static void clearMissingAttachment(@Nonnull Ref<EntityStore> entityRef,
        @Nonnull BodyAttachmentComponent attachment,
        @Nonnull PhysicsWorldRuntimeResource resource,
        @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        RigidBodyKey bodyKey = RigidBodyKey.of(attachment.getBodyUuid());
        resource.unregisterBodyAttachment(bodyKey, entityRef);
        resource.clearBodySyncState(entityRef);
        if (attachment.getLifecycle() == AttachmentLifecycle.GENERATED_PROXY) {
            removeProxy(commandBuffer, resource, bodyKey, entityRef);
        } else if (attachment.shouldRemoveEntityWhenBodyMissing()) {
            removeEntity(commandBuffer, entityRef);
        } else {
            commandBuffer.removeComponent(entityRef, ATTACHMENT_TYPE);
        }
    }

    private static void removeEntity(@Nonnull ComponentAccessor<EntityStore> accessor,
        @Nullable Ref<EntityStore> entityRef) {
        if (entityRef != null && entityRef.isValid()) {
            accessor.removeEntity(entityRef, newHolder(accessor), RemoveReason.REMOVE);
        }
    }

    @Nonnull
    private static Holder<EntityStore> newHolder(@Nonnull ComponentAccessor<EntityStore> accessor) {
        if (accessor instanceof Store<?> store) {
            return newHolder(store);
        }
        if (accessor instanceof CommandBuffer<?> commandBuffer) {
            return newHolder(commandBuffer.getStore());
        }
        throw new IllegalArgumentException("Unsupported component accessor type: "
            + accessor.getClass().getName());
    }

    @Nonnull
    @SuppressWarnings("unchecked")
    private static Holder<EntityStore> newHolder(@Nonnull Store<?> store) {
        return ((Store<EntityStore>) store).getRegistry().newHolder();
    }
}
