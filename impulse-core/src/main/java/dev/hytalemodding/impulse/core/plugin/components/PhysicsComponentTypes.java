package dev.hytalemodding.impulse.core.plugin.components;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Registered Hytale ECS component type handles for PhysicsStore entities.
 */
public final class PhysicsComponentTypes {

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

    private PhysicsComponentTypes() {
    }

    public static void setUuidComponentType(
        @Nonnull ComponentType<PhysicsStore, UuidComponent> type) {
        uuidComponentType = type;
    }

    public static void setSpaceComponentType(
        @Nonnull ComponentType<PhysicsStore, SpaceComponent> type) {
        spaceComponentType = type;
    }

    public static void setBodyComponentType(
        @Nonnull ComponentType<PhysicsStore, BodyComponent> type) {
        bodyComponentType = type;
    }

    public static void setBodyCommandComponentType(
        @Nonnull ComponentType<PhysicsStore, BodyCommandComponent> type) {
        bodyCommandComponentType = type;
    }

    public static void setDynamicsComponentType(
        @Nonnull ComponentType<PhysicsStore, DynamicsComponent> type) {
        dynamicsComponentType = type;
    }

    public static void setColliderComponentType(
        @Nonnull ComponentType<PhysicsStore, ColliderComponent> type) {
        colliderComponentType = type;
    }

    public static void setShapeComponentType(
        @Nonnull ComponentType<PhysicsStore, ShapeComponent> type) {
        shapeComponentType = type;
    }

    public static void setMaterialComponentType(
        @Nonnull ComponentType<PhysicsStore, MaterialComponent> type) {
        materialComponentType = type;
    }

    public static void setCollisionFilterComponentType(
        @Nonnull ComponentType<PhysicsStore, CollisionFilterComponent> type) {
        collisionFilterComponentType = type;
    }

    public static void setJointComponentType(
        @Nonnull ComponentType<PhysicsStore, JointComponent> type) {
        jointComponentType = type;
    }

    public static void setTargetComponentType(
        @Nonnull ComponentType<PhysicsStore, TargetComponent> type) {
        targetComponentType = type;
    }

    public static void setTerrainColliderComponentType(
        @Nonnull ComponentType<PhysicsStore, TerrainColliderComponent> type) {
        terrainColliderComponentType = type;
    }

    public static void setWorldCollisionComponentType(
        @Nonnull ComponentType<PhysicsStore, WorldCollisionComponent> type) {
        worldCollisionComponentType = type;
    }

    public static void setSolverSettingsComponentType(
        @Nonnull ComponentType<PhysicsStore, SolverSettingsComponent> type) {
        solverSettingsComponentType = type;
    }

    public static void setVisualSyncSettingsComponentType(
        @Nonnull ComponentType<PhysicsStore, VisualSyncSettingsComponent> type) {
        visualSyncSettingsComponentType = type;
    }

    public static void setVisualMaterializationSettingsComponentType(
        @Nonnull ComponentType<PhysicsStore, VisualMaterializationSettingsComponent> type) {
        visualMaterializationSettingsComponentType = type;
    }

    public static void setCollisionLodSettingsComponentType(
        @Nonnull ComponentType<PhysicsStore, CollisionLodSettingsComponent> type) {
        collisionLodSettingsComponentType = type;
    }

    public static void setExtensionSettingsComponentType(
        @Nonnull ComponentType<PhysicsStore, ExtensionSettingsComponent> type) {
        extensionSettingsComponentType = type;
    }

    @Nonnull
    public static ComponentType<PhysicsStore, UuidComponent> uuidComponentType() {
        return uuidComponentType;
    }

    @Nonnull
    public static ComponentType<PhysicsStore, SpaceComponent> spaceComponentType() {
        return spaceComponentType;
    }

    @Nonnull
    public static ComponentType<PhysicsStore, BodyComponent> bodyComponentType() {
        return bodyComponentType;
    }

    @Nonnull
    public static ComponentType<PhysicsStore, BodyCommandComponent> bodyCommandComponentType() {
        return bodyCommandComponentType;
    }

    @Nonnull
    public static ComponentType<PhysicsStore, DynamicsComponent> dynamicsComponentType() {
        return dynamicsComponentType;
    }

    @Nonnull
    public static ComponentType<PhysicsStore, ColliderComponent> colliderComponentType() {
        return colliderComponentType;
    }

    @Nonnull
    public static ComponentType<PhysicsStore, ShapeComponent> shapeComponentType() {
        return shapeComponentType;
    }

    @Nonnull
    public static ComponentType<PhysicsStore, MaterialComponent> materialComponentType() {
        return materialComponentType;
    }

    @Nonnull
    public static ComponentType<PhysicsStore, CollisionFilterComponent> collisionFilterComponentType() {
        return collisionFilterComponentType;
    }

    @Nonnull
    public static ComponentType<PhysicsStore, JointComponent> jointComponentType() {
        return jointComponentType;
    }

    @Nonnull
    public static ComponentType<PhysicsStore, TargetComponent> targetComponentType() {
        return targetComponentType;
    }

    @Nonnull
    public static ComponentType<PhysicsStore, TerrainColliderComponent> terrainColliderComponentType() {
        return terrainColliderComponentType;
    }

    @Nonnull
    public static ComponentType<PhysicsStore, WorldCollisionComponent> worldCollisionComponentType() {
        return worldCollisionComponentType;
    }

    @Nonnull
    public static ComponentType<PhysicsStore, SolverSettingsComponent> solverSettingsComponentType() {
        return solverSettingsComponentType;
    }

    @Nonnull
    public static ComponentType<PhysicsStore, VisualSyncSettingsComponent>
    visualSyncSettingsComponentType() {
        return visualSyncSettingsComponentType;
    }

    @Nonnull
    public static ComponentType<PhysicsStore, VisualMaterializationSettingsComponent>
    visualMaterializationSettingsComponentType() {
        return visualMaterializationSettingsComponentType;
    }

    @Nonnull
    public static ComponentType<PhysicsStore, CollisionLodSettingsComponent>
    collisionLodSettingsComponentType() {
        return collisionLodSettingsComponentType;
    }

    @Nonnull
    public static ComponentType<PhysicsStore, ExtensionSettingsComponent>
    extensionSettingsComponentType() {
        return extensionSettingsComponentType;
    }
}
