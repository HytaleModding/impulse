package dev.hytalemodding.impulse.core.internal.physicsstore;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.api.runtime.PhysicsBackendRuntime;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsBodyRegistrationResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsEventResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsIdentityIndexResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsRuntimeResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsSnapshotResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsSpaceCompatibilityIndexResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsTerrainMutationQueueResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsTerrainPayloadResource;
import dev.hytalemodding.impulse.core.internal.resources.BackendBodyHandle;
import dev.hytalemodding.impulse.core.internal.resources.BackendJointHandle;
import dev.hytalemodding.impulse.core.internal.resources.BackendSpaceHandle;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsRuntimeResetResult;
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import dev.hytalemodding.impulse.core.plugin.physicsstore.PhysicsStoreThreading;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.BodyComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.JointComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.TerrainColliderComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.UuidComponent;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Owner-lane topology mutations for public compatibility cleanup paths.
 */
public final class PhysicsStoreTopologyMutations {

    private PhysicsStoreTopologyMutations() {
    }

    public static void destroyBody(@Nonnull Store<PhysicsStore> store,
        @Nonnull RigidBodyKey bodyKey) {
        destroyBody(store, Objects.requireNonNull(bodyKey, "bodyKey").value());
    }

    public static void destroyBody(@Nonnull Store<PhysicsStore> store,
        @Nonnull UUID bodyUuid) {
        PhysicsStoreThreading.requireWorldThread(store, "destroy a PhysicsStore body row");
        PhysicsRuntimeResource runtime = store.getResource(PhysicsRuntimeResource.getResourceType());
        PhysicsIdentityIndexResource identity =
            store.getResource(PhysicsIdentityIndexResource.getResourceType());
        List<RowRemoval> removals = collectRows(store, null, bodyUuid);
        for (RowRemoval removal : removals) {
            if (removal.kind() == RowKind.JOINT) {
                removeRuntimeJoint(runtime, identity, removal.rowUuid());
            }
        }
        removeRuntimeBody(runtime, identity, bodyUuid);
        removeRows(store, removals);
    }

    @Nonnull
    public static PhysicsRuntimeResetResult clearBodiesKeepingSpaces(
        @Nonnull Store<PhysicsStore> store) {
        PhysicsStoreThreading.requireWorldThread(store, "clear PhysicsStore body rows");
        PhysicsRuntimeResource runtime = store.getResource(PhysicsRuntimeResource.getResourceType());
        PhysicsIdentityIndexResource identity =
            store.getResource(PhysicsIdentityIndexResource.getResourceType());
        TopologyCounts removed = countBackendTopology(runtime);
        for (BackendSpaceHandle spaceHandle : spaceHandles(runtime)) {
            removeRuntimeContentsForSpace(runtime, identity, spaceHandle);
        }
        removeRows(store, collectRows(store, null, null));
        clearCopiedBodyState(store);
        store.getResource(PhysicsTerrainMutationQueueResource.getResourceType()).clear();
        store.getResource(PhysicsTerrainPayloadResource.getResourceType()).clear();
        runtime.clearTransientBodyOperations();
        int keptSpaces = store.getResource(PhysicsSpaceCompatibilityIndexResource.getResourceType())
            .size();
        return new PhysicsRuntimeResetResult(removed.bodyCount(),
            removed.jointCount(),
            keptSpaces);
    }

    public static void removeSpaceWithContents(@Nonnull Store<PhysicsStore> store,
        @Nonnull SpaceId spaceId) {
        UUID spaceUuid = PhysicsStoreSpaceMutations.requireSpaceUuid(store, spaceId);
        removeSpaceWithContents(store, spaceUuid);
    }

    public static void removeSpaceWithContents(@Nonnull Store<PhysicsStore> store,
        @Nonnull UUID spaceUuid) {
        PhysicsStoreThreading.requireWorldThread(store, "remove a PhysicsStore space row");
        PhysicsRuntimeResource runtime = store.getResource(PhysicsRuntimeResource.getResourceType());
        PhysicsIdentityIndexResource identity =
            store.getResource(PhysicsIdentityIndexResource.getResourceType());
        BackendSpaceHandle spaceHandle = runtime.getSpaceHandle(spaceUuid);
        if (spaceHandle != null) {
            removeRuntimeContentsForSpace(runtime, identity, spaceHandle);
        }
        store.getResource(PhysicsTerrainMutationQueueResource.getResourceType())
            .removeIf(mutation -> spaceUuid.equals(mutation.spaceUuid()));
        removeRows(store, collectRows(store, spaceUuid, null));
        PhysicsStoreSpaceMutations.removeEmptySpace(store, spaceUuid);
    }

    public static int clearTerrainForSpace(@Nonnull Store<PhysicsStore> store,
        @Nonnull UUID spaceUuid) {
        PhysicsStoreThreading.requireWorldThread(store, "clear PhysicsStore terrain rows");
        PhysicsRuntimeResource runtime = store.getResource(PhysicsRuntimeResource.getResourceType());
        int removedBodies = 0;
        BackendSpaceHandle spaceHandle = runtime.getSpaceHandle(spaceUuid);
        if (spaceHandle != null) {
            for (UUID terrainUuid : runtime.terrainUuidsForSpaceHandle(spaceHandle)) {
                removedBodies += removeRuntimeTerrain(runtime, terrainUuid);
            }
        }
        store.getResource(PhysicsTerrainMutationQueueResource.getResourceType())
            .removeIf(mutation -> spaceUuid.equals(mutation.spaceUuid()));
        removeRows(store, collectTerrainRows(store, spaceUuid));
        return removedBodies;
    }

    private static void removeRuntimeContentsForSpace(@Nonnull PhysicsRuntimeResource runtime,
        @Nonnull PhysicsIdentityIndexResource identity,
        @Nonnull BackendSpaceHandle spaceHandle) {
        for (UUID jointUuid : runtime.jointUuidsForSpaceHandle(spaceHandle)) {
            removeRuntimeJoint(runtime, identity, jointUuid);
        }
        for (UUID terrainUuid : runtime.terrainUuidsForSpaceHandle(spaceHandle)) {
            removeRuntimeTerrain(runtime, terrainUuid);
        }
        for (UUID bodyUuid : runtime.bodyUuidsForSpaceHandle(spaceHandle)) {
            removeRuntimeBody(runtime, identity, bodyUuid);
        }
    }

    private static boolean removeRuntimeJoint(@Nonnull PhysicsRuntimeResource runtime,
        @Nonnull PhysicsIdentityIndexResource identity,
        @Nonnull UUID jointUuid) {
        BackendJointHandle jointHandle = runtime.getJointHandle(jointUuid);
        BackendSpaceHandle spaceHandle = runtime.getJointSpaceHandle(jointUuid);
        if (jointHandle == null) {
            runtime.removeJointHandle(jointUuid);
            return false;
        }
        PhysicsBackendRuntime backendRuntime = runtime.runtimeForSpaceHandle(spaceHandle);
        if (spaceHandle != null && backendRuntime != null) {
            backendRuntime.removeJoint(spaceHandle.value(), jointHandle.value());
        }
        identity.removeJointHandle(jointHandle);
        runtime.removeJointHandle(jointUuid);
        return true;
    }

    private static int removeRuntimeTerrain(@Nonnull PhysicsRuntimeResource runtime,
        @Nonnull UUID terrainUuid) {
        BackendSpaceHandle spaceHandle = runtime.getTerrainSpaceHandle(terrainUuid);
        LongArrayList bodyHandles = new LongArrayList();
        runtime.forEachTerrainBodyHandle(terrainUuid, bodyId -> bodyHandles.add(bodyId));
        if (spaceHandle != null) {
            PhysicsBackendRuntime backendRuntime = runtime.runtimeForSpaceHandle(spaceHandle);
            if (backendRuntime != null) {
                for (int index = 0; index < bodyHandles.size(); index++) {
                    backendRuntime.removeBody(spaceHandle.value(), bodyHandles.getLong(index));
                }
            }
        }
        runtime.removeTerrainHandles(terrainUuid);
        return bodyHandles.size();
    }

    private static boolean removeRuntimeBody(@Nonnull PhysicsRuntimeResource runtime,
        @Nonnull PhysicsIdentityIndexResource identity,
        @Nonnull UUID bodyUuid) {
        BackendBodyHandle bodyHandle = runtime.getBodyHandle(bodyUuid);
        BackendSpaceHandle spaceHandle = runtime.getBodySpaceHandle(bodyUuid);
        if (bodyHandle == null) {
            runtime.removeBodyHandle(bodyUuid);
            return false;
        }
        PhysicsBackendRuntime backendRuntime = runtime.runtimeForSpaceHandle(spaceHandle);
        if (spaceHandle != null && backendRuntime != null) {
            backendRuntime.removeBody(spaceHandle.value(), bodyHandle.value());
        }
        identity.removeBodyHandle(bodyHandle);
        runtime.removeBodyHandle(bodyUuid);
        return true;
    }

    @Nonnull
    private static List<BackendSpaceHandle> spaceHandles(@Nonnull PhysicsRuntimeResource runtime) {
        List<BackendSpaceHandle> handles = new ArrayList<>();
        runtime.forEachSpaceBinding((_, _, spaceHandle, _) -> handles.add(spaceHandle));
        return handles;
    }

    @Nonnull
    private static TopologyCounts countBackendTopology(@Nonnull PhysicsRuntimeResource runtime) {
        TopologyCounts counts = new TopologyCounts();
        runtime.forEachSpaceBinding((_, _, spaceHandle, backendRuntime) -> {
            counts.addBodies(backendRuntime.bodyCount(spaceHandle.value()));
            counts.addJoints(backendRuntime.jointCount(spaceHandle.value()));
        });
        return counts;
    }

    @Nonnull
    private static List<RowRemoval> collectRows(@Nonnull Store<PhysicsStore> store,
        @Nullable UUID spaceUuid,
        @Nullable UUID bodyUuid) {
        ComponentType<PhysicsStore, UuidComponent> uuidType = UuidComponent.getComponentType();
        ConcurrentLinkedQueue<RowRemoval> removals = new ConcurrentLinkedQueue<>();
        store.forEachEntityParallel(uuidType, (index, chunk, _) -> {
            UuidComponent uuid = chunk.getComponent(index, uuidType);
            if (uuid == null) {
                return;
            }
            UUID rowUuid = uuid.getUuid();
            Ref<PhysicsStore> ref = chunk.getReferenceTo(index);
            JointComponent joint = chunk.getComponent(index, JointComponent.getComponentType());
            if (matchesJoint(joint, spaceUuid, bodyUuid)) {
                removals.add(new RowRemoval(ref, rowUuid, RowKind.JOINT, null));
                return;
            }
            TerrainColliderComponent terrain = chunk.getComponent(index,
                TerrainColliderComponent.getComponentType());
            if (matchesTerrain(terrain, spaceUuid, bodyUuid)) {
                removals.add(new RowRemoval(ref,
                    rowUuid,
                    RowKind.TERRAIN,
                    terrain.getPayloadResourceKey()));
                return;
            }
            BodyComponent body = chunk.getComponent(index, BodyComponent.getComponentType());
            if (matchesBody(body, rowUuid, spaceUuid, bodyUuid)) {
                removals.add(new RowRemoval(ref, rowUuid, RowKind.BODY, null));
            }
        });
        return new ArrayList<>(removals);
    }

    @Nonnull
    private static List<RowRemoval> collectTerrainRows(@Nonnull Store<PhysicsStore> store,
        @Nonnull UUID spaceUuid) {
        ComponentType<PhysicsStore, UuidComponent> uuidType = UuidComponent.getComponentType();
        ConcurrentLinkedQueue<RowRemoval> removals = new ConcurrentLinkedQueue<>();
        store.forEachEntityParallel(uuidType, (index, chunk, _) -> {
            TerrainColliderComponent terrain = chunk.getComponent(index,
                TerrainColliderComponent.getComponentType());
            if (terrain == null || !spaceUuid.equals(terrain.getSpaceUuid())) {
                return;
            }
            UuidComponent uuid = chunk.getComponent(index, uuidType);
            if (uuid == null) {
                return;
            }
            removals.add(new RowRemoval(chunk.getReferenceTo(index),
                uuid.getUuid(),
                RowKind.TERRAIN,
                terrain.getPayloadResourceKey()));
        });
        return new ArrayList<>(removals);
    }

    private static boolean matchesJoint(@Nullable JointComponent joint,
        @Nullable UUID spaceUuid,
        @Nullable UUID bodyUuid) {
        if (joint == null) {
            return false;
        }
        if (spaceUuid != null && !spaceUuid.equals(joint.getSpaceUuid())) {
            return false;
        }
        return bodyUuid == null
            || bodyUuid.equals(joint.getBodyAUuid())
            || bodyUuid.equals(joint.getBodyBUuid());
    }

    private static boolean matchesTerrain(@Nullable TerrainColliderComponent terrain,
        @Nullable UUID spaceUuid,
        @Nullable UUID bodyUuid) {
        return bodyUuid == null
            && terrain != null
            && (spaceUuid == null || spaceUuid.equals(terrain.getSpaceUuid()));
    }

    private static boolean matchesBody(@Nullable BodyComponent body,
        @Nonnull UUID rowUuid,
        @Nullable UUID spaceUuid,
        @Nullable UUID bodyUuid) {
        if (body == null) {
            return false;
        }
        if (spaceUuid != null && !spaceUuid.equals(body.getSpaceUuid())) {
            return false;
        }
        return bodyUuid == null || bodyUuid.equals(rowUuid);
    }

    private static void removeRows(@Nonnull Store<PhysicsStore> store,
        @Nonnull List<RowRemoval> removals) {
        PhysicsIdentityIndexResource identity =
            store.getResource(PhysicsIdentityIndexResource.getResourceType());
        PhysicsTerrainPayloadResource terrainPayloads =
            store.getResource(PhysicsTerrainPayloadResource.getResourceType());
        PhysicsSnapshotResource snapshots = store.getResource(PhysicsSnapshotResource.getResourceType());
        PhysicsBodyRegistrationResource registrations =
            store.getResource(PhysicsBodyRegistrationResource.getResourceType());
        for (RowRemoval removal : removals) {
            if (!removal.ref().isValid()) {
                continue;
            }
            identity.removeUuid(removal.rowUuid(), removal.ref());
            if (removal.kind() == RowKind.TERRAIN
                && removal.payloadResourceKey() != null
                && !removal.payloadResourceKey().isBlank()) {
                terrainPayloads.remove(removal.payloadResourceKey());
            }
            if (removal.kind() == RowKind.BODY) {
                snapshots.removeBody(removal.rowUuid());
                registrations.removeBody(RigidBodyKey.of(removal.rowUuid()));
            }
            store.removeEntity(removal.ref(),
                store.getRegistry().newHolder(),
                RemoveReason.REMOVE);
        }
    }

    private static void clearCopiedBodyState(@Nonnull Store<PhysicsStore> store) {
        store.getResource(PhysicsSnapshotResource.getResourceType()).clear();
        store.getResource(PhysicsBodyRegistrationResource.getResourceType()).clear();
        store.getResource(PhysicsEventResource.getResourceType()).clear();
    }

    private enum RowKind {
        BODY,
        JOINT,
        TERRAIN
    }

    private record RowRemoval(@Nonnull Ref<PhysicsStore> ref,
                              @Nonnull UUID rowUuid,
                              @Nonnull RowKind kind,
                              @Nullable String payloadResourceKey) {
    }

    private static final class TopologyCounts {

        private int bodyCount;
        private int jointCount;

        private void addBodies(int count) {
            bodyCount += count;
        }

        private void addJoints(int count) {
            jointCount += count;
        }

        private int bodyCount() {
            return bodyCount;
        }

        private int jointCount() {
            return jointCount;
        }
    }
}
