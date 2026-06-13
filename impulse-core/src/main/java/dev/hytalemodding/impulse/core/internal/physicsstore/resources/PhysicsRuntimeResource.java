package dev.hytalemodding.impulse.core.internal.physicsstore.resources;

import com.hypixel.hytale.component.Resource;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.api.BackendId;
import dev.hytalemodding.impulse.api.runtime.PhysicsBackendRuntime;
import dev.hytalemodding.impulse.core.internal.resources.BackendBodyHandle;
import dev.hytalemodding.impulse.core.internal.resources.BackendJointHandle;
import dev.hytalemodding.impulse.core.internal.resources.BackendSpaceHandle;
import dev.hytalemodding.impulse.core.plugin.physicsstore.PhysicsStoreTypes;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.util.Map;
import java.util.UUID;
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
    private final Map<UUID, BackendBodyHandle> bodyHandlesByUuid =
        new Object2ObjectOpenHashMap<>();
    @Nonnull
    private final Map<UUID, BackendJointHandle> jointHandlesByUuid =
        new Object2ObjectOpenHashMap<>();
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

    public void putSpaceHandle(@Nonnull UUID spaceUuid, @Nonnull BackendSpaceHandle handle) {
        spaceHandlesByUuid.put(spaceUuid, handle);
    }

    @Nullable
    public BackendSpaceHandle getSpaceHandle(@Nonnull UUID spaceUuid) {
        return spaceHandlesByUuid.get(spaceUuid);
    }

    public void removeSpaceHandle(@Nonnull UUID spaceUuid) {
        spaceHandlesByUuid.remove(spaceUuid);
    }

    public void putBodyHandle(@Nonnull UUID bodyUuid, @Nonnull BackendBodyHandle handle) {
        bodyHandlesByUuid.put(bodyUuid, handle);
    }

    @Nullable
    public BackendBodyHandle getBodyHandle(@Nonnull UUID bodyUuid) {
        return bodyHandlesByUuid.get(bodyUuid);
    }

    public void removeBodyHandle(@Nonnull UUID bodyUuid) {
        bodyHandlesByUuid.remove(bodyUuid);
    }

    public void putJointHandle(@Nonnull UUID jointUuid, @Nonnull BackendJointHandle handle) {
        jointHandlesByUuid.put(jointUuid, handle);
    }

    @Nullable
    public BackendJointHandle getJointHandle(@Nonnull UUID jointUuid) {
        return jointHandlesByUuid.get(jointUuid);
    }

    public void removeJointHandle(@Nonnull UUID jointUuid) {
        jointHandlesByUuid.remove(jointUuid);
    }

    public void clear() {
        runtimesByBackend.clear();
        spaceHandlesByUuid.clear();
        bodyHandlesByUuid.clear();
        jointHandlesByUuid.clear();
        started = false;
    }

    @Nonnull
    @Override
    public PhysicsRuntimeResource clone() {
        PhysicsRuntimeResource copy = new PhysicsRuntimeResource();
        copy.runtimesByBackend.putAll(runtimesByBackend);
        copy.spaceHandlesByUuid.putAll(spaceHandlesByUuid);
        copy.bodyHandlesByUuid.putAll(bodyHandlesByUuid);
        copy.jointHandlesByUuid.putAll(jointHandlesByUuid);
        copy.started = started;
        return copy;
    }

    @Nonnull
    public static ResourceType<PhysicsStore, PhysicsRuntimeResource> getResourceType() {
        return PhysicsStoreTypes.runtimeResourceType();
    }
}
