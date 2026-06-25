package dev.hytalemodding.impulse.core.internal.modules.physicschunk.systems;

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
import dev.hytalemodding.impulse.core.internal.resources.PhysicsRestoreStatusResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsRuntimeResource;
import dev.hytalemodding.impulse.core.internal.modules.physicschunk.resources.PhysicsChunkCollisionMutationQueueResource;
import dev.hytalemodding.impulse.core.internal.modules.physicschunk.resources.PhysicsChunkCollisionPayloadResource;
import dev.hytalemodding.impulse.core.internal.modules.physicschunk.resources.PhysicsChunkSettingsIndexResource;
import dev.hytalemodding.impulse.core.internal.modules.physicschunk.ChunkCollisionMutation;
import dev.hytalemodding.impulse.core.internal.modules.physicschunk.ChunkCollisionPayload;
import dev.hytalemodding.impulse.core.internal.modules.physicschunk.ChunkCollisionPayload.BoxPayload;
import dev.hytalemodding.impulse.core.internal.modules.physicschunk.PhysicsChunkLifecycle;
import dev.hytalemodding.impulse.core.internal.modules.physicschunk.PhysicsChunkCollisionDefaults;
import dev.hytalemodding.impulse.core.internal.physics.PhysicsStoreRowCleanup;
import dev.hytalemodding.impulse.core.internal.systems.PhysicsStoreSystemSupport;
import dev.hytalemodding.impulse.core.internal.systems.SpaceSettingsApplicationSystem;
import dev.hytalemodding.impulse.core.internal.systems.binding.BodyBindingSystem;
import dev.hytalemodding.impulse.core.internal.systems.binding.SpaceBindingSystem;
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
import dev.hytalemodding.impulse.core.plugin.physics.PhysicsEntities;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
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
 * Applies copied PhysicsChunk chunk collision mutations as chunk collision body rows.
 */
public final class ChunkCollisionMutationDrainSystem extends TickingSystem<PhysicsStore> {

    private static final Set<Dependency<PhysicsStore>> DEPENDENCIES = Set.of(
        new SystemDependency<>(Order.AFTER, SpaceBindingSystem.class),
        new SystemDependency<>(Order.AFTER, SpaceSettingsApplicationSystem.class),
        new SystemDependency<>(Order.AFTER, PhysicsChunkSettingsIndexSystem.class),
        new SystemDependency<>(Order.BEFORE, BodyBindingSystem.class)
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
        PhysicsChunkCollisionPayloadResource chunkCollisionPayloads = store.getResource(
            PhysicsChunkCollisionPayloadResource.getResourceType());
        PhysicsChunkSettingsIndexResource settingsIndex = store.getResource(
            PhysicsChunkSettingsIndexResource.getResourceType());
        List<PreparedUpsert> upserts = prepareUpserts(store,
            runtime,
            settingsIndex,
            restore,
            mutations);

        removeGeneratedRows(store,
            runtime,
            removalKeys(mutations, upserts));
        applyPreparedUpserts(store,
            chunkCollisionPayloads,
            upserts);
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

    @Nonnull
    private static Map<UUID, ObjectOpenHashSet<String>> removalKeys(
        @Nonnull List<ChunkCollisionMutation> mutations,
        @Nonnull List<PreparedUpsert> upserts) {
        Map<UUID, ObjectOpenHashSet<String>> keys = new Object2ObjectOpenHashMap<>();
        for (ChunkCollisionMutation mutation : mutations) {
            if (mutation.remove()) {
                addRemovalKey(keys, mutation);
            }
        }
        for (PreparedUpsert upsert : upserts) {
            addRemovalKey(keys, upsert.mutation());
        }
        return keys;
    }

    private static void addRemovalKey(@Nonnull Map<UUID, ObjectOpenHashSet<String>> keys,
        @Nonnull ChunkCollisionMutation mutation) {
        keys.computeIfAbsent(mutation.spaceUuid(), _ -> new ObjectOpenHashSet<>())
            .add(mutation.sourceKey());
    }

    @Nonnull
    private static List<PreparedUpsert> prepareUpserts(@Nonnull Store<PhysicsStore> store,
        @Nonnull PhysicsRuntimeResource runtime,
        @Nonnull PhysicsChunkSettingsIndexResource settingsIndex,
        @Nonnull PhysicsRestoreStatusResource restore,
        @Nonnull List<ChunkCollisionMutation> mutations) {
        List<PreparedUpsert> upserts = new ArrayList<>();
        for (ChunkCollisionMutation mutation : mutations) {
            if (!mutation.remove() && isFreshUpsert(settingsIndex, mutation)) {
                PreparedUpsert upsert = prepareUpsert(store, runtime, restore, mutation);
                if (upsert != null) {
                    upserts.add(upsert);
                }
            }
        }
        return upserts;
    }

    private static boolean isFreshUpsert(@Nonnull PhysicsChunkSettingsIndexResource settingsIndex,
        @Nonnull ChunkCollisionMutation mutation) {
        return mutation.lifecycleGeneration() == PhysicsChunkLifecycle.generation()
            && mutation.settingsGeneration() == settingsIndex.generation()
            && settingsIndex.settings(mutation.spaceUuid()) != null;
    }

    @Nullable
    private static PreparedUpsert prepareUpsert(@Nonnull Store<PhysicsStore> store,
        @Nonnull PhysicsRuntimeResource runtime,
        @Nonnull PhysicsRestoreStatusResource restore,
        @Nonnull ChunkCollisionMutation mutation) {
        ChunkCollisionPayload payload = mutation.payload();
        if (payload == null || payload.isEmpty()) {
            restore.recordSoftSkip("Chunk collision upsert payload is missing: "
                + mutation.sourceKey());
            return null;
        }
        Ref<PhysicsStore> spaceRef = store.getExternalData().getRefFromUUID(mutation.spaceUuid());
        if (spaceRef != null && (spaceRef.getStore() != store || !spaceRef.isValid())) {
            spaceRef = null;
        }
        BackendSpaceHandle spaceHandle = spaceRef != null ? runtime.getSpaceHandle(spaceRef) : null;
        boolean nativeVoxel;
        try (PhysicsBackendRuntime backendRuntime = spaceRef != null
            ? runtime.runtimeForSpaceRef(spaceRef)
            : null) {
            if (spaceRef == null || spaceHandle == null || backendRuntime == null) {
                restore.recordSoftSkip("Chunk collision references unbound space: "
                    + mutation.sourceKey());
                return null;
            }
            nativeVoxel = payload.nativeVoxelCollisionEnabled()
                && payload.hasFullCubeVoxels()
                && backendRuntime.supportsVoxelTerrain(spaceHandle.value());
        }
        MaterialComponent material = material(store, spaceRef);
        CollisionFilterComponent filter = filter(store, spaceRef);
        return new PreparedUpsert(mutation, payload, spaceRef, material, filter, nativeVoxel);
    }

    private static void applyPreparedUpserts(@Nonnull Store<PhysicsStore> store,
        @Nonnull PhysicsChunkCollisionPayloadResource chunkCollisionPayloads,
        @Nonnull List<PreparedUpsert> upserts) {
        for (PreparedUpsert upsert : upserts) {
            applyPreparedUpsert(store, chunkCollisionPayloads, upsert);
        }
    }

    private static void applyPreparedUpsert(@Nonnull Store<PhysicsStore> store,
        @Nonnull PhysicsChunkCollisionPayloadResource chunkCollisionPayloads,
        @Nonnull PreparedUpsert upsert) {
        ChunkCollisionMutation mutation = upsert.mutation();
        ChunkCollisionPayload payload = upsert.payload();
        removePayload(chunkCollisionPayloads, mutation.payloadResourceKey());
        if (upsert.nativeVoxel()) {
            chunkCollisionPayloads.put(mutation.payloadResourceKey(), voxelPayload(payload));
            addNativeVoxelBody(store,
                upsert.spaceRef(),
                mutation,
                upsert.material(),
                upsert.filter());
        } else {
            addBoxBodies(store,
                upsert.spaceRef(),
                mutation,
                upsert.material(),
                upsert.filter(),
                payload.mergedFullCubeBoxes(),
                PartKind.BOX);
        }
        addBoxBodies(store,
            upsert.spaceRef(),
            mutation,
            upsert.material(),
            upsert.filter(),
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
        @Nonnull Ref<PhysicsStore> spaceRef,
        @Nonnull ChunkCollisionMutation mutation,
        @Nonnull MaterialComponent material,
        @Nonnull CollisionFilterComponent filter) {
        TargetComponent target = new TargetComponent();
        target.setPosition(new Vector3f(mutation.chunkX() << ChunkUtil.BITS,
            mutation.sectionY() << ChunkUtil.BITS,
            mutation.chunkZ() << ChunkUtil.BITS));
        addChunkCollisionBody(store,
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
        BodyComponent body = new BodyComponent(mutation.spaceUuid());
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
        @Nonnull Map<UUID, ObjectOpenHashSet<String>> keys) {
        if (keys.isEmpty()) {
            return;
        }
        List<GeneratedRow> rows = collectGeneratedRows(store, keys);
        rows.sort((first, second) -> Integer.compare(second.ref().getIndex(),
            first.ref().getIndex()));
        PhysicsChunkCollisionPayloadResource chunkCollisionPayloads = store.getResource(
            PhysicsChunkCollisionPayloadResource.getResourceType());
        List<PhysicsStoreRowCleanup.BodyEntityRemoval> bodyEntityRemovals =
            new ArrayList<>(rows.size());
        for (GeneratedRow row : rows) {
            PhysicsStoreRowCleanup.removeRuntimeBody(store, runtime, row.uuid(), row.ref());
            removePayload(chunkCollisionPayloads, row.payloadResourceKey());
            bodyEntityRemovals.add(new PhysicsStoreRowCleanup.BodyEntityRemoval(row.uuid(),
                row.ref()));
        }
        if (!bodyEntityRemovals.isEmpty()) {
            PhysicsStoreRowCleanup.removeBodyEntities(store, bodyEntityRemovals);
            PhysicsStoreRowCleanup.refreshIdentityAndRuntimeRefs(store);
        }
    }

    @Nonnull
    private static List<GeneratedRow> collectGeneratedRows(@Nonnull Store<PhysicsStore> store,
        @Nonnull Map<UUID, ObjectOpenHashSet<String>> keys) {
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
                && containsRemovalKey(keys, body, source)) {
                rows.add(new GeneratedRow(chunk.getReferenceTo(index),
                    rowUuid,
                    source.getPayloadResourceKey()));
            }
        });
        return new ArrayList<>(rows);
    }

    private static boolean containsRemovalKey(
        @Nonnull Map<UUID, ObjectOpenHashSet<String>> keys,
        @Nonnull BodyComponent body,
        @Nonnull ChunkCollisionSourceComponent source) {
        ObjectOpenHashSet<String> sourceKeys = keys.get(body.getSpaceUuid());
        return sourceKeys != null && sourceKeys.contains(source.getSourceKey());
    }

    private static void removePayload(@Nonnull PhysicsChunkCollisionPayloadResource chunkCollisionPayloads,
        @Nullable String payloadResourceKey) {
        if (payloadResourceKey != null && !payloadResourceKey.isBlank()) {
            chunkCollisionPayloads.remove(payloadResourceKey);
        }
    }

    @Nonnull
    public static UUID chunkCollisionBodyUuid(@Nonnull UUID spaceUuid,
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

    private record PreparedUpsert(@Nonnull ChunkCollisionMutation mutation,
                                  @Nonnull ChunkCollisionPayload payload,
                                  @Nonnull Ref<PhysicsStore> spaceRef,
                                  @Nonnull MaterialComponent material,
                                  @Nonnull CollisionFilterComponent filter,
                                  boolean nativeVoxel) {
        private PreparedUpsert {
            Objects.requireNonNull(mutation, "mutation");
            Objects.requireNonNull(payload, "payload");
            Objects.requireNonNull(spaceRef, "spaceRef");
            Objects.requireNonNull(material, "material");
            Objects.requireNonNull(filter, "filter");
        }
    }
}
