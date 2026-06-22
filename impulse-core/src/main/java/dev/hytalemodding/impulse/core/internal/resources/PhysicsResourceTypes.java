package dev.hytalemodding.impulse.core.internal.resources;

import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.core.internal.physics.resources.PhysicsDebugResource;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Registered Hytale ECS resource type handles for the internal PhysicsStore runtime.
 */
public final class PhysicsResourceTypes {

    @Nullable
    private static ResourceType<PhysicsStore, PhysicsRuntimeResource> runtimeResourceType;
    @Nullable
    private static ResourceType<PhysicsStore, PhysicsWorldSettingsResource> worldSettingsResourceType;
    @Nullable
    private static ResourceType<PhysicsStore, PhysicsStepSchedulerResource> stepSchedulerResourceType;
    @Nullable
    private static ResourceType<PhysicsStore, PhysicsSpaceCompatibilityIndexResource>
        spaceCompatibilityIndexResourceType;
    @Nullable
    private static ResourceType<PhysicsStore, PhysicsSnapshotResource> snapshotResourceType;
    @Nullable
    private static ResourceType<PhysicsStore, PhysicsEventResource> eventResourceType;
    @Nullable
    private static ResourceType<PhysicsStore, PhysicsStoreReadQueueResource> readQueueResourceType;
    @Nullable
    private static ResourceType<PhysicsStore, PhysicsRestoreStatusResource> restoreStatusResourceType;
    @Nullable
    private static ResourceType<PhysicsStore, PhysicsProfilingResource> profilingResourceType;
    @Nullable
    private static ResourceType<PhysicsStore,
            PhysicsDebugResource> debugResourceType;

    private PhysicsResourceTypes() {
    }

    public static void registerResourceTypes(
        @Nonnull ComponentRegistryProxy<PhysicsStore> registry) {
        runtimeResourceType = registry.registerResource(
            PhysicsRuntimeResource.class,
            PhysicsRuntimeResource::new);
        worldSettingsResourceType = registry.registerResource(
            PhysicsWorldSettingsResource.class,
            PhysicsWorldSettingsResource::new);
        stepSchedulerResourceType = registry.registerResource(
            PhysicsStepSchedulerResource.class,
            PhysicsStepSchedulerResource::new);
        spaceCompatibilityIndexResourceType = registry.registerResource(
            PhysicsSpaceCompatibilityIndexResource.class,
            PhysicsSpaceCompatibilityIndexResource::new);
        snapshotResourceType = registry.registerResource(
            PhysicsSnapshotResource.class,
            PhysicsSnapshotResource::new);
        eventResourceType = registry.registerResource(
            PhysicsEventResource.class,
            PhysicsEventResource::new);
        readQueueResourceType = registry.registerResource(
            PhysicsStoreReadQueueResource.class,
            PhysicsStoreReadQueueResource::new);
        restoreStatusResourceType = registry.registerResource(
            PhysicsRestoreStatusResource.class,
            PhysicsRestoreStatusResource::new);
        profilingResourceType = registry.registerResource(
            PhysicsProfilingResource.class,
            PhysicsProfilingResource::new);
        debugResourceType = registry.registerResource(
            PhysicsDebugResource.class,
            PhysicsDebugResource::new);
    }

    @Nonnull
    public static ResourceType<PhysicsStore, PhysicsRuntimeResource> runtimeResourceType() {
        return runtimeResourceType;
    }

    @Nonnull
    public static ResourceType<PhysicsStore, PhysicsWorldSettingsResource> worldSettingsResourceType() {
        return worldSettingsResourceType;
    }

    @Nonnull
    public static ResourceType<PhysicsStore, PhysicsStepSchedulerResource> stepSchedulerResourceType() {
        return stepSchedulerResourceType;
    }

    @Nonnull
    public static ResourceType<PhysicsStore, PhysicsSpaceCompatibilityIndexResource>
        spaceCompatibilityIndexResourceType() {
        return spaceCompatibilityIndexResourceType;
    }

    @Nonnull
    public static ResourceType<PhysicsStore, PhysicsSnapshotResource> snapshotResourceType() {
        return snapshotResourceType;
    }

    @Nonnull
    public static ResourceType<PhysicsStore, PhysicsEventResource> eventResourceType() {
        return eventResourceType;
    }

    @Nonnull
    public static ResourceType<PhysicsStore, PhysicsStoreReadQueueResource> readQueueResourceType() {
        return readQueueResourceType;
    }

    @Nonnull
    public static ResourceType<PhysicsStore, PhysicsRestoreStatusResource> restoreStatusResourceType() {
        return restoreStatusResourceType;
    }

    @Nonnull
    public static ResourceType<PhysicsStore, PhysicsProfilingResource> profilingResourceType() {
        return profilingResourceType;
    }

    @Nonnull
    public static ResourceType<PhysicsStore,
            PhysicsDebugResource> debugResourceType() {
        return debugResourceType;
    }
}
