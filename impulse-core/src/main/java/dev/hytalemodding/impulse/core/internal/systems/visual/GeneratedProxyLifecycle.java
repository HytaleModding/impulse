package dev.hytalemodding.impulse.core.internal.systems.visual;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsWorldRuntimeResource;
import dev.hytalemodding.impulse.core.plugin.modules.physicsentity.components.BodyAttachmentComponent;
import dev.hytalemodding.impulse.core.plugin.modules.physicsentity.components.BodyAttachmentComponent.AttachmentLifecycle;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Shared cleanup policy for generated visual proxies and missing body attachments.
 */
public final class GeneratedProxyLifecycle {

    private GeneratedProxyLifecycle() {
    }

    static void removeProxy(@Nonnull ComponentAccessor<EntityStore> accessor,
        @Nonnull PhysicsWorldRuntimeResource resource,
        @Nonnull UUID bodyUuid,
        @Nullable Ref<PhysicsStore> bodyRef,
        @Nullable Ref<EntityStore> proxy) {
        if (proxy == null) {
            resource.clearGeneratedVisualProxy(bodyUuid, bodyRef);
        } else {
            resource.clearGeneratedVisualProxy(bodyUuid, bodyRef, proxy);
        }
        removeEntity(accessor, proxy);
    }

    public static void clearMissingAttachment(@Nonnull Ref<EntityStore> entityRef,
        @Nonnull BodyAttachmentComponent attachment,
        @Nonnull PhysicsWorldRuntimeResource resource,
        @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        UUID bodyUuid = attachment.getBodyUuid();
        resource.unregisterBodyAttachment(bodyUuid, attachment.getBodyRef(), entityRef);
        resource.clearBodySyncState(entityRef);
        if (attachment.getLifecycle() == AttachmentLifecycle.GENERATED_PROXY) {
            resource.clearGeneratedVisualProxy(bodyUuid, attachment.getBodyRef(), entityRef);
            removeEntity(commandBuffer, entityRef);
        } else if (attachment.shouldRemoveEntityWhenBodyMissing()) {
            removeEntity(commandBuffer, entityRef);
        } else {
            commandBuffer.removeComponent(entityRef, BodyAttachmentComponent.getComponentType());
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
