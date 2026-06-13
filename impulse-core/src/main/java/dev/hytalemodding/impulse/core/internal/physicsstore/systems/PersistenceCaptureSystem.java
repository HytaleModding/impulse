package dev.hytalemodding.impulse.core.internal.physicsstore.systems;

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
import dev.hytalemodding.impulse.core.internal.physicsstore.persistence.PersistentBodyDto;
import dev.hytalemodding.impulse.core.internal.physicsstore.persistence.PersistentBodyRuntimeStateDto;
import dev.hytalemodding.impulse.core.internal.physicsstore.persistence.PersistentColliderDto;
import dev.hytalemodding.impulse.core.internal.physicsstore.persistence.PersistentJointDto;
import dev.hytalemodding.impulse.core.internal.physicsstore.persistence.PersistentMaterialDto;
import dev.hytalemodding.impulse.core.internal.physicsstore.persistence.PersistentPhysicsStoreResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.persistence.PersistentShapeDto;
import dev.hytalemodding.impulse.core.internal.physicsstore.persistence.PersistentSpaceDto;
import dev.hytalemodding.impulse.core.internal.physicsstore.persistence.PersistentTerrainColliderDto;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsRestoreStatusResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsSnapshotResource;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyPersistenceMode;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.BodyComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.ColliderComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.CollisionFilterComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.DynamicsComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.JointComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.MaterialComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.ShapeComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.SpaceComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.TargetComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.TerrainColliderComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.WorldCollisionComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.snapshots.PhysicsStoreBodySnapshot;
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
 * Captures serializable PhysicsStore rows into compact DTO resources.
 */
public final class PersistenceCaptureSystem extends TickingSystem<PhysicsStore>
    implements QuerySystem<PhysicsStore> {

    private static final Set<Dependency<PhysicsStore>> DEPENDENCIES = Set.of(
        new SystemDependency<>(Order.AFTER, CompletedStepPublicationSystem.class),
        new SystemDependency<>(Order.BEFORE, StepSubmissionSystem.class)
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
    private static Map<UUID, PhysicsStoreBodySnapshot> snapshotBodiesByUuid(
        @Nonnull Store<PhysicsStore> store) {
        PhysicsSnapshotResource snapshots = store.getResource(PhysicsSnapshotResource.getResourceType());
        Map<UUID, PhysicsStoreBodySnapshot> bodies = new Object2ObjectOpenHashMap<>();
        for (PhysicsStoreBodySnapshot body : snapshots.getLatestFrame().bodies()) {
            bodies.put(body.bodyUuid(), body);
        }
        return bodies;
    }

    @Nonnull
    @Override
    public Query<PhysicsStore> getQuery() {
        return PhysicsStoreSystemSupport.UUID_QUERY;
    }

    @Nonnull
    @Override
    public Set<Dependency<PhysicsStore>> getDependencies() {
        return DEPENDENCIES;
    }

    private static final class Capture {

        @Nonnull
        private final Map<UUID, PhysicsStoreBodySnapshot> snapshotsByBodyUuid;
        @Nonnull
        private final List<SpaceRow> spaceRows = new ArrayList<>();
        @Nonnull
        private final List<BodyRow> bodyRows = new ArrayList<>();
        @Nonnull
        private final List<ColliderRow> colliderRows = new ArrayList<>();
        @Nonnull
        private final List<ShapeRow> shapeRows = new ArrayList<>();
        @Nonnull
        private final List<MaterialRow> materialRows = new ArrayList<>();
        @Nonnull
        private final List<JointRow> jointRows = new ArrayList<>();
        @Nonnull
        private final List<TerrainColliderRow> terrainRows = new ArrayList<>();
        @Nonnull
        private final Map<UUID, CollisionFilterComponent> filtersByUuid =
            new Object2ObjectOpenHashMap<>();

        private Capture(@Nonnull Map<UUID, PhysicsStoreBodySnapshot> snapshotsByBodyUuid) {
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
                    chunk.getComponent(index, WorldCollisionComponent.getComponentType())));
            }
            BodyComponent body = chunk.getComponent(index, BodyComponent.getComponentType());
            if (body != null) {
                bodyRows.add(new BodyRow(uuid,
                    body,
                    chunk.getComponent(index, DynamicsComponent.getComponentType()),
                    chunk.getComponent(index, TargetComponent.getComponentType())));
            }
            ColliderComponent collider = chunk.getComponent(index, ColliderComponent.getComponentType());
            if (collider != null) {
                colliderRows.add(new ColliderRow(uuid, collider));
            }
            ShapeComponent shape = chunk.getComponent(index, ShapeComponent.getComponentType());
            if (shape != null) {
                shapeRows.add(new ShapeRow(uuid, shape));
            }
            MaterialComponent material = chunk.getComponent(index, MaterialComponent.getComponentType());
            if (material != null) {
                materialRows.add(new MaterialRow(uuid, material));
            }
            CollisionFilterComponent filter = chunk.getComponent(index,
                CollisionFilterComponent.getComponentType());
            if (filter != null) {
                filtersByUuid.put(uuid, filter);
            }
            JointComponent joint = chunk.getComponent(index, JointComponent.getComponentType());
            if (joint != null) {
                jointRows.add(new JointRow(uuid, joint));
            }
            TerrainColliderComponent terrain = chunk.getComponent(index,
                TerrainColliderComponent.getComponentType());
            if (terrain != null) {
                terrainRows.add(new TerrainColliderRow(uuid, terrain));
            }
        }

        private void writeTo(@Nonnull PersistentPhysicsStoreResource persistent) {
            ObjectOpenHashSet<UUID> bodyUuids = persistentBodyUuids();
            ObjectOpenHashSet<UUID> shapeUuids = new ObjectOpenHashSet<>();
            ObjectOpenHashSet<UUID> materialUuids = new ObjectOpenHashSet<>();
            Map<UUID, List<UUID>> colliderUuidsByBodyUuid =
                new Object2ObjectOpenHashMap<>();

            for (ColliderRow row : colliderRows) {
                if (!bodyUuids.contains(row.collider().getBodyUuid())) {
                    continue;
                }
                colliderUuidsByBodyUuid
                    .computeIfAbsent(row.collider().getBodyUuid(), _ -> new ArrayList<>())
                    .add(row.uuid());
                shapeUuids.add(row.collider().getShapeUuid());
                materialUuids.add(row.collider().getMaterialUuid());
            }

            persistent.setSpaces(spaceDtos());
            persistent.setBodies(bodyDtos(colliderUuidsByBodyUuid));
            persistent.setColliders(colliderDtos(bodyUuids));
            persistent.setShapes(shapeDtos(shapeUuids));
            persistent.setMaterials(materialDtos(materialUuids));
            persistent.setJoints(jointDtos(bodyUuids));
            persistent.setTerrainColliders(terrainDtos());
        }

        @Nonnull
        private ObjectOpenHashSet<UUID> persistentBodyUuids() {
            ObjectOpenHashSet<UUID> bodyUuids = new ObjectOpenHashSet<>();
            for (BodyRow row : bodyRows) {
                if (row.body().getPersistenceMode() == PhysicsBodyPersistenceMode.PERSISTENT) {
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
            WorldCollisionComponent worldCollision = row.worldCollision() != null
                ? row.worldCollision()
                : new WorldCollisionComponent();
            return new PersistentSpaceDto(row.uuid(),
                row.space().getBackendIdValue(),
                row.space().getGravity(),
                worldCollision.getMode(),
                worldCollision.isNativeVoxelTerrainEnabled(),
                worldCollision.getRadius(),
                worldCollision.getBodyRadius(),
                worldCollision.getTtlTicks(),
                worldCollision.getTerrainFriction(),
                worldCollision.getTerrainRestitution());
        }

        @Nonnull
        private PersistentBodyDto[] bodyDtos(
            @Nonnull Map<UUID, List<UUID>> colliderUuidsByBodyUuid) {
            return bodyRows.stream()
                .filter(row -> row.body().getPersistenceMode() == PhysicsBodyPersistenceMode.PERSISTENT)
                .map(row -> bodyDto(row,
                    colliderUuidsByBodyUuid.getOrDefault(row.uuid(), List.of())))
                .sorted(Comparator.comparing(PersistentBodyDto::getBodyUuid))
                .toArray(PersistentBodyDto[]::new);
        }

        @Nonnull
        private PersistentBodyDto bodyDto(@Nonnull BodyRow row,
            @Nonnull List<UUID> colliderUuids) {
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
                colliderUuids.stream().sorted().toArray(UUID[]::new),
                runtimeState(row.uuid(), row.target()));
        }

        @Nonnull
        private PersistentBodyRuntimeStateDto runtimeState(@Nonnull UUID bodyUuid,
            @Nullable TargetComponent target) {
            PhysicsStoreBodySnapshot snapshot = snapshotsByBodyUuid.get(bodyUuid);
            if (snapshot != null) {
                return new PersistentBodyRuntimeStateDto(snapshot.position(),
                    snapshot.rotation(),
                    snapshot.linearVelocity(),
                    snapshot.angularVelocity(),
                    snapshot.sleeping());
            }
            if (target != null && target.isActive()) {
                return new PersistentBodyRuntimeStateDto(target.getPosition(),
                    target.getRotation(),
                    target.getLinearVelocity(),
                    target.getAngularVelocity(),
                    false);
            }
            return new PersistentBodyRuntimeStateDto(new Vector3f(),
                new Quaternionf(),
                new Vector3f(),
                new Vector3f(),
                false);
        }

        @Nonnull
        private PersistentColliderDto[] colliderDtos(@Nonnull Set<UUID> bodyUuids) {
            return colliderRows.stream()
                .filter(row -> bodyUuids.contains(row.collider().getBodyUuid()))
                .map(this::colliderDto)
                .sorted(Comparator.comparing(PersistentColliderDto::getColliderUuid))
                .toArray(PersistentColliderDto[]::new);
        }

        @Nonnull
        private PersistentColliderDto colliderDto(@Nonnull ColliderRow row) {
            CollisionFilterComponent filter = filtersByUuid.get(row.collider().getFilterUuid());
            CollisionFilterComponent resolvedFilter = filter != null
                ? filter
                : new CollisionFilterComponent();
            return new PersistentColliderDto(row.uuid(),
                row.collider().getBodyUuid(),
                row.collider().getShapeUuid(),
                row.collider().getMaterialUuid(),
                row.collider().getLocalPosition(),
                row.collider().getLocalRotation(),
                row.collider().isSensor(),
                resolvedFilter.getCollisionGroup(),
                resolvedFilter.getCollisionMask());
        }

        @Nonnull
        private PersistentShapeDto[] shapeDtos(@Nonnull Set<UUID> shapeUuids) {
            return shapeRows.stream()
                .filter(row -> shapeUuids.contains(row.uuid()))
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
        private PersistentMaterialDto[] materialDtos(@Nonnull Set<UUID> materialUuids) {
            return materialRows.stream()
                .filter(row -> materialUuids.contains(row.uuid()))
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

        @Nonnull
        private PersistentTerrainColliderDto[] terrainDtos() {
            return terrainRows.stream()
                .filter(row -> row.terrain().isRetained())
                .map(row -> new PersistentTerrainColliderDto(row.uuid(),
                    row.terrain().getSpaceUuid(),
                    row.terrain().getSourceKey(),
                    row.terrain().getChunkX(),
                    row.terrain().getSectionY(),
                    row.terrain().getChunkZ(),
                    row.terrain().getPayloadResourceKey(),
                    row.terrain().isRetained()))
                .sorted(Comparator.comparing(PersistentTerrainColliderDto::getTerrainColliderUuid))
                .toArray(PersistentTerrainColliderDto[]::new);
        }
    }

    private record SpaceRow(@Nonnull UUID uuid,
                            @Nonnull SpaceComponent space,
                            @Nullable WorldCollisionComponent worldCollision) {
    }

    private record BodyRow(@Nonnull UUID uuid,
                           @Nonnull BodyComponent body,
                           @Nullable DynamicsComponent dynamics,
                           @Nullable TargetComponent target) {
    }

    private record ColliderRow(@Nonnull UUID uuid, @Nonnull ColliderComponent collider) {
    }

    private record ShapeRow(@Nonnull UUID uuid, @Nonnull ShapeComponent shape) {
    }

    private record MaterialRow(@Nonnull UUID uuid, @Nonnull MaterialComponent material) {
    }

    private record JointRow(@Nonnull UUID uuid, @Nonnull JointComponent joint) {
    }

    private record TerrainColliderRow(@Nonnull UUID uuid,
                                      @Nonnull TerrainColliderComponent terrain) {
    }
}
