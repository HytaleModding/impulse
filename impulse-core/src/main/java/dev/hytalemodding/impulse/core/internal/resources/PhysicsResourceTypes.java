package dev.hytalemodding.impulse.core.internal.resources;

import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.core.internal.persistence.PersistentPhysicsStoreResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsBodyRegistrationResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsIdentityIndexResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsProfilingResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsRuntimeResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsSnapshotResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsSpaceCompatibilityIndexResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsStepSchedulerResource;
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
    private static ResourceType<PhysicsStore, PhysicsTerrainMutationQueueResource> terrainMutationQueueResourceType;
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
    private static ResourceType<PhysicsStore, PhysicsTerrainPayloadResource> terrainPayloadResourceType;
    @Nullable
    private static ResourceType<PhysicsStore, PhysicsWorldCollisionIndexResource> worldCollisionIndexResourceType;
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

    public static void setRuntimeResourceType(
        @Nonnull ResourceType<PhysicsStore, PhysicsRuntimeResource> type) {
        runtimeResourceType = type;
    }

    public static void setWorldSettingsResourceType(
        @Nonnull ResourceType<PhysicsStore, PhysicsWorldSettingsResource> type) {
        worldSettingsResourceType = type;
    }

    public static void setStepSchedulerResourceType(
        @Nonnull ResourceType<PhysicsStore, PhysicsStepSchedulerResource> type) {
        stepSchedulerResourceType = type;
    }

    public static void setSpaceCompatibilityIndexResourceType(
        @Nonnull ResourceType<PhysicsStore, PhysicsSpaceCompatibilityIndexResource> type) {
        spaceCompatibilityIndexResourceType = type;
    }

    public static void setTerrainMutationQueueResourceType(
        @Nonnull ResourceType<PhysicsStore, PhysicsTerrainMutationQueueResource> type) {
        terrainMutationQueueResourceType = type;
    }

    public static void setIdentityIndexResourceType(
        @Nonnull ResourceType<PhysicsStore, PhysicsIdentityIndexResource> type) {
        identityIndexResourceType = type;
    }

    public static void setSnapshotResourceType(
        @Nonnull ResourceType<PhysicsStore, PhysicsSnapshotResource> type) {
        snapshotResourceType = type;
    }

    public static void setBodyRegistrationResourceType(
        @Nonnull ResourceType<PhysicsStore, PhysicsBodyRegistrationResource> type) {
        bodyRegistrationResourceType = type;
    }

    public static void setEventResourceType(
        @Nonnull ResourceType<PhysicsStore, PhysicsEventResource> type) {
        eventResourceType = type;
    }

    public static void setReadQueueResourceType(
        @Nonnull ResourceType<PhysicsStore, PhysicsStoreReadQueueResource> type) {
        readQueueResourceType = type;
    }

    public static void setTerrainPayloadResourceType(
        @Nonnull ResourceType<PhysicsStore, PhysicsTerrainPayloadResource> type) {
        terrainPayloadResourceType = type;
    }

    public static void setWorldCollisionIndexResourceType(
        @Nonnull ResourceType<PhysicsStore, PhysicsWorldCollisionIndexResource> type) {
        worldCollisionIndexResourceType = type;
    }

    public static void setPersistentStoreResourceType(
        @Nonnull ResourceType<PhysicsStore, PersistentPhysicsStoreResource> type) {
        persistentStoreResourceType = type;
    }

    public static void setRestoreStatusResourceType(
        @Nonnull ResourceType<PhysicsStore, PhysicsRestoreStatusResource> type) {
        restoreStatusResourceType = type;
    }

    public static void setProfilingResourceType(
        @Nonnull ResourceType<PhysicsStore, PhysicsProfilingResource> type) {
        profilingResourceType = type;
    }

    public static void setDebugResourceType(
        @Nonnull ResourceType<PhysicsStore,
            dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsDebugResource> type) {
        debugResourceType = type;
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
    public static ResourceType<PhysicsStore, PhysicsTerrainMutationQueueResource> terrainMutationQueueResourceType() {
        return terrainMutationQueueResourceType;
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
    public static ResourceType<PhysicsStore, PhysicsTerrainPayloadResource> terrainPayloadResourceType() {
        return terrainPayloadResourceType;
    }

    @Nonnull
    public static ResourceType<PhysicsStore, PhysicsWorldCollisionIndexResource> worldCollisionIndexResourceType() {
        return worldCollisionIndexResourceType;
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
