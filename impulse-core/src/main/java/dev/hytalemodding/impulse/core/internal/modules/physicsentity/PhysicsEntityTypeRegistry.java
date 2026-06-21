package dev.hytalemodding.impulse.core.internal.modules.physicsentity;

import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.event.WorldEventType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsBodySyncStateResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsDebugResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsProjectionIndexResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsVisualInterestResource;
import dev.hytalemodding.impulse.core.internal.resources.profiling.PhysicsRuntimeProfilingResource;
import dev.hytalemodding.impulse.core.internal.systems.debug.PhysicsDebugSystem;
import dev.hytalemodding.impulse.core.internal.systems.publication.PhysicsStoreEventPublicationSystem;
import dev.hytalemodding.impulse.core.internal.modules.physicsentity.systems.sync.PhysicsBodyAttachmentIndexSystem;
import dev.hytalemodding.impulse.core.internal.modules.physicsentity.systems.sync.PhysicsSyncSystem;
import dev.hytalemodding.impulse.core.internal.systems.visual.PhysicsProjectionCleanupSystem;
import dev.hytalemodding.impulse.core.plugin.events.PhysicsEventFramePublishedEvent;
import dev.hytalemodding.impulse.core.plugin.modules.physicsentity.components.BodyAttachmentComponent;
import dev.hytalemodding.impulse.core.plugin.modules.physicsentity.components.GeneratedVisualProxyComponent;
import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Registered EntityStore type handles owned by the PhysicsEntity subplugin.
 */
public final class PhysicsEntityTypeRegistry {

    @Nullable
    private static ComponentType<EntityStore, BodyAttachmentComponent> bodyAttachmentComponentType;
    @Nullable
    private static ComponentType<EntityStore, GeneratedVisualProxyComponent>
        generatedVisualProxyComponentType;
    @Nullable
    private static WorldEventType<EntityStore, PhysicsEventFramePublishedEvent>
        physicsEventFramePublishedEventType;
    @Nullable
    private static SystemGroup<EntityStore> persistenceRestoreGroup;

    private PhysicsEntityTypeRegistry() {
    }

    public static void registerComponentTypes(@Nonnull ComponentRegistryProxy<EntityStore> registry) {
        bodyAttachmentComponentType = registry.registerComponent(
            BodyAttachmentComponent.class,
            "BodyAttachment",
            BodyAttachmentComponent.CODEC);
        generatedVisualProxyComponentType = registry.registerComponent(
            GeneratedVisualProxyComponent.class,
            "GeneratedVisualProxy",
            GeneratedVisualProxyComponent.CODEC);
    }

    public static void registerResourceTypes(@Nonnull ComponentRegistryProxy<EntityStore> registry) {
        PhysicsDebugResource.setResourceType(registry.registerResource(PhysicsDebugResource.class,
            PhysicsDebugResource::new));
        PhysicsRuntimeProfilingResource.setResourceType(registry.registerResource(
            PhysicsRuntimeProfilingResource.class,
            PhysicsRuntimeProfilingResource::new));
        PhysicsProjectionIndexResource.setResourceType(registry.registerResource(
            PhysicsProjectionIndexResource.class,
            PhysicsProjectionIndexResource::new));
        PhysicsBodySyncStateResource.setResourceType(registry.registerResource(
            PhysicsBodySyncStateResource.class,
            PhysicsBodySyncStateResource::new));
        PhysicsVisualInterestResource.setResourceType(registry.registerResource(
            PhysicsVisualInterestResource.class,
            PhysicsVisualInterestResource::new));
    }

    public static void registerEventTypes(@Nonnull ComponentRegistryProxy<EntityStore> registry) {
        physicsEventFramePublishedEventType =
            registry.registerWorldEventType(PhysicsEventFramePublishedEvent.class);
    }

    public static void registerSystemGroups(@Nonnull ComponentRegistryProxy<EntityStore> registry) {
        persistenceRestoreGroup = registry.registerSystemGroup();
    }

    public static void registerSystems(@Nonnull ComponentRegistryProxy<EntityStore> registry) {
        registry.registerSystem(new PhysicsBodyAttachmentIndexSystem());
        registry.registerSystem(new PhysicsProjectionCleanupSystem());
        registry.registerSystem(new PhysicsSyncSystem());
        registry.registerSystem(new PhysicsDebugSystem());
        registry.registerSystem(new PhysicsStoreEventPublicationSystem());
    }

    public static void clearEntityStoreTypes() {
        bodyAttachmentComponentType = null;
        generatedVisualProxyComponentType = null;
        physicsEventFramePublishedEventType = null;
        persistenceRestoreGroup = null;
        PhysicsDebugResource.clearResourceType();
        PhysicsRuntimeProfilingResource.clearResourceType();
        PhysicsProjectionIndexResource.clearResourceType();
        PhysicsBodySyncStateResource.clearResourceType();
        PhysicsVisualInterestResource.clearResourceType();
    }

    public static boolean areEntityStoreTypesRegistered() {
        return bodyAttachmentComponentType != null
            && generatedVisualProxyComponentType != null
            && physicsEventFramePublishedEventType != null
            && persistenceRestoreGroup != null
            && PhysicsDebugResource.getResourceType() != null
            && PhysicsRuntimeProfilingResource.getResourceType() != null
            && PhysicsProjectionIndexResource.getResourceType() != null
            && PhysicsBodySyncStateResource.getResourceType() != null
            && PhysicsVisualInterestResource.getResourceType() != null;
    }

    public static boolean isBodyAttachmentComponentTypeRegistered() {
        return bodyAttachmentComponentType != null;
    }

    public static boolean isGeneratedVisualProxyComponentTypeRegistered() {
        return generatedVisualProxyComponentType != null;
    }

    @Nonnull
    public static ComponentType<EntityStore, BodyAttachmentComponent> bodyAttachmentComponentType() {
        return requireRegistered(bodyAttachmentComponentType,
            "Impulse BodyAttachment component type is not registered");
    }

    @Nonnull
    public static ComponentType<EntityStore, GeneratedVisualProxyComponent>
    generatedVisualProxyComponentType() {
        return requireRegistered(generatedVisualProxyComponentType,
            "Impulse GeneratedVisualProxy component type is not registered");
    }

    @Nonnull
    public static WorldEventType<EntityStore, PhysicsEventFramePublishedEvent>
    physicsEventFramePublishedEventType() {
        return requireRegistered(physicsEventFramePublishedEventType,
            "Impulse physics event-frame world event type is not registered");
    }

    @Nonnull
    public static SystemGroup<EntityStore> persistenceRestoreGroup() {
        return requireRegistered(persistenceRestoreGroup,
            "Impulse PhysicsEntity persistence restore group is not registered");
    }

    @Nonnull
    private static <T> T requireRegistered(@Nullable T value, @Nonnull String message) {
        if (value == null) {
            throw new IllegalStateException(Objects.requireNonNull(message, "message"));
        }
        return value;
    }
}
