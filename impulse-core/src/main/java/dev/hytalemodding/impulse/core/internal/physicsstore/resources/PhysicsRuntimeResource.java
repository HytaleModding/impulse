package dev.hytalemodding.impulse.core.internal.physicsstore.resources;

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
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import dev.hytalemodding.impulse.core.plugin.physicsstore.PhysicsStoreTypes;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.util.Map;
import java.util.Objects;
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
    private final Map<UUID, BackendBodyHandle> bodyHandlesByUuid =
        new Object2ObjectOpenHashMap<>();
    @Nonnull
    private final Map<UUID, BackendSpaceHandle> bodySpaceHandlesByUuid =
        new Object2ObjectOpenHashMap<>();
    @Nonnull
    private final Map<UUID, BackendJointHandle> jointHandlesByUuid =
        new Object2ObjectOpenHashMap<>();
    @Nonnull
    private final Map<UUID, BackendSpaceHandle> jointSpaceHandlesByUuid =
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
    private final Int2ObjectOpenHashMap<LongList> bodyHandlesBySpaceHandle =
        new Int2ObjectOpenHashMap<>();
    @Nonnull
    private final Long2ObjectOpenHashMap<BodyHitMetadata> bodyHitMetadataByHandle =
        new Long2ObjectOpenHashMap<>();
    private boolean started;

    public PhysicsRuntimeResource() {
    }

    public boolean isStarted() {
        return started;
    }

    public void setStarted(boolean started) {
        this.started = started;
    }

    public void putRuntime(@Nonnull BackendId backendId, @Nonnull PhysicsBackendRuntime runtime) {
        runtimesByBackend.put(backendId, runtime);
    }

    @Nullable
    public PhysicsBackendRuntime getRuntime(@Nonnull BackendId backendId) {
        return runtimesByBackend.get(backendId);
    }

    public void putSpaceBinding(@Nonnull UUID spaceUuid,
        @Nonnull BackendId backendId,
        @Nonnull BackendSpaceHandle handle) {
        backendIdsBySpaceUuid.put(spaceUuid, backendId);
        spaceHandlesByUuid.put(spaceUuid, handle);
    }

    @Nullable
    public BackendSpaceHandle getSpaceHandle(@Nonnull UUID spaceUuid) {
        return spaceHandlesByUuid.get(spaceUuid);
    }

    @Nullable
    public BackendId getSpaceBackendId(@Nonnull UUID spaceUuid) {
        return backendIdsBySpaceUuid.get(spaceUuid);
    }

    public void removeSpaceHandle(@Nonnull UUID spaceUuid) {
        BackendSpaceHandle removed = spaceHandlesByUuid.remove(spaceUuid);
        backendIdsBySpaceUuid.remove(spaceUuid);
        if (removed != null) {
            LongList bodyHandles = bodyHandlesBySpaceHandle.remove(removed.value());
            if (bodyHandles != null) {
                bodyHandles.forEach((long bodyHandle) -> bodyHitMetadataByHandle.remove(bodyHandle));
            }
            removeTerrainHandlesForSpace(removed);
        }
    }

    public void putBodyHandle(@Nonnull UUID bodyUuid,
        @Nonnull BackendSpaceHandle spaceHandle,
        @Nonnull BackendBodyHandle handle) {
        bodyHandlesByUuid.put(bodyUuid, handle);
        bodySpaceHandlesByUuid.put(bodyUuid, spaceHandle);
        bodyHandlesBySpaceHandle.computeIfAbsent(spaceHandle.value(), _ -> new LongArrayList())
            .add(handle.value());
    }

    @Nullable
    public BackendBodyHandle getBodyHandle(@Nonnull UUID bodyUuid) {
        return bodyHandlesByUuid.get(bodyUuid);
    }

    @Nullable
    public BackendSpaceHandle getBodySpaceHandle(@Nonnull UUID bodyUuid) {
        return bodySpaceHandlesByUuid.get(bodyUuid);
    }

    public void removeBodyHandle(@Nonnull UUID bodyUuid) {
        BackendBodyHandle removed = bodyHandlesByUuid.remove(bodyUuid);
        BackendSpaceHandle spaceHandle = bodySpaceHandlesByUuid.remove(bodyUuid);
        if (removed != null && spaceHandle != null) {
            LongList bodyHandles = bodyHandlesBySpaceHandle.get(spaceHandle.value());
            if (bodyHandles != null) {
                bodyHandles.rem(removed.value());
                if (bodyHandles.isEmpty()) {
                    bodyHandlesBySpaceHandle.remove(spaceHandle.value());
                }
            }
            bodyHitMetadataByHandle.remove(removed.value());
        }
    }

    public void putBodyHitMetadata(@Nonnull BackendBodyHandle handle,
        @Nullable RigidBodyKey bodyKey,
        @Nonnull PhysicsBodyType bodyType,
        @Nonnull ShapeType shapeType) {
        bodyHitMetadataByHandle.put(handle.value(),
            new BodyHitMetadata(bodyKey, bodyType, shapeType));
    }

    @Nullable
    public BodyHitMetadata getBodyHitMetadata(@Nonnull BackendBodyHandle handle) {
        return bodyHitMetadataByHandle.get(handle.value());
    }

    public void removeBodyHitMetadata(@Nonnull BackendBodyHandle handle) {
        bodyHitMetadataByHandle.remove(handle.value());
    }

    public void putJointHandle(@Nonnull UUID jointUuid,
        @Nonnull BackendSpaceHandle spaceHandle,
        @Nonnull BackendJointHandle handle) {
        jointHandlesByUuid.put(jointUuid, handle);
        jointSpaceHandlesByUuid.put(jointUuid, spaceHandle);
    }

    @Nullable
    public BackendJointHandle getJointHandle(@Nonnull UUID jointUuid) {
        return jointHandlesByUuid.get(jointUuid);
    }

    @Nullable
    public BackendSpaceHandle getJointSpaceHandle(@Nonnull UUID jointUuid) {
        return jointSpaceHandlesByUuid.get(jointUuid);
    }

    public void removeJointHandle(@Nonnull UUID jointUuid) {
        jointHandlesByUuid.remove(jointUuid);
        jointSpaceHandlesByUuid.remove(jointUuid);
    }

    public void putTerrainBodyHandle(@Nonnull UUID terrainUuid,
        @Nonnull BackendSpaceHandle spaceHandle,
        @Nonnull BackendBodyHandle handle,
        boolean voxelTerrainBody) {
        terrainSpaceHandlesByUuid.put(terrainUuid, spaceHandle);
        terrainBodyHandlesByUuid.computeIfAbsent(terrainUuid, _ -> new LongArrayList())
            .add(handle.value());
        if (voxelTerrainBody) {
            terrainVoxelBodyHandlesByUuid.put(terrainUuid, handle);
        }
    }

    public void markTerrainPayloadBound(@Nonnull UUID terrainUuid, @Nonnull String payloadKey) {
        terrainPayloadKeysByUuid.put(terrainUuid, payloadKey);
    }

    public boolean isTerrainPayloadBound(@Nonnull UUID terrainUuid, @Nonnull String payloadKey) {
        return payloadKey.equals(terrainPayloadKeysByUuid.get(terrainUuid));
    }

    public boolean hasTerrainBodyHandles(@Nonnull UUID terrainUuid) {
        LongList bodyHandles = terrainBodyHandlesByUuid.get(terrainUuid);
        return bodyHandles != null && !bodyHandles.isEmpty();
    }

    @Nullable
    public BackendSpaceHandle getTerrainSpaceHandle(@Nonnull UUID terrainUuid) {
        return terrainSpaceHandlesByUuid.get(terrainUuid);
    }

    @Nullable
    public BackendBodyHandle getTerrainVoxelBodyHandle(@Nonnull UUID terrainUuid) {
        return terrainVoxelBodyHandlesByUuid.get(terrainUuid);
    }

    public void forEachTerrainBodyHandle(@Nonnull UUID terrainUuid,
        @Nonnull LongConsumer consumer) {
        LongList bodyHandles = terrainBodyHandlesByUuid.get(terrainUuid);
        if (bodyHandles == null) {
            return;
        }
        bodyHandles.forEach(consumer);
    }

    public void removeTerrainHandles(@Nonnull UUID terrainUuid) {
        LongList bodyHandles = terrainBodyHandlesByUuid.get(terrainUuid);
        if (bodyHandles != null) {
            bodyHandles.forEach((long bodyHandle) -> bodyHitMetadataByHandle.remove(bodyHandle));
        }
        terrainBodyHandlesByUuid.remove(terrainUuid);
        terrainVoxelBodyHandlesByUuid.remove(terrainUuid);
        terrainSpaceHandlesByUuid.remove(terrainUuid);
        terrainPayloadKeysByUuid.remove(terrainUuid);
    }

    public void forEachSpaceBinding(@Nonnull SpaceBindingConsumer consumer) {
        spaceHandlesByUuid.forEach((spaceUuid, spaceHandle) -> {
            BackendId backendId = backendIdsBySpaceUuid.get(spaceUuid);
            PhysicsBackendRuntime runtime = backendId != null ? runtimesByBackend.get(backendId) : null;
            if (backendId != null && runtime != null) {
                consumer.accept(spaceUuid, backendId, spaceHandle, runtime);
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
        bodyHandlesByUuid.clear();
        bodySpaceHandlesByUuid.clear();
        jointHandlesByUuid.clear();
        jointSpaceHandlesByUuid.clear();
        terrainBodyHandlesByUuid.clear();
        terrainVoxelBodyHandlesByUuid.clear();
        terrainSpaceHandlesByUuid.clear();
        terrainPayloadKeysByUuid.clear();
        bodyHandlesBySpaceHandle.clear();
        bodyHitMetadataByHandle.clear();
        started = false;
    }

    @Nonnull
    @Override
    public PhysicsRuntimeResource clone() {
        PhysicsRuntimeResource copy = new PhysicsRuntimeResource();
        copy.runtimesByBackend.putAll(runtimesByBackend);
        copy.spaceHandlesByUuid.putAll(spaceHandlesByUuid);
        copy.backendIdsBySpaceUuid.putAll(backendIdsBySpaceUuid);
        copy.bodyHandlesByUuid.putAll(bodyHandlesByUuid);
        copy.bodySpaceHandlesByUuid.putAll(bodySpaceHandlesByUuid);
        copy.jointHandlesByUuid.putAll(jointHandlesByUuid);
        copy.jointSpaceHandlesByUuid.putAll(jointSpaceHandlesByUuid);
        terrainBodyHandlesByUuid.forEach((terrainUuid, bodyHandles) ->
            copy.terrainBodyHandlesByUuid.put(terrainUuid, new LongArrayList(bodyHandles)));
        copy.terrainVoxelBodyHandlesByUuid.putAll(terrainVoxelBodyHandlesByUuid);
        copy.terrainSpaceHandlesByUuid.putAll(terrainSpaceHandlesByUuid);
        copy.terrainPayloadKeysByUuid.putAll(terrainPayloadKeysByUuid);
        bodyHandlesBySpaceHandle.forEach((spaceHandle, bodyHandles) ->
            copy.bodyHandlesBySpaceHandle.put((int) spaceHandle, new LongArrayList(bodyHandles)));
        copy.bodyHitMetadataByHandle.putAll(bodyHitMetadataByHandle);
        copy.started = started;
        return copy;
    }

    @Nonnull
    public static ResourceType<PhysicsStore, PhysicsRuntimeResource> getResourceType() {
        return PhysicsStoreTypes.runtimeResourceType();
    }

    @FunctionalInterface
    public interface SpaceBindingConsumer {

        void accept(@Nonnull UUID spaceUuid,
            @Nonnull BackendId backendId,
            @Nonnull BackendSpaceHandle spaceHandle,
            @Nonnull PhysicsBackendRuntime runtime);
    }

    public record BodyHitMetadata(@Nullable RigidBodyKey bodyKey,
                                  @Nonnull PhysicsBodyType bodyType,
                                  @Nonnull ShapeType shapeType) {

        public BodyHitMetadata {
            Objects.requireNonNull(bodyType, "bodyType");
            Objects.requireNonNull(shapeType, "shapeType");
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
                bodyHandles.forEach((long bodyHandle) -> bodyHitMetadataByHandle.remove(bodyHandle));
            }
            terrainBodyHandlesByUuid.remove(terrainUuid);
            terrainVoxelBodyHandlesByUuid.remove(terrainUuid);
            terrainPayloadKeysByUuid.remove(terrainUuid);
            return true;
        });
    }
}
