package dev.hytalemodding.impulse.core.internal.systems.sync;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefChangeSystem;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsProjectionIndexResource;
import dev.hytalemodding.impulse.core.plugin.projection.BodyAttachmentComponent;
import dev.hytalemodding.impulse.core.plugin.projection.BodyAttachmentComponent.AttachmentLifecycle;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Keeps the body-key to entity-ref attachment index in sync with ECS component changes.
 */
public class PhysicsBodyAttachmentIndexSystem
    extends RefChangeSystem<EntityStore, BodyAttachmentComponent> {

    private static final ComponentType<EntityStore, BodyAttachmentComponent> ATTACHMENT_TYPE =
        BodyAttachmentComponent.getComponentType();
    private static final Query<EntityStore> QUERY = ATTACHMENT_TYPE;

    @Override
    public void onComponentAdded(@Nonnull Ref<EntityStore> ref,
        @Nonnull BodyAttachmentComponent component,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        registerAttachment(ref, component, commandBuffer);
    }

    @Override
    public void onComponentSet(@Nonnull Ref<EntityStore> ref,
        @Nullable BodyAttachmentComponent oldComponent,
        @Nonnull BodyAttachmentComponent newComponent,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        assert oldComponent != null;
        updateAttachment(ref, oldComponent, newComponent, commandBuffer);
    }

    @Override
    public void onComponentRemoved(@Nonnull Ref<EntityStore> ref,
        @Nonnull BodyAttachmentComponent component,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        unregisterAttachment(ref, component, commandBuffer);
    }

    private static void updateAttachment(@Nonnull Ref<EntityStore> ref,
        @Nonnull BodyAttachmentComponent oldComponent,
        @Nonnull BodyAttachmentComponent newComponent,
        @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        UUID oldUuid = oldComponent.getBodyUuid();
        UUID newUuid = newComponent.getBodyUuid();
        Ref<PhysicsStore> oldBodyRef = oldComponent.getBodyRef();
        Ref<PhysicsStore> newBodyRef = newComponent.getBodyRef();
        boolean sameUuid = oldUuid.equals(newUuid);
        boolean sameBodyRef = sameRef(oldBodyRef, newBodyRef);
        boolean oldGeneratedProxy = oldComponent.getLifecycle() == AttachmentLifecycle.GENERATED_PROXY;
        boolean newGeneratedProxy = newComponent.getLifecycle() == AttachmentLifecycle.GENERATED_PROXY;
        if (sameUuid && sameBodyRef && oldGeneratedProxy == newGeneratedProxy) {
            return;
        }
        PhysicsProjectionIndexResource resource = commandBuffer.getResource(
            PhysicsProjectionIndexResource.getResourceType());
        if (!sameUuid) {
            resource.unregisterAttachment(oldUuid, oldBodyRef, ref);
            resource.registerAttachment(newUuid, newBodyRef, ref);
        } else if (!sameBodyRef) {
            resource.updateAttachmentBodyRef(newUuid,
                oldBodyRef,
                newBodyRef,
                ref,
                newGeneratedProxy);
        }
        if (oldGeneratedProxy) {
            resource.clearGeneratedVisualProxy(oldUuid, oldBodyRef, ref);
        }
        if (newGeneratedProxy) {
            resource.setGeneratedVisualProxy(newUuid, newBodyRef, ref);
        }
    }

    private static void registerAttachment(@Nonnull Ref<EntityStore> ref,
        @Nonnull BodyAttachmentComponent component,
        @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        UUID bodyUuid = component.getBodyUuid();
        PhysicsProjectionIndexResource resource = commandBuffer.getResource(
            PhysicsProjectionIndexResource.getResourceType());
        resource.registerAttachment(bodyUuid, component.getBodyRef(), ref);
        if (component.getLifecycle() == AttachmentLifecycle.GENERATED_PROXY) {
            resource.setGeneratedVisualProxy(bodyUuid, component.getBodyRef(), ref);
        }
    }

    private static void unregisterAttachment(@Nonnull Ref<EntityStore> ref,
        @Nonnull BodyAttachmentComponent component,
        @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        UUID bodyUuid = component.getBodyUuid();
        PhysicsProjectionIndexResource resource = commandBuffer.getResource(
            PhysicsProjectionIndexResource.getResourceType());
        resource.unregisterAttachment(bodyUuid, component.getBodyRef(), ref);
        if (component.getLifecycle() == AttachmentLifecycle.GENERATED_PROXY) {
            resource.clearGeneratedVisualProxy(bodyUuid, component.getBodyRef(), ref);
        }
    }

    private static boolean sameRef(@Nullable Ref<PhysicsStore> first,
        @Nullable Ref<PhysicsStore> second) {
        return first == second
            || first != null
            && second != null
            && first.getStore() == second.getStore()
            && first.getIndex() == second.getIndex();
    }

    @Nonnull
    @Override
    public ComponentType<EntityStore, BodyAttachmentComponent> componentType() {
        return ATTACHMENT_TYPE;
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return QUERY;
    }
}
