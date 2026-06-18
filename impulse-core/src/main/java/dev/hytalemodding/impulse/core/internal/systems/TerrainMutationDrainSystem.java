package dev.hytalemodding.impulse.core.internal.systems;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.api.PhysicsAxis;
import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.ShapeType;
import dev.hytalemodding.impulse.api.runtime.PhysicsBackendRuntime;
import dev.hytalemodding.impulse.core.internal.modules.control.PhysicsControlRuntimeStates;
import dev.hytalemodding.impulse.core.internal.resources.BackendBodyHandle;
import dev.hytalemodding.impulse.core.internal.resources.BackendSpaceHandle;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsBodyRegistrationResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsIdentityIndexResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsRestoreStatusResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsRuntimeResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsSnapshotResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsTerrainMutationQueueResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsTerrainPayloadResource;
import dev.hytalemodding.impulse.core.internal.terrain.TerrainColliderMutation;
import dev.hytalemodding.impulse.core.internal.terrain.TerrainColliderPayload;
import dev.hytalemodding.impulse.core.internal.terrain.TerrainColliderPayload.BoxPayload;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyKind;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyPersistenceMode;
import dev.hytalemodding.impulse.core.plugin.components.BodyComponent;
import dev.hytalemodding.impulse.core.plugin.components.ColliderComponent;
import dev.hytalemodding.impulse.core.plugin.components.CollisionFilterComponent;
import dev.hytalemodding.impulse.core.plugin.components.DynamicsComponent;
import dev.hytalemodding.impulse.core.plugin.components.MaterialComponent;
import dev.hytalemodding.impulse.core.plugin.components.ShapeComponent;
import dev.hytalemodding.impulse.core.plugin.components.TargetComponent;
import dev.hytalemodding.impulse.core.plugin.components.UuidComponent;
import dev.hytalemodding.impulse.core.plugin.modules.physicschunk.components.ChunkCollisionSourceComponent;
import dev.hytalemodding.impulse.core.plugin.modules.physicschunk.components.ChunkCollisionSourceComponent.PartKind;
import dev.hytalemodding.impulse.core.plugin.physicsstore.PhysicsEntities;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Applies copied PhysicsChunk terrain mutations as runtime-only terrain body rows.
 */
public final class TerrainMutationDrainSystem extends TickingSystem<PhysicsStore> {

    private static final Set<Dependency<PhysicsStore>> DEPENDENCIES = Set.of(
        new SystemDependency<>(Order.AFTER, SpaceBindingSystem.class),
        new SystemDependency<>(Order.AFTER, SpaceSettingsApplicationSystem.class)
    );

    @Override
    public void tick(float dt, int systemIndex, @Nonnull Store<PhysicsStore> store) {
        PhysicsRestoreStatusResource restore = store.getResource(
            PhysicsRestoreStatusResource.getResourceType());
        if (restore.isFailed()) {
            return;
        }
        PhysicsTerrainMutationQueueResource queue = store.getResource(
            PhysicsTerrainMutationQueueResource.getResourceType());
        List<TerrainColliderMutation> mutations = queue.drain();
        if (mutations.isEmpty()) {
            return;
        }
        PhysicsRuntimeResource runtime = store.getResource(PhysicsRuntimeResource.getResourceType());
        PhysicsIdentityIndexResource identity = store.getResource(
            PhysicsIdentityIndexResource.getResourceType());
        PhysicsTerrainPayloadResource terrainPayloads = store.getResource(
            PhysicsTerrainPayloadResource.getResourceType());

        applyRemovals(store, runtime, identity, terrainPayloads, mutations);
        applyUpserts(store, runtime, identity, terrainPayloads, restore, mutations);
    }

    private static void applyRemovals(@Nonnull Store<PhysicsStore> store,
        @Nonnull PhysicsRuntimeResource runtime,
        @Nonnull PhysicsIdentityIndexResource identity,
        @Nonnull PhysicsTerrainPayloadResource terrainPayloads,
        @Nonnull List<TerrainColliderMutation> mutations) {
        for (TerrainColliderMutation mutation : mutations) {
            if (mutation.remove()) {
                removeGeneratedRows(store, runtime, identity, terrainPayloads, mutation);
                removePayload(terrainPayloads, mutation.payloadResourceKey());
            }
        }
    }

    private static void applyUpserts(@Nonnull Store<PhysicsStore> store,
        @Nonnull PhysicsRuntimeResource runtime,
        @Nonnull PhysicsIdentityIndexResource identity,
        @Nonnull PhysicsTerrainPayloadResource terrainPayloads,
        @Nonnull PhysicsRestoreStatusResource restore,
        @Nonnull List<TerrainColliderMutation> mutations) {
        for (TerrainColliderMutation mutation : mutations) {
            if (!mutation.remove()) {
                applyUpsert(store, runtime, identity, terrainPayloads, restore, mutation);
            }
        }
    }

    private static void applyUpsert(@Nonnull Store<PhysicsStore> store,
        @Nonnull PhysicsRuntimeResource runtime,
        @Nonnull PhysicsIdentityIndexResource identity,
        @Nonnull PhysicsTerrainPayloadResource terrainPayloads,
        @Nonnull PhysicsRestoreStatusResource restore,
        @Nonnull TerrainColliderMutation mutation) {
        TerrainColliderPayload payload = mutation.payload();
        if (payload == null || payload.isEmpty()) {
            restore.recordSoftSkip("Terrain upsert payload is missing: " + mutation.sourceKey());
            return;
        }
        Ref<PhysicsStore> spaceRef = PhysicsStoreSystemSupport.refForUuid(identity,
            mutation.spaceUuid());
        BackendSpaceHandle spaceHandle = spaceRef != null ? runtime.getSpaceHandle(spaceRef) : null;
        PhysicsBackendRuntime backendRuntime = spaceHandle != null
            ? runtime.runtimeForSpaceHandle(spaceHandle)
            : null;
        if (spaceRef == null || spaceHandle == null || backendRuntime == null) {
            restore.recordSoftSkip("Terrain references unbound space: " + mutation.sourceKey());
            return;
        }
        removeGeneratedRows(store, runtime, identity, terrainPayloads, mutation);
        terrainPayloads.put(mutation.payloadResourceKey(), payload);

        boolean nativeVoxel = payload.nativeVoxelTerrainEnabled()
            && payload.hasFullCubeVoxels()
            && backendRuntime.supportsVoxelTerrain(spaceHandle.value());
        if (nativeVoxel) {
            addVoxelBody(store, identity, spaceRef, mutation, payload);
        } else {
            addBoxBodies(store,
                identity,
                spaceRef,
                mutation,
                payload,
                payload.mergedFullCubeBoxes(),
                PartKind.BOX);
        }
        addBoxBodies(store,
            identity,
            spaceRef,
            mutation,
            payload,
            payload.detailBoxes(),
            PartKind.DETAIL_BOX);
    }

    private static void addVoxelBody(@Nonnull Store<PhysicsStore> store,
        @Nonnull PhysicsIdentityIndexResource identity,
        @Nonnull Ref<PhysicsStore> spaceRef,
        @Nonnull TerrainColliderMutation mutation,
        @Nonnull TerrainColliderPayload payload) {
        TargetComponent target = new TargetComponent();
        target.setPosition(new Vector3f(mutation.chunkX() << ChunkUtil.BITS,
            mutation.sectionY() << ChunkUtil.BITS,
            mutation.chunkZ() << ChunkUtil.BITS));
        addTerrainBody(store,
            identity,
            spaceRef,
            mutation,
            target,
            new ShapeComponent(ShapeType.VOXELS,
                0.0f,
                0.0f,
                0.0f,
                0.0f,
                0.0f,
                PhysicsAxis.Y,
                0.0f,
                mutation.payloadResourceKey()),
            material(payload),
            filter(payload),
            PartKind.VOXEL_TERRAIN,
            0);
    }

    private static void addBoxBodies(@Nonnull Store<PhysicsStore> store,
        @Nonnull PhysicsIdentityIndexResource identity,
        @Nonnull Ref<PhysicsStore> spaceRef,
        @Nonnull TerrainColliderMutation mutation,
        @Nonnull TerrainColliderPayload payload,
        @Nonnull List<BoxPayload> boxes,
        @Nonnull PartKind partKind) {
        for (int index = 0; index < boxes.size(); index++) {
            BoxPayload box = boxes.get(index);
            if (box.halfX() <= 0.0 || box.halfY() <= 0.0 || box.halfZ() <= 0.0) {
                continue;
            }
            TargetComponent target = new TargetComponent();
            target.setPosition(new Vector3f((float) box.centerX(),
                (float) box.centerY(),
                (float) box.centerZ()));
            addTerrainBody(store,
                identity,
                spaceRef,
                mutation,
                target,
                new ShapeComponent(ShapeType.BOX,
                    (float) box.halfX(),
                    (float) box.halfY(),
                    (float) box.halfZ(),
                    0.0f,
                    0.0f,
                    PhysicsAxis.Y,
                    0.0f,
                    ""),
                material(payload),
                filter(payload),
                partKind,
                index);
        }
    }

    private static void addTerrainBody(@Nonnull Store<PhysicsStore> store,
        @Nonnull PhysicsIdentityIndexResource identity,
        @Nonnull Ref<PhysicsStore> spaceRef,
        @Nonnull TerrainColliderMutation mutation,
        @Nonnull TargetComponent target,
        @Nonnull ShapeComponent shape,
        @Nonnull MaterialComponent material,
        @Nonnull CollisionFilterComponent filter,
        @Nonnull PartKind partKind,
        int partIndex) {
        UUID bodyUuid = terrainBodyUuid(mutation.spaceUuid(),
            mutation.sourceKey(),
            partKind,
            partIndex);
        BodyComponent body = new BodyComponent(mutation.spaceUuid(),
            PhysicsBodyKind.TERRAIN,
            PhysicsBodyPersistenceMode.RUNTIME_ONLY);
        body.setSpaceRef(spaceRef);
        var holder = PhysicsEntities.bodyHolder(store,
            bodyUuid,
            body,
            new DynamicsComponent(PhysicsBodyType.STATIC, 0.0f, 0.0f, 0.0f, false),
            target,
            new ColliderComponent(new Vector3f(), new Quaternionf(), false),
            shape,
            material,
            filter);
        holder.addComponent(ChunkCollisionSourceComponent.getComponentType(),
            new ChunkCollisionSourceComponent(mutation.sourceKey(),
                mutation.chunkX(),
                mutation.sectionY(),
                mutation.chunkZ(),
                mutation.payloadResourceKey(),
                partKind,
                partIndex));
        Ref<PhysicsStore> ref = store.addEntity(holder, AddReason.SPAWN);
        assert ref != null;
        identity.putUuid(bodyUuid, ref);
        store.getExternalData().putRefForUUID(bodyUuid, ref);
    }

    @Nonnull
    private static MaterialComponent material(@Nonnull TerrainColliderPayload payload) {
        return new MaterialComponent(payload.friction(), payload.restitution());
    }

    @Nonnull
    private static CollisionFilterComponent filter(@Nonnull TerrainColliderPayload payload) {
        return new CollisionFilterComponent(payload.collisionGroup(), payload.collisionMask());
    }

    private static void removeGeneratedRows(@Nonnull Store<PhysicsStore> store,
        @Nonnull PhysicsRuntimeResource runtime,
        @Nonnull PhysicsIdentityIndexResource identity,
        @Nonnull PhysicsTerrainPayloadResource terrainPayloads,
        @Nonnull TerrainColliderMutation mutation) {
        List<GeneratedRow> rows = collectGeneratedRows(store, mutation);
        PhysicsSnapshotResource snapshots = store.getResource(PhysicsSnapshotResource.getResourceType());
        PhysicsBodyRegistrationResource registrations = store.getResource(
            PhysicsBodyRegistrationResource.getResourceType());
        for (GeneratedRow row : rows) {
            removeRuntimeBody(runtime, identity, row);
            PhysicsControlRuntimeStates.clearControlled(row.ref());
            snapshots.removeBody(row.uuid());
            registrations.removeBody(row.uuid());
            removePayload(terrainPayloads, row.payloadResourceKey());
            identity.removeUuid(row.uuid(), row.ref());
            if (row.ref().isValid()) {
                store.removeEntity(row.ref(),
                    store.getRegistry().newHolder(),
                    RemoveReason.REMOVE);
            }
        }
    }

    @Nonnull
    private static List<GeneratedRow> collectGeneratedRows(@Nonnull Store<PhysicsStore> store,
        @Nonnull TerrainColliderMutation mutation) {
        ConcurrentLinkedQueue<GeneratedRow> rows = new ConcurrentLinkedQueue<>();
        store.forEachEntityParallel(UuidComponent.getComponentType(), (index, chunk, _) -> {
            UUID rowUuid = PhysicsStoreSystemSupport.rowUuid(chunk, index);
            if (PhysicsStoreSystemSupport.isNil(rowUuid)) {
                return;
            }
            ChunkCollisionSourceComponent source = chunk.getComponent(index,
                ChunkCollisionSourceComponent.getComponentType());
            BodyComponent body = chunk.getComponent(index, BodyComponent.getComponentType());
            if (source != null
                && body != null
                && matchesSource(mutation, body, source)) {
                rows.add(new GeneratedRow(chunk.getReferenceTo(index),
                    rowUuid,
                    source.getPayloadResourceKey()));
            }
        });
        return new ArrayList<>(rows);
    }

    private static boolean matchesSource(@Nonnull TerrainColliderMutation mutation,
        @Nonnull BodyComponent body,
        @Nonnull ChunkCollisionSourceComponent source) {
        return mutation.spaceUuid().equals(body.getSpaceUuid())
            && mutation.sourceKey().equals(source.getSourceKey());
    }

    private static void removeRuntimeBody(@Nonnull PhysicsRuntimeResource runtime,
        @Nonnull PhysicsIdentityIndexResource identity,
        @Nonnull GeneratedRow row) {
        BackendBodyHandle bodyHandle = runtime.getBodyHandle(row.ref());
        BackendSpaceHandle spaceHandle = runtime.getBodySpaceHandle(row.ref());
        if (bodyHandle != null && spaceHandle != null) {
            PhysicsBackendRuntime backendRuntime = runtime.runtimeForSpaceHandle(spaceHandle);
            if (backendRuntime != null) {
                backendRuntime.removeBody(spaceHandle.value(), bodyHandle.value());
            }
            identity.removeBodyHandle(bodyHandle);
        }
        runtime.removeBodyHandle(row.uuid(), row.ref());
    }

    private static void removePayload(@Nonnull PhysicsTerrainPayloadResource terrainPayloads,
        @Nullable String payloadResourceKey) {
        if (payloadResourceKey != null && !payloadResourceKey.isBlank()) {
            terrainPayloads.remove(payloadResourceKey);
        }
    }

    @Nonnull
    static UUID terrainBodyUuid(@Nonnull UUID spaceUuid,
        @Nonnull String sourceKey,
        @Nonnull PartKind partKind,
        int partIndex) {
        String key = spaceUuid + "|" + sourceKey + "|" + partKind.name() + "|" + partIndex;
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
    }

    @Nonnull
    @Override
    public Set<Dependency<PhysicsStore>> getDependencies() {
        return DEPENDENCIES;
    }

    private record GeneratedRow(@Nonnull Ref<PhysicsStore> ref,
                                @Nonnull UUID uuid,
                                @Nullable String payloadResourceKey) {
        private GeneratedRow {
            Objects.requireNonNull(ref, "ref");
            Objects.requireNonNull(uuid, "uuid");
        }
    }
}
