package dev.hytalemodding.impulse.core.internal.physics;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.api.runtime.PhysicsBackendRuntime;
import dev.hytalemodding.impulse.core.internal.modules.control.PhysicsControlRuntimeStates;
import dev.hytalemodding.impulse.core.internal.resources.BackendBodyHandle;
import dev.hytalemodding.impulse.core.internal.resources.BackendJointHandle;
import dev.hytalemodding.impulse.core.internal.resources.BackendSpaceHandle;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsBodyRegistrationResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsChunkCollisionPayloadResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsIdentityIndexResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsRuntimeResource;
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

    public static boolean removeRuntimeJoint(@Nonnull PhysicsRuntimeResource runtime,
        @Nonnull PhysicsIdentityIndexResource identity,
        @Nonnull UUID jointUuid,
        @Nonnull Ref<PhysicsStore> jointRef) {
        BackendJointHandle jointHandle = runtime.getJointHandle(jointRef);
        BackendSpaceHandle spaceHandle = runtime.getJointSpaceHandle(jointRef);
        if (jointHandle == null) {
            runtime.removeJointHandle(jointUuid, jointRef);
            return false;
        }
        PhysicsBackendRuntime backendRuntime = runtime.runtimeForJointRef(jointRef);
        if (spaceHandle != null && backendRuntime != null) {
            backendRuntime.removeJoint(spaceHandle.value(), jointHandle.value());
        }
        identity.removeJointHandle(jointHandle);
        runtime.removeJointHandle(jointUuid, jointRef);
        return true;
    }

    public static boolean removeRuntimeBody(@Nonnull PhysicsRuntimeResource runtime,
        @Nonnull PhysicsIdentityIndexResource identity,
        @Nonnull UUID bodyUuid,
        @Nonnull Ref<PhysicsStore> bodyRef) {
        return removeRuntimeBody(runtime, identity, bodyUuid, bodyRef, null);
    }

    public static boolean removeRuntimeBody(@Nonnull PhysicsRuntimeResource runtime,
        @Nonnull PhysicsIdentityIndexResource identity,
        @Nonnull UUID bodyUuid,
        @Nonnull Ref<PhysicsStore> bodyRef,
        @Nullable PhysicsBackendRuntime fallbackRuntime) {
        BackendBodyHandle bodyHandle = runtime.getBodyHandle(bodyRef);
        BackendSpaceHandle spaceHandle = runtime.getBodySpaceHandle(bodyRef);
        if (bodyHandle == null) {
            runtime.removeBodyHandle(bodyUuid, bodyRef);
            return false;
        }
        PhysicsBackendRuntime backendRuntime = runtime.runtimeForBodyRef(bodyRef);
        if (backendRuntime == null) {
            backendRuntime = fallbackRuntime;
        }
        if (spaceHandle != null && backendRuntime != null) {
            backendRuntime.removeBody(spaceHandle.value(), bodyHandle.value());
        }
        identity.removeBodyHandle(bodyHandle);
        runtime.removeBodyHandle(bodyUuid, bodyRef);
        return true;
    }

    public static void clearBodyCopiedState(@Nonnull Store<PhysicsStore> store,
        @Nonnull UUID bodyUuid,
        @Nonnull Ref<PhysicsStore> bodyRef) {
        clearBodyCopiedState(store, List.of(new BodyEntityRemoval(bodyUuid, bodyRef, null)));
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
            PhysicsControlRuntimeStates.clearControlled(removal.bodyRef());
        }
        store.getResource(PhysicsSnapshotResource.getResourceType()).removeBodies(bodyUuids);
        store.getResource(PhysicsBodyRegistrationResource.getResourceType())
            .removeBodies(bodyUuids);
    }

    public static void removeBodyEntity(@Nonnull Store<PhysicsStore> store,
        @Nonnull UUID bodyUuid,
        @Nonnull Ref<PhysicsStore> bodyRef,
        @Nullable String payloadResourceKey) {
        clearBodyCopiedState(store, bodyUuid, bodyRef);
        removeBodyEntityRow(store, bodyUuid, bodyRef, payloadResourceKey);
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
                removal.bodyRef(),
                removal.payloadResourceKey());
        }
    }

    static void removeBodyEntityRow(@Nonnull Store<PhysicsStore> store,
        @Nonnull UUID bodyUuid,
        @Nonnull Ref<PhysicsStore> bodyRef,
        @Nullable String payloadResourceKey) {
        store.getResource(PhysicsIdentityIndexResource.getResourceType()).removeUuid(bodyUuid,
            bodyRef);
        removePayload(store, payloadResourceKey);
        removeEntityIfValid(store, bodyRef);
    }

    public static void removeJointEntity(@Nonnull Store<PhysicsStore> store,
        @Nonnull UUID jointUuid,
        @Nonnull Ref<PhysicsStore> jointRef) {
        store.getResource(PhysicsIdentityIndexResource.getResourceType()).removeUuid(jointUuid,
            jointRef);
        removeEntityIfValid(store, jointRef);
    }

    public static void refreshIdentityAndRuntimeRefs(@Nonnull Store<PhysicsStore> store) {
        PhysicsIdentityIndexResource identity =
            store.getResource(PhysicsIdentityIndexResource.getResourceType());
        PhysicsRuntimeResource runtime = store.getResource(PhysicsRuntimeResource.getResourceType());
        ConcurrentLinkedQueue<UuidRef> uuidRefs = new ConcurrentLinkedQueue<>();
        store.forEachEntityParallel(UuidComponent.getComponentType(), (index, chunk, _) -> {
            UuidComponent uuid = chunk.getComponent(index, UuidComponent.getComponentType());
            if (uuid != null) {
                uuidRefs.add(new UuidRef(uuid.getUuid(), chunk.getReferenceTo(index)));
            }
        });
        // The identity maps are fastutil/Hytale mutable maps; rebuild them on one thread.
        identity.clearUuidRefs();
        store.getExternalData().clearUuidIndex();
        for (UuidRef uuidRef : uuidRefs) {
            identity.putUuid(uuidRef.uuid(), uuidRef.ref());
            store.getExternalData().putRefForUUID(uuidRef.uuid(), uuidRef.ref());
        }
        runtime.refreshRowRefs(identity);
    }

    private static void removePayload(@Nonnull Store<PhysicsStore> store,
        @Nullable String payloadResourceKey) {
        if (payloadResourceKey == null || payloadResourceKey.isBlank()) {
            return;
        }
        store.getResource(PhysicsChunkCollisionPayloadResource.getResourceType())
            .remove(payloadResourceKey);
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
        @Nonnull Ref<PhysicsStore> bodyRef,
        @Nullable String payloadResourceKey) {

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
