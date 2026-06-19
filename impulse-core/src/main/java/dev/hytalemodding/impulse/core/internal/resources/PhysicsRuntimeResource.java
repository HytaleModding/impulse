package dev.hytalemodding.impulse.core.internal.resources;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Resource;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.api.BackendId;
import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.ShapeType;
import dev.hytalemodding.impulse.api.runtime.PhysicsBackendRuntime;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
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
    private final Int2ObjectOpenHashMap<UUID> spaceUuidsByRowIndex =
        new Int2ObjectOpenHashMap<>();
    @Nonnull
    private final Int2ObjectOpenHashMap<BackendSpaceHandle> spaceHandlesByRowIndex =
        new Int2ObjectOpenHashMap<>();
    @Nonnull
    private final Int2ObjectOpenHashMap<BackendId> backendIdsBySpaceRowIndex =
        new Int2ObjectOpenHashMap<>();
    @Nonnull
    private final Int2ObjectOpenHashMap<BackendId> unambiguousBackendIdsBySpaceHandle =
        new Int2ObjectOpenHashMap<>();
    @Nonnull
    private final Int2IntOpenHashMap spaceHandleBindingCounts =
        new Int2IntOpenHashMap();
    @Nonnull
    private final Map<UUID, BackendBodyHandle> bodyHandlesByUuid =
        new Object2ObjectOpenHashMap<>();
    @Nonnull
    private final Map<UUID, BackendSpaceHandle> bodySpaceHandlesByUuid =
        new Object2ObjectOpenHashMap<>();
    @Nonnull
    private final Int2ObjectOpenHashMap<Ref<PhysicsStore>> bodyRefsByRowIndex =
        new Int2ObjectOpenHashMap<>();
    @Nonnull
    private final Int2ObjectOpenHashMap<BackendBodyHandle> bodyHandlesByRowIndex =
        new Int2ObjectOpenHashMap<>();
    @Nonnull
    private final Int2ObjectOpenHashMap<BackendSpaceHandle> bodySpaceHandlesByRowIndex =
        new Int2ObjectOpenHashMap<>();
    @Nonnull
    private final Int2ObjectOpenHashMap<BackendId> backendIdsByBodyRowIndex =
        new Int2ObjectOpenHashMap<>();
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
    private final Int2ObjectOpenHashMap<BackendJointHandle> jointHandlesByRowIndex =
        new Int2ObjectOpenHashMap<>();
    @Nonnull
    private final Int2ObjectOpenHashMap<BackendSpaceHandle> jointSpaceHandlesByRowIndex =
        new Int2ObjectOpenHashMap<>();
    @Nonnull
    private final Int2ObjectOpenHashMap<Ref<PhysicsStore>> jointRefsByRowIndex =
        new Int2ObjectOpenHashMap<>();
    @Nonnull
    private final Int2ObjectOpenHashMap<BackendId> backendIdsByJointRowIndex =
        new Int2ObjectOpenHashMap<>();
    @Nonnull
    private final Int2ObjectOpenHashMap<String> chunkCollisionPayloadKeysByRowIndex =
        new Int2ObjectOpenHashMap<>();
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
    private final Int2ObjectOpenHashMap<Ref<PhysicsStore>> pendingSpaceSettingsByRowIndex =
        new Int2ObjectOpenHashMap<>();
    private long registrationTopologyGeneration;
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
            int previousRowIndex = previousRef.getIndex();
            spaceUuidsByRowIndex.remove(previousRowIndex);
            backendIdsBySpaceRowIndex.remove(previousRowIndex);
            spaceHandlesByRowIndex.remove(previousRowIndex);
        }
        BackendSpaceHandle previousHandle = spaceHandlesByUuid.remove(spaceUuid);
        removeSpaceHandleRuntimeIndex(previousHandle);
        spaceHandlesByUuid.put(spaceUuid, handle);
        int rowIndex = checkedSpaceRef.getIndex();
        backendIdsBySpaceUuid.put(spaceUuid, backendId);
        spaceRefsByUuid.put(spaceUuid, checkedSpaceRef);
        spaceUuidsByRowIndex.put(rowIndex, spaceUuid);
        backendIdsBySpaceRowIndex.put(rowIndex, backendId);
        spaceHandlesByRowIndex.put(rowIndex, handle);
        addSpaceHandleRuntimeIndex(handle, backendId);
    }

    @Nullable
    public BackendSpaceHandle getSpaceHandle(@Nonnull Ref<PhysicsStore> spaceRef) {
        return spaceHandlesByRowIndex.get(spaceRef.getIndex());
    }

    @Nullable
    public UUID getSpaceUuid(@Nonnull Ref<PhysicsStore> spaceRef) {
        return spaceUuidsByRowIndex.get(spaceRef.getIndex());
    }

    @Nullable
    public BackendId getSpaceBackendId(@Nonnull Ref<PhysicsStore> spaceRef) {
        return backendIdsBySpaceRowIndex.get(spaceRef.getIndex());
    }

    public void removeSpaceHandle(@Nonnull UUID spaceUuid) {
        BackendSpaceHandle removed = spaceHandlesByUuid.remove(spaceUuid);
        backendIdsBySpaceUuid.remove(spaceUuid);
        Ref<PhysicsStore> spaceRef = spaceRefsByUuid.remove(spaceUuid);
        if (spaceRef != null) {
            int rowIndex = spaceRef.getIndex();
            spaceUuidsByRowIndex.remove(rowIndex);
            spaceHandlesByRowIndex.remove(rowIndex);
            backendIdsBySpaceRowIndex.remove(rowIndex);
        }
        if (removed != null) {
            removeSpaceHandleRuntimeIndex(removed);
            LongList bodyHandles = bodyHandlesBySpaceHandle.remove(removed.value());
            if (bodyHandles != null) {
                bodyHandles.forEach((long bodyHandle) -> {
                    bodyHitMetadataByHandle.remove(bodyHandle);
                    BodySnapshotMetadata metadata = bodySnapshotMetadataByHandle.remove(bodyHandle);
                    if (metadata != null) {
                        int rowIndex = metadata.bodyRef().getIndex();
                        bodyRefsByRowIndex.remove(rowIndex);
                        bodyHandlesByRowIndex.remove(rowIndex);
                        bodySpaceHandlesByRowIndex.remove(rowIndex);
                        backendIdsByBodyRowIndex.remove(rowIndex);
                        chunkCollisionPayloadKeysByRowIndex.remove(rowIndex);
                    }
                });
            }
            markRegistrationTopologyChanged();
        }
    }

    public void putBodyHandle(@Nonnull UUID bodyUuid,
        @Nonnull Ref<PhysicsStore> bodyRef,
        @Nonnull UUID spaceUuid,
        @Nonnull BackendSpaceHandle spaceHandle,
        @Nonnull BackendBodyHandle handle) {
        bodyHandlesByUuid.put(bodyUuid, handle);
        bodySpaceHandlesByUuid.put(bodyUuid, spaceHandle);
        int rowIndex = bodyRef.getIndex();
        BackendId backendId = backendIdsBySpaceUuid.get(spaceUuid);
        bodyRefsByRowIndex.put(rowIndex, bodyRef);
        bodyHandlesByRowIndex.put(rowIndex, handle);
        bodySpaceHandlesByRowIndex.put(rowIndex, spaceHandle);
        if (backendId != null) {
            backendIdsByBodyRowIndex.put(rowIndex, backendId);
        } else {
            backendIdsByBodyRowIndex.remove(rowIndex);
        }
        bodyHandlesBySpaceHandle.computeIfAbsent(spaceHandle.value(), _ -> new LongArrayList())
            .add(handle.value());
        bodySnapshotMetadataByHandle.put(handle.value(),
            new BodySnapshotMetadata(bodyUuid, bodyRef, spaceUuid));
        markRegistrationTopologyChanged();
    }

    @Nullable
    public BackendBodyHandle getBodyHandle(@Nonnull Ref<PhysicsStore> bodyRef) {
        return bodyHandlesByRowIndex.get(bodyRef.getIndex());
    }

    @Nullable
    public BackendSpaceHandle getBodySpaceHandle(@Nonnull Ref<PhysicsStore> bodyRef) {
        return bodySpaceHandlesByRowIndex.get(bodyRef.getIndex());
    }

    public void removeBodyHandle(@Nonnull UUID bodyUuid, @Nonnull Ref<PhysicsStore> bodyRef) {
        removePendingBodyOperations(bodyRef);
        BackendBodyHandle removed = bodyHandlesByUuid.remove(bodyUuid);
        BackendSpaceHandle spaceHandle = bodySpaceHandlesByUuid.remove(bodyUuid);
        int rowIndex = bodyRef.getIndex();
        bodyRefsByRowIndex.remove(rowIndex);
        BackendBodyHandle removedByRef = bodyHandlesByRowIndex.remove(rowIndex);
        BackendSpaceHandle spaceHandleByRef = bodySpaceHandlesByRowIndex.remove(rowIndex);
        backendIdsByBodyRowIndex.remove(rowIndex);
        chunkCollisionPayloadKeysByRowIndex.remove(rowIndex);
        removeBodyHandleIndexes(removed != null ? removed : removedByRef,
            spaceHandle != null ? spaceHandle : spaceHandleByRef);
        markRegistrationTopologyChanged();
    }

    @Nonnull
    public List<Ref<PhysicsStore>> bodyRefsForSpaceHandle(
        @Nonnull BackendSpaceHandle spaceHandle) {
        List<Ref<PhysicsStore>> bodyRefs = new ArrayList<>();
        int targetSpaceHandle = spaceHandle.value();
        bodySpaceHandlesByRowIndex.forEach((rowIndex, handle) -> {
            if (handle.value() == targetSpaceHandle) {
                Ref<PhysicsStore> bodyRef = bodyRefsByRowIndex.get((int) rowIndex);
                if (bodyRef != null) {
                    bodyRefs.add(bodyRef);
                }
            }
        });
        return bodyRefs;
    }

    public void putBodyHitMetadata(@Nonnull BackendBodyHandle handle,
        @Nullable Ref<PhysicsStore> bodyRef,
        @Nonnull PhysicsBodyType bodyType,
        @Nonnull ShapeType shapeType) {
        BodySnapshotMetadata metadata = bodySnapshotMetadataByHandle.get(handle.value());
        putBodyHitMetadata(handle,
            metadata != null ? metadata.bodyUuid() : new UUID(0L, 0L),
            bodyRef,
            bodyType,
            shapeType);
    }

    public void putBodyHitMetadata(@Nonnull BackendBodyHandle handle,
        @Nonnull UUID bodyUuid,
        @Nullable Ref<PhysicsStore> bodyRef,
        @Nonnull PhysicsBodyType bodyType,
        @Nonnull ShapeType shapeType) {
        bodyHitMetadataByHandle.put(handle.value(),
            new BodyHitMetadata(bodyUuid, bodyRef, bodyType, shapeType));
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
        Ref<PhysicsStore> checkedSpaceRef = Objects.requireNonNull(spaceRef, "spaceRef");
        pendingSpaceSettingsByRowIndex.put(checkedSpaceRef.getIndex(), checkedSpaceRef);
    }

    public void clearPendingSpaceSettings(@Nonnull Ref<PhysicsStore> spaceRef) {
        pendingSpaceSettingsByRowIndex.remove(Objects.requireNonNull(spaceRef, "spaceRef")
            .getIndex());
    }

    @Nonnull
    public Set<Ref<PhysicsStore>> drainPendingSpaceSettings() {
        if (pendingSpaceSettingsByRowIndex.isEmpty()) {
            return Set.of();
        }
        Set<Ref<PhysicsStore>> drained = new ObjectOpenHashSet<>(
            pendingSpaceSettingsByRowIndex.values());
        pendingSpaceSettingsByRowIndex.clear();
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

    public void removePendingBodyOperations(@Nonnull Ref<PhysicsStore> bodyRef) {
        Ref<PhysicsStore> checkedBodyRef = Objects.requireNonNull(bodyRef, "bodyRef");
        pendingBodyOperations.removeIf(operation ->
            operation.bodyRef().getStore() == checkedBodyRef.getStore()
                && operation.bodyRef().getIndex() == checkedBodyRef.getIndex());
    }

    private void putJointHandle(@Nonnull UUID jointUuid,
        @Nonnull Ref<PhysicsStore> jointRef,
        @Nonnull BackendId backendId,
        @Nonnull BackendSpaceHandle spaceHandle,
        @Nonnull BackendJointHandle handle) {
        Ref<PhysicsStore> checkedJointRef = Objects.requireNonNull(jointRef, "jointRef");
        Ref<PhysicsStore> previousRef = jointRefsByUuid.remove(jointUuid);
        if (previousRef != null) {
            int previousRowIndex = previousRef.getIndex();
            jointHandlesByRowIndex.remove(previousRowIndex);
            jointSpaceHandlesByRowIndex.remove(previousRowIndex);
            jointRefsByRowIndex.remove(previousRowIndex);
            backendIdsByJointRowIndex.remove(previousRowIndex);
        }
        int rowIndex = checkedJointRef.getIndex();
        jointHandlesByUuid.put(jointUuid, handle);
        jointSpaceHandlesByUuid.put(jointUuid, spaceHandle);
        jointRefsByUuid.put(jointUuid, checkedJointRef);
        jointHandlesByRowIndex.put(rowIndex, handle);
        jointSpaceHandlesByRowIndex.put(rowIndex, spaceHandle);
        jointRefsByRowIndex.put(rowIndex, checkedJointRef);
        backendIdsByJointRowIndex.put(rowIndex, backendId);
    }

    public void putJointHandle(@Nonnull Ref<PhysicsStore> jointRef,
        @Nonnull UUID jointUuid,
        @Nonnull BackendId backendId,
        @Nonnull BackendSpaceHandle spaceHandle,
        @Nonnull BackendJointHandle handle) {
        putJointHandle(jointUuid, jointRef, backendId, spaceHandle, handle);
    }

    @Nullable
    public BackendJointHandle getJointHandle(@Nonnull Ref<PhysicsStore> jointRef) {
        return jointHandlesByRowIndex.get(jointRef.getIndex());
    }

    @Nullable
    public BackendSpaceHandle getJointSpaceHandle(@Nonnull Ref<PhysicsStore> jointRef) {
        return jointSpaceHandlesByRowIndex.get(jointRef.getIndex());
    }

    public void removeJointHandle(@Nonnull UUID jointUuid) {
        jointHandlesByUuid.remove(jointUuid);
        jointSpaceHandlesByUuid.remove(jointUuid);
        Ref<PhysicsStore> jointRef = jointRefsByUuid.remove(jointUuid);
        if (jointRef != null) {
            int rowIndex = jointRef.getIndex();
            jointHandlesByRowIndex.remove(rowIndex);
            jointSpaceHandlesByRowIndex.remove(rowIndex);
            jointRefsByRowIndex.remove(rowIndex);
            backendIdsByJointRowIndex.remove(rowIndex);
        }
    }

    public void removeJointHandle(@Nonnull UUID jointUuid,
        @Nonnull Ref<PhysicsStore> jointRef) {
        removeJointHandle(jointUuid);
        int rowIndex = jointRef.getIndex();
        jointHandlesByRowIndex.remove(rowIndex);
        jointSpaceHandlesByRowIndex.remove(rowIndex);
        jointRefsByRowIndex.remove(rowIndex);
        backendIdsByJointRowIndex.remove(rowIndex);
    }

    @Nonnull
    public List<Ref<PhysicsStore>> jointRefsForSpaceHandle(
        @Nonnull BackendSpaceHandle spaceHandle) {
        List<Ref<PhysicsStore>> jointRefs = new ArrayList<>();
        int targetSpaceHandle = spaceHandle.value();
        jointSpaceHandlesByRowIndex.forEach((rowIndex, handle) -> {
            if (handle.value() == targetSpaceHandle) {
                Ref<PhysicsStore> jointRef = jointRefsByRowIndex.get((int) rowIndex);
                if (jointRef != null) {
                    jointRefs.add(jointRef);
                }
            }
        });
        return jointRefs;
    }

    public void markChunkCollisionPayloadBound(@Nonnull Ref<PhysicsStore> bodyRef,
        @Nonnull String payloadKey) {
        chunkCollisionPayloadKeysByRowIndex.put(bodyRef.getIndex(), payloadKey);
    }

    public boolean isChunkCollisionPayloadBound(@Nonnull Ref<PhysicsStore> bodyRef,
        @Nonnull String payloadKey) {
        return payloadKey.equals(chunkCollisionPayloadKeysByRowIndex.get(bodyRef.getIndex()));
    }

    public void clearChunkCollisionPayloadBound(@Nonnull Ref<PhysicsStore> bodyRef) {
        chunkCollisionPayloadKeysByRowIndex.remove(bodyRef.getIndex());
    }

    public void forEachRuntimeSpaceBinding(@Nonnull RuntimeSpaceBindingConsumer consumer) {
        spaceRefsByUuid.values().forEach(spaceRef -> {
            int rowIndex = spaceRef.getIndex();
            BackendSpaceHandle spaceHandle = spaceHandlesByRowIndex.get(rowIndex);
            BackendId backendId = backendIdsBySpaceRowIndex.get(rowIndex);
            PhysicsBackendRuntime runtime = backendId != null ? runtimesByBackend.get(backendId) : null;
            if (spaceHandle != null && backendId != null && runtime != null) {
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

    public int bodyHandleCount(@Nonnull BackendSpaceHandle spaceHandle) {
        LongList bodyHandles = bodyHandlesBySpaceHandle.get(spaceHandle.value());
        return bodyHandles != null ? bodyHandles.size() : 0;
    }

    public void clear() {
        runtimesByBackend.clear();
        spaceHandlesByUuid.clear();
        backendIdsBySpaceUuid.clear();
        spaceRefsByUuid.clear();
        spaceUuidsByRowIndex.clear();
        spaceHandlesByRowIndex.clear();
        backendIdsBySpaceRowIndex.clear();
        unambiguousBackendIdsBySpaceHandle.clear();
        spaceHandleBindingCounts.clear();
        bodyHandlesByUuid.clear();
        bodySpaceHandlesByUuid.clear();
        bodyRefsByRowIndex.clear();
        bodyHandlesByRowIndex.clear();
        bodySpaceHandlesByRowIndex.clear();
        backendIdsByBodyRowIndex.clear();
        jointHandlesByUuid.clear();
        jointSpaceHandlesByUuid.clear();
        jointRefsByUuid.clear();
        jointHandlesByRowIndex.clear();
        jointSpaceHandlesByRowIndex.clear();
        jointRefsByRowIndex.clear();
        backendIdsByJointRowIndex.clear();
        chunkCollisionPayloadKeysByRowIndex.clear();
        bodyHandlesBySpaceHandle.clear();
        bodyHitMetadataByHandle.clear();
        bodySnapshotMetadataByHandle.clear();
        pendingBodyOperations.clear();
        pendingSpaceSettingsByRowIndex.clear();
        started = false;
        markRegistrationTopologyChanged();
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
            int rowIndex = metadata.bodyRef().getIndex();
            bodyRefsByRowIndex.remove(rowIndex);
            bodyHandlesByRowIndex.remove(rowIndex);
            bodySpaceHandlesByRowIndex.remove(rowIndex);
            backendIdsByBodyRowIndex.remove(rowIndex);
        }
    }

    public void destroyBackendBindings() {
        RuntimeException failure = null;
        for (Map.Entry<UUID, BackendJointHandle> entry
            : new ArrayList<>(jointHandlesByUuid.entrySet())) {
            BackendSpaceHandle spaceHandle = jointSpaceHandlesByUuid.get(entry.getKey());
            Ref<PhysicsStore> jointRef = jointRefsByUuid.get(entry.getKey());
            PhysicsBackendRuntime runtime = jointRef != null
                ? runtimeForJointRef(jointRef)
                : runtimeForSpaceHandle(spaceHandle);
            if (spaceHandle == null || runtime == null) {
                continue;
            }
            try {
                runtime.removeJoint(spaceHandle.value(), entry.getValue().value());
            } catch (RuntimeException exception) {
                failure = appendShutdownFailure(failure, exception);
            }
        }
        for (Map.Entry<UUID, BackendBodyHandle> entry
            : new ArrayList<>(bodyHandlesByUuid.entrySet())) {
            BackendSpaceHandle spaceHandle = bodySpaceHandlesByUuid.get(entry.getKey());
            BodySnapshotMetadata metadata = bodySnapshotMetadataByHandle.get(entry.getValue()
                .value());
            PhysicsBackendRuntime runtime = metadata != null
                ? runtimeForBodyRef(metadata.bodyRef())
                : runtimeForSpaceHandle(spaceHandle);
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
        BackendId backendId = unambiguousBackendIdsBySpaceHandle.get(target.value());
        return backendId != null ? runtimesByBackend.get(backendId) : null;
    }

    @Nullable
    public PhysicsBackendRuntime runtimeForSpaceRef(@Nonnull Ref<PhysicsStore> spaceRef) {
        return runtimeForBackendId(backendIdsBySpaceRowIndex.get(
            Objects.requireNonNull(spaceRef, "spaceRef").getIndex()));
    }

    @Nullable
    public PhysicsBackendRuntime runtimeForBodyRef(@Nonnull Ref<PhysicsStore> bodyRef) {
        return runtimeForBackendId(backendIdsByBodyRowIndex.get(
            Objects.requireNonNull(bodyRef, "bodyRef").getIndex()));
    }

    @Nullable
    public PhysicsBackendRuntime runtimeForJointRef(@Nonnull Ref<PhysicsStore> jointRef) {
        return runtimeForBackendId(backendIdsByJointRowIndex.get(
            Objects.requireNonNull(jointRef, "jointRef").getIndex()));
    }

    @Nullable
    private PhysicsBackendRuntime runtimeForBackendId(@Nullable BackendId backendId) {
        return backendId != null ? runtimesByBackend.get(backendId) : null;
    }

    private void addSpaceHandleRuntimeIndex(@Nonnull BackendSpaceHandle handle,
        @Nonnull BackendId backendId) {
        int handleValue = handle.value();
        int bindingCount = spaceHandleBindingCounts.get(handleValue) + 1;
        spaceHandleBindingCounts.put(handleValue, bindingCount);
        if (bindingCount == 1) {
            unambiguousBackendIdsBySpaceHandle.put(handleValue, backendId);
        } else {
            unambiguousBackendIdsBySpaceHandle.remove(handleValue);
        }
    }

    private void removeSpaceHandleRuntimeIndex(@Nullable BackendSpaceHandle handle) {
        if (handle == null) {
            return;
        }
        int handleValue = handle.value();
        int bindingCount = spaceHandleBindingCounts.get(handleValue);
        if (bindingCount <= 1) {
            spaceHandleBindingCounts.remove(handleValue);
            unambiguousBackendIdsBySpaceHandle.remove(handleValue);
            return;
        }
        int remainingBindingCount = bindingCount - 1;
        spaceHandleBindingCounts.put(handleValue, remainingBindingCount);
        if (remainingBindingCount == 1) {
            BackendId backendId = uniqueBackendIdForSpaceHandleValue(handleValue);
            if (backendId != null) {
                unambiguousBackendIdsBySpaceHandle.put(handleValue, backendId);
            } else {
                unambiguousBackendIdsBySpaceHandle.remove(handleValue);
            }
        } else {
            unambiguousBackendIdsBySpaceHandle.remove(handleValue);
        }
    }

    @Nullable
    private BackendId uniqueBackendIdForSpaceHandleValue(int handleValue) {
        BackendId uniqueBackendId = null;
        for (Map.Entry<UUID, BackendSpaceHandle> entry : spaceHandlesByUuid.entrySet()) {
            if (entry.getValue().value() != handleValue) {
                continue;
            }
            BackendId backendId = backendIdsBySpaceUuid.get(entry.getKey());
            if (backendId == null) {
                continue;
            }
            if (uniqueBackendId != null && !uniqueBackendId.equals(backendId)) {
                return null;
            }
            uniqueBackendId = backendId;
        }
        return uniqueBackendId;
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
        copy.spaceUuidsByRowIndex.putAll(spaceUuidsByRowIndex);
        copy.spaceHandlesByRowIndex.putAll(spaceHandlesByRowIndex);
        copy.backendIdsBySpaceRowIndex.putAll(backendIdsBySpaceRowIndex);
        copy.unambiguousBackendIdsBySpaceHandle.putAll(unambiguousBackendIdsBySpaceHandle);
        copy.spaceHandleBindingCounts.putAll(spaceHandleBindingCounts);
        copy.bodyHandlesByUuid.putAll(bodyHandlesByUuid);
        copy.bodySpaceHandlesByUuid.putAll(bodySpaceHandlesByUuid);
        copy.bodyRefsByRowIndex.putAll(bodyRefsByRowIndex);
        copy.bodyHandlesByRowIndex.putAll(bodyHandlesByRowIndex);
        copy.bodySpaceHandlesByRowIndex.putAll(bodySpaceHandlesByRowIndex);
        copy.backendIdsByBodyRowIndex.putAll(backendIdsByBodyRowIndex);
        copy.jointHandlesByUuid.putAll(jointHandlesByUuid);
        copy.jointSpaceHandlesByUuid.putAll(jointSpaceHandlesByUuid);
        copy.jointRefsByUuid.putAll(jointRefsByUuid);
        copy.jointHandlesByRowIndex.putAll(jointHandlesByRowIndex);
        copy.jointSpaceHandlesByRowIndex.putAll(jointSpaceHandlesByRowIndex);
        copy.jointRefsByRowIndex.putAll(jointRefsByRowIndex);
        copy.backendIdsByJointRowIndex.putAll(backendIdsByJointRowIndex);
        copy.chunkCollisionPayloadKeysByRowIndex.putAll(chunkCollisionPayloadKeysByRowIndex);
        bodyHandlesBySpaceHandle.forEach((spaceHandle, bodyHandles) ->
            copy.bodyHandlesBySpaceHandle.put((int) spaceHandle, new LongArrayList(bodyHandles)));
        copy.bodyHitMetadataByHandle.putAll(bodyHitMetadataByHandle);
        copy.bodySnapshotMetadataByHandle.putAll(bodySnapshotMetadataByHandle);
        copy.pendingBodyOperations.addAll(pendingBodyOperations);
        copy.pendingSpaceSettingsByRowIndex.putAll(pendingSpaceSettingsByRowIndex);
        copy.registrationTopologyGeneration = registrationTopologyGeneration;
        copy.started = started;
        return copy;
    }

    @Nonnull
    public static ResourceType<PhysicsStore, PhysicsRuntimeResource> getResourceType() {
        return PhysicsResourceTypes.runtimeResourceType();
    }

    @FunctionalInterface
    public interface RuntimeSpaceBindingConsumer {

        void accept(@Nonnull Ref<PhysicsStore> spaceRef,
            @Nonnull BackendId backendId,
            @Nonnull BackendSpaceHandle spaceHandle,
            @Nonnull PhysicsBackendRuntime runtime);
    }

    public record BodyHitMetadata(@Nonnull UUID bodyUuid,
                                  @Nullable Ref<PhysicsStore> bodyRef,
                                  @Nonnull PhysicsBodyType bodyType,
                                  @Nonnull ShapeType shapeType) {

        public BodyHitMetadata {
            Objects.requireNonNull(bodyUuid, "bodyUuid");
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

    public long getRegistrationTopologyGeneration() {
        return registrationTopologyGeneration;
    }

    private void markRegistrationTopologyChanged() {
        registrationTopologyGeneration++;
    }

}
