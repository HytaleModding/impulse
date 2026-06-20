package dev.hytalemodding.impulse.core.internal.physics;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsBodyRegistrationResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsEventResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsIdentityIndexResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsRuntimeResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsSnapshotResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsSpaceCompatibilityIndexResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsChunkCollisionMutationQueueResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsChunkCollisionPayloadResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsRuntimeResetResult;
import dev.hytalemodding.impulse.core.internal.modules.control.PhysicsControlRuntimeStates;
import dev.hytalemodding.impulse.core.plugin.physics.PhysicsThreading;
import dev.hytalemodding.impulse.core.plugin.components.BodyComponent;
import dev.hytalemodding.impulse.core.plugin.components.JointComponent;
import dev.hytalemodding.impulse.core.plugin.components.UuidComponent;
import dev.hytalemodding.impulse.core.internal.modules.physicschunk.components.ChunkCollisionSourceComponent;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * World-thread topology mutations for public compatibility cleanup paths.
 */
public final class PhysicsTopologyMutations {

    private PhysicsTopologyMutations() {
    }

    public static void destroyBody(@Nonnull Store<PhysicsStore> store,
        @Nonnull UUID bodyUuid) {
        UUID checkedBodyUuid = Objects.requireNonNull(bodyUuid, "bodyUuid");
        PhysicsThreading.requireBackendIdle(store, "destroy a PhysicsStore body entity");
        PhysicsRuntimeResource runtime = store.getResource(PhysicsRuntimeResource.getResourceType());
        PhysicsIdentityIndexResource identity =
            store.getResource(PhysicsIdentityIndexResource.getResourceType());
        Ref<PhysicsStore> bodyRef = identity.getByUuid(checkedBodyUuid);
        List<RowRemoval> removals = collectRows(store, null, null, checkedBodyUuid, bodyRef);
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
        PhysicsControlRuntimeStates.clear(store);
        TopologyCounts removed = countBackendTopology(runtime);
        List<RowRemoval> removals = collectRows(store, null, null, null, null);
        removeRuntimeRows(runtime, identity, removals);
        removeRows(store, removals);
        clearCopiedBodyState(store);
        store.getResource(PhysicsChunkCollisionMutationQueueResource.getResourceType()).clear();
        store.getResource(PhysicsChunkCollisionPayloadResource.getResourceType()).clear();
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
        store.getResource(PhysicsChunkCollisionMutationQueueResource.getResourceType())
            .removeIf(mutation -> spaceUuid.equals(mutation.spaceUuid()));
        removeRows(store, removals);
        PhysicsSpaceMutations.removeEmptySpace(store, spaceUuid);
    }

    public static int clearChunkCollisionRowsForSpace(@Nonnull Store<PhysicsStore> store,
        @Nonnull UUID spaceUuid) {
        PhysicsThreading.requireBackendIdle(store, "clear PhysicsStore chunk-collision rows");
        PhysicsRuntimeResource runtime = store.getResource(PhysicsRuntimeResource.getResourceType());
        PhysicsIdentityIndexResource identity =
            store.getResource(PhysicsIdentityIndexResource.getResourceType());
        int removedBodies = 0;
        Ref<PhysicsStore> spaceRef = identity.getByUuid(spaceUuid);
        List<RowRemoval> removals = collectChunkCollisionRows(store, spaceUuid, spaceRef);
        for (RowRemoval removal : removals) {
            if (removeRuntimeBody(runtime, identity, removal)) {
                removedBodies++;
            }
        }
        store.getResource(PhysicsChunkCollisionMutationQueueResource.getResourceType())
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
            if (removal.kind() == RowKind.BODY) {
                removeRuntimeBody(runtime, identity, removal);
            }
        }
    }

    private static boolean removeRuntimeJoint(@Nonnull PhysicsRuntimeResource runtime,
        @Nonnull PhysicsIdentityIndexResource identity,
        @Nonnull RowRemoval removal) {
        return PhysicsStoreRowCleanup.removeRuntimeJoint(runtime,
            identity,
            removal.rowUuid(),
            removal.ref());
    }

    private static boolean removeRuntimeBody(@Nonnull PhysicsRuntimeResource runtime,
        @Nonnull PhysicsIdentityIndexResource identity,
        @Nonnull RowRemoval removal) {
        return PhysicsStoreRowCleanup.removeRuntimeBody(runtime,
            identity,
            removal.rowUuid(),
            removal.ref());
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
            BodyComponent body = chunk.getComponent(index, BodyComponent.getComponentType());
            ChunkCollisionSourceComponent source = chunk.getComponent(index,
                ChunkCollisionSourceComponent.getComponentType());
            if (matchesBody(body, rowUuid, ref, spaceUuid, spaceRef, bodyUuid, bodyRef)) {
                removals.add(new RowRemoval(ref,
                    rowUuid,
                    RowKind.BODY,
                    source != null ? source.getPayloadResourceKey() : null));
            }
        });
        return new ArrayList<>(removals);
    }

    @Nonnull
    private static List<RowRemoval> collectChunkCollisionRows(@Nonnull Store<PhysicsStore> store,
        @Nonnull UUID spaceUuid,
        @Nullable Ref<PhysicsStore> spaceRef) {
        ComponentType<PhysicsStore, UuidComponent> uuidType = UuidComponent.getComponentType();
        ConcurrentLinkedQueue<RowRemoval> removals = new ConcurrentLinkedQueue<>();
        store.forEachEntityParallel(uuidType, (index, chunk, _) -> {
            UuidComponent uuid = chunk.getComponent(index, uuidType);
            if (uuid == null) {
                return;
            }
            BodyComponent body = chunk.getComponent(index, BodyComponent.getComponentType());
            ChunkCollisionSourceComponent source = chunk.getComponent(index,
                ChunkCollisionSourceComponent.getComponentType());
            if (source != null
                && body != null
                && matchesSpace(body.getSpaceRef(), body.getSpaceUuid(), spaceRef, spaceUuid)) {
                removals.add(new RowRemoval(chunk.getReferenceTo(index),
                    uuid.getUuid(),
                    RowKind.BODY,
                    source.getPayloadResourceKey()));
            }
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
        boolean removedAny = false;
        for (RowRemoval removal : removals.stream()
            .sorted((first, second) -> Integer.compare(second.ref().getIndex(),
                first.ref().getIndex()))
            .toList()) {
            if (!removal.ref().isValid()) {
                continue;
            }
            if (removal.kind() == RowKind.BODY) {
                PhysicsStoreRowCleanup.removeBodyEntity(store,
                    removal.rowUuid(),
                    removal.ref(),
                    removal.payloadResourceKey());
            } else {
                PhysicsStoreRowCleanup.removeJointEntity(store,
                    removal.rowUuid(),
                    removal.ref());
            }
            removedAny = true;
        }
        if (removedAny) {
            PhysicsStoreRowCleanup.refreshIdentityAndRuntimeRefs(store);
        }
    }

    private static void clearCopiedBodyState(@Nonnull Store<PhysicsStore> store) {
        store.getResource(PhysicsSnapshotResource.getResourceType()).clear();
        store.getResource(PhysicsBodyRegistrationResource.getResourceType()).clear();
        store.getResource(PhysicsEventResource.getResourceType()).clear();
    }

    private enum RowKind {
        BODY,
        JOINT
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
