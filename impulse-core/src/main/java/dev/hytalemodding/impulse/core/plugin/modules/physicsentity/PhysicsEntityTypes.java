package dev.hytalemodding.impulse.core.plugin.modules.physicsentity;

import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.event.WorldEventType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsDebugResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsProjectionIndexResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsWorldRuntimeResource;
import dev.hytalemodding.impulse.core.internal.resources.profiling.PhysicsRuntimeProfilingResource;
import dev.hytalemodding.impulse.core.internal.systems.PhysicsWorldResourceAttachmentSystem;
import dev.hytalemodding.impulse.core.internal.systems.debug.PhysicsDebugSystem;
import dev.hytalemodding.impulse.core.internal.systems.publication.PhysicsStoreEventPublicationSystem;
import dev.hytalemodding.impulse.core.internal.systems.sync.PhysicsBodyAttachmentIndexSystem;
import dev.hytalemodding.impulse.core.internal.systems.sync.PhysicsSyncSystem;
import dev.hytalemodding.impulse.core.internal.systems.visual.PhysicsGeneratedProxyCleanupSystem;
import dev.hytalemodding.impulse.core.plugin.events.PhysicsEventFramePublishedEvent;
import dev.hytalemodding.impulse.core.plugin.modules.physicsentity.components.BodyAttachmentComponent;
import dev.hytalemodding.impulse.core.plugin.modules.physicsentity.components.GeneratedVisualProxyComponent;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsWorldResource;
import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Registered EntityStore type handles for the PhysicsEntity integration module.
 */
public final class PhysicsEntityTypes {

    @Nullable
    private static ComponentType<EntityStore, BodyAttachmentComponent> bodyAttachmentComponentType;
    @Nullable
    private static ComponentType<EntityStore, GeneratedVisualProxyComponent>
        generatedVisualProxyComponentType;
    @Nullable
    private static ResourceType<EntityStore, PhysicsWorldResource> physicsWorldResourceType;
    @Nullable
    private static WorldEventType<EntityStore, PhysicsEventFramePublishedEvent>
        physicsEventFramePublishedEventType;
    @Nullable
    private static SystemGroup<EntityStore> persistenceRestoreGroup;

    private PhysicsEntityTypes() {
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
        physicsWorldResourceType = registry.registerResource(PhysicsWorldResource.class,
            PhysicsWorldRuntimeResource::new);
        PhysicsDebugResource.setResourceType(registry.registerResource(PhysicsDebugResource.class,
            PhysicsDebugResource::new));
        PhysicsRuntimeProfilingResource.setResourceType(registry.registerResource(
            PhysicsRuntimeProfilingResource.class,
            PhysicsRuntimeProfilingResource::new));
        PhysicsProjectionIndexResource.setResourceType(registry.registerResource(
            PhysicsProjectionIndexResource.class,
            PhysicsProjectionIndexResource::new));
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
        registry.registerSystem(new PhysicsGeneratedProxyCleanupSystem());
        registry.registerSystem(new PhysicsSyncSystem());
        registry.registerSystem(new PhysicsDebugSystem());
        registry.registerSystem(new PhysicsStoreEventPublicationSystem());
        registry.registerSystem(new PhysicsWorldResourceAttachmentSystem());
    }

    public static void clearEntityStoreTypes() {
        bodyAttachmentComponentType = null;
        generatedVisualProxyComponentType = null;
        physicsWorldResourceType = null;
        physicsEventFramePublishedEventType = null;
        persistenceRestoreGroup = null;
        PhysicsDebugResource.clearResourceType();
        PhysicsRuntimeProfilingResource.clearResourceType();
        PhysicsProjectionIndexResource.clearResourceType();
    }

    public static boolean areEntityStoreTypesRegistered() {
        return bodyAttachmentComponentType != null
            && generatedVisualProxyComponentType != null
            && physicsWorldResourceType != null
            && physicsEventFramePublishedEventType != null
            && persistenceRestoreGroup != null
            && PhysicsDebugResource.getResourceType() != null
            && PhysicsRuntimeProfilingResource.getResourceType() != null
            && PhysicsProjectionIndexResource.getResourceType() != null;
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
    public static ResourceType<EntityStore, PhysicsWorldResource> physicsWorldResourceType() {
        return requireRegistered(physicsWorldResourceType,
            "Impulse PhysicsWorld resource type is not registered");
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
