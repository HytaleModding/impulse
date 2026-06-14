package dev.hytalemodding.impulse.core.plugin.physicsstore;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsDebugResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsBodyRegistrationResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsEventResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsIdentityIndexResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsProfilingResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsTerrainMutationQueueResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsRestoreStatusResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsRuntimeResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsSpaceCompatibilityIndexResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsSnapshotResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsStoreReadQueueResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsTerrainPayloadResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsWorldSettingsResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsWorldCollisionIndexResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.persistence.PersistentPhysicsStoreResource;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.BodyCommandComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.BodyComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.ColliderComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.CollisionLodSettingsComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.CollisionFilterComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.DynamicsComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.ExtensionSettingsComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.JointComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.MaterialComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.ShapeComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.SolverSettingsComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.SpaceComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.TargetComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.TerrainColliderComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.UuidComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.VisualMaterializationSettingsComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.VisualSyncSettingsComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.WorldCollisionComponent;
import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Registered Hytale ECS type handles for the authoritative PhysicsStore.
 */
public final class PhysicsStoreTypes {

    @Nullable
    private static ComponentType<PhysicsStore, UuidComponent> uuidComponentType;
    @Nullable
    private static ComponentType<PhysicsStore, SpaceComponent> spaceComponentType;
    @Nullable
    private static ComponentType<PhysicsStore, BodyComponent> bodyComponentType;
    @Nullable
    private static ComponentType<PhysicsStore, BodyCommandComponent> bodyCommandComponentType;
    @Nullable
    private static ComponentType<PhysicsStore, DynamicsComponent> dynamicsComponentType;
    @Nullable
    private static ComponentType<PhysicsStore, ColliderComponent> colliderComponentType;
    @Nullable
    private static ComponentType<PhysicsStore, ShapeComponent> shapeComponentType;
    @Nullable
    private static ComponentType<PhysicsStore, MaterialComponent> materialComponentType;
    @Nullable
    private static ComponentType<PhysicsStore, CollisionFilterComponent> collisionFilterComponentType;
    @Nullable
    private static ComponentType<PhysicsStore, JointComponent> jointComponentType;
    @Nullable
    private static ComponentType<PhysicsStore, TargetComponent> targetComponentType;
    @Nullable
    private static ComponentType<PhysicsStore, TerrainColliderComponent> terrainColliderComponentType;
    @Nullable
    private static ComponentType<PhysicsStore, WorldCollisionComponent> worldCollisionComponentType;
    @Nullable
    private static ComponentType<PhysicsStore, SolverSettingsComponent> solverSettingsComponentType;
    @Nullable
    private static ComponentType<PhysicsStore, VisualSyncSettingsComponent> visualSyncSettingsComponentType;
    @Nullable
    private static ComponentType<PhysicsStore, VisualMaterializationSettingsComponent>
        visualMaterializationSettingsComponentType;
    @Nullable
    private static ComponentType<PhysicsStore, CollisionLodSettingsComponent> collisionLodSettingsComponentType;
    @Nullable
    private static ComponentType<PhysicsStore, ExtensionSettingsComponent> extensionSettingsComponentType;

    @Nullable
    private static ResourceType<PhysicsStore, PhysicsRuntimeResource> runtimeResourceType;
    @Nullable
    private static ResourceType<PhysicsStore, PhysicsWorldSettingsResource> worldSettingsResourceType;
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
    private static ResourceType<PhysicsStore, PhysicsDebugResource> debugResourceType;

    private PhysicsStoreTypes() {
    }

    public static void setUuidComponentType(
        @Nonnull ComponentType<PhysicsStore, UuidComponent> type) {
        uuidComponentType = Objects.requireNonNull(type, "type");
    }

    public static void setSpaceComponentType(
        @Nonnull ComponentType<PhysicsStore, SpaceComponent> type) {
        spaceComponentType = Objects.requireNonNull(type, "type");
    }

    public static void setBodyComponentType(
        @Nonnull ComponentType<PhysicsStore, BodyComponent> type) {
        bodyComponentType = Objects.requireNonNull(type, "type");
    }

    public static void setBodyCommandComponentType(
        @Nonnull ComponentType<PhysicsStore, BodyCommandComponent> type) {
        bodyCommandComponentType = Objects.requireNonNull(type, "type");
    }

    public static void setDynamicsComponentType(
        @Nonnull ComponentType<PhysicsStore, DynamicsComponent> type) {
        dynamicsComponentType = Objects.requireNonNull(type, "type");
    }

    public static void setColliderComponentType(
        @Nonnull ComponentType<PhysicsStore, ColliderComponent> type) {
        colliderComponentType = Objects.requireNonNull(type, "type");
    }

    public static void setShapeComponentType(
        @Nonnull ComponentType<PhysicsStore, ShapeComponent> type) {
        shapeComponentType = Objects.requireNonNull(type, "type");
    }

    public static void setMaterialComponentType(
        @Nonnull ComponentType<PhysicsStore, MaterialComponent> type) {
        materialComponentType = Objects.requireNonNull(type, "type");
    }

    public static void setCollisionFilterComponentType(
        @Nonnull ComponentType<PhysicsStore, CollisionFilterComponent> type) {
        collisionFilterComponentType = Objects.requireNonNull(type, "type");
    }

    public static void setJointComponentType(
        @Nonnull ComponentType<PhysicsStore, JointComponent> type) {
        jointComponentType = Objects.requireNonNull(type, "type");
    }

    public static void setTargetComponentType(
        @Nonnull ComponentType<PhysicsStore, TargetComponent> type) {
        targetComponentType = Objects.requireNonNull(type, "type");
    }

    public static void setTerrainColliderComponentType(
        @Nonnull ComponentType<PhysicsStore, TerrainColliderComponent> type) {
        terrainColliderComponentType = Objects.requireNonNull(type, "type");
    }

    public static void setWorldCollisionComponentType(
        @Nonnull ComponentType<PhysicsStore, WorldCollisionComponent> type) {
        worldCollisionComponentType = Objects.requireNonNull(type, "type");
    }

    public static void setSolverSettingsComponentType(
        @Nonnull ComponentType<PhysicsStore, SolverSettingsComponent> type) {
        solverSettingsComponentType = Objects.requireNonNull(type, "type");
    }

    public static void setVisualSyncSettingsComponentType(
        @Nonnull ComponentType<PhysicsStore, VisualSyncSettingsComponent> type) {
        visualSyncSettingsComponentType = Objects.requireNonNull(type, "type");
    }

    public static void setVisualMaterializationSettingsComponentType(
        @Nonnull ComponentType<PhysicsStore, VisualMaterializationSettingsComponent> type) {
        visualMaterializationSettingsComponentType = Objects.requireNonNull(type, "type");
    }

    public static void setCollisionLodSettingsComponentType(
        @Nonnull ComponentType<PhysicsStore, CollisionLodSettingsComponent> type) {
        collisionLodSettingsComponentType = Objects.requireNonNull(type, "type");
    }

    public static void setExtensionSettingsComponentType(
        @Nonnull ComponentType<PhysicsStore, ExtensionSettingsComponent> type) {
        extensionSettingsComponentType = Objects.requireNonNull(type, "type");
    }

    public static void setRuntimeResourceType(
        @Nonnull ResourceType<PhysicsStore, PhysicsRuntimeResource> type) {
        runtimeResourceType = Objects.requireNonNull(type, "type");
    }

    public static void setWorldSettingsResourceType(
        @Nonnull ResourceType<PhysicsStore, PhysicsWorldSettingsResource> type) {
        worldSettingsResourceType = Objects.requireNonNull(type, "type");
    }

    public static void setSpaceCompatibilityIndexResourceType(
        @Nonnull ResourceType<PhysicsStore, PhysicsSpaceCompatibilityIndexResource> type) {
        spaceCompatibilityIndexResourceType = Objects.requireNonNull(type, "type");
    }

    public static void setTerrainMutationQueueResourceType(
        @Nonnull ResourceType<PhysicsStore, PhysicsTerrainMutationQueueResource> type) {
        terrainMutationQueueResourceType = Objects.requireNonNull(type, "type");
    }

    public static void setIdentityIndexResourceType(
        @Nonnull ResourceType<PhysicsStore, PhysicsIdentityIndexResource> type) {
        identityIndexResourceType = Objects.requireNonNull(type, "type");
    }

    public static void setSnapshotResourceType(
        @Nonnull ResourceType<PhysicsStore, PhysicsSnapshotResource> type) {
        snapshotResourceType = Objects.requireNonNull(type, "type");
    }

    public static void setBodyRegistrationResourceType(
        @Nonnull ResourceType<PhysicsStore, PhysicsBodyRegistrationResource> type) {
        bodyRegistrationResourceType = Objects.requireNonNull(type, "type");
    }

    public static void setEventResourceType(
        @Nonnull ResourceType<PhysicsStore, PhysicsEventResource> type) {
        eventResourceType = Objects.requireNonNull(type, "type");
    }

    public static void setReadQueueResourceType(
        @Nonnull ResourceType<PhysicsStore, PhysicsStoreReadQueueResource> type) {
        readQueueResourceType = Objects.requireNonNull(type, "type");
    }

    public static void setTerrainPayloadResourceType(
        @Nonnull ResourceType<PhysicsStore, PhysicsTerrainPayloadResource> type) {
        terrainPayloadResourceType = Objects.requireNonNull(type, "type");
    }

    public static void setWorldCollisionIndexResourceType(
        @Nonnull ResourceType<PhysicsStore, PhysicsWorldCollisionIndexResource> type) {
        worldCollisionIndexResourceType = Objects.requireNonNull(type, "type");
    }

    public static void setPersistentStoreResourceType(
        @Nonnull ResourceType<PhysicsStore, PersistentPhysicsStoreResource> type) {
        persistentStoreResourceType = Objects.requireNonNull(type, "type");
    }

    public static void setRestoreStatusResourceType(
        @Nonnull ResourceType<PhysicsStore, PhysicsRestoreStatusResource> type) {
        restoreStatusResourceType = Objects.requireNonNull(type, "type");
    }

    public static void setProfilingResourceType(
        @Nonnull ResourceType<PhysicsStore, PhysicsProfilingResource> type) {
        profilingResourceType = Objects.requireNonNull(type, "type");
    }

    public static void setDebugResourceType(
        @Nonnull ResourceType<PhysicsStore, PhysicsDebugResource> type) {
        debugResourceType = Objects.requireNonNull(type, "type");
    }

    @Nonnull
    public static ComponentType<PhysicsStore, UuidComponent> uuidComponentType() {
        return require(uuidComponentType, "UuidComponent");
    }

    @Nonnull
    public static ComponentType<PhysicsStore, SpaceComponent> spaceComponentType() {
        return require(spaceComponentType, "SpaceComponent");
    }

    @Nonnull
    public static ComponentType<PhysicsStore, BodyComponent> bodyComponentType() {
        return require(bodyComponentType, "BodyComponent");
    }

    @Nonnull
    public static ComponentType<PhysicsStore, BodyCommandComponent> bodyCommandComponentType() {
        return require(bodyCommandComponentType, "BodyCommandComponent");
    }

    @Nonnull
    public static ComponentType<PhysicsStore, DynamicsComponent> dynamicsComponentType() {
        return require(dynamicsComponentType, "DynamicsComponent");
    }

    @Nonnull
    public static ComponentType<PhysicsStore, ColliderComponent> colliderComponentType() {
        return require(colliderComponentType, "ColliderComponent");
    }

    @Nonnull
    public static ComponentType<PhysicsStore, ShapeComponent> shapeComponentType() {
        return require(shapeComponentType, "ShapeComponent");
    }

    @Nonnull
    public static ComponentType<PhysicsStore, MaterialComponent> materialComponentType() {
        return require(materialComponentType, "MaterialComponent");
    }

    @Nonnull
    public static ComponentType<PhysicsStore, CollisionFilterComponent> collisionFilterComponentType() {
        return require(collisionFilterComponentType, "CollisionFilterComponent");
    }

    @Nonnull
    public static ComponentType<PhysicsStore, JointComponent> jointComponentType() {
        return require(jointComponentType, "JointComponent");
    }

    @Nonnull
    public static ComponentType<PhysicsStore, TargetComponent> targetComponentType() {
        return require(targetComponentType, "TargetComponent");
    }

    @Nonnull
    public static ComponentType<PhysicsStore, TerrainColliderComponent> terrainColliderComponentType() {
        return require(terrainColliderComponentType, "TerrainColliderComponent");
    }

    @Nonnull
    public static ComponentType<PhysicsStore, WorldCollisionComponent> worldCollisionComponentType() {
        return require(worldCollisionComponentType, "WorldCollisionComponent");
    }

    @Nonnull
    public static ComponentType<PhysicsStore, SolverSettingsComponent> solverSettingsComponentType() {
        return require(solverSettingsComponentType, "SolverSettingsComponent");
    }

    @Nonnull
    public static ComponentType<PhysicsStore, VisualSyncSettingsComponent>
    visualSyncSettingsComponentType() {
        return require(visualSyncSettingsComponentType, "VisualSyncSettingsComponent");
    }

    @Nonnull
    public static ComponentType<PhysicsStore, VisualMaterializationSettingsComponent>
    visualMaterializationSettingsComponentType() {
        return require(visualMaterializationSettingsComponentType,
            "VisualMaterializationSettingsComponent");
    }

    @Nonnull
    public static ComponentType<PhysicsStore, CollisionLodSettingsComponent>
    collisionLodSettingsComponentType() {
        return require(collisionLodSettingsComponentType, "CollisionLodSettingsComponent");
    }

    @Nonnull
    public static ComponentType<PhysicsStore, ExtensionSettingsComponent>
    extensionSettingsComponentType() {
        return require(extensionSettingsComponentType, "ExtensionSettingsComponent");
    }

    @Nonnull
    public static ResourceType<PhysicsStore, PhysicsRuntimeResource> runtimeResourceType() {
        return require(runtimeResourceType, "PhysicsRuntimeResource");
    }

    @Nonnull
    public static ResourceType<PhysicsStore, PhysicsWorldSettingsResource> worldSettingsResourceType() {
        return require(worldSettingsResourceType, "PhysicsWorldSettingsResource");
    }

    @Nonnull
    public static ResourceType<PhysicsStore, PhysicsSpaceCompatibilityIndexResource>
        spaceCompatibilityIndexResourceType() {
        return require(spaceCompatibilityIndexResourceType, "PhysicsSpaceCompatibilityIndexResource");
    }

    @Nonnull
    public static ResourceType<PhysicsStore, PhysicsTerrainMutationQueueResource> terrainMutationQueueResourceType() {
        return require(terrainMutationQueueResourceType, "PhysicsTerrainMutationQueueResource");
    }

    @Nonnull
    public static ResourceType<PhysicsStore, PhysicsIdentityIndexResource> identityIndexResourceType() {
        return require(identityIndexResourceType, "PhysicsIdentityIndexResource");
    }

    @Nonnull
    public static ResourceType<PhysicsStore, PhysicsSnapshotResource> snapshotResourceType() {
        return require(snapshotResourceType, "PhysicsSnapshotResource");
    }

    @Nonnull
    public static ResourceType<PhysicsStore, PhysicsBodyRegistrationResource>
        bodyRegistrationResourceType() {
        return require(bodyRegistrationResourceType, "PhysicsBodyRegistrationResource");
    }

    @Nonnull
    public static ResourceType<PhysicsStore, PhysicsEventResource> eventResourceType() {
        return require(eventResourceType, "PhysicsEventResource");
    }

    @Nonnull
    public static ResourceType<PhysicsStore, PhysicsStoreReadQueueResource> readQueueResourceType() {
        return require(readQueueResourceType, "PhysicsStoreReadQueueResource");
    }

    @Nonnull
    public static ResourceType<PhysicsStore, PhysicsTerrainPayloadResource> terrainPayloadResourceType() {
        return require(terrainPayloadResourceType, "PhysicsTerrainPayloadResource");
    }

    @Nonnull
    public static ResourceType<PhysicsStore, PhysicsWorldCollisionIndexResource> worldCollisionIndexResourceType() {
        return require(worldCollisionIndexResourceType, "PhysicsWorldCollisionIndexResource");
    }

    @Nonnull
    public static ResourceType<PhysicsStore, PersistentPhysicsStoreResource> persistentStoreResourceType() {
        return require(persistentStoreResourceType, "PersistentPhysicsStoreResource");
    }

    @Nonnull
    public static ResourceType<PhysicsStore, PhysicsRestoreStatusResource> restoreStatusResourceType() {
        return require(restoreStatusResourceType, "PhysicsRestoreStatusResource");
    }

    @Nonnull
    public static ResourceType<PhysicsStore, PhysicsProfilingResource> profilingResourceType() {
        return require(profilingResourceType, "PhysicsProfilingResource");
    }

    @Nonnull
    public static ResourceType<PhysicsStore, PhysicsDebugResource> debugResourceType() {
        return require(debugResourceType, "PhysicsDebugResource");
    }

    @Nonnull
    private static <T> T require(@Nullable T type, @Nonnull String name) {
        if (type == null) {
            throw new IllegalStateException("PhysicsStore " + name + " is not registered");
        }
        return type;
    }
}
