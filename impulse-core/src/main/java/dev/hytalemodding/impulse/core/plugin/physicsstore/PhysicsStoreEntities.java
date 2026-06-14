package dev.hytalemodding.impulse.core.plugin.physicsstore;

import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.BodyComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.ColliderComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.CollisionFilterComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.CollisionLodSettingsComponent;
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
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Holder/component helpers for authoritative PhysicsStore aggregate entities.
 */
public final class PhysicsStoreEntities {

    private PhysicsStoreEntities() {
    }

    @Nonnull
    public static Holder<PhysicsStore> rowHolder(@Nonnull Store<PhysicsStore> store,
        @Nonnull UUID rowUuid) {
        Holder<PhysicsStore> holder = store.getRegistry().newHolder();
        addUuid(holder, rowUuid);
        return holder;
    }

    public static void addUuid(@Nonnull Holder<PhysicsStore> holder,
        @Nonnull UUID rowUuid) {
        Objects.requireNonNull(holder, "holder")
            .addComponent(UuidComponent.getComponentType(),
                new UuidComponent(Objects.requireNonNull(rowUuid, "rowUuid")));
    }

    @Nonnull
    public static Holder<PhysicsStore> spaceHolder(@Nonnull Store<PhysicsStore> store,
        @Nonnull UUID spaceUuid,
        @Nonnull SpaceComponent space,
        @Nonnull WorldCollisionComponent worldCollision,
        @Nonnull SolverSettingsComponent solverSettings,
        @Nonnull VisualSyncSettingsComponent visualSyncSettings,
        @Nonnull VisualMaterializationSettingsComponent visualMaterializationSettings,
        @Nonnull CollisionLodSettingsComponent collisionLodSettings,
        @Nonnull ExtensionSettingsComponent extensionSettings) {
        Holder<PhysicsStore> holder = rowHolder(store, spaceUuid);
        addSpaceComponents(holder,
            space,
            worldCollision,
            solverSettings,
            visualSyncSettings,
            visualMaterializationSettings,
            collisionLodSettings,
            extensionSettings);
        return holder;
    }

    @Nonnull
    public static Holder<PhysicsStore> bodyHolder(@Nonnull Store<PhysicsStore> store,
        @Nonnull UUID bodyUuid,
        @Nonnull BodyComponent body,
        @Nonnull DynamicsComponent dynamics,
        @Nullable TargetComponent target,
        @Nonnull ColliderComponent collider,
        @Nonnull ShapeComponent shape,
        @Nonnull MaterialComponent material,
        @Nonnull CollisionFilterComponent filter) {
        Holder<PhysicsStore> holder = rowHolder(store, bodyUuid);
        addBodyComponents(holder, body, dynamics, target, collider, shape, material, filter);
        return holder;
    }

    @Nonnull
    public static Holder<PhysicsStore> jointHolder(@Nonnull Store<PhysicsStore> store,
        @Nonnull UUID jointUuid,
        @Nonnull JointComponent joint) {
        Holder<PhysicsStore> holder = rowHolder(store, jointUuid);
        holder.addComponent(JointComponent.getComponentType(),
            Objects.requireNonNull(joint, "joint").clone());
        return holder;
    }

    @Nonnull
    public static Holder<PhysicsStore> terrainColliderHolder(@Nonnull Store<PhysicsStore> store,
        @Nonnull UUID terrainColliderUuid,
        @Nonnull TerrainColliderComponent terrainCollider) {
        Holder<PhysicsStore> holder = rowHolder(store, terrainColliderUuid);
        holder.addComponent(TerrainColliderComponent.getComponentType(),
            Objects.requireNonNull(terrainCollider, "terrainCollider").clone());
        return holder;
    }

    public static void addSpaceComponents(@Nonnull Holder<PhysicsStore> holder,
        @Nonnull SpaceComponent space,
        @Nonnull WorldCollisionComponent worldCollision,
        @Nonnull SolverSettingsComponent solverSettings,
        @Nonnull VisualSyncSettingsComponent visualSyncSettings,
        @Nonnull VisualMaterializationSettingsComponent visualMaterializationSettings,
        @Nonnull CollisionLodSettingsComponent collisionLodSettings,
        @Nonnull ExtensionSettingsComponent extensionSettings) {
        Objects.requireNonNull(holder, "holder")
            .addComponent(SpaceComponent.getComponentType(),
                Objects.requireNonNull(space, "space").clone());
        holder.addComponent(WorldCollisionComponent.getComponentType(),
            Objects.requireNonNull(worldCollision, "worldCollision").clone());
        addSpaceSettingsComponents(holder,
            solverSettings,
            visualSyncSettings,
            visualMaterializationSettings,
            collisionLodSettings,
            extensionSettings);
    }

    public static void addSpaceSettingsComponents(@Nonnull Holder<PhysicsStore> holder,
        @Nonnull SolverSettingsComponent solverSettings,
        @Nonnull VisualSyncSettingsComponent visualSyncSettings,
        @Nonnull VisualMaterializationSettingsComponent visualMaterializationSettings,
        @Nonnull CollisionLodSettingsComponent collisionLodSettings,
        @Nonnull ExtensionSettingsComponent extensionSettings) {
        Objects.requireNonNull(holder, "holder")
            .addComponent(SolverSettingsComponent.getComponentType(),
                Objects.requireNonNull(solverSettings, "solverSettings").clone());
        holder.addComponent(VisualSyncSettingsComponent.getComponentType(),
            Objects.requireNonNull(visualSyncSettings, "visualSyncSettings").clone());
        holder.addComponent(VisualMaterializationSettingsComponent.getComponentType(),
            Objects.requireNonNull(visualMaterializationSettings,
                "visualMaterializationSettings").clone());
        holder.addComponent(CollisionLodSettingsComponent.getComponentType(),
            Objects.requireNonNull(collisionLodSettings, "collisionLodSettings").clone());
        holder.addComponent(ExtensionSettingsComponent.getComponentType(),
            Objects.requireNonNull(extensionSettings, "extensionSettings").clone());
    }

    public static void addBodyComponents(@Nonnull Holder<PhysicsStore> holder,
        @Nonnull BodyComponent body,
        @Nonnull DynamicsComponent dynamics,
        @Nullable TargetComponent target,
        @Nonnull ColliderComponent collider,
        @Nonnull ShapeComponent shape,
        @Nonnull MaterialComponent material,
        @Nonnull CollisionFilterComponent filter) {
        Objects.requireNonNull(holder, "holder")
            .addComponent(BodyComponent.getComponentType(),
                Objects.requireNonNull(body, "body").clone());
        holder.addComponent(DynamicsComponent.getComponentType(),
            Objects.requireNonNull(dynamics, "dynamics").clone());
        holder.addComponent(ColliderComponent.getComponentType(),
            Objects.requireNonNull(collider, "collider").clone());
        holder.addComponent(ShapeComponent.getComponentType(),
            Objects.requireNonNull(shape, "shape").clone());
        holder.addComponent(MaterialComponent.getComponentType(),
            Objects.requireNonNull(material, "material").clone());
        holder.addComponent(CollisionFilterComponent.getComponentType(),
            Objects.requireNonNull(filter, "filter").clone());
        if (target != null) {
            holder.addComponent(TargetComponent.getComponentType(), target.clone());
        }
    }

    public static void putSpaceComponents(@Nonnull Store<PhysicsStore> store,
        @Nonnull Ref<PhysicsStore> ref,
        @Nonnull SpaceComponent space,
        @Nonnull WorldCollisionComponent worldCollision,
        @Nonnull SolverSettingsComponent solverSettings,
        @Nonnull VisualSyncSettingsComponent visualSyncSettings,
        @Nonnull VisualMaterializationSettingsComponent visualMaterializationSettings,
        @Nonnull CollisionLodSettingsComponent collisionLodSettings,
        @Nonnull ExtensionSettingsComponent extensionSettings) {
        Objects.requireNonNull(store, "store");
        Objects.requireNonNull(ref, "ref");
        store.putComponent(ref,
            SpaceComponent.getComponentType(),
            Objects.requireNonNull(space, "space").clone());
        store.putComponent(ref,
            WorldCollisionComponent.getComponentType(),
            Objects.requireNonNull(worldCollision, "worldCollision").clone());
        putSpaceSettingsComponents(store,
            ref,
            solverSettings,
            visualSyncSettings,
            visualMaterializationSettings,
            collisionLodSettings,
            extensionSettings);
    }

    public static void putSpaceSettingsComponents(@Nonnull Store<PhysicsStore> store,
        @Nonnull Ref<PhysicsStore> ref,
        @Nonnull SolverSettingsComponent solverSettings,
        @Nonnull VisualSyncSettingsComponent visualSyncSettings,
        @Nonnull VisualMaterializationSettingsComponent visualMaterializationSettings,
        @Nonnull CollisionLodSettingsComponent collisionLodSettings,
        @Nonnull ExtensionSettingsComponent extensionSettings) {
        Objects.requireNonNull(store, "store");
        Objects.requireNonNull(ref, "ref");
        store.putComponent(ref,
            SolverSettingsComponent.getComponentType(),
            Objects.requireNonNull(solverSettings, "solverSettings").clone());
        store.putComponent(ref,
            VisualSyncSettingsComponent.getComponentType(),
            Objects.requireNonNull(visualSyncSettings, "visualSyncSettings").clone());
        store.putComponent(ref,
            VisualMaterializationSettingsComponent.getComponentType(),
            Objects.requireNonNull(visualMaterializationSettings,
                "visualMaterializationSettings").clone());
        store.putComponent(ref,
            CollisionLodSettingsComponent.getComponentType(),
            Objects.requireNonNull(collisionLodSettings, "collisionLodSettings").clone());
        store.putComponent(ref,
            ExtensionSettingsComponent.getComponentType(),
            Objects.requireNonNull(extensionSettings, "extensionSettings").clone());
    }

    public static void putBodyComponents(@Nonnull Store<PhysicsStore> store,
        @Nonnull Ref<PhysicsStore> ref,
        @Nonnull BodyComponent body,
        @Nonnull DynamicsComponent dynamics,
        @Nullable TargetComponent target,
        @Nonnull ColliderComponent collider,
        @Nonnull ShapeComponent shape,
        @Nonnull MaterialComponent material,
        @Nonnull CollisionFilterComponent filter) {
        Objects.requireNonNull(store, "store");
        Objects.requireNonNull(ref, "ref");
        store.putComponent(ref,
            BodyComponent.getComponentType(),
            Objects.requireNonNull(body, "body").clone());
        store.putComponent(ref,
            DynamicsComponent.getComponentType(),
            Objects.requireNonNull(dynamics, "dynamics").clone());
        store.putComponent(ref,
            ColliderComponent.getComponentType(),
            Objects.requireNonNull(collider, "collider").clone());
        store.putComponent(ref,
            ShapeComponent.getComponentType(),
            Objects.requireNonNull(shape, "shape").clone());
        store.putComponent(ref,
            MaterialComponent.getComponentType(),
            Objects.requireNonNull(material, "material").clone());
        store.putComponent(ref,
            CollisionFilterComponent.getComponentType(),
            Objects.requireNonNull(filter, "filter").clone());
        if (target != null) {
            store.putComponent(ref, TargetComponent.getComponentType(), target.clone());
        } else {
            store.removeComponent(ref, TargetComponent.getComponentType());
        }
    }

    public static void putJointComponent(@Nonnull Store<PhysicsStore> store,
        @Nonnull Ref<PhysicsStore> ref,
        @Nonnull JointComponent joint) {
        Objects.requireNonNull(store, "store")
            .putComponent(Objects.requireNonNull(ref, "ref"),
                JointComponent.getComponentType(),
                Objects.requireNonNull(joint, "joint").clone());
    }

    public static void putTerrainColliderComponent(@Nonnull Store<PhysicsStore> store,
        @Nonnull Ref<PhysicsStore> ref,
        @Nonnull TerrainColliderComponent terrainCollider) {
        Objects.requireNonNull(store, "store")
            .putComponent(Objects.requireNonNull(ref, "ref"),
                TerrainColliderComponent.getComponentType(),
                Objects.requireNonNull(terrainCollider, "terrainCollider").clone());
    }
}
