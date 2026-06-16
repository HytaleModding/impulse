package dev.hytalemodding.impulse.core.internal.physicsstore.resources;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Resource;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.api.BackendId;
import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.ShapeType;
import dev.hytalemodding.impulse.api.runtime.PhysicsBackendRuntime;
import dev.hytalemodding.impulse.core.internal.resources.BackendBodyHandle;
import dev.hytalemodding.impulse.core.internal.resources.BackendJointHandle;
import dev.hytalemodding.impulse.core.internal.resources.BackendSpaceHandle;
import dev.hytalemodding.impulse.core.plugin.physicsstore.PhysicsStoreTypes;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import lombok.Getter;
import lombok.Setter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongConsumer;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Runtime-only backend bindings for PhysicsStore spaces, bodies, and joints.
 */
public final class PhysicsRuntimeResource implements Resource<PhysicsStore> {

    @Nonnull
    private final Map<BackendId, PhysicsBackendRuntime> runtimesByBackend =
        new Object2ObjectOpenHashMap<>();
    @Nonnull
    private final Map<UUID, BackendSpaceHandle> spaceHandlesByUuid =
        new Object2ObjectOpenHashMap<>();
    @Nonnull
    private final Map<UUID, BackendId> backendIdsBySpaceUuid =
        new Object2ObjectOpenHashMap<>();
    @Nonnull
    private final Map<UUID, Ref<PhysicsStore>> spaceRefsByUuid =
        new Object2ObjectOpenHashMap<>();
    @Nonnull
    private final Map<Ref<PhysicsStore>, UUID> spaceUuidsByRef =
        new Object2ObjectOpenHashMap<>();
    @Nonnull
    private final Map<Ref<PhysicsStore>, BackendSpaceHandle> spaceHandlesByRef =
        new Object2ObjectOpenHashMap<>();
    @Nonnull
    private final Map<Ref<PhysicsStore>, BackendId> backendIdsBySpaceRef =
        new Object2ObjectOpenHashMap<>();
    @Nonnull
    private final Map<UUID, BackendBodyHandle> bodyHandlesByUuid =
        new Object2ObjectOpenHashMap<>();
    @Nonnull
    private final Map<UUID, BackendSpaceHandle> bodySpaceHandlesByUuid =
        new Object2ObjectOpenHashMap<>();
    @Nonnull
    private final Map<Ref<PhysicsStore>, BackendBodyHandle> bodyHandlesByRef =
        new Object2ObjectOpenHashMap<>();
    @Nonnull
    private final Map<Ref<PhysicsStore>, BackendSpaceHandle> bodySpaceHandlesByRef =
        new Object2ObjectOpenHashMap<>();
    @Nonnull
    private final Map<UUID, BackendJointHandle> jointHandlesByUuid =
        new Object2ObjectOpenHashMap<>();
    @Nonnull
    private final Map<UUID, BackendSpaceHandle> jointSpaceHandlesByUuid =
        new Object2ObjectOpenHashMap<>();
    @Nonnull
    private final Map<UUID, Ref<PhysicsStore>> jointRefsByUuid =
        new Object2ObjectOpenHashMap<>();
    @Nonnull
    private final Map<Ref<PhysicsStore>, BackendJointHandle> jointHandlesByRef =
        new Object2ObjectOpenHashMap<>();
    @Nonnull
    private final Map<Ref<PhysicsStore>, BackendSpaceHandle> jointSpaceHandlesByRef =
        new Object2ObjectOpenHashMap<>();
    @Nonnull
    private final Map<UUID, LongList> terrainBodyHandlesByUuid =
        new Object2ObjectOpenHashMap<>();
    @Nonnull
    private final Map<UUID, BackendBodyHandle> terrainVoxelBodyHandlesByUuid =
        new Object2ObjectOpenHashMap<>();
    @Nonnull
    private final Map<UUID, BackendSpaceHandle> terrainSpaceHandlesByUuid =
        new Object2ObjectOpenHashMap<>();
    @Nonnull
    private final Map<UUID, String> terrainPayloadKeysByUuid =
        new Object2ObjectOpenHashMap<>();
    @Nonnull
    private final Map<UUID, Ref<PhysicsStore>> terrainRefsByUuid =
        new Object2ObjectOpenHashMap<>();
    @Nonnull
    private final Map<Ref<PhysicsStore>, LongList> terrainBodyHandlesByRef =
        new Object2ObjectOpenHashMap<>();
    @Nonnull
    private final Map<Ref<PhysicsStore>, BackendBodyHandle> terrainVoxelBodyHandlesByRef =
        new Object2ObjectOpenHashMap<>();
    @Nonnull
    private final Map<Ref<PhysicsStore>, BackendSpaceHandle> terrainSpaceHandlesByRef =
        new Object2ObjectOpenHashMap<>();
    @Nonnull
    private final Map<Ref<PhysicsStore>, String> terrainPayloadKeysByRef =
        new Object2ObjectOpenHashMap<>();
    @Nonnull
    private final Int2ObjectOpenHashMap<LongList> bodyHandlesBySpaceHandle =
        new Int2ObjectOpenHashMap<>();
    @Nonnull
    private final Long2ObjectOpenHashMap<BodyHitMetadata> bodyHitMetadataByHandle =
        new Long2ObjectOpenHashMap<>();
    @Nonnull
    private final Long2ObjectOpenHashMap<BodySnapshotMetadata> bodySnapshotMetadataByHandle =
        new Long2ObjectOpenHashMap<>();
    @Nonnull
    private final List<PendingBodyOperation> pendingBodyOperations = new ArrayList<>();
    @Nonnull
    private final ObjectOpenHashSet<Ref<PhysicsStore>> pendingSpaceSettings =
        new ObjectOpenHashSet<>();
    @Setter
    @Getter
    private boolean started;

    public PhysicsRuntimeResource() {
    }

    public void putRuntime(@Nonnull BackendId backendId, @Nonnull PhysicsBackendRuntime runtime) {
        runtimesByBackend.put(backendId, runtime);
    }

    @Nullable
    public PhysicsBackendRuntime getRuntime(@Nonnull BackendId backendId) {
        return runtimesByBackend.get(backendId);
    }

    public void putSpaceBinding(@Nonnull UUID spaceUuid,
        @Nonnull Ref<PhysicsStore> spaceRef,
        @Nonnull BackendId backendId,
        @Nonnull BackendSpaceHandle handle) {
        Ref<PhysicsStore> checkedSpaceRef = Objects.requireNonNull(spaceRef, "spaceRef");
        Ref<PhysicsStore> previousRef = spaceRefsByUuid.remove(spaceUuid);
        if (previousRef != null) {
            spaceUuidsByRef.remove(previousRef);
            backendIdsBySpaceRef.remove(previousRef);
            spaceHandlesByRef.remove(previousRef);
        }
        backendIdsBySpaceUuid.put(spaceUuid, backendId);
        spaceHandlesByUuid.put(spaceUuid, handle);
        spaceRefsByUuid.put(spaceUuid, checkedSpaceRef);
        spaceUuidsByRef.put(checkedSpaceRef, spaceUuid);
        backendIdsBySpaceRef.put(checkedSpaceRef, backendId);
        spaceHandlesByRef.put(checkedSpaceRef, handle);
    }

    @Nullable
    public BackendSpaceHandle getSpaceHandle(@Nonnull Ref<PhysicsStore> spaceRef) {
        return spaceHandlesByRef.get(spaceRef);
    }

    @Nullable
    public UUID getSpaceUuid(@Nonnull Ref<PhysicsStore> spaceRef) {
        return spaceUuidsByRef.get(spaceRef);
    }

    @Nullable
    public BackendId getSpaceBackendId(@Nonnull Ref<PhysicsStore> spaceRef) {
        return backendIdsBySpaceRef.get(spaceRef);
    }

    public void removeSpaceHandle(@Nonnull UUID spaceUuid) {
        BackendSpaceHandle removed = spaceHandlesByUuid.remove(spaceUuid);
        backendIdsBySpaceUuid.remove(spaceUuid);
        Ref<PhysicsStore> spaceRef = spaceRefsByUuid.remove(spaceUuid);
        if (spaceRef != null) {
            spaceUuidsByRef.remove(spaceRef);
            spaceHandlesByRef.remove(spaceRef);
            backendIdsBySpaceRef.remove(spaceRef);
        }
        if (removed != null) {
            LongList bodyHandles = bodyHandlesBySpaceHandle.remove(removed.value());
            if (bodyHandles != null) {
                bodyHandles.forEach((long bodyHandle) -> {
                    bodyHitMetadataByHandle.remove(bodyHandle);
                    BodySnapshotMetadata metadata = bodySnapshotMetadataByHandle.remove(bodyHandle);
                    if (metadata != null) {
                        bodyHandlesByRef.remove(metadata.bodyRef());
                        bodySpaceHandlesByRef.remove(metadata.bodyRef());
                    }
                });
            }
            removeTerrainHandlesForSpace(removed);
        }
    }

    public void putBodyHandle(@Nonnull UUID bodyUuid,
        @Nonnull Ref<PhysicsStore> bodyRef,
        @Nonnull UUID spaceUuid,
        @Nonnull BackendSpaceHandle spaceHandle,
        @Nonnull BackendBodyHandle handle) {
        bodyHandlesByUuid.put(bodyUuid, handle);
        bodySpaceHandlesByUuid.put(bodyUuid, spaceHandle);
        bodyHandlesByRef.put(bodyRef, handle);
        bodySpaceHandlesByRef.put(bodyRef, spaceHandle);
        bodyHandlesBySpaceHandle.computeIfAbsent(spaceHandle.value(), _ -> new LongArrayList())
            .add(handle.value());
        bodySnapshotMetadataByHandle.put(handle.value(),
            new BodySnapshotMetadata(bodyUuid, bodyRef, spaceUuid));
    }

    @Nullable
    public BackendBodyHandle getBodyHandle(@Nonnull Ref<PhysicsStore> bodyRef) {
        return bodyHandlesByRef.get(bodyRef);
    }

    @Nullable
    public BackendSpaceHandle getBodySpaceHandle(@Nonnull Ref<PhysicsStore> bodyRef) {
        return bodySpaceHandlesByRef.get(bodyRef);
    }

    public void removeBodyHandle(@Nonnull UUID bodyUuid, @Nonnull Ref<PhysicsStore> bodyRef) {
        BackendBodyHandle removed = bodyHandlesByUuid.remove(bodyUuid);
        BackendSpaceHandle spaceHandle = bodySpaceHandlesByUuid.remove(bodyUuid);
        BackendBodyHandle removedByRef = bodyHandlesByRef.remove(bodyRef);
        BackendSpaceHandle spaceHandleByRef = bodySpaceHandlesByRef.remove(bodyRef);
        removeBodyHandleIndexes(removed != null ? removed : removedByRef,
            spaceHandle != null ? spaceHandle : spaceHandleByRef);
    }

    @Nonnull
    public List<Ref<PhysicsStore>> bodyRefsForSpaceHandle(
        @Nonnull BackendSpaceHandle spaceHandle) {
        List<Ref<PhysicsStore>> bodyRefs = new ArrayList<>();
        int targetSpaceHandle = spaceHandle.value();
        bodySpaceHandlesByRef.forEach((bodyRef, handle) -> {
            if (handle.value() == targetSpaceHandle) {
                bodyRefs.add(bodyRef);
            }
        });
        return bodyRefs;
    }

    public void putBodyHitMetadata(@Nonnull BackendBodyHandle handle,
        @Nullable Ref<PhysicsStore> bodyRef,
        @Nonnull PhysicsBodyType bodyType,
        @Nonnull ShapeType shapeType) {
        bodyHitMetadataByHandle.put(handle.value(),
            new BodyHitMetadata(bodyRef, bodyType, shapeType));
    }

    @Nullable
    public BodyHitMetadata getBodyHitMetadata(@Nonnull BackendBodyHandle handle) {
        return getBodyHitMetadata(handle.value());
    }

    @Nullable
    public BodyHitMetadata getBodyHitMetadata(long bodyHandle) {
        return bodyHitMetadataByHandle.get(bodyHandle);
    }

    public void removeBodyHitMetadata(@Nonnull BackendBodyHandle handle) {
        bodyHitMetadataByHandle.remove(handle.value());
    }

    @Nullable
    public BodySnapshotMetadata getBodySnapshotMetadata(long bodyHandle) {
        return bodySnapshotMetadataByHandle.get(bodyHandle);
    }

    public void enqueuePendingBodyOperation(@Nonnull PendingBodyOperation operation) {
        pendingBodyOperations.add(Objects.requireNonNull(operation, "operation"));
    }

    public void markSpaceSettingsPending(@Nonnull Ref<PhysicsStore> spaceRef) {
        pendingSpaceSettings.add(Objects.requireNonNull(spaceRef, "spaceRef"));
    }

    public void clearPendingSpaceSettings(@Nonnull Ref<PhysicsStore> spaceRef) {
        pendingSpaceSettings.remove(spaceRef);
    }

    @Nonnull
    public Set<Ref<PhysicsStore>> drainPendingSpaceSettings() {
        if (pendingSpaceSettings.isEmpty()) {
            return Set.of();
        }
        Set<Ref<PhysicsStore>> drained = new ObjectOpenHashSet<>(pendingSpaceSettings);
        pendingSpaceSettings.clear();
        return drained;
    }

    @Nonnull
    public List<PendingBodyOperation> drainPendingBodyOperations() {
        if (pendingBodyOperations.isEmpty()) {
            return List.of();
        }
        List<PendingBodyOperation> drained = new ArrayList<>(pendingBodyOperations);
        pendingBodyOperations.clear();
        return drained;
    }

    private void putJointHandle(@Nonnull UUID jointUuid,
        @Nonnull Ref<PhysicsStore> jointRef,
        @Nonnull BackendSpaceHandle spaceHandle,
        @Nonnull BackendJointHandle handle) {
        Ref<PhysicsStore> checkedJointRef = Objects.requireNonNull(jointRef, "jointRef");
        Ref<PhysicsStore> previousRef = jointRefsByUuid.remove(jointUuid);
        if (previousRef != null) {
            jointHandlesByRef.remove(previousRef);
            jointSpaceHandlesByRef.remove(previousRef);
        }
        jointHandlesByUuid.put(jointUuid, handle);
        jointSpaceHandlesByUuid.put(jointUuid, spaceHandle);
        jointRefsByUuid.put(jointUuid, checkedJointRef);
        jointHandlesByRef.put(checkedJointRef, handle);
        jointSpaceHandlesByRef.put(checkedJointRef, spaceHandle);
    }

    public void putJointHandle(@Nonnull Ref<PhysicsStore> jointRef,
        @Nonnull UUID jointUuid,
        @Nonnull BackendSpaceHandle spaceHandle,
        @Nonnull BackendJointHandle handle) {
        putJointHandle(jointUuid, jointRef, spaceHandle, handle);
    }

    @Nullable
    public BackendJointHandle getJointHandle(@Nonnull Ref<PhysicsStore> jointRef) {
        return jointHandlesByRef.get(jointRef);
    }

    @Nullable
    public BackendSpaceHandle getJointSpaceHandle(@Nonnull Ref<PhysicsStore> jointRef) {
        return jointSpaceHandlesByRef.get(jointRef);
    }

    public void removeJointHandle(@Nonnull UUID jointUuid) {
        jointHandlesByUuid.remove(jointUuid);
        jointSpaceHandlesByUuid.remove(jointUuid);
        Ref<PhysicsStore> jointRef = jointRefsByUuid.remove(jointUuid);
        if (jointRef != null) {
            jointHandlesByRef.remove(jointRef);
            jointSpaceHandlesByRef.remove(jointRef);
        }
    }

    public void removeJointHandle(@Nonnull UUID jointUuid,
        @Nonnull Ref<PhysicsStore> jointRef) {
        removeJointHandle(jointUuid);
        jointHandlesByRef.remove(jointRef);
        jointSpaceHandlesByRef.remove(jointRef);
    }

    @Nonnull
    public List<Ref<PhysicsStore>> jointRefsForSpaceHandle(
        @Nonnull BackendSpaceHandle spaceHandle) {
        List<Ref<PhysicsStore>> jointRefs = new ArrayList<>();
        int targetSpaceHandle = spaceHandle.value();
        jointSpaceHandlesByRef.forEach((jointRef, handle) -> {
            if (handle.value() == targetSpaceHandle) {
                jointRefs.add(jointRef);
            }
        });
        return jointRefs;
    }

    public void putTerrainBodyHandle(@Nonnull UUID terrainUuid,
        @Nonnull BackendSpaceHandle spaceHandle,
        @Nonnull BackendBodyHandle handle,
        boolean voxelTerrainBody) {
        putTerrainBodyHandle(terrainUuid, null, spaceHandle, handle, voxelTerrainBody);
    }

    public void putTerrainBodyHandle(@Nonnull UUID terrainUuid,
        @Nullable Ref<PhysicsStore> terrainRef,
        @Nonnull BackendSpaceHandle spaceHandle,
        @Nonnull BackendBodyHandle handle,
        boolean voxelTerrainBody) {
        bindTerrainRef(terrainUuid, terrainRef);
        terrainSpaceHandlesByUuid.put(terrainUuid, spaceHandle);
        terrainBodyHandlesByUuid.computeIfAbsent(terrainUuid, _ -> new LongArrayList())
            .add(handle.value());
        if (voxelTerrainBody) {
            terrainVoxelBodyHandlesByUuid.put(terrainUuid, handle);
        }
        if (terrainRef != null) {
            terrainSpaceHandlesByRef.put(terrainRef, spaceHandle);
            terrainBodyHandlesByRef.computeIfAbsent(terrainRef, _ -> new LongArrayList())
                .add(handle.value());
            if (voxelTerrainBody) {
                terrainVoxelBodyHandlesByRef.put(terrainRef, handle);
            }
        }
    }

    public void putTerrainBodyHandle(@Nonnull Ref<PhysicsStore> terrainRef,
        @Nonnull UUID terrainUuid,
        @Nonnull BackendSpaceHandle spaceHandle,
        @Nonnull BackendBodyHandle handle,
        boolean voxelTerrainBody) {
        putTerrainBodyHandle(terrainUuid, terrainRef, spaceHandle, handle, voxelTerrainBody);
    }

    public void markTerrainPayloadBound(@Nonnull UUID terrainUuid, @Nonnull String payloadKey) {
        terrainPayloadKeysByUuid.put(terrainUuid, payloadKey);
    }

    public void markTerrainPayloadBound(@Nonnull Ref<PhysicsStore> terrainRef,
        @Nonnull UUID terrainUuid,
        @Nonnull String payloadKey) {
        bindTerrainRef(terrainUuid, terrainRef);
        terrainPayloadKeysByUuid.put(terrainUuid, payloadKey);
        terrainPayloadKeysByRef.put(terrainRef, payloadKey);
    }

    public boolean isTerrainPayloadBound(@Nonnull UUID terrainUuid, @Nonnull String payloadKey) {
        return payloadKey.equals(terrainPayloadKeysByUuid.get(terrainUuid));
    }

    public boolean isTerrainPayloadBound(@Nonnull Ref<PhysicsStore> terrainRef,
        @Nonnull String payloadKey) {
        return payloadKey.equals(terrainPayloadKeysByRef.get(terrainRef));
    }

    public boolean hasTerrainBodyHandles(@Nonnull Ref<PhysicsStore> terrainRef) {
        LongList bodyHandles = terrainBodyHandlesByRef.get(terrainRef);
        return bodyHandles != null && !bodyHandles.isEmpty();
    }

    @Nullable
    public BackendSpaceHandle getTerrainSpaceHandle(@Nonnull Ref<PhysicsStore> terrainRef) {
        return terrainSpaceHandlesByRef.get(terrainRef);
    }

    @Nullable
    public BackendBodyHandle getTerrainVoxelBodyHandle(@Nonnull Ref<PhysicsStore> terrainRef) {
        return terrainVoxelBodyHandlesByRef.get(terrainRef);
    }

    public void forEachTerrainBodyHandle(@Nonnull Ref<PhysicsStore> terrainRef,
        @Nonnull LongConsumer consumer) {
        LongList bodyHandles = terrainBodyHandlesByRef.get(terrainRef);
        if (bodyHandles == null) {
            return;
        }
        bodyHandles.forEach(consumer);
    }

    public void removeTerrainHandles(@Nonnull UUID terrainUuid) {
        LongList bodyHandles = terrainBodyHandlesByUuid.remove(terrainUuid);
        if (bodyHandles != null) {
            bodyHandles.forEach(bodyHitMetadataByHandle::remove);
        }
        terrainVoxelBodyHandlesByUuid.remove(terrainUuid);
        terrainSpaceHandlesByUuid.remove(terrainUuid);
        terrainPayloadKeysByUuid.remove(terrainUuid);
        Ref<PhysicsStore> terrainRef = terrainRefsByUuid.remove(terrainUuid);
        if (terrainRef != null) {
            removeTerrainRefMaps(terrainRef);
        }
    }

    public void removeTerrainHandles(@Nonnull UUID terrainUuid,
        @Nonnull Ref<PhysicsStore> terrainRef) {
        removeTerrainHandles(terrainUuid);
        removeTerrainRefMaps(terrainRef);
    }

    public void removeTerrainHandles(@Nonnull Ref<PhysicsStore> terrainRef,
        @Nonnull UUID terrainUuid) {
        removeTerrainHandles(terrainUuid, terrainRef);
    }

    @Nonnull
    public List<Ref<PhysicsStore>> terrainRefsForSpaceHandle(
        @Nonnull BackendSpaceHandle spaceHandle) {
        List<Ref<PhysicsStore>> terrainRefs = new ArrayList<>();
        int targetSpaceHandle = spaceHandle.value();
        terrainSpaceHandlesByRef.forEach((terrainRef, handle) -> {
            if (handle.value() == targetSpaceHandle) {
                terrainRefs.add(terrainRef);
            }
        });
        return terrainRefs;
    }

    public void forEachRuntimeSpaceBinding(@Nonnull RuntimeSpaceBindingConsumer consumer) {
        spaceHandlesByRef.forEach((spaceRef, spaceHandle) -> {
            BackendId backendId = backendIdsBySpaceRef.get(spaceRef);
            PhysicsBackendRuntime runtime = backendId != null ? runtimesByBackend.get(backendId) : null;
            if (backendId != null && runtime != null) {
                consumer.accept(spaceRef, backendId, spaceHandle, runtime);
            }
        });
    }

    public void forEachBodyHandle(@Nonnull BackendSpaceHandle spaceHandle,
        @Nonnull LongConsumer consumer) {
        LongList bodyHandles = bodyHandlesBySpaceHandle.get(spaceHandle.value());
        if (bodyHandles == null) {
            return;
        }
        bodyHandles.forEach(consumer);
    }

    public void clear() {
        runtimesByBackend.clear();
        spaceHandlesByUuid.clear();
        backendIdsBySpaceUuid.clear();
        spaceRefsByUuid.clear();
        spaceUuidsByRef.clear();
        spaceHandlesByRef.clear();
        backendIdsBySpaceRef.clear();
        bodyHandlesByUuid.clear();
        bodySpaceHandlesByUuid.clear();
        bodyHandlesByRef.clear();
        bodySpaceHandlesByRef.clear();
        jointHandlesByUuid.clear();
        jointSpaceHandlesByUuid.clear();
        jointRefsByUuid.clear();
        jointHandlesByRef.clear();
        jointSpaceHandlesByRef.clear();
        terrainBodyHandlesByUuid.clear();
        terrainVoxelBodyHandlesByUuid.clear();
        terrainSpaceHandlesByUuid.clear();
        terrainPayloadKeysByUuid.clear();
        terrainRefsByUuid.clear();
        terrainBodyHandlesByRef.clear();
        terrainVoxelBodyHandlesByRef.clear();
        terrainSpaceHandlesByRef.clear();
        terrainPayloadKeysByRef.clear();
        bodyHandlesBySpaceHandle.clear();
        bodyHitMetadataByHandle.clear();
        bodySnapshotMetadataByHandle.clear();
        pendingBodyOperations.clear();
        pendingSpaceSettings.clear();
        started = false;
    }

    public void clearTransientBodyOperations() {
        pendingBodyOperations.clear();
    }

    private void removeBodyHandleIndexes(@Nullable BackendBodyHandle removed,
        @Nullable BackendSpaceHandle spaceHandle) {
        if (removed == null || spaceHandle == null) {
            return;
        }
        LongList bodyHandles = bodyHandlesBySpaceHandle.get(spaceHandle.value());
        if (bodyHandles != null) {
            bodyHandles.rem(removed.value());
            if (bodyHandles.isEmpty()) {
                bodyHandlesBySpaceHandle.remove(spaceHandle.value());
            }
        }
        bodyHitMetadataByHandle.remove(removed.value());
        BodySnapshotMetadata metadata = bodySnapshotMetadataByHandle.remove(removed.value());
        if (metadata != null) {
            bodyHandlesByRef.remove(metadata.bodyRef());
            bodySpaceHandlesByRef.remove(metadata.bodyRef());
        }
    }

    public void destroyBackendBindings() {
        RuntimeException failure = null;
        for (Map.Entry<UUID, BackendJointHandle> entry
            : new ArrayList<>(jointHandlesByUuid.entrySet())) {
            BackendSpaceHandle spaceHandle = jointSpaceHandlesByUuid.get(entry.getKey());
            PhysicsBackendRuntime runtime = runtimeForSpaceHandle(spaceHandle);
            if (spaceHandle == null || runtime == null) {
                continue;
            }
            try {
                runtime.removeJoint(spaceHandle.value(), entry.getValue().value());
            } catch (RuntimeException exception) {
                failure = appendShutdownFailure(failure, exception);
            }
        }
        for (Map.Entry<UUID, LongList> entry
            : new ArrayList<>(terrainBodyHandlesByUuid.entrySet())) {
            BackendSpaceHandle spaceHandle = terrainSpaceHandlesByUuid.get(entry.getKey());
            PhysicsBackendRuntime runtime = runtimeForSpaceHandle(spaceHandle);
            if (spaceHandle == null || runtime == null) {
                continue;
            }
            LongList bodyHandles = new LongArrayList(entry.getValue());
            for (int index = 0; index < bodyHandles.size(); index++) {
                try {
                    runtime.removeBody(spaceHandle.value(), bodyHandles.getLong(index));
                } catch (RuntimeException exception) {
                    failure = appendShutdownFailure(failure, exception);
                }
            }
        }
        for (Map.Entry<UUID, BackendBodyHandle> entry
            : new ArrayList<>(bodyHandlesByUuid.entrySet())) {
            BackendSpaceHandle spaceHandle = bodySpaceHandlesByUuid.get(entry.getKey());
            PhysicsBackendRuntime runtime = runtimeForSpaceHandle(spaceHandle);
            if (spaceHandle == null || runtime == null) {
                continue;
            }
            try {
                runtime.removeBody(spaceHandle.value(), entry.getValue().value());
            } catch (RuntimeException exception) {
                failure = appendShutdownFailure(failure, exception);
            }
        }
        for (Map.Entry<UUID, BackendSpaceHandle> entry
            : new ArrayList<>(spaceHandlesByUuid.entrySet())) {
            BackendId backendId = backendIdsBySpaceUuid.get(entry.getKey());
            PhysicsBackendRuntime runtime = backendId != null ? runtimesByBackend.get(backendId) : null;
            if (runtime == null) {
                continue;
            }
            try {
                runtime.destroySpace(entry.getValue().value());
            } catch (RuntimeException exception) {
                failure = appendShutdownFailure(failure, exception);
            }
        }
        clear();
        if (failure != null) {
            throw failure;
        }
    }

    @Nullable
    public PhysicsBackendRuntime runtimeForSpaceHandle(@Nullable BackendSpaceHandle target) {
        if (target == null) {
            return null;
        }
        for (Map.Entry<UUID, BackendSpaceHandle> entry : spaceHandlesByUuid.entrySet()) {
            if (entry.getValue().value() != target.value()) {
                continue;
            }
            BackendId backendId = backendIdsBySpaceUuid.get(entry.getKey());
            return backendId != null ? runtimesByBackend.get(backendId) : null;
        }
        return null;
    }

    @Nonnull
    private static RuntimeException appendShutdownFailure(@Nullable RuntimeException failure,
        @Nonnull RuntimeException exception) {
        if (failure == null) {
            return exception;
        }
        failure.addSuppressed(exception);
        return failure;
    }

    @Nonnull
    @Override
    public PhysicsRuntimeResource clone() {
        PhysicsRuntimeResource copy = new PhysicsRuntimeResource();
        copy.runtimesByBackend.putAll(runtimesByBackend);
        copy.spaceHandlesByUuid.putAll(spaceHandlesByUuid);
        copy.backendIdsBySpaceUuid.putAll(backendIdsBySpaceUuid);
        copy.spaceRefsByUuid.putAll(spaceRefsByUuid);
        copy.spaceUuidsByRef.putAll(spaceUuidsByRef);
        copy.spaceHandlesByRef.putAll(spaceHandlesByRef);
        copy.backendIdsBySpaceRef.putAll(backendIdsBySpaceRef);
        copy.bodyHandlesByUuid.putAll(bodyHandlesByUuid);
        copy.bodySpaceHandlesByUuid.putAll(bodySpaceHandlesByUuid);
        copy.bodyHandlesByRef.putAll(bodyHandlesByRef);
        copy.bodySpaceHandlesByRef.putAll(bodySpaceHandlesByRef);
        copy.jointHandlesByUuid.putAll(jointHandlesByUuid);
        copy.jointSpaceHandlesByUuid.putAll(jointSpaceHandlesByUuid);
        copy.jointRefsByUuid.putAll(jointRefsByUuid);
        copy.jointHandlesByRef.putAll(jointHandlesByRef);
        copy.jointSpaceHandlesByRef.putAll(jointSpaceHandlesByRef);
        terrainBodyHandlesByUuid.forEach((terrainUuid, bodyHandles) ->
            copy.terrainBodyHandlesByUuid.put(terrainUuid, new LongArrayList(bodyHandles)));
        copy.terrainVoxelBodyHandlesByUuid.putAll(terrainVoxelBodyHandlesByUuid);
        copy.terrainSpaceHandlesByUuid.putAll(terrainSpaceHandlesByUuid);
        copy.terrainPayloadKeysByUuid.putAll(terrainPayloadKeysByUuid);
        copy.terrainRefsByUuid.putAll(terrainRefsByUuid);
        terrainBodyHandlesByRef.forEach((terrainRef, bodyHandles) ->
            copy.terrainBodyHandlesByRef.put(terrainRef, new LongArrayList(bodyHandles)));
        copy.terrainVoxelBodyHandlesByRef.putAll(terrainVoxelBodyHandlesByRef);
        copy.terrainSpaceHandlesByRef.putAll(terrainSpaceHandlesByRef);
        copy.terrainPayloadKeysByRef.putAll(terrainPayloadKeysByRef);
        bodyHandlesBySpaceHandle.forEach((spaceHandle, bodyHandles) ->
            copy.bodyHandlesBySpaceHandle.put((int) spaceHandle, new LongArrayList(bodyHandles)));
        copy.bodyHitMetadataByHandle.putAll(bodyHitMetadataByHandle);
        copy.bodySnapshotMetadataByHandle.putAll(bodySnapshotMetadataByHandle);
        copy.pendingBodyOperations.addAll(pendingBodyOperations);
        copy.pendingSpaceSettings.addAll(pendingSpaceSettings);
        copy.started = started;
        return copy;
    }

    @Nonnull
    public static ResourceType<PhysicsStore, PhysicsRuntimeResource> getResourceType() {
        return PhysicsStoreTypes.runtimeResourceType();
    }

    @FunctionalInterface
    public interface RuntimeSpaceBindingConsumer {

        void accept(@Nonnull Ref<PhysicsStore> spaceRef,
            @Nonnull BackendId backendId,
            @Nonnull BackendSpaceHandle spaceHandle,
            @Nonnull PhysicsBackendRuntime runtime);
    }

    public record BodyHitMetadata(@Nullable Ref<PhysicsStore> bodyRef,
                                  @Nonnull PhysicsBodyType bodyType,
                                  @Nonnull ShapeType shapeType) {

        public BodyHitMetadata {
            Objects.requireNonNull(bodyType, "bodyType");
            Objects.requireNonNull(shapeType, "shapeType");
        }
    }

    public record BodySnapshotMetadata(@Nonnull UUID bodyUuid,
                                       @Nonnull Ref<PhysicsStore> bodyRef,
                                       @Nonnull UUID spaceUuid) {

        public BodySnapshotMetadata {
            Objects.requireNonNull(bodyUuid, "bodyUuid");
            Objects.requireNonNull(bodyRef, "bodyRef");
            Objects.requireNonNull(spaceUuid, "spaceUuid");
        }
    }

    public record PendingBodyOperation(@Nonnull Kind kind,
                                       @Nonnull UUID bodyUuid,
                                       @Nonnull Ref<PhysicsStore> bodyRef,
                                       @Nullable BackendSpaceHandle spaceHandle,
                                       @Nullable BackendBodyHandle bodyHandle,
                                       float x,
                                       float y,
                                       float z,
                                       boolean hasOffset,
                                       float offsetX,
                                       float offsetY,
                                       float offsetZ) {

        public PendingBodyOperation {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(bodyUuid, "bodyUuid");
            Objects.requireNonNull(bodyRef, "bodyRef");
        }

        @Nonnull
        public static PendingBodyOperation wake(@Nonnull UUID bodyUuid,
            @Nonnull Ref<PhysicsStore> bodyRef,
            @Nullable BackendSpaceHandle spaceHandle,
            @Nullable BackendBodyHandle bodyHandle) {
            return empty(Kind.WAKE, bodyUuid, bodyRef, spaceHandle, bodyHandle);
        }

        @Nonnull
        public static PendingBodyOperation sleep(@Nonnull UUID bodyUuid,
            @Nonnull Ref<PhysicsStore> bodyRef,
            @Nullable BackendSpaceHandle spaceHandle,
            @Nullable BackendBodyHandle bodyHandle) {
            return empty(Kind.SLEEP, bodyUuid, bodyRef, spaceHandle, bodyHandle);
        }

        @Nonnull
        public static PendingBodyOperation vector(@Nonnull Kind kind,
            @Nonnull UUID bodyUuid,
            @Nonnull Ref<PhysicsStore> bodyRef,
            @Nullable BackendSpaceHandle spaceHandle,
            @Nullable BackendBodyHandle bodyHandle,
            float x,
            float y,
            float z,
            boolean hasOffset,
            float offsetX,
            float offsetY,
            float offsetZ) {
            return new PendingBodyOperation(kind,
                bodyUuid,
                bodyRef,
                spaceHandle,
                bodyHandle,
                x,
                y,
                z,
                hasOffset,
                offsetX,
                offsetY,
                offsetZ);
        }

        @Nonnull
        private static PendingBodyOperation empty(@Nonnull Kind kind,
            @Nonnull UUID bodyUuid,
            @Nonnull Ref<PhysicsStore> bodyRef,
            @Nullable BackendSpaceHandle spaceHandle,
            @Nullable BackendBodyHandle bodyHandle) {
            return new PendingBodyOperation(kind,
                bodyUuid,
                bodyRef,
                spaceHandle,
                bodyHandle,
                0.0f,
                0.0f,
                0.0f,
                false,
                0.0f,
                0.0f,
                0.0f);
        }

        public enum Kind {
            WAKE,
            SLEEP,
            IMPULSE,
            TORQUE_IMPULSE,
            FORCE,
            TORQUE
        }
    }

    private void removeTerrainHandlesForSpace(@Nonnull BackendSpaceHandle spaceHandle) {
        terrainSpaceHandlesByUuid.entrySet().removeIf(entry -> {
            if (entry.getValue().value() != spaceHandle.value()) {
                return false;
            }
            UUID terrainUuid = entry.getKey();
            LongList bodyHandles = terrainBodyHandlesByUuid.get(terrainUuid);
            if (bodyHandles != null) {
                bodyHandles.forEach(bodyHitMetadataByHandle::remove);
            }
            terrainBodyHandlesByUuid.remove(terrainUuid);
            terrainVoxelBodyHandlesByUuid.remove(terrainUuid);
            terrainPayloadKeysByUuid.remove(terrainUuid);
            Ref<PhysicsStore> terrainRef = terrainRefsByUuid.remove(terrainUuid);
            if (terrainRef != null) {
                removeTerrainRefMaps(terrainRef);
            }
            return true;
        });
    }

    private void bindTerrainRef(@Nonnull UUID terrainUuid,
        @Nullable Ref<PhysicsStore> terrainRef) {
        if (terrainRef == null) {
            return;
        }
        Ref<PhysicsStore> previousRef = terrainRefsByUuid.put(terrainUuid, terrainRef);
        if (previousRef != null && !sameRef(previousRef, terrainRef)) {
            removeTerrainRefMaps(previousRef);
        }
    }

    private void removeTerrainRefMaps(@Nonnull Ref<PhysicsStore> terrainRef) {
        terrainBodyHandlesByRef.remove(terrainRef);
        terrainVoxelBodyHandlesByRef.remove(terrainRef);
        terrainSpaceHandlesByRef.remove(terrainRef);
        terrainPayloadKeysByRef.remove(terrainRef);
    }

    private static boolean sameRef(@Nonnull Ref<PhysicsStore> first,
        @Nonnull Ref<PhysicsStore> second) {
        return first == second || first.equals(second);
    }
}
