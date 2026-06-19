package dev.hytalemodding.impulse.core.internal.systems;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Ref;
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
import dev.hytalemodding.impulse.core.internal.resources.BackendSpaceHandle;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsIdentityIndexResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsRestoreStatusResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsRuntimeResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsChunkCollisionMutationQueueResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsChunkCollisionPayloadResource;
import dev.hytalemodding.impulse.core.internal.modules.physicschunk.ChunkCollisionMutation;
import dev.hytalemodding.impulse.core.internal.modules.physicschunk.ChunkCollisionPayload;
import dev.hytalemodding.impulse.core.internal.modules.physicschunk.ChunkCollisionPayload.BoxPayload;
import dev.hytalemodding.impulse.core.internal.modules.physicschunk.PhysicsChunkCollisionDefaults;
import dev.hytalemodding.impulse.core.internal.physicsstore.PhysicsStoreRowCleanup;
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
import dev.hytalemodding.impulse.core.internal.modules.physicschunk.components.ChunkCollisionSourceComponent;
import dev.hytalemodding.impulse.core.internal.modules.physicschunk.components.ChunkCollisionSourceComponent.PartKind;
import dev.hytalemodding.impulse.core.plugin.physicsstore.PhysicsEntities;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Applies copied PhysicsChunk chunk collision mutations as runtime-only chunk collision body rows.
 */
public final class ChunkCollisionMutationDrainSystem extends TickingSystem<PhysicsStore> {

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
        PhysicsChunkCollisionMutationQueueResource queue = store.getResource(
            PhysicsChunkCollisionMutationQueueResource.getResourceType());
        List<ChunkCollisionMutation> mutations = coalesceLastMutationPerSource(queue.drain());
        if (mutations.isEmpty()) {
            return;
        }
        PhysicsRuntimeResource runtime = store.getResource(PhysicsRuntimeResource.getResourceType());
        PhysicsIdentityIndexResource identity = store.getResource(
            PhysicsIdentityIndexResource.getResourceType());
        PhysicsChunkCollisionPayloadResource chunkCollisionPayloads = store.getResource(
            PhysicsChunkCollisionPayloadResource.getResourceType());

        applyRemovals(store, runtime, identity, chunkCollisionPayloads, mutations);
        applyUpserts(store, runtime, identity, chunkCollisionPayloads, restore, mutations);
    }

    @Nonnull
    private static List<ChunkCollisionMutation> coalesceLastMutationPerSource(
        @Nonnull List<ChunkCollisionMutation> mutations) {
        Map<MutationKey, ChunkCollisionMutation> latest = new LinkedHashMap<>();
        for (ChunkCollisionMutation mutation : mutations) {
            MutationKey key = new MutationKey(mutation.spaceUuid(), mutation.sourceKey());
            latest.remove(key);
            latest.put(key, mutation);
        }
        return new ArrayList<>(latest.values());
    }

    private static void applyRemovals(@Nonnull Store<PhysicsStore> store,
        @Nonnull PhysicsRuntimeResource runtime,
        @Nonnull PhysicsIdentityIndexResource identity,
        @Nonnull PhysicsChunkCollisionPayloadResource chunkCollisionPayloads,
        @Nonnull List<ChunkCollisionMutation> mutations) {
        for (ChunkCollisionMutation mutation : mutations) {
            if (mutation.remove()) {
                removeGeneratedRows(store, runtime, identity, mutation);
                removePayload(chunkCollisionPayloads, mutation.payloadResourceKey());
            }
        }
    }

    private static void applyUpserts(@Nonnull Store<PhysicsStore> store,
        @Nonnull PhysicsRuntimeResource runtime,
        @Nonnull PhysicsIdentityIndexResource identity,
        @Nonnull PhysicsChunkCollisionPayloadResource chunkCollisionPayloads,
        @Nonnull PhysicsRestoreStatusResource restore,
        @Nonnull List<ChunkCollisionMutation> mutations) {
        for (ChunkCollisionMutation mutation : mutations) {
            if (!mutation.remove()) {
                applyUpsert(store, runtime, identity, chunkCollisionPayloads, restore, mutation);
            }
        }
    }

    private static void applyUpsert(@Nonnull Store<PhysicsStore> store,
        @Nonnull PhysicsRuntimeResource runtime,
        @Nonnull PhysicsIdentityIndexResource identity,
        @Nonnull PhysicsChunkCollisionPayloadResource chunkCollisionPayloads,
        @Nonnull PhysicsRestoreStatusResource restore,
        @Nonnull ChunkCollisionMutation mutation) {
        ChunkCollisionPayload payload = mutation.payload();
        if (payload == null || payload.isEmpty()) {
            restore.recordSoftSkip("Chunk collision upsert payload is missing: "
                + mutation.sourceKey());
            return;
        }
        Ref<PhysicsStore> spaceRef = PhysicsStoreSystemSupport.refForUuid(identity,
            mutation.spaceUuid());
        BackendSpaceHandle spaceHandle = spaceRef != null ? runtime.getSpaceHandle(spaceRef) : null;
        PhysicsBackendRuntime backendRuntime = spaceRef != null
            ? runtime.runtimeForSpaceRef(spaceRef)
            : null;
        if (spaceRef == null || spaceHandle == null || backendRuntime == null) {
            restore.recordSoftSkip("Chunk collision references unbound space: "
                + mutation.sourceKey());
            return;
        }
        boolean nativeVoxel = payload.nativeVoxelCollisionEnabled()
            && payload.hasFullCubeVoxels()
            && backendRuntime.supportsVoxelTerrain(spaceHandle.value());
        MaterialComponent material = material(store, spaceRef);
        CollisionFilterComponent filter = filter(store, spaceRef);
        removeGeneratedRows(store, runtime, identity, mutation);
        removePayload(chunkCollisionPayloads, mutation.payloadResourceKey());
        if (nativeVoxel) {
            chunkCollisionPayloads.put(mutation.payloadResourceKey(), voxelPayload(payload));
            addNativeVoxelBody(store, identity, spaceRef, mutation, material, filter);
        } else {
            addBoxBodies(store,
                identity,
                spaceRef,
                mutation,
                material,
                filter,
                payload.mergedFullCubeBoxes(),
                PartKind.BOX);
        }
        addBoxBodies(store,
            identity,
            spaceRef,
            mutation,
            material,
            filter,
            payload.detailBoxes(),
            PartKind.DETAIL_BOX);
    }

    @Nonnull
    private static ChunkCollisionPayload voxelPayload(@Nonnull ChunkCollisionPayload payload) {
        return new ChunkCollisionPayload(payload.voxelSizeX(),
            payload.voxelSizeY(),
            payload.voxelSizeZ(),
            payload.voxelCoordinates(),
            List.of(),
            List.of(),
            true,
            payload.neighbors());
    }

    private static void addNativeVoxelBody(@Nonnull Store<PhysicsStore> store,
        @Nonnull PhysicsIdentityIndexResource identity,
        @Nonnull Ref<PhysicsStore> spaceRef,
        @Nonnull ChunkCollisionMutation mutation,
        @Nonnull MaterialComponent material,
        @Nonnull CollisionFilterComponent filter) {
        TargetComponent target = new TargetComponent();
        target.setPosition(new Vector3f(mutation.chunkX() << ChunkUtil.BITS,
            mutation.sectionY() << ChunkUtil.BITS,
            mutation.chunkZ() << ChunkUtil.BITS));
        addChunkCollisionBody(store,
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
            material,
            filter,
            mutation.payloadResourceKey(),
            PartKind.NATIVE_VOXELS,
            0);
    }

    private static void addBoxBodies(@Nonnull Store<PhysicsStore> store,
        @Nonnull PhysicsIdentityIndexResource identity,
        @Nonnull Ref<PhysicsStore> spaceRef,
        @Nonnull ChunkCollisionMutation mutation,
        @Nonnull MaterialComponent material,
        @Nonnull CollisionFilterComponent filter,
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
            addChunkCollisionBody(store,
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
                material,
                filter,
                "",
                partKind,
                index);
        }
    }

    private static void addChunkCollisionBody(@Nonnull Store<PhysicsStore> store,
        @Nonnull PhysicsIdentityIndexResource identity,
        @Nonnull Ref<PhysicsStore> spaceRef,
        @Nonnull ChunkCollisionMutation mutation,
        @Nonnull TargetComponent target,
        @Nonnull ShapeComponent shape,
        @Nonnull MaterialComponent material,
        @Nonnull CollisionFilterComponent filter,
        @Nonnull String payloadResourceKey,
        @Nonnull PartKind partKind,
        int partIndex) {
        UUID bodyUuid = chunkCollisionBodyUuid(mutation.spaceUuid(),
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
                payloadResourceKey,
                partKind,
                partIndex));
        Ref<PhysicsStore> ref = store.addEntity(holder, AddReason.SPAWN);
        assert ref != null;
        identity.putUuid(bodyUuid, ref);
        store.getExternalData().putRefForUUID(bodyUuid, ref);
    }

    @Nonnull
    private static MaterialComponent material(@Nonnull Store<PhysicsStore> store,
        @Nonnull Ref<PhysicsStore> spaceRef) {
        MaterialComponent material =
            store.getComponent(spaceRef, MaterialComponent.getComponentType());
        return material != null
            ? material.clone()
            : new MaterialComponent(PhysicsChunkCollisionDefaults.FRICTION,
                PhysicsChunkCollisionDefaults.RESTITUTION);
    }

    @Nonnull
    private static CollisionFilterComponent filter(@Nonnull Store<PhysicsStore> store,
        @Nonnull Ref<PhysicsStore> spaceRef) {
        CollisionFilterComponent filter =
            store.getComponent(spaceRef, CollisionFilterComponent.getComponentType());
        return filter != null
            ? filter.clone()
            : new CollisionFilterComponent(PhysicsChunkCollisionDefaults.COLLISION_GROUP,
                PhysicsChunkCollisionDefaults.COLLISION_MASK);
    }

    private static void removeGeneratedRows(@Nonnull Store<PhysicsStore> store,
        @Nonnull PhysicsRuntimeResource runtime,
        @Nonnull PhysicsIdentityIndexResource identity,
        @Nonnull ChunkCollisionMutation mutation) {
        List<GeneratedRow> rows = collectGeneratedRows(store, mutation);
        for (GeneratedRow row : rows) {
            PhysicsStoreRowCleanup.removeRuntimeBody(runtime, identity, row.uuid(), row.ref());
            PhysicsStoreRowCleanup.removeBodyEntity(store,
                row.uuid(),
                row.ref(),
                row.payloadResourceKey());
        }
    }

    @Nonnull
    private static List<GeneratedRow> collectGeneratedRows(@Nonnull Store<PhysicsStore> store,
        @Nonnull ChunkCollisionMutation mutation) {
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

    private static boolean matchesSource(@Nonnull ChunkCollisionMutation mutation,
        @Nonnull BodyComponent body,
        @Nonnull ChunkCollisionSourceComponent source) {
        return mutation.spaceUuid().equals(body.getSpaceUuid())
            && mutation.sourceKey().equals(source.getSourceKey());
    }

    private static void removePayload(@Nonnull PhysicsChunkCollisionPayloadResource chunkCollisionPayloads,
        @Nullable String payloadResourceKey) {
        if (payloadResourceKey != null && !payloadResourceKey.isBlank()) {
            chunkCollisionPayloads.remove(payloadResourceKey);
        }
    }

    @Nonnull
    static UUID chunkCollisionBodyUuid(@Nonnull UUID spaceUuid,
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

    private record MutationKey(@Nonnull UUID spaceUuid,
                               @Nonnull String sourceKey) {
        private MutationKey {
            Objects.requireNonNull(spaceUuid, "spaceUuid");
            Objects.requireNonNull(sourceKey, "sourceKey");
        }
    }
}
