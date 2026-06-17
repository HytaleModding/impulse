package dev.hytalemodding.impulse.core.internal.physicsstore;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.api.runtime.PhysicsBackendRuntime;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsBodyRegistrationResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsEventResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsIdentityIndexResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsRuntimeResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsSnapshotResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsSpaceCompatibilityIndexResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsTerrainMutationQueueResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsTerrainPayloadResource;
import dev.hytalemodding.impulse.core.internal.resources.BackendBodyHandle;
import dev.hytalemodding.impulse.core.internal.resources.BackendJointHandle;
import dev.hytalemodding.impulse.core.internal.resources.BackendSpaceHandle;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsRuntimeResetResult;
import dev.hytalemodding.impulse.core.plugin.physicsstore.PhysicsThreading;
import dev.hytalemodding.impulse.core.plugin.components.BodyComponent;
import dev.hytalemodding.impulse.core.plugin.components.JointComponent;
import dev.hytalemodding.impulse.core.plugin.components.TerrainColliderComponent;
import dev.hytalemodding.impulse.core.plugin.components.UuidComponent;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * World-thread topology mutations for public compatibility cleanup paths.
 */
public final class PhysicsStoreTopologyMutations {

    private PhysicsStoreTopologyMutations() {
    }

    public static void destroyBody(@Nonnull Store<PhysicsStore> store,
        @Nonnull UUID bodyUuid) {
        PhysicsThreading.requireBackendIdle(store, "destroy a PhysicsStore body entity");
        PhysicsRuntimeResource runtime = store.getResource(PhysicsRuntimeResource.getResourceType());
        PhysicsIdentityIndexResource identity =
            store.getResource(PhysicsIdentityIndexResource.getResourceType());
        Ref<PhysicsStore> bodyRef = identity.getByUuid(bodyUuid);
        List<RowRemoval> removals = collectRows(store, null, null, bodyUuid, bodyRef);
        removeRuntimeRows(runtime, identity, removals);
        removeRows(store, removals);
    }

    @Nonnull
    public static PhysicsRuntimeResetResult clearBodiesKeepingSpaces(
        @Nonnull Store<PhysicsStore> store) {
        PhysicsThreading.requireBackendIdle(store, "clear PhysicsStore body entities");
        PhysicsRuntimeResource runtime = store.getResource(PhysicsRuntimeResource.getResourceType());
        PhysicsIdentityIndexResource identity =
            store.getResource(PhysicsIdentityIndexResource.getResourceType());
        TopologyCounts removed = countBackendTopology(runtime);
        List<RowRemoval> removals = collectRows(store, null, null, null, null);
        removeRuntimeRows(runtime, identity, removals);
        removeRows(store, removals);
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
        @Nonnull UUID spaceUuid) {
        PhysicsThreading.requireBackendIdle(store, "remove a PhysicsStore space entity");
        PhysicsRuntimeResource runtime = store.getResource(PhysicsRuntimeResource.getResourceType());
        PhysicsIdentityIndexResource identity =
            store.getResource(PhysicsIdentityIndexResource.getResourceType());
        Ref<PhysicsStore> spaceRef = identity.getByUuid(spaceUuid);
        List<RowRemoval> removals = collectRows(store, spaceUuid, spaceRef, null, null);
        removeRuntimeRows(runtime, identity, removals);
        store.getResource(PhysicsTerrainMutationQueueResource.getResourceType())
            .removeIf(mutation -> spaceUuid.equals(mutation.spaceUuid()));
        removeRows(store, removals);
        PhysicsStoreSpaceMutations.removeEmptySpace(store, spaceUuid);
    }

    public static int clearTerrainForSpace(@Nonnull Store<PhysicsStore> store,
        @Nonnull UUID spaceUuid) {
        PhysicsThreading.requireBackendIdle(store, "clear PhysicsStore terrain rows");
        PhysicsRuntimeResource runtime = store.getResource(PhysicsRuntimeResource.getResourceType());
        int removedBodies = 0;
        Ref<PhysicsStore> spaceRef = store.getResource(PhysicsIdentityIndexResource.getResourceType())
            .getByUuid(spaceUuid);
        List<RowRemoval> removals = collectTerrainRows(store, spaceUuid, spaceRef);
        for (RowRemoval removal : removals) {
            removedBodies += removeRuntimeTerrain(runtime, removal);
        }
        store.getResource(PhysicsTerrainMutationQueueResource.getResourceType())
            .removeIf(mutation -> spaceUuid.equals(mutation.spaceUuid()));
        removeRows(store, removals);
        return removedBodies;
    }

    private static void removeRuntimeRows(@Nonnull PhysicsRuntimeResource runtime,
        @Nonnull PhysicsIdentityIndexResource identity,
        @Nonnull List<RowRemoval> removals) {
        for (RowRemoval removal : removals) {
            if (removal.kind() == RowKind.JOINT) {
                removeRuntimeJoint(runtime, identity, removal);
            }
        }
        for (RowRemoval removal : removals) {
            if (removal.kind() == RowKind.TERRAIN) {
                removeRuntimeTerrain(runtime, removal);
            }
        }
        for (RowRemoval removal : removals) {
            if (removal.kind() == RowKind.BODY) {
                removeRuntimeBody(runtime, identity, removal);
            }
        }
    }

    private static boolean removeRuntimeJoint(@Nonnull PhysicsRuntimeResource runtime,
        @Nonnull PhysicsIdentityIndexResource identity,
        @Nonnull RowRemoval removal) {
        BackendJointHandle jointHandle = runtime.getJointHandle(removal.ref());
        BackendSpaceHandle spaceHandle = runtime.getJointSpaceHandle(removal.ref());
        if (jointHandle == null) {
            runtime.removeJointHandle(removal.rowUuid(), removal.ref());
            return false;
        }
        PhysicsBackendRuntime backendRuntime = runtime.runtimeForSpaceHandle(spaceHandle);
        if (spaceHandle != null && backendRuntime != null) {
            backendRuntime.removeJoint(spaceHandle.value(), jointHandle.value());
        }
        identity.removeJointHandle(jointHandle);
        runtime.removeJointHandle(removal.rowUuid(), removal.ref());
        return true;
    }

    private static int removeRuntimeTerrain(@Nonnull PhysicsRuntimeResource runtime,
        @Nonnull RowRemoval removal) {
        BackendSpaceHandle spaceHandle = runtime.getTerrainSpaceHandle(removal.ref());
        LongArrayList bodyHandles = new LongArrayList();
        runtime.forEachTerrainBodyHandle(removal.ref(), bodyId -> bodyHandles.add(bodyId));
        if (spaceHandle != null) {
            PhysicsBackendRuntime backendRuntime = runtime.runtimeForSpaceHandle(spaceHandle);
            if (backendRuntime != null) {
                for (int index = 0; index < bodyHandles.size(); index++) {
                    backendRuntime.removeBody(spaceHandle.value(), bodyHandles.getLong(index));
                }
            }
        }
        runtime.removeTerrainHandles(removal.ref(), removal.rowUuid());
        return bodyHandles.size();
    }

    private static boolean removeRuntimeBody(@Nonnull PhysicsRuntimeResource runtime,
        @Nonnull PhysicsIdentityIndexResource identity,
        @Nonnull RowRemoval removal) {
        BackendBodyHandle bodyHandle = runtime.getBodyHandle(removal.ref());
        BackendSpaceHandle spaceHandle = runtime.getBodySpaceHandle(removal.ref());
        if (bodyHandle == null) {
            runtime.removeBodyHandle(removal.rowUuid(), removal.ref());
            return false;
        }
        PhysicsBackendRuntime backendRuntime = runtime.runtimeForSpaceHandle(spaceHandle);
        if (spaceHandle != null && backendRuntime != null) {
            backendRuntime.removeBody(spaceHandle.value(), bodyHandle.value());
        }
        identity.removeBodyHandle(bodyHandle);
        runtime.removeBodyHandle(removal.rowUuid(), removal.ref());
        return true;
    }

    @Nonnull
    private static TopologyCounts countBackendTopology(@Nonnull PhysicsRuntimeResource runtime) {
        TopologyCounts counts = new TopologyCounts();
        runtime.forEachRuntimeSpaceBinding((_, _, spaceHandle, backendRuntime) -> {
            counts.addBodies(backendRuntime.bodyCount(spaceHandle.value()));
            counts.addJoints(backendRuntime.jointCount(spaceHandle.value()));
        });
        return counts;
    }

    @Nonnull
    private static List<RowRemoval> collectRows(@Nonnull Store<PhysicsStore> store,
        @Nullable UUID spaceUuid,
        @Nullable Ref<PhysicsStore> spaceRef,
        @Nullable UUID bodyUuid,
        @Nullable Ref<PhysicsStore> bodyRef) {
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
            if (matchesJoint(joint, spaceUuid, spaceRef, bodyUuid, bodyRef)) {
                removals.add(new RowRemoval(ref, rowUuid, RowKind.JOINT, null));
                return;
            }
            TerrainColliderComponent terrain = chunk.getComponent(index,
                TerrainColliderComponent.getComponentType());
            if (matchesTerrain(terrain, spaceUuid, spaceRef, bodyUuid)) {
                removals.add(new RowRemoval(ref,
                    rowUuid,
                    RowKind.TERRAIN,
                    terrain.getPayloadResourceKey()));
                return;
            }
            BodyComponent body = chunk.getComponent(index, BodyComponent.getComponentType());
            if (matchesBody(body, rowUuid, ref, spaceUuid, spaceRef, bodyUuid, bodyRef)) {
                removals.add(new RowRemoval(ref, rowUuid, RowKind.BODY, null));
            }
        });
        return new ArrayList<>(removals);
    }

    @Nonnull
    private static List<RowRemoval> collectTerrainRows(@Nonnull Store<PhysicsStore> store,
        @Nonnull UUID spaceUuid,
        @Nullable Ref<PhysicsStore> spaceRef) {
        ComponentType<PhysicsStore, UuidComponent> uuidType = UuidComponent.getComponentType();
        ConcurrentLinkedQueue<RowRemoval> removals = new ConcurrentLinkedQueue<>();
        store.forEachEntityParallel(uuidType, (index, chunk, _) -> {
            TerrainColliderComponent terrain = chunk.getComponent(index,
                TerrainColliderComponent.getComponentType());
            if (terrain == null || !matchesSpace(terrain.getSpaceRef(),
                terrain.getSpaceUuid(),
                spaceRef,
                spaceUuid)) {
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
        @Nullable Ref<PhysicsStore> spaceRef,
        @Nullable UUID bodyUuid,
        @Nullable Ref<PhysicsStore> bodyRef) {
        if (joint == null) {
            return false;
        }
        if (!matchesSpace(joint.getSpaceRef(), joint.getSpaceUuid(), spaceRef, spaceUuid)) {
            return false;
        }
        return bodyUuid == null
            || matchesEndpoint(joint.getBodyARef(), joint.getBodyAUuid(), bodyRef, bodyUuid)
            || matchesEndpoint(joint.getBodyBRef(), joint.getBodyBUuid(), bodyRef, bodyUuid);
    }

    private static boolean matchesTerrain(@Nullable TerrainColliderComponent terrain,
        @Nullable UUID spaceUuid,
        @Nullable Ref<PhysicsStore> spaceRef,
        @Nullable UUID bodyUuid) {
        return bodyUuid == null
            && terrain != null
            && matchesSpace(terrain.getSpaceRef(), terrain.getSpaceUuid(), spaceRef, spaceUuid);
    }

    private static boolean matchesBody(@Nullable BodyComponent body,
        @Nonnull UUID rowUuid,
        @Nonnull Ref<PhysicsStore> rowRef,
        @Nullable UUID spaceUuid,
        @Nullable Ref<PhysicsStore> spaceRef,
        @Nullable UUID bodyUuid,
        @Nullable Ref<PhysicsStore> bodyRef) {
        if (body == null) {
            return false;
        }
        if (!matchesSpace(body.getSpaceRef(), body.getSpaceUuid(), spaceRef, spaceUuid)) {
            return false;
        }
        if (bodyRef != null) {
            return sameRef(rowRef, bodyRef);
        }
        return bodyUuid == null || bodyUuid.equals(rowUuid);
    }

    private static boolean matchesSpace(@Nullable Ref<PhysicsStore> rowSpaceRef,
        @Nonnull UUID rowSpaceUuid,
        @Nullable Ref<PhysicsStore> spaceRef,
        @Nullable UUID spaceUuid) {
        if (spaceRef != null && rowSpaceRef != null) {
            return sameRef(rowSpaceRef, spaceRef);
        }
        return spaceUuid == null || spaceUuid.equals(rowSpaceUuid);
    }

    private static boolean matchesEndpoint(@Nullable Ref<PhysicsStore> endpointRef,
        @Nonnull UUID endpointUuid,
        @Nullable Ref<PhysicsStore> bodyRef,
        @Nonnull UUID bodyUuid) {
        if (bodyRef != null && endpointRef != null) {
            return sameRef(endpointRef, bodyRef);
        }
        return bodyUuid.equals(endpointUuid);
    }

    private static boolean sameRef(@Nonnull Ref<PhysicsStore> first,
        @Nonnull Ref<PhysicsStore> second) {
        return first.getStore() == second.getStore()
            && first.getIndex() == second.getIndex();
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
                registrations.removeBody(removal.rowUuid());
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
