package dev.hytalemodding.impulse.core.internal.systems;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.QuerySystem;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.api.PhysicsCollisionFilters;
import dev.hytalemodding.impulse.core.internal.persistence.PersistentBodyDto;
import dev.hytalemodding.impulse.core.internal.persistence.PersistentBodyRuntimeStateDto;
import dev.hytalemodding.impulse.core.internal.persistence.PersistentColliderDto;
import dev.hytalemodding.impulse.core.internal.persistence.PersistentJointDto;
import dev.hytalemodding.impulse.core.internal.persistence.PersistentMaterialDto;
import dev.hytalemodding.impulse.core.internal.persistence.PersistentPhysicsStoreResource;
import dev.hytalemodding.impulse.core.internal.persistence.PersistentShapeDto;
import dev.hytalemodding.impulse.core.internal.persistence.PersistentSpaceDto;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsRestoreStatusResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsSnapshotResource;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyPersistenceMode;
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
import dev.hytalemodding.impulse.core.plugin.components.VisualMaterializationSettingsComponent;
import dev.hytalemodding.impulse.core.plugin.components.VisualSyncSettingsComponent;
import dev.hytalemodding.impulse.core.plugin.modules.physicschunk.components.ChunkCollisionSettingsComponent;
import dev.hytalemodding.impulse.core.plugin.modules.physicschunk.settings.PhysicsChunkTerrainSettings;
import dev.hytalemodding.impulse.core.plugin.snapshots.PhysicsBodySnapshot;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Captures serializable PhysicsStore entities into compact DTO resources.
 */
public final class PersistenceCaptureSystem extends TickingSystem<PhysicsStore>
    implements QuerySystem<PhysicsStore> {

    private static final Set<Dependency<PhysicsStore>> DEPENDENCIES = Set.of(
        new SystemDependency<>(Order.AFTER, PhysicsStoreQueuedReadSystem.class)
    );

    @Override
    public void tick(float dt, int systemIndex, @Nonnull Store<PhysicsStore> store) {
        PhysicsRestoreStatusResource restore = store.getResource(
            PhysicsRestoreStatusResource.getResourceType());
        if (restore.isPending() || restore.isFailed()) {
            return;
        }
        Capture capture = new Capture(snapshotBodiesByUuid(store));
        BiConsumer<ArchetypeChunk<PhysicsStore>, CommandBuffer<PhysicsStore>> collector =
            (chunk, _) -> capture.collectChunk(chunk);
        store.forEachChunk(systemIndex, collector);
        capture.writeTo(store.getResource(PersistentPhysicsStoreResource.getResourceType()));
    }

    @Nonnull
    private static Map<UUID, PhysicsBodySnapshot> snapshotBodiesByUuid(
        @Nonnull Store<PhysicsStore> store) {
        PhysicsSnapshotResource snapshots = store.getResource(PhysicsSnapshotResource.getResourceType());
        Map<UUID, PhysicsBodySnapshot> bodies = new Object2ObjectOpenHashMap<>();
        for (PhysicsBodySnapshot body : snapshots.getLatestFrame().bodies()) {
            bodies.put(body.bodyUuid(), body);
        }
        return bodies;
    }

    @Nonnull
    @Override
    public Query<PhysicsStore> getQuery() {
        return PhysicsStoreSystemSupport.uuidQuery();
    }

    @Nonnull
    @Override
    public Set<Dependency<PhysicsStore>> getDependencies() {
        return DEPENDENCIES;
    }

    private static final class Capture {

        @Nonnull
        private final Map<UUID, PhysicsBodySnapshot> snapshotsByBodyUuid;
        @Nonnull
        private final List<SpaceRow> spaceRows = new ArrayList<>();
        @Nonnull
        private final List<BodyRow> bodyRows = new ArrayList<>();
        @Nonnull
        private final List<JointRow> jointRows = new ArrayList<>();

        private Capture(@Nonnull Map<UUID, PhysicsBodySnapshot> snapshotsByBodyUuid) {
            this.snapshotsByBodyUuid = snapshotsByBodyUuid;
        }

        private void collectChunk(@Nonnull ArchetypeChunk<PhysicsStore> chunk) {
            for (int index = 0; index < chunk.size(); index++) {
                UUID uuid = PhysicsStoreSystemSupport.rowUuid(chunk, index);
                if (PhysicsStoreSystemSupport.isNil(uuid)) {
                    continue;
                }
                collectRow(uuid, chunk, index);
            }
        }

        private void collectRow(@Nonnull UUID uuid,
            @Nonnull ArchetypeChunk<PhysicsStore> chunk,
            int index) {
            SpaceComponent space = chunk.getComponent(index, SpaceComponent.getComponentType());
            if (space != null) {
                spaceRows.add(new SpaceRow(uuid,
                    space,
                    chunk.getComponent(index, ChunkCollisionSettingsComponent.getComponentType()),
                    chunk.getComponent(index, MaterialComponent.getComponentType()),
                    chunk.getComponent(index, CollisionFilterComponent.getComponentType()),
                    chunk.getComponent(index, SolverSettingsComponent.getComponentType()),
                    chunk.getComponent(index, VisualSyncSettingsComponent.getComponentType()),
                    chunk.getComponent(index,
                        VisualMaterializationSettingsComponent.getComponentType()),
                    chunk.getComponent(index, CollisionLodSettingsComponent.getComponentType()),
                    chunk.getComponent(index, ExtensionSettingsComponent.getComponentType())));
            }
            BodyComponent body = chunk.getComponent(index, BodyComponent.getComponentType());
            if (body != null) {
                bodyRows.add(new BodyRow(uuid,
                    body,
                    chunk.getComponent(index, DynamicsComponent.getComponentType()),
                    chunk.getComponent(index, TargetComponent.getComponentType()),
                    chunk.getComponent(index, ColliderComponent.getComponentType()),
                    chunk.getComponent(index, ShapeComponent.getComponentType()),
                    chunk.getComponent(index, MaterialComponent.getComponentType()),
                    chunk.getComponent(index, CollisionFilterComponent.getComponentType())));
            }
            JointComponent joint = chunk.getComponent(index, JointComponent.getComponentType());
            if (joint != null) {
                jointRows.add(new JointRow(uuid, joint));
            }
        }

        private void writeTo(@Nonnull PersistentPhysicsStoreResource persistent) {
            ObjectOpenHashSet<UUID> bodyUuids = persistentBodyUuids();

            persistent.setSpaces(spaceDtos());
            persistent.setBodies(bodyDtos());
            persistent.setColliders(colliderDtos(bodyUuids));
            persistent.setShapes(shapeDtos(bodyUuids));
            persistent.setMaterials(materialDtos(bodyUuids));
            persistent.setJoints(jointDtos(bodyUuids));
        }

        @Nonnull
        private ObjectOpenHashSet<UUID> persistentBodyUuids() {
            ObjectOpenHashSet<UUID> bodyUuids = new ObjectOpenHashSet<>();
            for (BodyRow row : bodyRows) {
                if (row.body().getPersistenceMode() == PhysicsBodyPersistenceMode.PERSISTENT
                    && row.hasAggregateCollider()) {
                    bodyUuids.add(row.uuid());
                }
            }
            return bodyUuids;
        }

        @Nonnull
        private PersistentSpaceDto[] spaceDtos() {
            return spaceRows.stream()
                .map(this::spaceDto)
                .sorted(Comparator.comparing(PersistentSpaceDto::getSpaceUuid))
                .toArray(PersistentSpaceDto[]::new);
        }

        @Nonnull
        private PersistentSpaceDto spaceDto(@Nonnull SpaceRow row) {
            ChunkCollisionSettingsComponent chunkCollision = row.chunkCollisionSettings() != null
                ? row.chunkCollisionSettings()
                : new ChunkCollisionSettingsComponent();
            MaterialComponent material = row.material() != null
                ? row.material()
                : new MaterialComponent(PhysicsChunkTerrainSettings.DEFAULT_CHUNK_COLLISION_FRICTION,
                    PhysicsChunkTerrainSettings.DEFAULT_CHUNK_COLLISION_RESTITUTION);
            CollisionFilterComponent filter = row.filter() != null
                ? row.filter()
                : new CollisionFilterComponent(PhysicsCollisionFilters.TERRAIN,
                    PhysicsCollisionFilters.ALL);
            return new PersistentSpaceDto(row.uuid(),
                row.space().getBackendIdValue(),
                row.space().getGravity(),
                chunkCollision.getMode(),
                chunkCollision.getEntityChunkBoundaryMode(),
                chunkCollision.isNativeVoxelCollisionEnabled(),
                chunkCollision.getRadius(),
                chunkCollision.getBodyRadius(),
                chunkCollision.getTtlTicks(),
                material.getFriction(),
                material.getRestitution(),
                filter.getCollisionGroup(),
                filter.getCollisionMask(),
                row.solverSettings() != null
                    ? row.solverSettings()
                    : new SolverSettingsComponent(),
                row.visualSyncSettings() != null
                    ? row.visualSyncSettings()
                    : new VisualSyncSettingsComponent(),
                row.visualMaterializationSettings() != null
                    ? row.visualMaterializationSettings()
                    : new VisualMaterializationSettingsComponent(),
                row.collisionLodSettings() != null
                    ? row.collisionLodSettings()
                    : new CollisionLodSettingsComponent(),
                row.extensionSettings() != null
                    ? row.extensionSettings()
                    : new ExtensionSettingsComponent());
        }

        @Nonnull
        private PersistentBodyDto[] bodyDtos() {
            return bodyRows.stream()
                .filter(row -> row.body().getPersistenceMode() == PhysicsBodyPersistenceMode.PERSISTENT)
                .filter(BodyRow::hasAggregateCollider)
                .map(this::bodyDto)
                .sorted(Comparator.comparing(PersistentBodyDto::getBodyUuid))
                .toArray(PersistentBodyDto[]::new);
        }

        @Nonnull
        private PersistentBodyDto bodyDto(@Nonnull BodyRow row) {
            DynamicsComponent dynamics = row.dynamics() != null
                ? row.dynamics()
                : new DynamicsComponent();
            return new PersistentBodyDto(row.uuid(),
                row.body().getSpaceUuid(),
                row.body().getKind(),
                row.body().getPersistenceMode(),
                dynamics.getBodyType(),
                dynamics.getMass(),
                dynamics.getLinearDamping(),
                dynamics.getAngularDamping(),
                dynamics.isContinuousCollisionEnabled(),
                new UUID[] {row.uuid()},
                runtimeState(row.uuid(), row.target()));
        }

        @Nonnull
        private PersistentBodyRuntimeStateDto runtimeState(@Nonnull UUID bodyUuid,
            @Nullable TargetComponent target) {
            PhysicsBodySnapshot snapshot = snapshotsByBodyUuid.get(bodyUuid);
            if (snapshot != null) {
                return new PersistentBodyRuntimeStateDto(snapshot.position(),
                    snapshot.rotation(),
                    snapshot.linearVelocity(),
                    snapshot.angularVelocity(),
                    snapshot.sleeping());
            }
            if (target != null) {
                return new PersistentBodyRuntimeStateDto(target.getPosition(),
                    target.getRotation(),
                    target.getLinearVelocity(),
                    target.getAngularVelocity(),
                    !target.isActive() && !target.isActivate());
            }
            return new PersistentBodyRuntimeStateDto(new Vector3f(),
                new Quaternionf(),
                new Vector3f(),
                new Vector3f(),
                false);
        }

        @Nonnull
        private PersistentColliderDto[] colliderDtos(@Nonnull Set<UUID> bodyUuids) {
            return bodyRows.stream()
                .filter(row -> bodyUuids.contains(row.uuid()))
                .filter(BodyRow::hasAggregateCollider)
                .map(this::colliderDto)
                .sorted(Comparator.comparing(PersistentColliderDto::getColliderUuid))
                .toArray(PersistentColliderDto[]::new);
        }

        @Nonnull
        private PersistentColliderDto colliderDto(@Nonnull BodyRow row) {
            CollisionFilterComponent resolvedFilter = row.filter() != null
                ? row.filter()
                : new CollisionFilterComponent();
            return new PersistentColliderDto(row.uuid(),
                row.uuid(),
                row.uuid(),
                row.uuid(),
                row.collider().getLocalPosition(),
                row.collider().getLocalRotation(),
                row.collider().isSensor(),
                resolvedFilter.getCollisionGroup(),
                resolvedFilter.getCollisionMask());
        }

        @Nonnull
        private PersistentShapeDto[] shapeDtos(@Nonnull Set<UUID> bodyUuids) {
            return bodyRows.stream()
                .filter(row -> bodyUuids.contains(row.uuid()))
                .filter(BodyRow::hasAggregateCollider)
                .map(row -> new PersistentShapeDto(row.uuid(),
                    row.shape().getShapeType(),
                    row.shape().getHalfExtentX(),
                    row.shape().getHalfExtentY(),
                    row.shape().getHalfExtentZ(),
                    row.shape().getRadius(),
                    row.shape().getHalfHeight(),
                    row.shape().getAxis(),
                    row.shape().getGroundY(),
                    row.shape().getResourceKey()))
                .sorted(Comparator.comparing(PersistentShapeDto::getShapeUuid))
                .toArray(PersistentShapeDto[]::new);
        }

        @Nonnull
        private PersistentMaterialDto[] materialDtos(@Nonnull Set<UUID> bodyUuids) {
            return bodyRows.stream()
                .filter(row -> bodyUuids.contains(row.uuid()))
                .filter(BodyRow::hasAggregateCollider)
                .map(row -> new PersistentMaterialDto(row.uuid(),
                    row.material().getFriction(),
                    row.material().getRestitution()))
                .sorted(Comparator.comparing(PersistentMaterialDto::getMaterialUuid))
                .toArray(PersistentMaterialDto[]::new);
        }

        @Nonnull
        private PersistentJointDto[] jointDtos(@Nonnull Set<UUID> bodyUuids) {
            return jointRows.stream()
                .filter(row -> bodyUuids.contains(row.joint().getBodyAUuid()))
                .filter(row -> bodyUuids.contains(row.joint().getBodyBUuid()))
                .map(row -> new PersistentJointDto(row.uuid(),
                    row.joint().getSpaceUuid(),
                    row.joint().getBodyAUuid(),
                    row.joint().getBodyBUuid(),
                    row.joint().getType(),
                    row.joint().getAnchorA(),
                    row.joint().getAnchorB(),
                    row.joint().getAxis(),
                    row.joint().getLowerLimit(),
                    row.joint().getUpperLimit(),
                    row.joint().isEnabled(),
                    row.joint().isMotorEnabled(),
                    row.joint().getMotorTargetVelocity(),
                    row.joint().getMotorMaxForce(),
                    row.joint().getSpringRestLength(),
                    row.joint().getSpringStiffness(),
                    row.joint().getSpringDamping()))
                .sorted(Comparator.comparing(PersistentJointDto::getJointUuid))
                .toArray(PersistentJointDto[]::new);
        }

    }

    private record SpaceRow(@Nonnull UUID uuid,
                            @Nonnull SpaceComponent space,
                            @Nullable ChunkCollisionSettingsComponent chunkCollisionSettings,
                            @Nullable MaterialComponent material,
                            @Nullable CollisionFilterComponent filter,
                            @Nullable SolverSettingsComponent solverSettings,
                            @Nullable VisualSyncSettingsComponent visualSyncSettings,
                            @Nullable VisualMaterializationSettingsComponent visualMaterializationSettings,
                            @Nullable CollisionLodSettingsComponent collisionLodSettings,
                            @Nullable ExtensionSettingsComponent extensionSettings) {
    }

    private record BodyRow(@Nonnull UUID uuid,
                           @Nonnull BodyComponent body,
                           @Nullable DynamicsComponent dynamics,
                           @Nullable TargetComponent target,
                           @Nullable ColliderComponent collider,
                           @Nullable ShapeComponent shape,
                           @Nullable MaterialComponent material,
                           @Nullable CollisionFilterComponent filter) {

        private boolean hasAggregateCollider() {
            return collider != null && shape != null && material != null && filter != null;
        }
    }

    private record JointRow(@Nonnull UUID uuid, @Nonnull JointComponent joint) {
    }
}
