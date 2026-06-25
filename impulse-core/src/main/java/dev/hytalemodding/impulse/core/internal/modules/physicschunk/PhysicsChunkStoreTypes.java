package dev.hytalemodding.impulse.core.internal.modules.physicschunk;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Resource;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.core.internal.modules.physicschunk.components.ChunkCollisionRestoreDependencyComponent;
import dev.hytalemodding.impulse.core.internal.modules.physicschunk.components.ChunkCollisionSourceComponent;
import dev.hytalemodding.impulse.core.internal.modules.physicschunk.components.ChunkCollisionSourceComponent.PartKind;
import dev.hytalemodding.impulse.core.internal.modules.physicschunk.resources.PhysicsChunkCollisionMutationQueueResource;
import dev.hytalemodding.impulse.core.internal.modules.physicschunk.resources.PhysicsChunkCollisionPayloadResource;
import dev.hytalemodding.impulse.core.internal.modules.physicschunk.resources.PhysicsChunkComponentSyncResource;
import dev.hytalemodding.impulse.core.internal.modules.physicschunk.resources.PhysicsChunkSettingsIndexResource;
import dev.hytalemodding.impulse.core.internal.modules.physicschunk.resources.PhysicsChunkSettingsIndexResource.PhysicsChunkSpaceSettings;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsRestoreStatusResource;
import dev.hytalemodding.impulse.core.internal.modules.physicschunk.systems.ChunkCollisionComponentSyncSystem;
import dev.hytalemodding.impulse.core.internal.modules.physicschunk.systems.ChunkCollisionMutationDrainSystem;
import dev.hytalemodding.impulse.core.internal.modules.physicschunk.systems.ChunkCollisionRestorePrewarmSystem;
import dev.hytalemodding.impulse.core.internal.modules.physicschunk.systems.ChunkCollisionVoxelStitchingSystem;
import dev.hytalemodding.impulse.core.internal.modules.physicschunk.systems.PhysicsChunkSettingsIndexSystem;
import dev.hytalemodding.impulse.core.internal.physics.PhysicsStoreCleanupHooks;
import dev.hytalemodding.impulse.core.internal.physics.PhysicsStoreRowCleanup;
import dev.hytalemodding.impulse.core.plugin.components.BodyComponent;
import dev.hytalemodding.impulse.core.plugin.components.DynamicsComponent;
import dev.hytalemodding.impulse.core.plugin.components.UuidComponent;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsRuntimeResource;
import dev.hytalemodding.impulse.core.plugin.modules.physicschunk.PhysicsChunkCollisionMode;
import dev.hytalemodding.impulse.core.plugin.physics.PhysicsThreading;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3f;

/**
 * PhysicsStore-side type registration owned by the PhysicsChunk module.
 */
public final class PhysicsChunkStoreTypes {

    @Nonnull
    private static final Consumer<Store<PhysicsStore>> FULL_STORE_CLEANUP =
        PhysicsChunkStoreTypes::clearPhysicsStoreRuntimeResources;
    @Nonnull
    private static final Consumer<Store<PhysicsStore>> BODY_RUNTIME_CLEANUP =
        PhysicsChunkStoreTypes::clearPhysicsStoreBodyRuntimeResources;
    @Nonnull
    private static final PhysicsStoreCleanupHooks.SpaceCleanup SPACE_CLEANUP =
        PhysicsChunkStoreTypes::clearPhysicsStoreSpaceRuntimeResources;
    @Nonnull
    private static final PhysicsStoreCleanupHooks.BodyRowCleanup BODY_ROW_CLEANUP =
        PhysicsChunkStoreTypes::clearPhysicsStoreBodyRowRuntimeResources;

    private PhysicsChunkStoreTypes() {
    }

    public static void registerPhysicsStoreResourceTypes(
        @Nonnull ComponentRegistryProxy<PhysicsStore> registry) {
        PhysicsChunkCollisionMutationQueueResource.setResourceType(registry.registerResource(
            PhysicsChunkCollisionMutationQueueResource.class,
            PhysicsChunkCollisionMutationQueueResource::new));
        PhysicsChunkCollisionPayloadResource.setResourceType(registry.registerResource(
            PhysicsChunkCollisionPayloadResource.class,
            PhysicsChunkCollisionPayloadResource::new));
        PhysicsChunkSettingsIndexResource.setResourceType(registry.registerResource(
            PhysicsChunkSettingsIndexResource.class,
            PhysicsChunkSettingsIndexResource::new));
        PhysicsChunkComponentSyncResource.setResourceType(registry.registerResource(
            PhysicsChunkComponentSyncResource.class,
            PhysicsChunkComponentSyncResource::new));
    }

    public static void clearPhysicsStoreResourceTypes() {
        PhysicsChunkCollisionMutationQueueResource.clearResourceType();
        PhysicsChunkCollisionPayloadResource.clearResourceType();
        PhysicsChunkSettingsIndexResource.clearResourceType();
        PhysicsChunkComponentSyncResource.clearResourceType();
    }

    public static void registerPhysicsStoreCleanupHooks() {
        PhysicsStoreCleanupHooks.registerFullStoreCleanup(FULL_STORE_CLEANUP);
        PhysicsStoreCleanupHooks.registerBodyRuntimeCleanup(BODY_RUNTIME_CLEANUP);
        PhysicsStoreCleanupHooks.registerSpaceCleanup(SPACE_CLEANUP);
        PhysicsStoreCleanupHooks.registerBodyRowCleanup(BODY_ROW_CLEANUP);
    }

    public static void clearPhysicsStoreCleanupHooks() {
        PhysicsStoreCleanupHooks.unregisterFullStoreCleanup(FULL_STORE_CLEANUP);
        PhysicsStoreCleanupHooks.unregisterBodyRuntimeCleanup(BODY_RUNTIME_CLEANUP);
        PhysicsStoreCleanupHooks.unregisterSpaceCleanup(SPACE_CLEANUP);
        PhysicsStoreCleanupHooks.unregisterBodyRowCleanup(BODY_ROW_CLEANUP);
    }

    public static void registerSpaceBindingSystems(
        @Nonnull ComponentRegistryProxy<PhysicsStore> registry) {
        registry.registerSystem(new PhysicsChunkSettingsIndexSystem());
    }

    public static void registerPreBodyBindingSystems(
        @Nonnull ComponentRegistryProxy<PhysicsStore> registry) {
        registry.registerSystem(new ChunkCollisionMutationDrainSystem());
        registry.registerSystem(new ChunkCollisionRestorePrewarmSystem());
    }

    public static void registerPostBodyBindingSystems(
        @Nonnull ComponentRegistryProxy<PhysicsStore> registry) {
        registry.registerSystem(new ChunkCollisionComponentSyncSystem());
        registry.registerSystem(new ChunkCollisionVoxelStitchingSystem());
    }

    public static void clearPhysicsStoreRuntimeResources(@Nonnull Store<PhysicsStore> store) {
        clearIfPresent(store,
            PhysicsChunkCollisionMutationQueueResource.getResourceType(),
            PhysicsChunkCollisionMutationQueueResource::clear);
        clearIfPresent(store,
            PhysicsChunkCollisionPayloadResource.getResourceType(),
            PhysicsChunkCollisionPayloadResource::clear);
        clearIfPresent(store,
            PhysicsChunkSettingsIndexResource.getResourceType(),
            PhysicsChunkSettingsIndexResource::clear);
        clearIfPresent(store,
            PhysicsChunkComponentSyncResource.getResourceType(),
            PhysicsChunkComponentSyncResource::clear);
    }

    private static void clearPhysicsStoreBodyRuntimeResources(
        @Nonnull Store<PhysicsStore> store) {
        clearIfPresent(store,
            PhysicsChunkCollisionMutationQueueResource.getResourceType(),
            PhysicsChunkCollisionMutationQueueResource::clear);
        clearIfPresent(store,
            PhysicsChunkCollisionPayloadResource.getResourceType(),
            PhysicsChunkCollisionPayloadResource::clear);
    }

    private static void clearPhysicsStoreSpaceRuntimeResources(
        @Nonnull Store<PhysicsStore> store,
        @Nonnull UUID spaceUuid) {
        PhysicsChunkCollisionMutationQueueResource queue =
            resourceIfPresent(store, PhysicsChunkCollisionMutationQueueResource.getResourceType());
        if (queue != null) {
            queue.removeIf(mutation -> spaceUuid.equals(mutation.spaceUuid()));
        }
    }

    private static void clearPhysicsStoreBodyRowRuntimeResources(
        @Nonnull Store<PhysicsStore> store,
        @Nonnull UUID bodyUuid,
        @Nonnull Ref<PhysicsStore> bodyRef) {
        if (!bodyRef.isValid()) {
            return;
        }
        ChunkCollisionSourceComponent source = store.getComponent(bodyRef,
            ChunkCollisionSourceComponent.getComponentType());
        if (source == null) {
            return;
        }
        removePayload(store, source.getPayloadResourceKey());
    }

    public static int clearChunkCollisionRowsForSpace(@Nonnull Store<PhysicsStore> store,
        @Nonnull UUID spaceUuid) {
        PhysicsThreading.requireBackendIdle(store, "clear PhysicsStore chunk-collision rows");
        PhysicsRuntimeResource runtime = store.getResource(PhysicsRuntimeResource.getResourceType());
        Ref<PhysicsStore> spaceRef = store.getExternalData().getRefFromUUID(spaceUuid);
        List<GeneratedBodyRow> rows = collectChunkCollisionRows(store, spaceUuid, spaceRef);
        rows.sort((first, second) -> Integer.compare(second.ref().getIndex(),
            first.ref().getIndex()));
        int removedBodies = 0;
        List<PhysicsStoreRowCleanup.BodyEntityRemoval> bodyRemovals =
            new ArrayList<>(rows.size());
        for (GeneratedBodyRow row : rows) {
            if (PhysicsStoreRowCleanup.removeRuntimeBody(store, runtime, row.uuid(), row.ref())) {
                removedBodies++;
            }
            bodyRemovals.add(new PhysicsStoreRowCleanup.BodyEntityRemoval(row.uuid(),
                row.ref()));
        }
        clearPhysicsStoreSpaceRuntimeResources(store, spaceUuid);
        if (!bodyRemovals.isEmpty()) {
            PhysicsStoreRowCleanup.removeBodyEntities(store, bodyRemovals);
            PhysicsStoreRowCleanup.refreshIdentityAndRuntimeRefs(store);
        }
        return removedBodies;
    }

    @Nullable
    public static PhysicsChunkCollisionPayloadResource collisionPayloadsIfPresent(
        @Nonnull Store<PhysicsStore> store) {
        return resourceIfPresent(store, PhysicsChunkCollisionPayloadResource.getResourceType());
    }

    public static void prewarmRestoreDependencies(@Nonnull Store<PhysicsStore> store) {
        if (!PhysicsChunkLifecycle.isEnabled()) {
            return;
        }
        PhysicsChunkSettingsIndexResource settingsIndex =
            resourceIfPresent(store, PhysicsChunkSettingsIndexResource.getResourceType());
        PhysicsChunkCollisionMutationQueueResource queue =
            resourceIfPresent(store, PhysicsChunkCollisionMutationQueueResource.getResourceType());
        if (settingsIndex == null || queue == null) {
            return;
        }
        World world = PhysicsThreading.world(store);
        if (!world.isInThread()) {
            return;
        }
        PhysicsChunkCollisionStreamingResource streaming = streamingIfPresent(world);
        if (streaming == null) {
            return;
        }

        Map<RestorePrewarmKey, List<Vector3d>> centersByKey =
            collectRestorePrewarmCenters(store, settingsIndex);
        if (centersByKey.isEmpty()) {
            return;
        }
        queue.updateStamp(PhysicsChunkLifecycle.generation(), settingsIndex.generation());
        long tick = Math.max(0L, world.getTick());
        for (Map.Entry<RestorePrewarmKey, List<Vector3d>> entry : centersByKey.entrySet()) {
            RestorePrewarmKey key = entry.getKey();
            streaming.ensureAround(world,
                key.settings().spaceUuid(),
                queue,
                entry.getValue(),
                key.radius(),
                tick,
                null,
                key.settings().buildOptions());
        }
    }

    public static boolean shouldDeferChunkCollisionRestore(@Nonnull Store<PhysicsStore> store,
        @Nonnull CommandBuffer<PhysicsStore> commandBuffer,
        @Nonnull Ref<PhysicsStore> bodyRef,
        @Nonnull BodyComponent body,
        @Nullable DynamicsComponent dynamics,
        @Nonnull PhysicsRestoreStatusResource restore) {
        ChunkCollisionRestoreDependencyComponent dependency = store.getComponent(bodyRef,
            ChunkCollisionRestoreDependencyComponent.getComponentType());
        if (dependency == null) {
            return false;
        }
        if (!dependency.getSpaceUuid().equals(body.getSpaceUuid())) {
            clearRestoreDependency(commandBuffer, bodyRef);
            return false;
        }
        if (dynamics == null || dynamics.getBodyType() != PhysicsBodyType.DYNAMIC) {
            clearRestoreDependency(commandBuffer, bodyRef);
            return false;
        }
        if (!PhysicsChunkLifecycle.isEnabled()) {
            clearRestoreDependency(commandBuffer, bodyRef);
            return false;
        }
        PhysicsChunkSettingsIndexResource settingsIndex =
            resourceIfPresent(store, PhysicsChunkSettingsIndexResource.getResourceType());
        if (settingsIndex == null) {
            clearRestoreDependency(commandBuffer, bodyRef);
            return false;
        }
        PhysicsChunkSpaceSettings settings = settingsIndex.settings(body.getSpaceUuid());
        if (settings == null || settings.mode() == PhysicsChunkCollisionMode.NONE) {
            clearRestoreDependency(commandBuffer, bodyRef);
            return false;
        }
        prewarmRestoreDependency(store, settings, dependency);
        if (hasGeneratedSupportRow(store, dependency)) {
            clearRestoreDependency(commandBuffer, bodyRef);
            return false;
        }
        restore.recordSoftSkip("Body restore dependency pending chunk collision");
        return true;
    }

    private static <T extends Resource<PhysicsStore>> void clearIfPresent(
        @Nonnull Store<PhysicsStore> store,
        @Nullable ResourceType<PhysicsStore, T> type,
        @Nonnull Consumer<T> clear) {
        T resource = resourceIfPresent(store, type);
        if (resource != null) {
            clear.accept(resource);
        }
    }

    @Nullable
    private static <T extends Resource<PhysicsStore>> T resourceIfPresent(
        @Nonnull Store<PhysicsStore> store,
        @Nullable ResourceType<PhysicsStore, T> type) {
        if (type == null) {
            return null;
        }
        try {
            type.validate();
            return store.getResource(type);
        } catch (ArrayIndexOutOfBoundsException | IllegalArgumentException | IllegalStateException _) {
            // PhysicsChunk resources are optional when the PhysicsChunk module is not registered.
            return null;
        }
    }

    @Nonnull
    private static List<GeneratedBodyRow> collectChunkCollisionRows(
        @Nonnull Store<PhysicsStore> store,
        @Nonnull UUID spaceUuid,
        @Nullable Ref<PhysicsStore> spaceRef) {
        ComponentType<PhysicsStore, UuidComponent> uuidType = UuidComponent.getComponentType();
        ConcurrentLinkedQueue<GeneratedBodyRow> rows = new ConcurrentLinkedQueue<>();
        store.forEachEntityParallel(uuidType, (index, chunk, _) -> {
            UuidComponent uuid = chunk.getComponent(index, uuidType);
            if (uuid == null) {
                return;
            }
            BodyComponent body = chunk.getComponent(index, BodyComponent.getComponentType());
            ChunkCollisionSourceComponent source = chunk.getComponent(index,
                ChunkCollisionSourceComponent.getComponentType());
            if (source != null
                && body != null
                && matchesSpace(body.getSpaceRef(), body.getSpaceUuid(), spaceRef, spaceUuid)) {
                rows.add(new GeneratedBodyRow(chunk.getReferenceTo(index), uuid.getUuid()));
            }
        });
        return new ArrayList<>(rows);
    }

    private static boolean matchesSpace(@Nullable Ref<PhysicsStore> rowSpaceRef,
        @Nonnull UUID rowSpaceUuid,
        @Nullable Ref<PhysicsStore> spaceRef,
        @Nonnull UUID spaceUuid) {
        if (spaceRef != null && rowSpaceRef != null) {
            return sameRef(rowSpaceRef, spaceRef);
        }
        return spaceUuid.equals(rowSpaceUuid);
    }

    private static boolean sameRef(@Nonnull Ref<PhysicsStore> first,
        @Nonnull Ref<PhysicsStore> second) {
        return first.getStore() == second.getStore()
            && first.getIndex() == second.getIndex();
    }

    private static void removePayload(@Nonnull Store<PhysicsStore> store,
        @Nullable String payloadResourceKey) {
        if (payloadResourceKey == null || payloadResourceKey.isBlank()) {
            return;
        }
        PhysicsChunkCollisionPayloadResource payloads =
            resourceIfPresent(store, PhysicsChunkCollisionPayloadResource.getResourceType());
        if (payloads != null) {
            payloads.remove(payloadResourceKey);
        }
    }

    @Nonnull
    private static Map<RestorePrewarmKey, List<Vector3d>> collectRestorePrewarmCenters(
        @Nonnull Store<PhysicsStore> store,
        @Nonnull PhysicsChunkSettingsIndexResource settingsIndex) {
        Map<RestorePrewarmKey, List<Vector3d>> centersByKey = new Object2ObjectOpenHashMap<>();
        BiConsumer<ArchetypeChunk<PhysicsStore>, CommandBuffer<PhysicsStore>> collector =
            (chunk, _) -> collectRestorePrewarmCenters(chunk, settingsIndex, centersByKey);
        store.forEachChunk(UuidComponent.getComponentType(), collector);
        return centersByKey;
    }

    private static void collectRestorePrewarmCenters(
        @Nonnull ArchetypeChunk<PhysicsStore> chunk,
        @Nonnull PhysicsChunkSettingsIndexResource settingsIndex,
        @Nonnull Map<RestorePrewarmKey, List<Vector3d>> centersByKey) {
        for (int index = 0; index < chunk.size(); index++) {
            BodyComponent body = chunk.getComponent(index, BodyComponent.getComponentType());
            if (body == null) {
                continue;
            }
            DynamicsComponent dynamics = chunk.getComponent(index,
                DynamicsComponent.getComponentType());
            if (dynamics == null || dynamics.getBodyType() != PhysicsBodyType.DYNAMIC) {
                continue;
            }
            ChunkCollisionRestoreDependencyComponent dependency = chunk.getComponent(index,
                ChunkCollisionRestoreDependencyComponent.getComponentType());
            if (dependency == null) {
                continue;
            }
            PhysicsChunkSpaceSettings settings = settingsIndex.settings(body.getSpaceUuid());
            if (settings == null || settings.mode() == PhysicsChunkCollisionMode.NONE) {
                continue;
            }
            RestorePrewarmKey key = new RestorePrewarmKey(settings, dependency.getRadius());
            centersByKey.computeIfAbsent(key, _ -> new ArrayList<>())
                .add(vector3d(dependency.getCenter()));
        }
    }

    private static void prewarmRestoreDependency(@Nonnull Store<PhysicsStore> store,
        @Nonnull PhysicsChunkSpaceSettings settings,
        @Nonnull ChunkCollisionRestoreDependencyComponent dependency) {
        if (!PhysicsChunkLifecycle.isEnabled()) {
            return;
        }
        World world = PhysicsThreading.world(store);
        if (!world.isInThread()) {
            return;
        }
        PhysicsChunkCollisionStreamingResource streaming = streamingIfPresent(world);
        PhysicsChunkCollisionMutationQueueResource queue =
            resourceIfPresent(store, PhysicsChunkCollisionMutationQueueResource.getResourceType());
        PhysicsChunkSettingsIndexResource settingsIndex =
            resourceIfPresent(store, PhysicsChunkSettingsIndexResource.getResourceType());
        if (streaming == null || queue == null || settingsIndex == null) {
            return;
        }
        queue.updateStamp(PhysicsChunkLifecycle.generation(), settingsIndex.generation());
        streaming.ensureAround(world,
            settings.spaceUuid(),
            queue,
            List.of(vector3d(dependency.getCenter())),
            dependency.getRadius(),
            Math.max(0L, world.getTick()),
            null,
            settings.buildOptions());
    }

    @Nullable
    private static PhysicsChunkCollisionStreamingResource streamingIfPresent(@Nonnull World world) {
        try {
            ResourceType<EntityStore, PhysicsChunkCollisionStreamingResource> type =
                PhysicsChunkCollisionStreamingResource.getResourceType();
            return world.getEntityStore().getStore().getResource(type);
        } catch (ArrayIndexOutOfBoundsException | IllegalArgumentException | IllegalStateException _) {
            return null;
        }
    }

    private static boolean hasGeneratedSupportRow(@Nonnull Store<PhysicsStore> store,
        @Nonnull ChunkCollisionRestoreDependencyComponent dependency) {
        Vector3f center = dependency.getCenter();
        int radius = dependency.getRadius();
        int minX = (int) Math.floor(center.x) - radius;
        int maxX = (int) Math.floor(center.x) + radius;
        int minY = Math.max(0, (int) Math.floor(center.y) - radius);
        int maxY = Math.min(ChunkUtil.HEIGHT_MINUS_1, (int) Math.floor(center.y) + radius);
        int minZ = (int) Math.floor(center.z) - radius;
        int maxZ = (int) Math.floor(center.z) + radius;

        for (int chunkX = ChunkUtil.chunkCoordinate(minX);
             chunkX <= ChunkUtil.chunkCoordinate(maxX);
             chunkX++) {
            for (int sectionY = ChunkUtil.indexSection(minY);
                 sectionY <= ChunkUtil.indexSection(maxY);
                 sectionY++) {
                for (int chunkZ = ChunkUtil.chunkCoordinate(minZ);
                     chunkZ <= ChunkUtil.chunkCoordinate(maxZ);
                     chunkZ++) {
                    if (hasGeneratedSectionRow(store, dependency.getSpaceUuid(), chunkX,
                        sectionY, chunkZ)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean hasGeneratedSectionRow(@Nonnull Store<PhysicsStore> store,
        @Nonnull UUID spaceUuid,
        int chunkX,
        int sectionY,
        int chunkZ) {
        String sourceKey = PhysicsStoreChunkCollisionMutations.sourceKey(chunkX,
            sectionY,
            chunkZ);
        return hasGeneratedSectionPart(store, spaceUuid, sourceKey, PartKind.BOX)
            || hasGeneratedSectionPart(store, spaceUuid, sourceKey, PartKind.DETAIL_BOX)
            || hasGeneratedSectionPart(store, spaceUuid, sourceKey, PartKind.NATIVE_VOXELS);
    }

    private static boolean hasGeneratedSectionPart(@Nonnull Store<PhysicsStore> store,
        @Nonnull UUID spaceUuid,
        @Nonnull String sourceKey,
        @Nonnull PartKind partKind) {
        Ref<PhysicsStore> ref = store.getExternalData().getRefFromUUID(
            ChunkCollisionMutationDrainSystem.chunkCollisionBodyUuid(spaceUuid,
                sourceKey,
                partKind,
                0));
        return ref != null && ref.isValid();
    }

    private static void clearRestoreDependency(@Nonnull CommandBuffer<PhysicsStore> commandBuffer,
        @Nonnull Ref<PhysicsStore> bodyRef) {
        commandBuffer.removeComponent(bodyRef,
            ChunkCollisionRestoreDependencyComponent.getComponentType());
    }

    @Nonnull
    private static Vector3d vector3d(@Nonnull Vector3f vector) {
        return new Vector3d(vector.x, vector.y, vector.z);
    }

    private record RestorePrewarmKey(@Nonnull PhysicsChunkSpaceSettings settings, int radius) {
    }

    private record GeneratedBodyRow(@Nonnull Ref<PhysicsStore> ref, @Nonnull UUID uuid) {
    }
}
