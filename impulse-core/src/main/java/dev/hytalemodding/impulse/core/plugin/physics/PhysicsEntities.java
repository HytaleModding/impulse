package dev.hytalemodding.impulse.core.plugin.physics;

import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.core.plugin.components.BodyComponent;
import dev.hytalemodding.impulse.core.plugin.components.ColliderComponent;
import dev.hytalemodding.impulse.core.plugin.components.CollisionFilterComponent;
import dev.hytalemodding.impulse.core.plugin.modules.physicschunk.components.CollisionLodSettingsComponent;
import dev.hytalemodding.impulse.core.plugin.components.DynamicsComponent;
import dev.hytalemodding.impulse.core.plugin.components.ExtensionSettingsComponent;
import dev.hytalemodding.impulse.core.plugin.components.JointComponent;
import dev.hytalemodding.impulse.core.plugin.components.MaterialComponent;
import dev.hytalemodding.impulse.core.plugin.components.ShapeComponent;
import dev.hytalemodding.impulse.core.plugin.components.SolverSettingsComponent;
import dev.hytalemodding.impulse.core.plugin.components.SpaceComponent;
import dev.hytalemodding.impulse.core.plugin.components.TargetComponent;
import dev.hytalemodding.impulse.core.plugin.components.UuidComponent;
import dev.hytalemodding.impulse.core.plugin.modules.physicsentity.components.VisualMaterializationSettingsComponent;
import dev.hytalemodding.impulse.core.plugin.modules.physicsentity.components.VisualSyncSettingsComponent;
import dev.hytalemodding.impulse.core.plugin.modules.physicschunk.components.ChunkCollisionSettingsComponent;
import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Holder/component helpers for authoritative PhysicsStore aggregate entities.
 */
public final class PhysicsEntities {

    private PhysicsEntities() {
    }

    @Nonnull
    public static Holder<PhysicsStore> entityHolder(@Nonnull Store<PhysicsStore> store,
        @Nonnull UUID entityUuid) {
        Holder<PhysicsStore> holder = store.getRegistry().newHolder();
        addUuid(holder, entityUuid);
        return holder;
    }

    public static void addUuid(@Nonnull Holder<PhysicsStore> holder,
        @Nonnull UUID entityUuid) {
        Objects.requireNonNull(holder, "holder")
            .addComponent(UuidComponent.getComponentType(),
                new UuidComponent(Objects.requireNonNull(entityUuid, "entityUuid")));
    }

    @Nullable
    public static Ref<PhysicsStore> resolveRef(@Nonnull Store<PhysicsStore> store,
        @Nonnull UUID entityUuid) {
        Store<PhysicsStore> checkedStore = Objects.requireNonNull(store, "store");
        PhysicsThreading.requireWorldThread(checkedStore, "resolve a PhysicsStore entity ref");
        Ref<PhysicsStore> ref = checkedStore.getExternalData()
            .getRefFromUUID(Objects.requireNonNull(entityUuid, "entityUuid"));
        return ref != null && ref.getStore() == checkedStore && ref.isValid() ? ref : null;
    }

    @Nonnull
    public static Holder<PhysicsStore> spaceHolder(@Nonnull Store<PhysicsStore> store,
        @Nonnull UUID spaceUuid,
        @Nonnull SpaceComponent space) {
        Holder<PhysicsStore> holder = entityHolder(store, spaceUuid);
        addSpaceComponent(holder, space);
        return holder;
    }

    @Nonnull
    public static Holder<PhysicsStore> spaceHolder(@Nonnull Store<PhysicsStore> store,
        @Nonnull UUID spaceUuid,
        @Nonnull SpaceComponent space,
        @Nonnull ChunkCollisionSettingsComponent chunkCollisionSettings,
        @Nonnull SolverSettingsComponent solverSettings,
        @Nonnull VisualSyncSettingsComponent visualSyncSettings,
        @Nonnull VisualMaterializationSettingsComponent visualMaterializationSettings,
        @Nonnull CollisionLodSettingsComponent collisionLodSettings,
        @Nonnull ExtensionSettingsComponent extensionSettings) {
        Holder<PhysicsStore> holder = entityHolder(store, spaceUuid);
        addSpaceComponents(holder,
            space,
            chunkCollisionSettings,
            solverSettings,
            visualSyncSettings,
            visualMaterializationSettings,
            collisionLodSettings,
            extensionSettings);
        return holder;
    }

    public static void addSpaceComponent(@Nonnull Holder<PhysicsStore> holder,
        @Nonnull SpaceComponent space) {
        Objects.requireNonNull(holder, "holder")
            .addComponent(SpaceComponent.getComponentType(),
                Objects.requireNonNull(space, "space").clone());
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
        Holder<PhysicsStore> holder = entityHolder(store, bodyUuid);
        addBodyComponents(holder, body, dynamics, target, collider, shape, material, filter);
        return holder;
    }

    @Nonnull
    public static Holder<PhysicsStore> jointHolder(@Nonnull Store<PhysicsStore> store,
        @Nonnull UUID jointUuid,
        @Nonnull JointComponent joint) {
        Holder<PhysicsStore> holder = entityHolder(store, jointUuid);
        holder.addComponent(JointComponent.getComponentType(),
            Objects.requireNonNull(joint, "joint").clone());
        return holder;
    }

    public static void addSpaceComponents(@Nonnull Holder<PhysicsStore> holder,
        @Nonnull SpaceComponent space,
        @Nonnull ChunkCollisionSettingsComponent chunkCollisionSettings,
        @Nonnull SolverSettingsComponent solverSettings,
        @Nonnull VisualSyncSettingsComponent visualSyncSettings,
        @Nonnull VisualMaterializationSettingsComponent visualMaterializationSettings,
        @Nonnull CollisionLodSettingsComponent collisionLodSettings,
        @Nonnull ExtensionSettingsComponent extensionSettings) {
        addSpaceComponent(holder, space);
        addChunkCollisionSettingsComponent(holder, chunkCollisionSettings);
        addSpaceSettingsComponents(holder,
            solverSettings,
            visualSyncSettings,
            visualMaterializationSettings,
            collisionLodSettings,
            extensionSettings);
    }

    public static void addChunkCollisionSettingsComponent(@Nonnull Holder<PhysicsStore> holder,
        @Nonnull ChunkCollisionSettingsComponent chunkCollisionSettings) {
        ChunkCollisionSettingsComponent component =
            Objects.requireNonNull(chunkCollisionSettings, "chunkCollisionSettings");
        addIfNonDefault(holder,
            ChunkCollisionSettingsComponent.getComponentType(),
            component,
            component.isDefault());
    }

    public static void addSpaceSettingsComponents(@Nonnull Holder<PhysicsStore> holder,
        @Nonnull SolverSettingsComponent solverSettings,
        @Nonnull VisualSyncSettingsComponent visualSyncSettings,
        @Nonnull VisualMaterializationSettingsComponent visualMaterializationSettings,
        @Nonnull CollisionLodSettingsComponent collisionLodSettings,
        @Nonnull ExtensionSettingsComponent extensionSettings) {
        SolverSettingsComponent solver = Objects.requireNonNull(solverSettings,
            "solverSettings");
        addIfNonDefault(holder,
            SolverSettingsComponent.getComponentType(),
            solver,
            solver.isDefault());
        VisualSyncSettingsComponent visualSync = Objects.requireNonNull(visualSyncSettings,
            "visualSyncSettings");
        addIfNonDefault(holder,
            VisualSyncSettingsComponent.getComponentType(),
            visualSync,
            visualSync.isDefault());
        VisualMaterializationSettingsComponent visualMaterialization = Objects.requireNonNull(
            visualMaterializationSettings,
            "visualMaterializationSettings");
        addIfNonDefault(holder,
            VisualMaterializationSettingsComponent.getComponentType(),
            visualMaterialization,
            visualMaterialization.isDefault());
        CollisionLodSettingsComponent collisionLod = Objects.requireNonNull(collisionLodSettings,
            "collisionLodSettings");
        addIfNonDefault(holder,
            CollisionLodSettingsComponent.getComponentType(),
            collisionLod,
            collisionLod.isDefault());
        ExtensionSettingsComponent extension = Objects.requireNonNull(extensionSettings,
            "extensionSettings");
        addIfNonDefault(holder,
            ExtensionSettingsComponent.getComponentType(),
            extension,
            extension.isDefault());
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
        @Nonnull ChunkCollisionSettingsComponent chunkCollisionSettings,
        @Nonnull SolverSettingsComponent solverSettings,
        @Nonnull VisualSyncSettingsComponent visualSyncSettings,
        @Nonnull VisualMaterializationSettingsComponent visualMaterializationSettings,
        @Nonnull CollisionLodSettingsComponent collisionLodSettings,
        @Nonnull ExtensionSettingsComponent extensionSettings) {
        Store<PhysicsStore> checkedStore = Objects.requireNonNull(store, "store");
        PhysicsThreading.requireWorldThread(checkedStore, "put PhysicsStore space components");
        Objects.requireNonNull(ref, "ref");
        checkedStore.putComponent(ref,
            SpaceComponent.getComponentType(),
            Objects.requireNonNull(space, "space").clone());
        putChunkCollisionSettingsComponent(checkedStore, ref, chunkCollisionSettings);
        putSpaceSettingsComponents(checkedStore,
            ref,
            solverSettings,
            visualSyncSettings,
            visualMaterializationSettings,
            collisionLodSettings,
            extensionSettings);
    }

    public static void putChunkCollisionSettingsComponent(@Nonnull Store<PhysicsStore> store,
        @Nonnull Ref<PhysicsStore> ref,
        @Nonnull ChunkCollisionSettingsComponent chunkCollisionSettings) {
        Store<PhysicsStore> checkedStore = Objects.requireNonNull(store, "store");
        PhysicsThreading.requireWorldThread(checkedStore,
            "put PhysicsStore chunk collision settings component");
        ChunkCollisionSettingsComponent component =
            Objects.requireNonNull(chunkCollisionSettings, "chunkCollisionSettings");
        putOrRemoveDefault(checkedStore,
            Objects.requireNonNull(ref, "ref"),
            ChunkCollisionSettingsComponent.getComponentType(),
            component,
            component.isDefault());
    }

    public static void putSpaceSettingsComponents(@Nonnull Store<PhysicsStore> store,
        @Nonnull Ref<PhysicsStore> ref,
        @Nonnull SolverSettingsComponent solverSettings,
        @Nonnull VisualSyncSettingsComponent visualSyncSettings,
        @Nonnull VisualMaterializationSettingsComponent visualMaterializationSettings,
        @Nonnull CollisionLodSettingsComponent collisionLodSettings,
        @Nonnull ExtensionSettingsComponent extensionSettings) {
        Store<PhysicsStore> checkedStore = Objects.requireNonNull(store, "store");
        PhysicsThreading.requireWorldThread(checkedStore,
            "put PhysicsStore space settings components");
        Objects.requireNonNull(ref, "ref");
        SolverSettingsComponent solver = Objects.requireNonNull(solverSettings,
            "solverSettings");
        putOrRemoveDefault(checkedStore,
            ref,
            SolverSettingsComponent.getComponentType(),
            solver,
            solver.isDefault());
        VisualSyncSettingsComponent visualSync = Objects.requireNonNull(visualSyncSettings,
            "visualSyncSettings");
        putOrRemoveDefault(checkedStore,
            ref,
            VisualSyncSettingsComponent.getComponentType(),
            visualSync,
            visualSync.isDefault());
        VisualMaterializationSettingsComponent visualMaterialization = Objects.requireNonNull(
            visualMaterializationSettings,
            "visualMaterializationSettings");
        putOrRemoveDefault(checkedStore,
            ref,
            VisualMaterializationSettingsComponent.getComponentType(),
            visualMaterialization,
            visualMaterialization.isDefault());
        CollisionLodSettingsComponent collisionLod = Objects.requireNonNull(collisionLodSettings,
            "collisionLodSettings");
        putOrRemoveDefault(checkedStore,
            ref,
            CollisionLodSettingsComponent.getComponentType(),
            collisionLod,
            collisionLod.isDefault());
        ExtensionSettingsComponent extension = Objects.requireNonNull(extensionSettings,
            "extensionSettings");
        putOrRemoveDefault(checkedStore,
            ref,
            ExtensionSettingsComponent.getComponentType(),
            extension,
            extension.isDefault());
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
        Store<PhysicsStore> checkedStore = Objects.requireNonNull(store, "store");
        PhysicsThreading.requireWorldThread(checkedStore, "put PhysicsStore body components");
        Objects.requireNonNull(ref, "ref");
        checkedStore.putComponent(ref,
            BodyComponent.getComponentType(),
            Objects.requireNonNull(body, "body").clone());
        checkedStore.putComponent(ref,
            DynamicsComponent.getComponentType(),
            Objects.requireNonNull(dynamics, "dynamics").clone());
        checkedStore.putComponent(ref,
            ColliderComponent.getComponentType(),
            Objects.requireNonNull(collider, "collider").clone());
        checkedStore.putComponent(ref,
            ShapeComponent.getComponentType(),
            Objects.requireNonNull(shape, "shape").clone());
        checkedStore.putComponent(ref,
            MaterialComponent.getComponentType(),
            Objects.requireNonNull(material, "material").clone());
        checkedStore.putComponent(ref,
            CollisionFilterComponent.getComponentType(),
            Objects.requireNonNull(filter, "filter").clone());
        if (target != null) {
            checkedStore.putComponent(ref, TargetComponent.getComponentType(), target.clone());
        } else {
            checkedStore.removeComponent(ref, TargetComponent.getComponentType());
        }
    }

    public static void putJointComponent(@Nonnull Store<PhysicsStore> store,
        @Nonnull Ref<PhysicsStore> ref,
        @Nonnull JointComponent joint) {
        Store<PhysicsStore> checkedStore = Objects.requireNonNull(store, "store");
        PhysicsThreading.requireWorldThread(checkedStore, "put PhysicsStore joint component");
        checkedStore
            .putComponent(Objects.requireNonNull(ref, "ref"),
                JointComponent.getComponentType(),
                Objects.requireNonNull(joint, "joint").clone());
    }

    private static <T extends Component<PhysicsStore>> void addIfNonDefault(
        @Nonnull Holder<PhysicsStore> holder,
        @Nonnull ComponentType<PhysicsStore, T> componentType,
        @Nonnull T component,
        boolean defaultValue) {
        if (!defaultValue) {
            Objects.requireNonNull(holder, "holder").addComponent(componentType, copy(component));
        }
    }

    private static <T extends Component<PhysicsStore>> void putOrRemoveDefault(
        @Nonnull Store<PhysicsStore> store,
        @Nonnull Ref<PhysicsStore> ref,
        @Nonnull ComponentType<PhysicsStore, T> componentType,
        @Nonnull T component,
        boolean defaultValue) {
        if (defaultValue) {
            store.removeComponentIfExists(ref, componentType);
        } else {
            store.putComponent(ref, componentType, copy(component));
        }
    }

    @Nonnull
    @SuppressWarnings("unchecked")
    private static <T extends Component<PhysicsStore>> T copy(@Nonnull T component) {
        return (T) component.clone();
    }

}
