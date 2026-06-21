package dev.hytalemodding.impulse.core.internal.systems;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.api.BackendId;
import dev.hytalemodding.impulse.core.internal.persistence.PersistentBodyDto;
import dev.hytalemodding.impulse.core.internal.persistence.PersistentBodyRuntimeStateDto;
import dev.hytalemodding.impulse.core.internal.persistence.PersistentColliderDto;
import dev.hytalemodding.impulse.core.internal.persistence.PersistentJointDto;
import dev.hytalemodding.impulse.core.internal.persistence.PersistentMaterialDto;
import dev.hytalemodding.impulse.core.internal.persistence.PersistentPhysicsStorePreflight;
import dev.hytalemodding.impulse.core.internal.persistence.PersistentPhysicsStoreResource;
import dev.hytalemodding.impulse.core.internal.persistence.PersistentPhysicsStoreStorage;
import dev.hytalemodding.impulse.core.internal.persistence.PersistentShapeDto;
import dev.hytalemodding.impulse.core.internal.persistence.PersistentSpaceDto;
import dev.hytalemodding.impulse.core.internal.persistence.PhysicsStoreHolderStorage;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsEventResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsIdentityIndexResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsRestoreStatusResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsRuntimeResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsSnapshotResource;
import dev.hytalemodding.impulse.core.plugin.physics.PhysicsEntities;
import dev.hytalemodding.impulse.core.plugin.components.BodyComponent;
import dev.hytalemodding.impulse.core.plugin.components.ColliderComponent;
import dev.hytalemodding.impulse.core.plugin.modules.physicschunk.components.CollisionLodSettingsComponent;
import dev.hytalemodding.impulse.core.plugin.components.CollisionFilterComponent;
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
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nonnull;

/**
 * Rehydrates persisted PhysicsStore DTOs into ECS rows before backend mutation is allowed.
 */
public final class PersistenceHydrationSystem extends TickingSystem<PhysicsStore> {

    private static final Set<Dependency<PhysicsStore>> DEPENDENCIES = Set.of();

    @Override
    public void tick(float dt, int systemIndex, @Nonnull Store<PhysicsStore> store) {
        PhysicsRestoreStatusResource restore = store.getResource(
            PhysicsRestoreStatusResource.getResourceType());
        if (restore.isFailed() || restore.isHydrated()) {
            return;
        }
        try {
            prepareTransientRestoreState(store);
            PhysicsStoreHolderStorage.LoadResult holderLoad = PhysicsStoreHolderStorage.load(store);
            if (holderLoad.present()) {
                restore.markComplete();
                restore.markHydrated();
                return;
            }
        } catch (RuntimeException exception) {
            restore.markFailed(exception.getMessage());
            return;
        }
        PersistentPhysicsStoreStorage.LoadResult persistentLoad;
        try {
            persistentLoad = PersistentPhysicsStoreStorage.load(store);
        } catch (RuntimeException exception) {
            restore.markFailed(exception.getMessage());
            return;
        }
        PersistentPhysicsStoreResource persistent = persistentLoad.resource();
        PersistentPhysicsStorePreflight.Result result = persistent.preflight();
        if (!result.valid()) {
            restore.markFailed(String.join("; ", result.errors()));
            return;
        }
        hydrateRows(store, persistent);
        restore.markComplete();
        restore.markHydrated();
    }

    private static void prepareTransientRestoreState(@Nonnull Store<PhysicsStore> store) {
        store.getResource(PhysicsRuntimeResource.getResourceType()).destroyBackendBindings();
        store.getResource(PhysicsIdentityIndexResource.getResourceType()).clear();
        store.getExternalData().clearUuidIndex();
        store.getResource(PhysicsSnapshotResource.getResourceType()).clear();
        store.getResource(PhysicsEventResource.getResourceType()).clear();
    }

    private static void hydrateRows(@Nonnull Store<PhysicsStore> store,
        @Nonnull PersistentPhysicsStoreResource persistent) {
        for (PersistentSpaceDto dto : persistent.getSpaces()) {
            addSpace(store, dto);
        }
        addBodies(store, persistent);
        for (PersistentJointDto dto : persistent.getJoints()) {
            addJoint(store, dto);
        }
    }

    private static void addSpace(@Nonnull Store<PhysicsStore> store,
        @Nonnull PersistentSpaceDto dto) {
        Holder<PhysicsStore> holder = row(store, dto.getSpaceUuid());
        holder.addComponent(SpaceComponent.getComponentType(),
            new SpaceComponent(new BackendId(dto.getBackendId()), dto.getGravity()));
        addIfNonDefault(holder,
            ChunkCollisionSettingsComponent.getComponentType(),
            dto.getChunkCollisionSettings(),
            dto.getChunkCollisionSettings().isDefault());
        addIfNonDefault(holder,
            MaterialComponent.getComponentType(),
            dto.getChunkCollisionMaterial(),
            dto.isDefaultChunkCollisionMaterial());
        addIfNonDefault(holder,
            CollisionFilterComponent.getComponentType(),
            dto.getChunkCollisionFilter(),
            dto.isDefaultChunkCollisionFilter());
        addIfNonDefault(holder,
            SolverSettingsComponent.getComponentType(),
            dto.getSolverSettings(),
            dto.getSolverSettings().isDefault());
        addIfNonDefault(holder,
            VisualSyncSettingsComponent.getComponentType(),
            dto.getVisualSyncSettings(),
            dto.getVisualSyncSettings().isDefault());
        addIfNonDefault(holder,
            VisualMaterializationSettingsComponent.getComponentType(),
            dto.getVisualMaterializationSettings(),
            dto.getVisualMaterializationSettings().isDefault());
        addIfNonDefault(holder,
            CollisionLodSettingsComponent.getComponentType(),
            dto.getCollisionLodSettings(),
            dto.getCollisionLodSettings().isDefault());
        addIfNonDefault(holder,
            ExtensionSettingsComponent.getComponentType(),
            dto.getExtensionSettings(),
            dto.getExtensionSettings().isDefault());
        add(store, holder);
    }

    private static <T extends Component<PhysicsStore>> void addIfNonDefault(
        @Nonnull Holder<PhysicsStore> holder,
        @Nonnull ComponentType<PhysicsStore, T> componentType,
        @Nonnull T component,
        boolean defaultValue) {
        if (!defaultValue) {
            holder.addComponent(componentType, component);
        }
    }

    private static void addBodies(@Nonnull Store<PhysicsStore> store,
        @Nonnull PersistentPhysicsStoreResource persistent) {
        Map<UUID, PersistentColliderDto> collidersByUuid = new Object2ObjectOpenHashMap<>();
        Map<UUID, PersistentColliderDto> collidersByBodyUuid = new Object2ObjectOpenHashMap<>();
        for (PersistentColliderDto collider : persistent.getColliders()) {
            collidersByUuid.put(collider.getColliderUuid(), collider);
            collidersByBodyUuid.putIfAbsent(collider.getBodyUuid(), collider);
        }
        Map<UUID, PersistentShapeDto> shapesByUuid = new Object2ObjectOpenHashMap<>();
        for (PersistentShapeDto shape : persistent.getShapes()) {
            shapesByUuid.put(shape.getShapeUuid(), shape);
        }
        Map<UUID, PersistentMaterialDto> materialsByUuid = new Object2ObjectOpenHashMap<>();
        for (PersistentMaterialDto material : persistent.getMaterials()) {
            materialsByUuid.put(material.getMaterialUuid(), material);
        }
        for (PersistentBodyDto dto : persistent.getBodies()) {
            addBody(store,
                dto,
                colliderFor(dto, collidersByUuid, collidersByBodyUuid),
                shapesByUuid,
                materialsByUuid);
        }
    }

    private static void addBody(@Nonnull Store<PhysicsStore> store,
        @Nonnull PersistentBodyDto dto,
        PersistentColliderDto collider,
        @Nonnull Map<UUID, PersistentShapeDto> shapesByUuid,
        @Nonnull Map<UUID, PersistentMaterialDto> materialsByUuid) {
        Holder<PhysicsStore> holder = row(store, dto.getBodyUuid());
        BodyComponent body = new BodyComponent(dto.getSpaceUuid());
        DynamicsComponent dynamics = new DynamicsComponent(dto.getBodyType(),
            dto.getMass(),
            dto.getLinearDamping(),
            dto.getAngularDamping(),
            dto.isContinuousCollisionEnabled());
        TargetComponent target = inactiveTarget(dto.getRuntimeState());
        if (collider != null) {
            PersistentShapeDto shape = shapesByUuid.get(collider.getShapeUuid());
            PersistentMaterialDto material = materialsByUuid.get(collider.getMaterialUuid());
            if (shape != null && material != null) {
                PhysicsEntities.addBodyComponents(holder,
                    body,
                    dynamics,
                    target,
                    new ColliderComponent(collider.getLocalPosition(),
                        collider.getLocalRotation(),
                        collider.isSensor()),
                    new ShapeComponent(shape.getShapeType(),
                        shape.getHalfExtentX(),
                        shape.getHalfExtentY(),
                        shape.getHalfExtentZ(),
                        shape.getRadius(),
                        shape.getHalfHeight(),
                        shape.getAxis(),
                        shape.getGroundY(),
                        shape.getResourceKey()),
                    new MaterialComponent(material.getFriction(), material.getRestitution()),
                    new CollisionFilterComponent(collider.getCollisionGroup(),
                        collider.getCollisionMask()));
            } else {
                holder.addComponent(BodyComponent.getComponentType(), body);
                holder.addComponent(DynamicsComponent.getComponentType(), dynamics);
                holder.addComponent(TargetComponent.getComponentType(), target);
            }
        } else {
            holder.addComponent(BodyComponent.getComponentType(), body);
            holder.addComponent(DynamicsComponent.getComponentType(), dynamics);
            holder.addComponent(TargetComponent.getComponentType(), target);
        }
        add(store, holder);
    }

    private static PersistentColliderDto colliderFor(@Nonnull PersistentBodyDto body,
        @Nonnull Map<UUID, PersistentColliderDto> collidersByUuid,
        @Nonnull Map<UUID, PersistentColliderDto> collidersByBodyUuid) {
        for (UUID colliderUuid : body.getColliderUuids()) {
            PersistentColliderDto collider = collidersByUuid.get(colliderUuid);
            if (collider != null) {
                return collider;
            }
        }
        return collidersByBodyUuid.get(body.getBodyUuid());
    }

    private static void addJoint(@Nonnull Store<PhysicsStore> store,
        @Nonnull PersistentJointDto dto) {
        Holder<PhysicsStore> holder = row(store, dto.getJointUuid());
        JointComponent joint = new JointComponent();
        joint.setSpaceUuid(dto.getSpaceUuid());
        joint.setBodyAUuid(dto.getBodyAUuid());
        joint.setBodyBUuid(dto.getBodyBUuid());
        joint.setType(dto.getType());
        joint.setAnchorA(dto.getAnchorA());
        joint.setAnchorB(dto.getAnchorB());
        joint.setAxis(dto.getAxis());
        joint.setLowerLimit(dto.getLowerLimit());
        joint.setUpperLimit(dto.getUpperLimit());
        joint.setEnabled(dto.isEnabled());
        joint.setMotorEnabled(dto.isMotorEnabled());
        joint.setMotorTargetVelocity(dto.getMotorTargetVelocity());
        joint.setMotorMaxForce(dto.getMotorMaxForce());
        joint.setSpringRestLength(dto.getSpringRestLength());
        joint.setSpringStiffness(dto.getSpringStiffness());
        joint.setSpringDamping(dto.getSpringDamping());
        holder.addComponent(JointComponent.getComponentType(), joint);
        add(store, holder);
    }

    @Nonnull
    private static TargetComponent inactiveTarget(@Nonnull PersistentBodyRuntimeStateDto dto) {
        TargetComponent target = new TargetComponent();
        target.setActive(false);
        target.setPosition(dto.getPosition());
        target.setRotation(dto.getRotation());
        target.setLinearVelocity(dto.getLinearVelocity());
        target.setAngularVelocity(dto.getAngularVelocity());
        target.setTransformEnabled(true);
        target.setVelocityEnabled(true);
        target.setActivate(!dto.isSleeping());
        return target;
    }

    @Nonnull
    private static Holder<PhysicsStore> row(@Nonnull Store<PhysicsStore> store,
        @Nonnull UUID uuid) {
        Holder<PhysicsStore> holder = store.getRegistry().newHolder();
        holder.addComponent(UuidComponent.getComponentType(), new UuidComponent(uuid));
        return holder;
    }

    private static void add(@Nonnull Store<PhysicsStore> store,
        @Nonnull Holder<PhysicsStore> holder) {
        store.addEntity(holder, AddReason.LOAD);
    }

    @Nonnull
    @Override
    public Set<Dependency<PhysicsStore>> getDependencies() {
        return DEPENDENCIES;
    }
}
