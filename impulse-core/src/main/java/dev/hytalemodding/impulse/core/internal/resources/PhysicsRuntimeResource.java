package dev.hytalemodding.impulse.core.internal.resources;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Resource;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.api.BackendId;
import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.ShapeType;
import dev.hytalemodding.impulse.api.runtime.PhysicsBackendRuntime;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongConsumer;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import lombok.Getter;
import lombok.Setter;

/**
 * Runtime-only backend bindings for PhysicsStore spaces, bodies, and joints.
 */
public final class PhysicsRuntimeResource implements Resource<PhysicsStore> {

    @Nonnull
    private final Map<BackendId, PhysicsBackendRuntime> runtimesByBackend =
        new Object2ObjectOpenHashMap<>();
    @Nonnull
    private final Int2ObjectOpenHashMap<Ref<PhysicsStore>> spaceRefsByRowIndex =
        new Int2ObjectOpenHashMap<>();
    @Nonnull
    private final Int2ObjectOpenHashMap<BackendSpaceHandle> spaceHandlesByRowIndex =
        new Int2ObjectOpenHashMap<>();
    @Nonnull
    private final Int2ObjectOpenHashMap<BackendId> backendIdsBySpaceRowIndex =
        new Int2ObjectOpenHashMap<>();
    @Nonnull
    private final Map<BackendSpaceKey, SpaceMetadata> spaceMetadataByKey =
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
    private final Map<BackendJointKey, JointMetadata> jointMetadataByKey =
        new Object2ObjectOpenHashMap<>();
    @Nonnull
    private final Map<UUID, BackendJointKey> jointKeysByUuid =
        new Object2ObjectOpenHashMap<>();
    @Nonnull
    private final Int2ObjectOpenHashMap<String> chunkCollisionPayloadKeysByRowIndex =
        new Int2ObjectOpenHashMap<>();
    @Nonnull
    private final Map<BackendSpaceKey, LongList> bodyHandlesBySpaceKey =
        new Object2ObjectOpenHashMap<>();
    @Nonnull
    private final Map<BackendBodyKey, BodyHitMetadata> bodyHitMetadataByKey =
        new Object2ObjectOpenHashMap<>();
    @Nonnull
    private final Map<BackendBodyKey, BodySnapshotMetadata> bodySnapshotMetadataByKey =
        new Object2ObjectOpenHashMap<>();
    @Nonnull
    private final Map<UUID, BackendBodyKey> bodySnapshotKeysByUuid =
        new Object2ObjectOpenHashMap<>();
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
        runtimesByBackend.put(Objects.requireNonNull(backendId, "backendId"),
            Objects.requireNonNull(runtime, "runtime"));
    }

    @Nullable
    public PhysicsBackendRuntime getRuntime(@Nonnull BackendId backendId) {
        return runtimesByBackend.get(backendId);
    }

    public void putSpaceHandle(@Nonnull Ref<PhysicsStore> spaceRef,
        @Nonnull BackendId backendId,
        @Nonnull BackendSpaceHandle handle) {
        Ref<PhysicsStore> checkedSpaceRef = Objects.requireNonNull(spaceRef, "spaceRef");
        BackendId checkedBackendId = Objects.requireNonNull(backendId, "backendId");
        BackendSpaceHandle checkedHandle = Objects.requireNonNull(handle, "handle");
        int rowIndex = checkedSpaceRef.getIndex();
        BackendId previousBackendId = backendIdsBySpaceRowIndex.get(rowIndex);
        BackendSpaceHandle previousHandle = spaceHandlesByRowIndex.get(rowIndex);
        if (previousBackendId != null && previousHandle != null) {
            removeSpaceMetadata(new BackendSpaceKey(previousBackendId, previousHandle));
        }
        spaceRefsByRowIndex.put(rowIndex, checkedSpaceRef);
        backendIdsBySpaceRowIndex.put(rowIndex, checkedBackendId);
        spaceHandlesByRowIndex.put(rowIndex, checkedHandle);
        markRegistrationTopologyChanged();
    }

    public void putSpaceMetadata(@Nonnull BackendId backendId,
        @Nonnull BackendSpaceHandle handle,
        @Nonnull UUID spaceUuid,
        @Nonnull Ref<PhysicsStore> spaceRef) {
        BackendSpaceKey key = new BackendSpaceKey(backendId, handle);
        removeSpaceMetadataForUuid(spaceUuid, key);
        spaceMetadataByKey.put(key, new SpaceMetadata(spaceUuid, spaceRef));
    }

    @Nullable
    public BackendSpaceHandle getSpaceHandle(@Nonnull Ref<PhysicsStore> spaceRef) {
        return spaceHandlesByRowIndex.get(spaceRef.getIndex());
    }

    @Nullable
    public UUID getSpaceUuid(@Nonnull Ref<PhysicsStore> spaceRef) {
        BackendSpaceKey key = spaceKey(spaceRef);
        SpaceMetadata metadata = key != null ? spaceMetadataByKey.get(key) : null;
        return metadata != null ? metadata.spaceUuid() : null;
    }

    @Nullable
    public BackendId getSpaceBackendId(@Nonnull Ref<PhysicsStore> spaceRef) {
        return backendIdsBySpaceRowIndex.get(spaceRef.getIndex());
    }

    public void removeSpaceHandle(@Nonnull Ref<PhysicsStore> spaceRef) {
        int rowIndex = Objects.requireNonNull(spaceRef, "spaceRef").getIndex();
        BackendId backendId = backendIdsBySpaceRowIndex.remove(rowIndex);
        BackendSpaceHandle removed = spaceHandlesByRowIndex.remove(rowIndex);
        spaceRefsByRowIndex.remove(rowIndex);
        if (backendId == null || removed == null) {
            return;
        }
        removeSpaceMetadata(new BackendSpaceKey(backendId, removed));
        markRegistrationTopologyChanged();
    }

    private void removeSpaceMetadata(@Nonnull BackendSpaceKey key) {
        spaceMetadataByKey.remove(key);
        LongList bodyHandles = bodyHandlesBySpaceKey.remove(key);
        if (bodyHandles != null) {
            bodyHandles.forEach((long bodyHandle) -> removeBodyMetadata(
                new BackendBodyKey(key.backendId(), key.spaceHandle(), bodyHandle)));
        }
        List<BackendJointKey> removedJointKeys = new ArrayList<>();
        jointMetadataByKey.keySet().forEach(jointKey -> {
            if (jointKey.backendId().equals(key.backendId())
                && jointKey.spaceHandle() == key.spaceHandle()) {
                removedJointKeys.add(jointKey);
            }
        });
        removedJointKeys.forEach(this::removeJointMetadata);
    }

    private void removeSpaceMetadataForUuid(@Nonnull UUID spaceUuid,
        @Nonnull BackendSpaceKey replacementKey) {
        List<BackendSpaceKey> removedKeys = new ArrayList<>();
        spaceMetadataByKey.forEach((key, metadata) -> {
            if (!key.equals(replacementKey) && metadata.spaceUuid().equals(spaceUuid)) {
                removedKeys.add(key);
            }
        });
        removedKeys.forEach(this::removeSpaceMetadata);
    }

    public void putBodyHandle(@Nonnull Ref<PhysicsStore> bodyRef,
        @Nonnull Ref<PhysicsStore> spaceRef,
        @Nonnull BackendSpaceHandle spaceHandle,
        @Nonnull BackendBodyHandle handle) {
        Ref<PhysicsStore> checkedBodyRef = Objects.requireNonNull(bodyRef, "bodyRef");
        BackendId backendId = getSpaceBackendId(Objects.requireNonNull(spaceRef, "spaceRef"));
        int rowIndex = checkedBodyRef.getIndex();
        BackendId previousBackendId = backendIdsByBodyRowIndex.remove(rowIndex);
        BackendBodyHandle previousHandle = bodyHandlesByRowIndex.remove(rowIndex);
        BackendSpaceHandle previousSpaceHandle = bodySpaceHandlesByRowIndex.remove(rowIndex);
        bodyRefsByRowIndex.remove(rowIndex);
        chunkCollisionPayloadKeysByRowIndex.remove(rowIndex);
        removeBodyHandleIndexes(previousBackendId, previousHandle, previousSpaceHandle);
        bodyRefsByRowIndex.put(rowIndex, checkedBodyRef);
        bodyHandlesByRowIndex.put(rowIndex, Objects.requireNonNull(handle, "handle"));
        bodySpaceHandlesByRowIndex.put(rowIndex, Objects.requireNonNull(spaceHandle, "spaceHandle"));
        if (backendId != null) {
            backendIdsByBodyRowIndex.put(rowIndex, backendId);
            bodyHandlesBySpaceKey.computeIfAbsent(new BackendSpaceKey(backendId, spaceHandle),
                _ -> new LongArrayList()).add(handle.value());
        } else {
            backendIdsByBodyRowIndex.remove(rowIndex);
        }
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

    @Nullable
    public BackendId getBodyBackendId(@Nonnull Ref<PhysicsStore> bodyRef) {
        return backendIdsByBodyRowIndex.get(bodyRef.getIndex());
    }

    public void removeBodyHandle(@Nonnull Ref<PhysicsStore> bodyRef) {
        Ref<PhysicsStore> checkedBodyRef = Objects.requireNonNull(bodyRef, "bodyRef");
        removePendingBodyOperations(checkedBodyRef);
        int rowIndex = checkedBodyRef.getIndex();
        BackendId backendId = backendIdsByBodyRowIndex.remove(rowIndex);
        BackendBodyHandle removed = bodyHandlesByRowIndex.remove(rowIndex);
        BackendSpaceHandle spaceHandle = bodySpaceHandlesByRowIndex.remove(rowIndex);
        bodyRefsByRowIndex.remove(rowIndex);
        chunkCollisionPayloadKeysByRowIndex.remove(rowIndex);
        removeBodyHandleIndexes(backendId, removed, spaceHandle);
        markRegistrationTopologyChanged();
    }

    @Nonnull
    public List<Ref<PhysicsStore>> bodyRefsForSpaceHandle(@Nonnull BackendId backendId,
        @Nonnull BackendSpaceHandle spaceHandle) {
        List<Ref<PhysicsStore>> bodyRefs = new ArrayList<>();
        BackendSpaceKey target = new BackendSpaceKey(backendId, spaceHandle);
        bodySpaceHandlesByRowIndex.forEach((rowIndex, handle) -> {
            BackendId rowBackendId = backendIdsByBodyRowIndex.get((int) rowIndex);
            if (rowBackendId != null
                && rowBackendId.equals(target.backendId())
                && handle.value() == target.spaceHandle()) {
                Ref<PhysicsStore> bodyRef = bodyRefsByRowIndex.get((int) rowIndex);
                if (bodyRef != null) {
                    bodyRefs.add(bodyRef);
                }
            }
        });
        return bodyRefs;
    }

    public void putBodySnapshotMetadata(@Nonnull BackendId backendId,
        @Nonnull BackendSpaceHandle spaceHandle,
        @Nonnull BackendBodyHandle handle,
        @Nonnull UUID bodyUuid,
        @Nonnull Ref<PhysicsStore> bodyRef,
        @Nonnull UUID spaceUuid) {
        BackendBodyKey key = new BackendBodyKey(backendId, spaceHandle, handle);
        BodySnapshotMetadata previousMetadata = bodySnapshotMetadataByKey.get(key);
        if (previousMetadata != null) {
            bodySnapshotKeysByUuid.remove(previousMetadata.bodyUuid(), key);
        }
        removeBodyMetadataForUuid(bodyUuid, key, bodyRef);
        bodySnapshotMetadataByKey.put(key,
            new BodySnapshotMetadata(bodyUuid, bodyRef, spaceUuid));
        bodySnapshotKeysByUuid.put(bodyUuid, key);
    }

    public void putBodyHitMetadata(@Nonnull BackendId backendId,
        @Nonnull BackendSpaceHandle spaceHandle,
        @Nonnull BackendBodyHandle handle,
        @Nullable Ref<PhysicsStore> bodyRef,
        @Nonnull PhysicsBodyType bodyType,
        @Nonnull ShapeType shapeType) {
        BackendBodyKey key = new BackendBodyKey(backendId, spaceHandle, handle);
        BodySnapshotMetadata metadata = bodySnapshotMetadataByKey.get(key);
        putBodyHitMetadata(backendId,
            spaceHandle,
            handle,
            metadata != null ? metadata.bodyUuid() : new UUID(0L, 0L),
            bodyRef,
            bodyType,
            shapeType);
    }

    public void putBodyHitMetadata(@Nonnull BackendId backendId,
        @Nonnull BackendSpaceHandle spaceHandle,
        @Nonnull BackendBodyHandle handle,
        @Nonnull UUID bodyUuid,
        @Nullable Ref<PhysicsStore> bodyRef,
        @Nonnull PhysicsBodyType bodyType,
        @Nonnull ShapeType shapeType) {
        bodyHitMetadataByKey.put(new BackendBodyKey(backendId, spaceHandle, handle),
            new BodyHitMetadata(bodyUuid, bodyRef, bodyType, shapeType));
    }

    @Nullable
    public BodyHitMetadata getBodyHitMetadata(@Nonnull BackendId backendId,
        @Nonnull BackendSpaceHandle spaceHandle,
        long bodyHandle) {
        return bodyHitMetadataByKey.get(new BackendBodyKey(backendId, spaceHandle, bodyHandle));
    }

    @Nullable
    public BodySnapshotMetadata getBodySnapshotMetadata(@Nonnull BackendId backendId,
        @Nonnull BackendSpaceHandle spaceHandle,
        long bodyHandle) {
        return bodySnapshotMetadataByKey.get(new BackendBodyKey(backendId, spaceHandle, bodyHandle));
    }

    public void removeBodyHitMetadata(@Nonnull BackendId backendId,
        @Nonnull BackendSpaceHandle spaceHandle,
        @Nonnull BackendBodyHandle handle) {
        bodyHitMetadataByKey.remove(new BackendBodyKey(backendId, spaceHandle, handle));
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

    public void putJointHandle(@Nonnull Ref<PhysicsStore> jointRef,
        @Nonnull Ref<PhysicsStore> spaceRef,
        @Nonnull BackendSpaceHandle spaceHandle,
        @Nonnull BackendJointHandle handle) {
        Ref<PhysicsStore> checkedJointRef = Objects.requireNonNull(jointRef, "jointRef");
        BackendId backendId = getSpaceBackendId(Objects.requireNonNull(spaceRef, "spaceRef"));
        int rowIndex = checkedJointRef.getIndex();
        BackendJointHandle previousHandle = jointHandlesByRowIndex.remove(rowIndex);
        BackendSpaceHandle previousSpaceHandle = jointSpaceHandlesByRowIndex.remove(rowIndex);
        BackendId previousBackendId = backendIdsByJointRowIndex.remove(rowIndex);
        if (previousBackendId != null && previousSpaceHandle != null && previousHandle != null) {
            removeJointMetadata(new BackendJointKey(previousBackendId,
                previousSpaceHandle,
                previousHandle));
        }
        jointRefsByRowIndex.put(rowIndex, checkedJointRef);
        jointHandlesByRowIndex.put(rowIndex, Objects.requireNonNull(handle, "handle"));
        jointSpaceHandlesByRowIndex.put(rowIndex, Objects.requireNonNull(spaceHandle, "spaceHandle"));
        if (backendId != null) {
            backendIdsByJointRowIndex.put(rowIndex, backendId);
        }
    }

    public void putJointMetadata(@Nonnull BackendId backendId,
        @Nonnull BackendSpaceHandle spaceHandle,
        @Nonnull BackendJointHandle handle,
        @Nonnull UUID jointUuid,
        @Nonnull Ref<PhysicsStore> jointRef) {
        BackendJointKey key = new BackendJointKey(backendId, spaceHandle, handle);
        JointMetadata previousMetadata = jointMetadataByKey.get(key);
        if (previousMetadata != null) {
            jointKeysByUuid.remove(previousMetadata.jointUuid(), key);
        }
        removeJointMetadataForUuid(jointUuid, key, jointRef);
        jointMetadataByKey.put(key,
            new JointMetadata(jointUuid, jointRef));
        jointKeysByUuid.put(jointUuid, key);
    }

    @Nullable
    public BackendJointHandle getJointHandle(@Nonnull Ref<PhysicsStore> jointRef) {
        return jointHandlesByRowIndex.get(jointRef.getIndex());
    }

    @Nullable
    public UUID getJointUuid(@Nonnull BackendId backendId,
        @Nonnull BackendSpaceHandle spaceHandle,
        long jointHandle) {
        JointMetadata metadata = jointMetadataByKey.get(new BackendJointKey(backendId,
            spaceHandle.value(),
            jointHandle));
        return metadata != null ? metadata.jointUuid() : null;
    }

    @Nullable
    public BackendSpaceHandle getJointSpaceHandle(@Nonnull Ref<PhysicsStore> jointRef) {
        return jointSpaceHandlesByRowIndex.get(jointRef.getIndex());
    }

    @Nullable
    public BackendId getJointBackendId(@Nonnull Ref<PhysicsStore> jointRef) {
        return backendIdsByJointRowIndex.get(jointRef.getIndex());
    }

    public void removeJointHandle(@Nonnull Ref<PhysicsStore> jointRef) {
        int rowIndex = Objects.requireNonNull(jointRef, "jointRef").getIndex();
        BackendJointHandle removed = jointHandlesByRowIndex.remove(rowIndex);
        BackendSpaceHandle spaceHandle = jointSpaceHandlesByRowIndex.remove(rowIndex);
        BackendId backendId = backendIdsByJointRowIndex.remove(rowIndex);
        jointRefsByRowIndex.remove(rowIndex);
        if (backendId != null && spaceHandle != null && removed != null) {
            removeJointMetadata(new BackendJointKey(backendId, spaceHandle, removed));
        }
    }

    @Nonnull
    public List<Ref<PhysicsStore>> jointRefsForSpaceHandle(@Nonnull BackendId backendId,
        @Nonnull BackendSpaceHandle spaceHandle) {
        List<Ref<PhysicsStore>> jointRefs = new ArrayList<>();
        BackendSpaceKey target = new BackendSpaceKey(backendId, spaceHandle);
        jointSpaceHandlesByRowIndex.forEach((rowIndex, handle) -> {
            BackendId rowBackendId = backendIdsByJointRowIndex.get((int) rowIndex);
            if (rowBackendId != null
                && rowBackendId.equals(target.backendId())
                && handle.value() == target.spaceHandle()) {
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
        Objects.requireNonNull(consumer, "consumer");
        spaceRefsByRowIndex.forEach((rowIndex, spaceRef) -> {
            BackendSpaceHandle spaceHandle = spaceHandlesByRowIndex.get((int) rowIndex);
            BackendId backendId = backendIdsBySpaceRowIndex.get((int) rowIndex);
            PhysicsBackendRuntime runtime = backendId != null ? runtimesByBackend.get(backendId) : null;
            if (spaceHandle != null && backendId != null && runtime != null) {
                consumer.accept(spaceRef, backendId, spaceHandle, runtime);
            }
        });
    }

    public void forEachBodyHandle(@Nonnull BackendId backendId,
        @Nonnull BackendSpaceHandle spaceHandle,
        @Nonnull LongConsumer consumer) {
        LongList bodyHandles = bodyHandlesBySpaceKey.get(new BackendSpaceKey(backendId, spaceHandle));
        if (bodyHandles == null) {
            return;
        }
        bodyHandles.forEach(consumer);
    }

    public int bodyHandleCount(@Nonnull BackendId backendId,
        @Nonnull BackendSpaceHandle spaceHandle) {
        LongList bodyHandles = bodyHandlesBySpaceKey.get(new BackendSpaceKey(backendId, spaceHandle));
        return bodyHandles != null ? bodyHandles.size() : 0;
    }

    public void clear() {
        runtimesByBackend.clear();
        spaceRefsByRowIndex.clear();
        spaceHandlesByRowIndex.clear();
        backendIdsBySpaceRowIndex.clear();
        spaceMetadataByKey.clear();
        bodyRefsByRowIndex.clear();
        bodyHandlesByRowIndex.clear();
        bodySpaceHandlesByRowIndex.clear();
        backendIdsByBodyRowIndex.clear();
        jointHandlesByRowIndex.clear();
        jointSpaceHandlesByRowIndex.clear();
        jointRefsByRowIndex.clear();
        backendIdsByJointRowIndex.clear();
        jointMetadataByKey.clear();
        jointKeysByUuid.clear();
        chunkCollisionPayloadKeysByRowIndex.clear();
        bodyHandlesBySpaceKey.clear();
        bodyHitMetadataByKey.clear();
        bodySnapshotMetadataByKey.clear();
        bodySnapshotKeysByUuid.clear();
        pendingBodyOperations.clear();
        pendingSpaceSettingsByRowIndex.clear();
        started = false;
        markRegistrationTopologyChanged();
    }

    public void clearTransientBodyOperations() {
        pendingBodyOperations.clear();
    }

    public long registrationTopologyGeneration() {
        return registrationTopologyGeneration;
    }

    private void markRegistrationTopologyChanged() {
        registrationTopologyGeneration++;
    }

    public void refreshRowRefs(@Nonnull PhysicsStore physicsStore) {
        PhysicsStore checkedPhysicsStore = Objects.requireNonNull(physicsStore, "physicsStore");
        refreshSpaceRefs(checkedPhysicsStore);
        refreshBodyRefs(checkedPhysicsStore);
        refreshJointRefs(checkedPhysicsStore);
    }

    private void refreshSpaceRefs(@Nonnull PhysicsStore physicsStore) {
        spaceRefsByRowIndex.clear();
        spaceHandlesByRowIndex.clear();
        backendIdsBySpaceRowIndex.clear();
        spaceMetadataByKey.replaceAll((key, metadata) -> {
            Ref<PhysicsStore> spaceRef = physicsStore.getRefFromUUID(metadata.spaceUuid());
            if (spaceRef == null) {
                return metadata;
            }
            int rowIndex = spaceRef.getIndex();
            spaceRefsByRowIndex.put(rowIndex, spaceRef);
            spaceHandlesByRowIndex.put(rowIndex, new BackendSpaceHandle(key.spaceHandle()));
            backendIdsBySpaceRowIndex.put(rowIndex, key.backendId());
            return new SpaceMetadata(metadata.spaceUuid(), spaceRef);
        });
    }

    private void refreshBodyRefs(@Nonnull PhysicsStore physicsStore) {
        bodyRefsByRowIndex.clear();
        bodyHandlesByRowIndex.clear();
        bodySpaceHandlesByRowIndex.clear();
        backendIdsByBodyRowIndex.clear();
        bodySnapshotMetadataByKey.replaceAll((key, metadata) -> {
            Ref<PhysicsStore> bodyRef = physicsStore.getRefFromUUID(metadata.bodyUuid());
            if (bodyRef == null) {
                return metadata;
            }
            int rowIndex = bodyRef.getIndex();
            bodyRefsByRowIndex.put(rowIndex, bodyRef);
            bodyHandlesByRowIndex.put(rowIndex, new BackendBodyHandle(key.bodyHandle()));
            bodySpaceHandlesByRowIndex.put(rowIndex, new BackendSpaceHandle(key.spaceHandle()));
            backendIdsByBodyRowIndex.put(rowIndex, key.backendId());
            return new BodySnapshotMetadata(metadata.bodyUuid(), bodyRef, metadata.spaceUuid());
        });
        bodyHitMetadataByKey.replaceAll((_, metadata) -> {
            Ref<PhysicsStore> bodyRef = physicsStore.getRefFromUUID(metadata.bodyUuid());
            if (bodyRef == null) {
                return metadata;
            }
            return new BodyHitMetadata(metadata.bodyUuid(),
                bodyRef,
                metadata.bodyType(),
                metadata.shapeType());
        });
    }

    private void refreshJointRefs(@Nonnull PhysicsStore physicsStore) {
        jointHandlesByRowIndex.clear();
        jointSpaceHandlesByRowIndex.clear();
        jointRefsByRowIndex.clear();
        backendIdsByJointRowIndex.clear();
        jointMetadataByKey.replaceAll((key, metadata) -> {
            Ref<PhysicsStore> jointRef = physicsStore.getRefFromUUID(metadata.jointUuid());
            if (jointRef == null) {
                return metadata;
            }
            int rowIndex = jointRef.getIndex();
            jointRefsByRowIndex.put(rowIndex, jointRef);
            jointHandlesByRowIndex.put(rowIndex, new BackendJointHandle(key.jointHandle()));
            jointSpaceHandlesByRowIndex.put(rowIndex, new BackendSpaceHandle(key.spaceHandle()));
            backendIdsByJointRowIndex.put(rowIndex, key.backendId());
            return new JointMetadata(metadata.jointUuid(), jointRef);
        });
    }

    private void removeBodyHandleIndexes(@Nullable BackendId backendId,
        @Nullable BackendBodyHandle removed,
        @Nullable BackendSpaceHandle spaceHandle) {
        if (backendId == null || removed == null || spaceHandle == null) {
            return;
        }
        BackendBodyKey bodyKey = new BackendBodyKey(backendId, spaceHandle, removed);
        removeBodyHandleFromSpaceIndex(bodyKey);
        removeBodyMetadata(bodyKey);
    }

    private void removeBodyHandleFromSpaceIndex(@Nonnull BackendBodyKey key) {
        BackendSpaceKey spaceKey = new BackendSpaceKey(key.backendId(), key.spaceHandle());
        LongList bodyHandles = bodyHandlesBySpaceKey.get(spaceKey);
        if (bodyHandles != null) {
            bodyHandles.rem(key.bodyHandle());
            if (bodyHandles.isEmpty()) {
                bodyHandlesBySpaceKey.remove(spaceKey);
            }
        }
    }

    private void removeBodyMetadata(@Nonnull BackendBodyKey key) {
        bodyHitMetadataByKey.remove(key);
        BodySnapshotMetadata metadata = bodySnapshotMetadataByKey.remove(key);
        if (metadata == null) {
            return;
        }
        bodySnapshotKeysByUuid.remove(metadata.bodyUuid(), key);
        int rowIndex = metadata.bodyRef().getIndex();
        bodyRefsByRowIndex.remove(rowIndex);
        bodyHandlesByRowIndex.remove(rowIndex);
        bodySpaceHandlesByRowIndex.remove(rowIndex);
        backendIdsByBodyRowIndex.remove(rowIndex);
        chunkCollisionPayloadKeysByRowIndex.remove(rowIndex);
    }

    private void removeBodyMetadataForUuid(@Nonnull UUID bodyUuid,
        @Nonnull BackendBodyKey replacementKey,
        @Nonnull Ref<PhysicsStore> replacementRef) {
        BackendBodyKey removedKey = bodySnapshotKeysByUuid.get(bodyUuid);
        if (removedKey == null || removedKey.equals(replacementKey)) {
            return;
        }
        removeBodyHandleFromSpaceIndex(removedKey);
        bodyHitMetadataByKey.remove(removedKey);
        BodySnapshotMetadata metadata = bodySnapshotMetadataByKey.remove(removedKey);
        bodySnapshotKeysByUuid.remove(bodyUuid, removedKey);
        if (metadata != null && !sameRow(metadata.bodyRef(), replacementRef)) {
            int rowIndex = metadata.bodyRef().getIndex();
            bodyRefsByRowIndex.remove(rowIndex);
            bodyHandlesByRowIndex.remove(rowIndex);
            bodySpaceHandlesByRowIndex.remove(rowIndex);
            backendIdsByBodyRowIndex.remove(rowIndex);
            chunkCollisionPayloadKeysByRowIndex.remove(rowIndex);
        }
    }

    private void removeJointMetadata(@Nonnull BackendJointKey key) {
        JointMetadata metadata = jointMetadataByKey.remove(key);
        if (metadata == null) {
            return;
        }
        jointKeysByUuid.remove(metadata.jointUuid(), key);
        int rowIndex = metadata.jointRef().getIndex();
        jointHandlesByRowIndex.remove(rowIndex);
        jointSpaceHandlesByRowIndex.remove(rowIndex);
        jointRefsByRowIndex.remove(rowIndex);
        backendIdsByJointRowIndex.remove(rowIndex);
    }

    private void removeJointMetadataForUuid(@Nonnull UUID jointUuid,
        @Nonnull BackendJointKey replacementKey,
        @Nonnull Ref<PhysicsStore> replacementRef) {
        BackendJointKey removedKey = jointKeysByUuid.get(jointUuid);
        if (removedKey == null || removedKey.equals(replacementKey)) {
            return;
        }
        JointMetadata metadata = jointMetadataByKey.remove(removedKey);
        jointKeysByUuid.remove(jointUuid, removedKey);
        if (metadata != null && !sameRow(metadata.jointRef(), replacementRef)) {
            int rowIndex = metadata.jointRef().getIndex();
            jointHandlesByRowIndex.remove(rowIndex);
            jointSpaceHandlesByRowIndex.remove(rowIndex);
            jointRefsByRowIndex.remove(rowIndex);
            backendIdsByJointRowIndex.remove(rowIndex);
        }
    }

    private static boolean sameRow(@Nonnull Ref<PhysicsStore> first,
        @Nonnull Ref<PhysicsStore> second) {
        return first.getStore() == second.getStore() && first.getIndex() == second.getIndex();
    }

    public void destroyBackendBindings() {
        RuntimeException failure = null;
        for (Map.Entry<BackendJointKey, JointMetadata> entry
            : new ArrayList<>(jointMetadataByKey.entrySet())) {
            PhysicsBackendRuntime runtime = runtimesByBackend.get(entry.getKey().backendId());
            if (runtime == null) {
                continue;
            }
            try {
                runtime.removeJoint(entry.getKey().spaceHandle(), entry.getKey().jointHandle());
            } catch (RuntimeException exception) {
                failure = appendShutdownFailure(failure, exception);
            }
        }
        for (Map.Entry<BackendBodyKey, BodySnapshotMetadata> entry
            : new ArrayList<>(bodySnapshotMetadataByKey.entrySet())) {
            PhysicsBackendRuntime runtime = runtimesByBackend.get(entry.getKey().backendId());
            if (runtime == null) {
                continue;
            }
            try {
                runtime.removeBody(entry.getKey().spaceHandle(), entry.getKey().bodyHandle());
            } catch (RuntimeException exception) {
                failure = appendShutdownFailure(failure, exception);
            }
        }
        for (BackendSpaceKey key : new ArrayList<>(spaceMetadataByKey.keySet())) {
            PhysicsBackendRuntime runtime = runtimesByBackend.get(key.backendId());
            if (runtime == null) {
                continue;
            }
            try {
                runtime.destroySpace(key.spaceHandle());
            } catch (RuntimeException exception) {
                failure = appendShutdownFailure(failure, exception);
            }
        }
        for (PhysicsBackendRuntime runtime : new ArrayList<>(runtimesByBackend.values())) {
            try {
                runtime.close();
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

    @Nullable
    private BackendSpaceKey spaceKey(@Nonnull Ref<PhysicsStore> spaceRef) {
        int rowIndex = Objects.requireNonNull(spaceRef, "spaceRef").getIndex();
        BackendId backendId = backendIdsBySpaceRowIndex.get(rowIndex);
        BackendSpaceHandle handle = spaceHandlesByRowIndex.get(rowIndex);
        return backendId != null && handle != null ? new BackendSpaceKey(backendId, handle) : null;
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
        copy.spaceRefsByRowIndex.putAll(spaceRefsByRowIndex);
        copy.spaceHandlesByRowIndex.putAll(spaceHandlesByRowIndex);
        copy.backendIdsBySpaceRowIndex.putAll(backendIdsBySpaceRowIndex);
        copy.spaceMetadataByKey.putAll(spaceMetadataByKey);
        copy.bodyRefsByRowIndex.putAll(bodyRefsByRowIndex);
        copy.bodyHandlesByRowIndex.putAll(bodyHandlesByRowIndex);
        copy.bodySpaceHandlesByRowIndex.putAll(bodySpaceHandlesByRowIndex);
        copy.backendIdsByBodyRowIndex.putAll(backendIdsByBodyRowIndex);
        copy.jointHandlesByRowIndex.putAll(jointHandlesByRowIndex);
        copy.jointSpaceHandlesByRowIndex.putAll(jointSpaceHandlesByRowIndex);
        copy.jointRefsByRowIndex.putAll(jointRefsByRowIndex);
        copy.backendIdsByJointRowIndex.putAll(backendIdsByJointRowIndex);
        copy.jointMetadataByKey.putAll(jointMetadataByKey);
        copy.jointKeysByUuid.putAll(jointKeysByUuid);
        copy.chunkCollisionPayloadKeysByRowIndex.putAll(chunkCollisionPayloadKeysByRowIndex);
        bodyHandlesBySpaceKey.forEach((key, bodyHandles) ->
            copy.bodyHandlesBySpaceKey.put(key, new LongArrayList(bodyHandles)));
        copy.bodyHitMetadataByKey.putAll(bodyHitMetadataByKey);
        copy.bodySnapshotMetadataByKey.putAll(bodySnapshotMetadataByKey);
        copy.bodySnapshotKeysByUuid.putAll(bodySnapshotKeysByUuid);
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

    private record BackendSpaceKey(@Nonnull BackendId backendId, int spaceHandle) {

        private BackendSpaceKey(@Nonnull BackendId backendId,
            @Nonnull BackendSpaceHandle spaceHandle) {
            this(backendId, spaceHandle.value());
        }

        private BackendSpaceKey {
            Objects.requireNonNull(backendId, "backendId");
        }
    }

    private record BackendBodyKey(@Nonnull BackendId backendId,
                                  int spaceHandle,
                                  long bodyHandle) {

        private BackendBodyKey(@Nonnull BackendId backendId,
            @Nonnull BackendSpaceHandle spaceHandle,
            @Nonnull BackendBodyHandle bodyHandle) {
            this(backendId, spaceHandle.value(), bodyHandle.value());
        }

        private BackendBodyKey(@Nonnull BackendId backendId,
            @Nonnull BackendSpaceHandle spaceHandle,
            long bodyHandle) {
            this(backendId, spaceHandle.value(), bodyHandle);
        }

        private BackendBodyKey {
            Objects.requireNonNull(backendId, "backendId");
        }
    }

    private record BackendJointKey(@Nonnull BackendId backendId,
                                   int spaceHandle,
                                   long jointHandle) {

        private BackendJointKey(@Nonnull BackendId backendId,
            @Nonnull BackendSpaceHandle spaceHandle,
            @Nonnull BackendJointHandle jointHandle) {
            this(backendId, spaceHandle.value(), jointHandle.value());
        }

        private BackendJointKey {
            Objects.requireNonNull(backendId, "backendId");
        }
    }

    private record SpaceMetadata(@Nonnull UUID spaceUuid,
                                 @Nonnull Ref<PhysicsStore> spaceRef) {

        private SpaceMetadata {
            Objects.requireNonNull(spaceUuid, "spaceUuid");
            Objects.requireNonNull(spaceRef, "spaceRef");
        }
    }

    private record JointMetadata(@Nonnull UUID jointUuid,
                                 @Nonnull Ref<PhysicsStore> jointRef) {

        private JointMetadata {
            Objects.requireNonNull(jointUuid, "jointUuid");
            Objects.requireNonNull(jointRef, "jointRef");
        }
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
            @Nonnull Ref<PhysicsStore> bodyRef) {
            return empty(Kind.WAKE, bodyUuid, bodyRef);
        }

        @Nonnull
        public static PendingBodyOperation sleep(@Nonnull UUID bodyUuid,
            @Nonnull Ref<PhysicsStore> bodyRef) {
            return empty(Kind.SLEEP, bodyUuid, bodyRef);
        }

        @Nonnull
        public static PendingBodyOperation vector(@Nonnull Kind kind,
            @Nonnull UUID bodyUuid,
            @Nonnull Ref<PhysicsStore> bodyRef,
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
            @Nonnull Ref<PhysicsStore> bodyRef) {
            return new PendingBodyOperation(kind,
                bodyUuid,
                bodyRef,
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
}
