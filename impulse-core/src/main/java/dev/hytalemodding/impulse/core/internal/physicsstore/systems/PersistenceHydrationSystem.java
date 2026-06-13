package dev.hytalemodding.impulse.core.internal.physicsstore.systems;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.api.BackendId;
import dev.hytalemodding.impulse.core.internal.physicsstore.persistence.PersistentBodyDto;
import dev.hytalemodding.impulse.core.internal.physicsstore.persistence.PersistentBodyRuntimeStateDto;
import dev.hytalemodding.impulse.core.internal.physicsstore.persistence.PersistentColliderDto;
import dev.hytalemodding.impulse.core.internal.physicsstore.persistence.PersistentJointDto;
import dev.hytalemodding.impulse.core.internal.physicsstore.persistence.PersistentMaterialDto;
import dev.hytalemodding.impulse.core.internal.physicsstore.persistence.PersistentPhysicsStorePreflight;
import dev.hytalemodding.impulse.core.internal.physicsstore.persistence.PersistentPhysicsStoreResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.persistence.PersistentShapeDto;
import dev.hytalemodding.impulse.core.internal.physicsstore.persistence.PersistentSpaceDto;
import dev.hytalemodding.impulse.core.internal.physicsstore.persistence.PersistentTerrainColliderDto;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsRestoreStatusResource;
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
        PersistentPhysicsStoreResource persistent = store.getResource(
            PersistentPhysicsStoreResource.getResourceType());
        PersistentPhysicsStorePreflight.Result result = persistent.preflight();
        if (!result.valid()) {
            restore.markFailed(String.join("; ", result.errors()));
            return;
        }
        hydrateRows(store, persistent);
        restore.markComplete();
        restore.markHydrated();
    }

    private static void hydrateRows(@Nonnull Store<PhysicsStore> store,
        @Nonnull PersistentPhysicsStoreResource persistent) {
        for (PersistentSpaceDto dto : persistent.getSpaces()) {
            addSpace(store, dto);
        }
        for (PersistentShapeDto dto : persistent.getShapes()) {
            addShape(store, dto);
        }
        for (PersistentMaterialDto dto : persistent.getMaterials()) {
            addMaterial(store, dto);
        }
        for (PersistentBodyDto dto : persistent.getBodies()) {
            addBody(store, dto);
        }
        for (PersistentColliderDto dto : persistent.getColliders()) {
            addCollider(store, dto);
        }
        for (PersistentJointDto dto : persistent.getJoints()) {
            addJoint(store, dto);
        }
        for (PersistentTerrainColliderDto dto : persistent.getTerrainColliders()) {
            addTerrainCollider(store, dto);
        }
    }

    private static void addSpace(@Nonnull Store<PhysicsStore> store,
        @Nonnull PersistentSpaceDto dto) {
        Holder<PhysicsStore> holder = row(store, dto.getSpaceUuid());
        holder.addComponent(SpaceComponent.getComponentType(),
            new SpaceComponent(new BackendId(dto.getBackendId()), dto.getGravity()));
        holder.addComponent(WorldCollisionComponent.getComponentType(),
            dto.getWorldCollision());
        holder.addComponent(SolverSettingsComponent.getComponentType(),
            dto.getSolverSettings());
        holder.addComponent(VisualSyncSettingsComponent.getComponentType(),
            dto.getVisualSyncSettings());
        holder.addComponent(VisualMaterializationSettingsComponent.getComponentType(),
            dto.getVisualMaterializationSettings());
        holder.addComponent(CollisionLodSettingsComponent.getComponentType(),
            dto.getCollisionLodSettings());
        holder.addComponent(ExtensionSettingsComponent.getComponentType(),
            dto.getExtensionSettings());
        add(store, holder);
    }

    private static void addShape(@Nonnull Store<PhysicsStore> store,
        @Nonnull PersistentShapeDto dto) {
        Holder<PhysicsStore> holder = row(store, dto.getShapeUuid());
        holder.addComponent(ShapeComponent.getComponentType(),
            new ShapeComponent(dto.getShapeType(),
                dto.getHalfExtentX(),
                dto.getHalfExtentY(),
                dto.getHalfExtentZ(),
                dto.getRadius(),
                dto.getHalfHeight(),
                dto.getAxis(),
                dto.getGroundY(),
                dto.getResourceKey()));
        add(store, holder);
    }

    private static void addMaterial(@Nonnull Store<PhysicsStore> store,
        @Nonnull PersistentMaterialDto dto) {
        Holder<PhysicsStore> holder = row(store, dto.getMaterialUuid());
        holder.addComponent(MaterialComponent.getComponentType(),
            new MaterialComponent(dto.getFriction(), dto.getRestitution()));
        add(store, holder);
    }

    private static void addBody(@Nonnull Store<PhysicsStore> store,
        @Nonnull PersistentBodyDto dto) {
        Holder<PhysicsStore> holder = row(store, dto.getBodyUuid());
        holder.addComponent(BodyComponent.getComponentType(),
            new BodyComponent(dto.getSpaceUuid(),
                dto.getKind(),
                dto.getPersistenceMode()));
        holder.addComponent(DynamicsComponent.getComponentType(),
            new DynamicsComponent(dto.getBodyType(),
                dto.getMass(),
                dto.getLinearDamping(),
                dto.getAngularDamping(),
                dto.isContinuousCollisionEnabled()));
        holder.addComponent(TargetComponent.getComponentType(),
            inactiveTarget(dto.getRuntimeState()));
        add(store, holder);
    }

    private static void addCollider(@Nonnull Store<PhysicsStore> store,
        @Nonnull PersistentColliderDto dto) {
        Holder<PhysicsStore> holder = row(store, dto.getColliderUuid());
        holder.addComponent(ColliderComponent.getComponentType(),
            new ColliderComponent(dto.getBodyUuid(),
                dto.getShapeUuid(),
                dto.getMaterialUuid(),
                dto.getColliderUuid(),
                dto.getLocalPosition(),
                dto.getLocalRotation(),
                dto.isSensor()));
        holder.addComponent(CollisionFilterComponent.getComponentType(),
            new CollisionFilterComponent(dto.getCollisionGroup(), dto.getCollisionMask()));
        add(store, holder);
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

    private static void addTerrainCollider(@Nonnull Store<PhysicsStore> store,
        @Nonnull PersistentTerrainColliderDto dto) {
        Holder<PhysicsStore> holder = row(store, dto.getTerrainColliderUuid());
        holder.addComponent(TerrainColliderComponent.getComponentType(),
            new TerrainColliderComponent(dto.getSpaceUuid(),
                dto.getSourceKey(),
                dto.getChunkX(),
                dto.getSectionY(),
                dto.getChunkZ(),
                dto.getPayloadResourceKey(),
                dto.isRetained()));
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
