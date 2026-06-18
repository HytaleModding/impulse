package dev.hytalemodding.impulse.core.internal.registration;

import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.core.plugin.components.BodyCommandComponent;
import dev.hytalemodding.impulse.core.plugin.components.BodyComponent;
import dev.hytalemodding.impulse.core.plugin.components.ColliderComponent;
import dev.hytalemodding.impulse.core.plugin.components.CollisionFilterComponent;
import dev.hytalemodding.impulse.core.plugin.components.DynamicsComponent;
import dev.hytalemodding.impulse.core.plugin.components.ExtensionSettingsComponent;
import dev.hytalemodding.impulse.core.plugin.components.JointComponent;
import dev.hytalemodding.impulse.core.plugin.components.MaterialComponent;
import dev.hytalemodding.impulse.core.plugin.components.ShapeComponent;
import dev.hytalemodding.impulse.core.plugin.components.SolverSettingsComponent;
import dev.hytalemodding.impulse.core.plugin.components.SpaceComponent;
import dev.hytalemodding.impulse.core.plugin.components.TargetComponent;
import dev.hytalemodding.impulse.core.plugin.components.TerrainColliderComponent;
import dev.hytalemodding.impulse.core.plugin.components.UuidComponent;
import dev.hytalemodding.impulse.core.plugin.components.VisualMaterializationSettingsComponent;
import dev.hytalemodding.impulse.core.plugin.components.VisualSyncSettingsComponent;
import dev.hytalemodding.impulse.core.plugin.modules.physicschunk.components.CollisionLodSettingsComponent;
import dev.hytalemodding.impulse.core.plugin.modules.physicschunk.components.PhysicsChunkTerrainComponent;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Registered Hytale ECS component type handles for PhysicsStore entities.
 */
public final class PhysicsComponentTypeRegistry {

    @Nullable
    private static ComponentType<PhysicsStore, UuidComponent> uuidComponentType;
    @Nullable
    private static ComponentType<PhysicsStore, SpaceComponent> spaceComponentType;
    @Nullable
    private static ComponentType<PhysicsStore, BodyComponent> bodyComponentType;
    @Nullable
    private static ComponentType<PhysicsStore, BodyCommandComponent> bodyCommandComponentType;
    @Nullable
    private static ComponentType<PhysicsStore, TerrainColliderComponent> terrainColliderComponentType;
    @Nullable
    private static ComponentType<PhysicsStore, PhysicsChunkTerrainComponent> physicsChunkTerrainComponentType;
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

    private PhysicsComponentTypeRegistry() {
    }

    public static void registerComponentTypes(
        @Nonnull ComponentRegistryProxy<PhysicsStore> registry) {
        uuidComponentType = registry.registerComponent(
            UuidComponent.class,
            "Uuid",
            UuidComponent.CODEC);
        spaceComponentType = registry.registerComponent(
            SpaceComponent.class,
            "Space",
            SpaceComponent.CODEC);
        bodyComponentType = registry.registerComponent(
            BodyComponent.class,
            "Body",
            BodyComponent.CODEC);
        bodyCommandComponentType = registry.registerComponent(
            BodyCommandComponent.class,
            "BodyCommand",
            BodyCommandComponent.CODEC);
        terrainColliderComponentType = registry.registerComponent(
            TerrainColliderComponent.class,
            "TerrainCollider",
            TerrainColliderComponent.CODEC);
        physicsChunkTerrainComponentType = registry.registerComponent(
            PhysicsChunkTerrainComponent.class,
            "PhysicsChunkTerrain",
            PhysicsChunkTerrainComponent.CODEC);
        dynamicsComponentType = registry.registerComponent(
            DynamicsComponent.class,
            "Dynamics",
            DynamicsComponent.CODEC);
        colliderComponentType = registry.registerComponent(
            ColliderComponent.class,
            "Collider",
            ColliderComponent.CODEC);
        shapeComponentType = registry.registerComponent(
            ShapeComponent.class,
            "Shape",
            ShapeComponent.CODEC);
        materialComponentType = registry.registerComponent(
            MaterialComponent.class,
            "Material",
            MaterialComponent.CODEC);
        collisionFilterComponentType = registry.registerComponent(
            CollisionFilterComponent.class,
            "CollisionFilter",
            CollisionFilterComponent.CODEC);
        jointComponentType = registry.registerComponent(
            JointComponent.class,
            "Joint",
            JointComponent.CODEC);
        targetComponentType = registry.registerComponent(
            TargetComponent.class,
            "Target",
            TargetComponent.CODEC);
        solverSettingsComponentType = registry.registerComponent(
            SolverSettingsComponent.class,
            "SolverSettings",
            SolverSettingsComponent.CODEC);
        visualSyncSettingsComponentType = registry.registerComponent(
            VisualSyncSettingsComponent.class,
            "VisualSyncSettings",
            VisualSyncSettingsComponent.CODEC);
        visualMaterializationSettingsComponentType = registry.registerComponent(
            VisualMaterializationSettingsComponent.class,
            "VisualMaterializationSettings",
            VisualMaterializationSettingsComponent.CODEC);
        collisionLodSettingsComponentType = registry.registerComponent(
            CollisionLodSettingsComponent.class,
            "CollisionLodSettings",
            CollisionLodSettingsComponent.CODEC);
        extensionSettingsComponentType = registry.registerComponent(
            ExtensionSettingsComponent.class,
            "ExtensionSettings",
            ExtensionSettingsComponent.CODEC);
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
    public static ComponentType<PhysicsStore, TerrainColliderComponent>
    terrainColliderComponentType() {
        return terrainColliderComponentType;
    }

    @Nonnull
    public static ComponentType<PhysicsStore, PhysicsChunkTerrainComponent>
    physicsChunkTerrainComponentType() {
        return physicsChunkTerrainComponentType;
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
