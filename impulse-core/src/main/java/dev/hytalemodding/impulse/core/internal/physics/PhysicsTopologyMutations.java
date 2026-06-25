package dev.hytalemodding.impulse.core.internal.physics;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsEventResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsRuntimeResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsRestoreStatusResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsSnapshotResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsSpaceCompatibilityIndexResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsRuntimeResetResult;
import dev.hytalemodding.impulse.core.internal.physics.PhysicsStoreRowCleanup.BodyEntityRemoval;
import dev.hytalemodding.impulse.core.plugin.physics.PhysicsThreading;
import dev.hytalemodding.impulse.core.plugin.components.BodyComponent;
import dev.hytalemodding.impulse.core.plugin.components.JointComponent;
import dev.hytalemodding.impulse.core.plugin.components.UuidComponent;
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
        Ref<PhysicsStore> bodyRef = store.getExternalData().getRefFromUUID(checkedBodyUuid);
        List<RowRemoval> removals = collectRows(store, null, null, checkedBodyUuid, bodyRef);
        removeRuntimeRows(store, runtime, removals);
        removeRows(store, removals);
    }

    @Nonnull
    public static PhysicsRuntimeResetResult clearBodiesKeepingSpaces(
        @Nonnull Store<PhysicsStore> store) {
        PhysicsThreading.requireBackendIdle(store, "clear PhysicsStore body entities");
        PhysicsRuntimeResource runtime = store.getResource(PhysicsRuntimeResource.getResourceType());
        TopologyCounts removed = countBackendTopology(runtime);
        List<RowRemoval> removals = collectRows(store, null, null, null, null);
        runtime.destroyBackendBindings();
        removeRows(store, removals, false);
        clearCopiedBodyState(store);
        PhysicsStoreCleanupHooks.clearBodyRuntimeResources(store);
        int keptSpaces = store.getResource(PhysicsSpaceCompatibilityIndexResource.getResourceType())
            .size();
        store.getResource(PhysicsRestoreStatusResource.getResourceType())
            .markRecoveredFromCleanup();
        return new PhysicsRuntimeResetResult(removed.bodyCount(),
            removed.jointCount(),
            keptSpaces);
    }

    public static void removeSpaceWithContents(@Nonnull Store<PhysicsStore> store,
        @Nonnull UUID spaceUuid) {
        PhysicsThreading.requireBackendIdle(store, "remove a PhysicsStore space entity");
        PhysicsRuntimeResource runtime = store.getResource(PhysicsRuntimeResource.getResourceType());
        Ref<PhysicsStore> spaceRef = store.getExternalData().getRefFromUUID(spaceUuid);
        List<RowRemoval> removals = collectRows(store, spaceUuid, spaceRef, null, null);
        removeRuntimeRows(store, runtime, removals);
        PhysicsStoreCleanupHooks.cleanupSpaceRuntimeResources(store, spaceUuid);
        removeRows(store, removals);
        PhysicsSpaceMutations.removeEmptySpace(store, spaceUuid);
    }

    private static void removeRuntimeRows(@Nonnull Store<PhysicsStore> store,
        @Nonnull PhysicsRuntimeResource runtime,
        @Nonnull List<RowRemoval> removals) {
        for (RowRemoval removal : removals) {
            if (removal.kind() == RowKind.JOINT) {
                removeRuntimeJoint(store, runtime, removal);
            }
        }
        for (RowRemoval removal : removals) {
            if (removal.kind() == RowKind.BODY) {
                removeRuntimeBody(store, runtime, removal);
            }
        }
    }

    private static boolean removeRuntimeJoint(@Nonnull Store<PhysicsStore> store,
        @Nonnull PhysicsRuntimeResource runtime,
        @Nonnull RowRemoval removal) {
        return PhysicsStoreRowCleanup.removeRuntimeJoint(store,
            runtime,
            removal.rowUuid(),
            removal.ref());
    }

    private static boolean removeRuntimeBody(@Nonnull Store<PhysicsStore> store,
        @Nonnull PhysicsRuntimeResource runtime,
        @Nonnull RowRemoval removal) {
        return PhysicsStoreRowCleanup.removeRuntimeBody(store,
            runtime,
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
                removals.add(new RowRemoval(ref, rowUuid, RowKind.JOINT));
                return;
            }
            BodyComponent body = chunk.getComponent(index, BodyComponent.getComponentType());
            if (matchesBody(body, rowUuid, ref, spaceUuid, spaceRef, bodyUuid, bodyRef)) {
                removals.add(new RowRemoval(ref, rowUuid, RowKind.BODY));
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
        removeRows(store, removals, true);
    }

    private static void removeRows(@Nonnull Store<PhysicsStore> store,
        @Nonnull List<RowRemoval> removals,
        boolean clearCopiedState) {
        boolean removedAny = false;
        List<RowRemoval> orderedRemovals = removals.stream()
            .sorted((first, second) -> Integer.compare(second.ref().getIndex(),
                first.ref().getIndex()))
            .toList();
        if (clearCopiedState) {
            PhysicsStoreRowCleanup.clearBodyCopiedState(store,
                bodyEntityRemovals(orderedRemovals));
        }
        for (RowRemoval removal : orderedRemovals) {
            if (!removal.ref().isValid()) {
                continue;
            }
            if (removal.kind() == RowKind.BODY) {
                PhysicsStoreRowCleanup.removeBodyEntityRow(store,
                    removal.rowUuid(),
                    removal.ref());
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

    @Nonnull
    private static List<BodyEntityRemoval> bodyEntityRemovals(
        @Nonnull List<RowRemoval> removals) {
        List<BodyEntityRemoval> bodyRemovals = new ArrayList<>();
        for (RowRemoval removal : removals) {
            if (removal.kind() == RowKind.BODY && removal.ref().isValid()) {
                bodyRemovals.add(new BodyEntityRemoval(removal.rowUuid(),
                    removal.ref()));
            }
        }
        return bodyRemovals;
    }

    private static void clearCopiedBodyState(@Nonnull Store<PhysicsStore> store) {
        store.getResource(PhysicsSnapshotResource.getResourceType()).clear();
        store.getResource(PhysicsEventResource.getResourceType()).clear();
    }

    private enum RowKind {
        BODY,
        JOINT
    }

    private record RowRemoval(@Nonnull Ref<PhysicsStore> ref,
                              @Nonnull UUID rowUuid,
                              @Nonnull RowKind kind) {
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
