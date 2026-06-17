package dev.hytalemodding.impulse.core.plugin.components;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.core.internal.registration.PhysicsComponentTypeRegistry;
import dev.hytalemodding.impulse.core.plugin.modules.physicschunk.components.CollisionLodSettingsComponent;
import dev.hytalemodding.impulse.core.plugin.modules.physicschunk.components.PhysicsChunkTerrainComponent;
import javax.annotation.Nonnull;

/**
 * Public Hytale ECS component type handles for PhysicsStore entities.
 */
public final class PhysicsComponentTypes {

    private PhysicsComponentTypes() {
    }

    @Nonnull
    public static ComponentType<PhysicsStore, UuidComponent> uuidComponentType() {
        return PhysicsComponentTypeRegistry.uuidComponentType();
    }

    @Nonnull
    public static ComponentType<PhysicsStore, SpaceComponent> spaceComponentType() {
        return PhysicsComponentTypeRegistry.spaceComponentType();
    }

    @Nonnull
    public static ComponentType<PhysicsStore, BodyComponent> bodyComponentType() {
        return PhysicsComponentTypeRegistry.bodyComponentType();
    }

    @Nonnull
    public static ComponentType<PhysicsStore, BodyCommandComponent> bodyCommandComponentType() {
        return PhysicsComponentTypeRegistry.bodyCommandComponentType();
    }

    @Nonnull
    public static ComponentType<PhysicsStore, TerrainColliderComponent>
    terrainColliderComponentType() {
        return PhysicsComponentTypeRegistry.terrainColliderComponentType();
    }

    @Nonnull
    public static ComponentType<PhysicsStore, PhysicsChunkTerrainComponent>
    physicsChunkTerrainComponentType() {
        return PhysicsComponentTypeRegistry.physicsChunkTerrainComponentType();
    }

    @Nonnull
    public static ComponentType<PhysicsStore, DynamicsComponent> dynamicsComponentType() {
        return PhysicsComponentTypeRegistry.dynamicsComponentType();
    }

    @Nonnull
    public static ComponentType<PhysicsStore, ColliderComponent> colliderComponentType() {
        return PhysicsComponentTypeRegistry.colliderComponentType();
    }

    @Nonnull
    public static ComponentType<PhysicsStore, ShapeComponent> shapeComponentType() {
        return PhysicsComponentTypeRegistry.shapeComponentType();
    }

    @Nonnull
    public static ComponentType<PhysicsStore, MaterialComponent> materialComponentType() {
        return PhysicsComponentTypeRegistry.materialComponentType();
    }

    @Nonnull
    public static ComponentType<PhysicsStore, CollisionFilterComponent> collisionFilterComponentType() {
        return PhysicsComponentTypeRegistry.collisionFilterComponentType();
    }

    @Nonnull
    public static ComponentType<PhysicsStore, JointComponent> jointComponentType() {
        return PhysicsComponentTypeRegistry.jointComponentType();
    }

    @Nonnull
    public static ComponentType<PhysicsStore, TargetComponent> targetComponentType() {
        return PhysicsComponentTypeRegistry.targetComponentType();
    }

    @Nonnull
    public static ComponentType<PhysicsStore, SolverSettingsComponent> solverSettingsComponentType() {
        return PhysicsComponentTypeRegistry.solverSettingsComponentType();
    }

    @Nonnull
    public static ComponentType<PhysicsStore, VisualSyncSettingsComponent>
    visualSyncSettingsComponentType() {
        return PhysicsComponentTypeRegistry.visualSyncSettingsComponentType();
    }

    @Nonnull
    public static ComponentType<PhysicsStore, VisualMaterializationSettingsComponent>
    visualMaterializationSettingsComponentType() {
        return PhysicsComponentTypeRegistry.visualMaterializationSettingsComponentType();
    }

    @Nonnull
    public static ComponentType<PhysicsStore, CollisionLodSettingsComponent>
    collisionLodSettingsComponentType() {
        return PhysicsComponentTypeRegistry.collisionLodSettingsComponentType();
    }

    @Nonnull
    public static ComponentType<PhysicsStore, ExtensionSettingsComponent>
    extensionSettingsComponentType() {
        return PhysicsComponentTypeRegistry.extensionSettingsComponentType();
    }
}
