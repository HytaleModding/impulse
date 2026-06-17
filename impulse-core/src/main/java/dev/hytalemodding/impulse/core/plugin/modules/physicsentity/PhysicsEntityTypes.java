package dev.hytalemodding.impulse.core.plugin.modules.physicsentity;

import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.event.WorldEventType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.core.internal.components.GeneratedVisualProxyComponent;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsDebugResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsProjectionIndexResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsWorldRuntimeResource;
import dev.hytalemodding.impulse.core.internal.resources.profiling.PhysicsRuntimeProfilingResource;
import dev.hytalemodding.impulse.core.plugin.events.PhysicsEventFramePublishedEvent;
import dev.hytalemodding.impulse.core.plugin.projection.BodyAttachmentComponent;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsWorldResource;
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
    private static ResourceType<EntityStore, PhysicsDebugResource> physicsDebugResourceType;
    @Nullable
    private static ResourceType<EntityStore, PhysicsRuntimeProfilingResource>
        physicsRuntimeProfilingResourceType;
    @Nullable
    private static ResourceType<EntityStore, PhysicsProjectionIndexResource>
        physicsProjectionIndexResourceType;
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
        physicsDebugResourceType = registry.registerResource(PhysicsDebugResource.class,
            PhysicsDebugResource::new);
        physicsRuntimeProfilingResourceType = registry.registerResource(
            PhysicsRuntimeProfilingResource.class,
            PhysicsRuntimeProfilingResource::new);
        physicsProjectionIndexResourceType = registry.registerResource(
            PhysicsProjectionIndexResource.class,
            PhysicsProjectionIndexResource::new);
    }

    public static void registerEventTypes(@Nonnull ComponentRegistryProxy<EntityStore> registry) {
        physicsEventFramePublishedEventType =
            registry.registerWorldEventType(PhysicsEventFramePublishedEvent.class);
    }

    public static void registerSystemGroups(@Nonnull ComponentRegistryProxy<EntityStore> registry) {
        persistenceRestoreGroup = registry.registerSystemGroup();
    }

    @Nonnull
    public static ComponentType<EntityStore, BodyAttachmentComponent> bodyAttachmentComponentType() {
        return bodyAttachmentComponentType;
    }

    @Nonnull
    public static ComponentType<EntityStore, GeneratedVisualProxyComponent>
    generatedVisualProxyComponentType() {
        return generatedVisualProxyComponentType;
    }

    @Nonnull
    public static ResourceType<EntityStore, PhysicsWorldResource> physicsWorldResourceType() {
        return physicsWorldResourceType;
    }

    @Nonnull
    public static ResourceType<EntityStore, PhysicsDebugResource> physicsDebugResourceType() {
        return physicsDebugResourceType;
    }

    @Nonnull
    public static ResourceType<EntityStore, PhysicsRuntimeProfilingResource>
    physicsRuntimeProfilingResourceType() {
        return physicsRuntimeProfilingResourceType;
    }

    @Nonnull
    public static ResourceType<EntityStore, PhysicsProjectionIndexResource>
    physicsProjectionIndexResourceType() {
        return physicsProjectionIndexResourceType;
    }

    @Nonnull
    public static WorldEventType<EntityStore, PhysicsEventFramePublishedEvent>
    physicsEventFramePublishedEventType() {
        return physicsEventFramePublishedEventType;
    }

    @Nonnull
    public static SystemGroup<EntityStore> persistenceRestoreGroup() {
        return persistenceRestoreGroup;
    }
}
