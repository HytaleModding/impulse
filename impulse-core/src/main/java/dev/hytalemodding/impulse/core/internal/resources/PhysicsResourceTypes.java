package dev.hytalemodding.impulse.core.internal.resources;

import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.core.internal.persistence.PersistentPhysicsStoreResource;
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
    private static ResourceType<PhysicsStore, PhysicsIdentityIndexResource> identityIndexResourceType;
    @Nullable
    private static ResourceType<PhysicsStore, PhysicsSnapshotResource> snapshotResourceType;
    @Nullable
    private static ResourceType<PhysicsStore, PhysicsBodyRegistrationResource>
        bodyRegistrationResourceType;
    @Nullable
    private static ResourceType<PhysicsStore, PhysicsEventResource> eventResourceType;
    @Nullable
    private static ResourceType<PhysicsStore, PhysicsStoreReadQueueResource> readQueueResourceType;
    @Nullable
    private static ResourceType<PhysicsStore, PersistentPhysicsStoreResource> persistentStoreResourceType;
    @Nullable
    private static ResourceType<PhysicsStore, PhysicsRestoreStatusResource> restoreStatusResourceType;
    @Nullable
    private static ResourceType<PhysicsStore, PhysicsProfilingResource> profilingResourceType;
    @Nullable
    private static ResourceType<PhysicsStore,
        dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsDebugResource> debugResourceType;

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
        identityIndexResourceType = registry.registerResource(
            PhysicsIdentityIndexResource.class,
            PhysicsIdentityIndexResource::new);
        snapshotResourceType = registry.registerResource(
            PhysicsSnapshotResource.class,
            PhysicsSnapshotResource::new);
        bodyRegistrationResourceType = registry.registerResource(
            PhysicsBodyRegistrationResource.class,
            PhysicsBodyRegistrationResource::new);
        eventResourceType = registry.registerResource(
            PhysicsEventResource.class,
            PhysicsEventResource::new);
        readQueueResourceType = registry.registerResource(
            PhysicsStoreReadQueueResource.class,
            PhysicsStoreReadQueueResource::new);
        persistentStoreResourceType = registry.registerResource(
            PersistentPhysicsStoreResource.class,
            "PersistentPhysicsStore",
            PersistentPhysicsStoreResource.CODEC);
        restoreStatusResourceType = registry.registerResource(
            PhysicsRestoreStatusResource.class,
            PhysicsRestoreStatusResource::new);
        profilingResourceType = registry.registerResource(
            PhysicsProfilingResource.class,
            PhysicsProfilingResource::new);
        debugResourceType = registry.registerResource(
            dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsDebugResource.class,
            dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsDebugResource::new);
        PhysicsTerrainMutationQueueResource.setResourceType(registry.registerResource(
            PhysicsTerrainMutationQueueResource.class,
            PhysicsTerrainMutationQueueResource::new));
        PhysicsTerrainPayloadResource.setResourceType(registry.registerResource(
            PhysicsTerrainPayloadResource.class,
            PhysicsTerrainPayloadResource::new));
        PhysicsWorldCollisionIndexResource.setResourceType(registry.registerResource(
            PhysicsWorldCollisionIndexResource.class,
            PhysicsWorldCollisionIndexResource::new));
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
    public static ResourceType<PhysicsStore, PhysicsIdentityIndexResource> identityIndexResourceType() {
        return identityIndexResourceType;
    }

    @Nonnull
    public static ResourceType<PhysicsStore, PhysicsSnapshotResource> snapshotResourceType() {
        return snapshotResourceType;
    }

    @Nonnull
    public static ResourceType<PhysicsStore, PhysicsBodyRegistrationResource>
        bodyRegistrationResourceType() {
        return bodyRegistrationResourceType;
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
    public static ResourceType<PhysicsStore, PersistentPhysicsStoreResource> persistentStoreResourceType() {
        return persistentStoreResourceType;
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
        dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsDebugResource> debugResourceType() {
        return debugResourceType;
    }
}
