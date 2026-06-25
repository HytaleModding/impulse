package dev.hytalemodding.impulse.core.internal.physics;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.api.BackendId;
import dev.hytalemodding.impulse.api.runtime.PhysicsBackendRuntime;
import dev.hytalemodding.impulse.core.internal.resources.BackendBodyHandle;
import dev.hytalemodding.impulse.core.internal.resources.BackendJointHandle;
import dev.hytalemodding.impulse.core.internal.resources.BackendSpaceHandle;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsRuntimeResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsRuntimeResource.BodySnapshotMetadata;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsSnapshotResource;
import dev.hytalemodding.impulse.core.plugin.components.UuidComponent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Shared cleanup for PhysicsStore-owned body and joint rows.
 */
public final class PhysicsStoreRowCleanup {

    private PhysicsStoreRowCleanup() {
    }

    public static boolean removeRuntimeJoint(@Nonnull Store<PhysicsStore> store,
        @Nonnull PhysicsRuntimeResource runtime,
        @Nonnull UUID jointUuid,
        @Nonnull Ref<PhysicsStore> jointRef) {
        Ref<PhysicsStore> resolvedJointRef = cleanupRefForUuid(store, jointUuid, jointRef);
        BackendJointHandle jointHandle = runtime.getJointHandle(resolvedJointRef);
        BackendSpaceHandle spaceHandle = runtime.getJointSpaceHandle(resolvedJointRef);
        if (jointHandle == null) {
            if (refMatchesUuid(resolvedJointRef, jointUuid)) {
                runtime.removeJointHandle(resolvedJointRef);
            }
            return false;
        }
        BackendId backendId = runtime.getJointBackendId(resolvedJointRef);
        if (!jointBindingMatchesUuid(runtime, jointUuid, backendId, spaceHandle, jointHandle)) {
            return false;
        }
        PhysicsBackendRuntime backendRuntime = runtime.runtimeForJointRef(resolvedJointRef);
        if (backendRuntime != null) {
            backendRuntime.removeJoint(spaceHandle.value(), jointHandle.value());
        }
        runtime.removeJointHandle(resolvedJointRef);
        return true;
    }

    public static boolean removeRuntimeBody(@Nonnull Store<PhysicsStore> store,
        @Nonnull PhysicsRuntimeResource runtime,
        @Nonnull UUID bodyUuid,
        @Nonnull Ref<PhysicsStore> bodyRef) {
        return removeRuntimeBody(store, runtime, bodyUuid, bodyRef, null);
    }

    public static boolean removeRuntimeBody(@Nonnull Store<PhysicsStore> store,
        @Nonnull PhysicsRuntimeResource runtime,
        @Nonnull UUID bodyUuid,
        @Nonnull Ref<PhysicsStore> bodyRef,
        @Nullable PhysicsBackendRuntime fallbackRuntime) {
        Ref<PhysicsStore> resolvedBodyRef = cleanupRefForUuid(store, bodyUuid, bodyRef);
        BackendBodyHandle bodyHandle = runtime.getBodyHandle(resolvedBodyRef);
        BackendSpaceHandle spaceHandle = runtime.getBodySpaceHandle(resolvedBodyRef);
        if (bodyHandle == null) {
            if (refMatchesUuid(resolvedBodyRef, bodyUuid)) {
                runtime.removeBodyHandle(resolvedBodyRef);
                return false;
            }
            if (resolvedBodyRef.getStore() == store && resolvedBodyRef.isValid()) {
                return false;
            }
            return runtime.removeBackendBody(bodyUuid, fallbackRuntime);
        }
        BackendId backendId = runtime.getBodyBackendId(resolvedBodyRef);
        if (!bodyBindingMatchesUuid(runtime, bodyUuid, backendId, spaceHandle, bodyHandle)) {
            return false;
        }
        PhysicsBackendRuntime backendRuntime = runtime.runtimeForBodyRef(resolvedBodyRef);
        if (backendRuntime == null) {
            backendRuntime = fallbackRuntime;
        }
        if (spaceHandle != null && backendRuntime != null) {
            backendRuntime.removeBody(spaceHandle.value(), bodyHandle.value());
        }
        runtime.removeBodyHandle(resolvedBodyRef);
        return true;
    }

    @Nonnull
    private static Ref<PhysicsStore> cleanupRefForUuid(@Nonnull Store<PhysicsStore> store,
        @Nonnull UUID rowUuid,
        @Nonnull Ref<PhysicsStore> suppliedRef) {
        Ref<PhysicsStore> indexedRef = store.getExternalData().getRefFromUUID(rowUuid);
        if (refMatchesUuid(indexedRef, rowUuid)) {
            return indexedRef;
        }
        if (refMatchesUuid(suppliedRef, rowUuid)) {
            return suppliedRef;
        }
        return suppliedRef;
    }

    private static boolean bodyBindingMatchesUuid(@Nonnull PhysicsRuntimeResource runtime,
        @Nonnull UUID bodyUuid,
        @Nullable BackendId backendId,
        @Nullable BackendSpaceHandle spaceHandle,
        @Nonnull BackendBodyHandle bodyHandle) {
        if (backendId == null || spaceHandle == null) {
            return false;
        }
        BodySnapshotMetadata metadata = runtime.getBodySnapshotMetadata(backendId,
            spaceHandle,
            bodyHandle.value());
        return metadata != null && bodyUuid.equals(metadata.bodyUuid());
    }

    private static boolean jointBindingMatchesUuid(@Nonnull PhysicsRuntimeResource runtime,
        @Nonnull UUID jointUuid,
        @Nullable BackendId backendId,
        @Nullable BackendSpaceHandle spaceHandle,
        @Nonnull BackendJointHandle jointHandle) {
        if (backendId == null || spaceHandle == null) {
            return false;
        }
        UUID boundJointUuid = runtime.getJointUuid(backendId, spaceHandle, jointHandle.value());
        return jointUuid.equals(boundJointUuid);
    }

    private static boolean refMatchesUuid(@Nullable Ref<PhysicsStore> ref,
        @Nonnull UUID rowUuid) {
        if (ref == null || !ref.isValid() || ref.getStore() == null) {
            return false;
        }
        UuidComponent uuid = ref.getStore().getComponent(ref, UuidComponent.getComponentType());
        return uuid != null && rowUuid.equals(uuid.getUuid());
    }

    public static void clearBodyCopiedState(@Nonnull Store<PhysicsStore> store,
        @Nonnull UUID bodyUuid,
        @Nonnull Ref<PhysicsStore> bodyRef) {
        clearBodyCopiedState(store, List.of(new BodyEntityRemoval(bodyUuid, bodyRef)));
    }

    public static void clearBodyCopiedState(@Nonnull Store<PhysicsStore> store,
        @Nonnull Collection<BodyEntityRemoval> removals) {
        Objects.requireNonNull(removals, "removals");
        if (removals.isEmpty()) {
            return;
        }
        List<UUID> bodyUuids = new ArrayList<>(removals.size());
        for (BodyEntityRemoval removal : removals) {
            Objects.requireNonNull(removal, "removal");
            bodyUuids.add(removal.bodyUuid());
        }
        store.getResource(PhysicsSnapshotResource.getResourceType()).removeBodies(bodyUuids);
    }

    public static void removeBodyEntity(@Nonnull Store<PhysicsStore> store,
        @Nonnull UUID bodyUuid,
        @Nonnull Ref<PhysicsStore> bodyRef) {
        clearBodyCopiedState(store, bodyUuid, bodyRef);
        removeBodyEntityRow(store, bodyUuid, bodyRef);
    }

    public static void removeBodyEntities(@Nonnull Store<PhysicsStore> store,
        @Nonnull Collection<BodyEntityRemoval> removals) {
        Objects.requireNonNull(removals, "removals");
        if (removals.isEmpty()) {
            return;
        }
        clearBodyCopiedState(store, removals);
        for (BodyEntityRemoval removal : removals) {
            removeBodyEntityRow(store,
                removal.bodyUuid(),
                removal.bodyRef());
        }
    }

    static void removeBodyEntityRow(@Nonnull Store<PhysicsStore> store,
        @Nonnull UUID bodyUuid,
        @Nonnull Ref<PhysicsStore> bodyRef) {
        PhysicsStoreCleanupHooks.cleanupBodyRowRuntimeResources(store, bodyUuid, bodyRef);
        store.getExternalData().removeRefForUUID(bodyUuid, bodyRef);
        removeEntityIfValid(store, bodyRef);
    }

    public static void removeJointEntity(@Nonnull Store<PhysicsStore> store,
        @Nonnull UUID jointUuid,
        @Nonnull Ref<PhysicsStore> jointRef) {
        store.getExternalData().removeRefForUUID(jointUuid, jointRef);
        removeEntityIfValid(store, jointRef);
    }

    public static void refreshIdentityAndRuntimeRefs(@Nonnull Store<PhysicsStore> store) {
        PhysicsRuntimeResource runtime = store.getResource(PhysicsRuntimeResource.getResourceType());
        ConcurrentLinkedQueue<UuidRef> uuidRefs = new ConcurrentLinkedQueue<>();
        store.forEachEntityParallel(UuidComponent.getComponentType(), (index, chunk, _) -> {
            UuidComponent uuid = chunk.getComponent(index, UuidComponent.getComponentType());
            if (uuid != null) {
                uuidRefs.add(new UuidRef(uuid.getUuid(), chunk.getReferenceTo(index)));
            }
        });
        // The UUID map is mutable store state; rebuild it on one thread.
        store.getExternalData().clearUuidIndex();
        for (UuidRef uuidRef : uuidRefs) {
            store.getExternalData().putRefForUUID(uuidRef.uuid(), uuidRef.ref());
        }
        runtime.refreshRowRefs(store.getExternalData());
    }

    private static void removeEntityIfValid(@Nonnull Store<PhysicsStore> store,
        @Nonnull Ref<PhysicsStore> ref) {
        if (!ref.isValid()) {
            return;
        }
        store.removeEntity(ref, store.getRegistry().newHolder(), RemoveReason.REMOVE);
    }

    public record BodyEntityRemoval(
        @Nonnull UUID bodyUuid,
        @Nonnull Ref<PhysicsStore> bodyRef) {

        public BodyEntityRemoval {
            Objects.requireNonNull(bodyUuid, "bodyUuid");
            Objects.requireNonNull(bodyRef, "bodyRef");
        }
    }

    private record UuidRef(@Nonnull UUID uuid,
                           @Nonnull Ref<PhysicsStore> ref) {

        private UuidRef {
            Objects.requireNonNull(uuid, "uuid");
            Objects.requireNonNull(ref, "ref");
        }
    }
}
