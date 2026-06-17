package dev.hytalemodding.impulse.core.plugin.modules.physicsentity;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.event.WorldEventType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.core.internal.modules.physicsentity.PhysicsEntityTypeRegistry;
import dev.hytalemodding.impulse.core.plugin.events.PhysicsEventFramePublishedEvent;
import dev.hytalemodding.impulse.core.plugin.modules.physicsentity.components.BodyAttachmentComponent;
import dev.hytalemodding.impulse.core.plugin.modules.physicsentity.components.GeneratedVisualProxyComponent;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsWorldResource;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Public EntityStore type handles for the PhysicsEntity subplugin.
 */
public final class PhysicsEntityTypes {

    @Nullable
    private static ComponentType<EntityStore, BodyAttachmentComponent> bodyAttachmentComponentType;

    private PhysicsEntityTypes() {
    }

    public static boolean areEntityStoreTypesRegistered() {
        return PhysicsEntityTypeRegistry.areEntityStoreTypesRegistered();
    }

    public static boolean isBodyAttachmentComponentTypeRegistered() {
        return bodyAttachmentComponentType != null
            || PhysicsEntityTypeRegistry.isBodyAttachmentComponentTypeRegistered();
    }

    public static boolean isGeneratedVisualProxyComponentTypeRegistered() {
        return PhysicsEntityTypeRegistry.isGeneratedVisualProxyComponentTypeRegistered();
    }

    @Nonnull
    public static ComponentType<EntityStore, BodyAttachmentComponent> bodyAttachmentComponentType() {
        ComponentType<EntityStore, BodyAttachmentComponent> compatibilityType =
            bodyAttachmentComponentType;
        if (compatibilityType != null) {
            return compatibilityType;
        }
        return PhysicsEntityTypeRegistry.bodyAttachmentComponentType();
    }

    @Nonnull
    public static ComponentType<EntityStore, GeneratedVisualProxyComponent>
    generatedVisualProxyComponentType() {
        return PhysicsEntityTypeRegistry.generatedVisualProxyComponentType();
    }

    @Nonnull
    public static ResourceType<EntityStore, PhysicsWorldResource> physicsWorldResourceType() {
        return PhysicsEntityTypeRegistry.physicsWorldResourceType();
    }

    @Nonnull
    public static WorldEventType<EntityStore, PhysicsEventFramePublishedEvent>
    physicsEventFramePublishedEventType() {
        return PhysicsEntityTypeRegistry.physicsEventFramePublishedEventType();
    }

    @Nonnull
    public static SystemGroup<EntityStore> persistenceRestoreGroup() {
        return PhysicsEntityTypeRegistry.persistenceRestoreGroup();
    }
}
