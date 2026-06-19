package dev.hytalemodding.impulse.core.internal.physicsstore;

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
import java.util.UUID;
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
        PhysicsControlRuntimeStates.clearControlled(bodyRef);
        store.getResource(PhysicsSnapshotResource.getResourceType()).removeBody(bodyUuid);
        store.getResource(PhysicsBodyRegistrationResource.getResourceType()).removeBody(bodyUuid);
    }

    public static void removeBodyEntity(@Nonnull Store<PhysicsStore> store,
        @Nonnull UUID bodyUuid,
        @Nonnull Ref<PhysicsStore> bodyRef,
        @Nullable String payloadResourceKey) {
        clearBodyCopiedState(store, bodyUuid, bodyRef);
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
        identity.clearUuidRefs();
        store.getExternalData().clearUuidIndex();
        store.forEachEntityParallel(UuidComponent.getComponentType(), (index, chunk, _) -> {
            UuidComponent uuid = chunk.getComponent(index, UuidComponent.getComponentType());
            if (uuid == null) {
                return;
            }
            Ref<PhysicsStore> ref = chunk.getReferenceTo(index);
            identity.putUuid(uuid.getUuid(), ref);
            store.getExternalData().putRefForUUID(uuid.getUuid(), ref);
        });
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
}
