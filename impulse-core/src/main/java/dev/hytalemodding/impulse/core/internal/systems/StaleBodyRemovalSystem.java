package dev.hytalemodding.impulse.core.internal.systems;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.api.BackendId;
import dev.hytalemodding.impulse.api.runtime.PhysicsBackendRuntime;
import dev.hytalemodding.impulse.core.internal.resources.BackendSpaceHandle;
import dev.hytalemodding.impulse.core.internal.physics.PhysicsStoreRowCleanup;
import dev.hytalemodding.impulse.core.internal.physics.PhysicsStoreRowCleanup.BodyEntityRemoval;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsRestoreStatusResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsRuntimeResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsRuntimeResource.BodySnapshotMetadata;
import dev.hytalemodding.impulse.core.internal.systems.binding.JointBindingSystem;
import dev.hytalemodding.impulse.core.plugin.components.BodyComponent;
import dev.hytalemodding.impulse.core.plugin.components.JointComponent;
import dev.hytalemodding.impulse.core.plugin.components.UuidComponent;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import javax.annotation.Nonnull;

/**
 * Removes backend bodies after their authoritative PhysicsStore body entity is gone.
 */
public final class StaleBodyRemovalSystem extends TickingSystem<PhysicsStore> {

    private static final Set<Dependency<PhysicsStore>> DEPENDENCIES = Set.of(
        new SystemDependency<>(Order.AFTER, JointBindingSystem.class)
    );

    @Override
    public void tick(float dt, int systemIndex, @Nonnull Store<PhysicsStore> store) {
        PhysicsRestoreStatusResource restore = store.getResource(
            PhysicsRestoreStatusResource.getResourceType());
        if (restore.isFailed()) {
            return;
        }
        PhysicsRuntimeResource runtime = store.getResource(PhysicsRuntimeResource.getResourceType());
        removeStaleBodies(store, runtime, restore);
    }

    private static void removeStaleBodies(@Nonnull Store<PhysicsStore> store,
        @Nonnull PhysicsRuntimeResource runtime,
        @Nonnull PhysicsRestoreStatusResource restore) {
        List<BoundBody> staleBodies = new ArrayList<>();
        runtime.forEachRuntimeSpaceBinding((_, backendId, spaceHandle, backendRuntime) ->
            runtime.forEachBodyHandle(backendId,
                spaceHandle,
                bodyId -> collectStaleBody(store,
                    runtime,
                    restore,
                    staleBodies,
                    backendId,
                    spaceHandle,
                    backendRuntime,
                    bodyId)));
        if (restore.isFailed()) {
            return;
        }
        Set<UUID> staleBodyUuids = new ObjectOpenHashSet<>();
        staleBodies.forEach(body -> staleBodyUuids.add(body.bodyUuid()));
        if (!removeDependentJoints(store, runtime, restore, staleBodyUuids)) {
            return;
        }
        List<BoundBody> orderedBodies = currentBodyRefs(store, staleBodies);
        List<BodyEntityRemoval> bodyEntityRemovals = new ArrayList<>(orderedBodies.size());
        for (BoundBody body : orderedBodies) {
            try {
                PhysicsStoreRowCleanup.removeRuntimeBody(store,
                    runtime,
                    body.bodyUuid(),
                    body.bodyRef(),
                    body.backendRuntime());
            } catch (RuntimeException exception) {
                removeBodyEntities(store, bodyEntityRemovals);
                restore.markFailed("PhysicsStore body " + body.bodyUuid()
                    + " failed backend removal: " + exception.getMessage());
                return;
            }
            bodyEntityRemovals.add(new BodyEntityRemoval(body.bodyUuid(), body.bodyRef()));
        }
        removeBodyEntities(store, bodyEntityRemovals);
    }

    private static boolean removeDependentJoints(@Nonnull Store<PhysicsStore> store,
        @Nonnull PhysicsRuntimeResource runtime,
        @Nonnull PhysicsRestoreStatusResource restore,
        @Nonnull Set<UUID> staleBodyUuids) {
        if (staleBodyUuids.isEmpty()) {
            return true;
        }
        List<BoundJoint> joints = collectDependentJoints(store, staleBodyUuids);
        joints.sort((first, second) -> Integer.compare(second.ref().getIndex(),
            first.ref().getIndex()));
        boolean removedAny = false;
        for (BoundJoint joint : joints) {
            try {
                PhysicsStoreRowCleanup.removeRuntimeJoint(store,
                    runtime,
                    joint.jointUuid(),
                    joint.ref());
            } catch (RuntimeException exception) {
                restore.markFailed("PhysicsStore joint " + joint.jointUuid()
                    + " failed backend removal: " + exception.getMessage());
                return false;
            }
            if (joint.removeRow() && joint.ref().isValid()) {
                PhysicsStoreRowCleanup.removeJointEntity(store, joint.jointUuid(), joint.ref());
                removedAny = true;
            }
        }
        if (removedAny) {
            PhysicsStoreRowCleanup.refreshIdentityAndRuntimeRefs(store);
        }
        return true;
    }

    private static void removeBodyEntities(@Nonnull Store<PhysicsStore> store,
        @Nonnull List<BodyEntityRemoval> removals) {
        if (removals.isEmpty()) {
            return;
        }
        PhysicsStoreRowCleanup.removeBodyEntities(store, removals);
        PhysicsStoreRowCleanup.refreshIdentityAndRuntimeRefs(store);
    }

    @Nonnull
    private static List<BoundBody> currentBodyRefs(@Nonnull Store<PhysicsStore> store,
        @Nonnull List<BoundBody> staleBodies) {
        List<BoundBody> currentBodies = new ArrayList<>(staleBodies.size());
        for (BoundBody body : staleBodies) {
            Ref<PhysicsStore> bodyRef = store.getExternalData().getRefFromUUID(body.bodyUuid());
            if (bodyRef != null && (bodyRef.getStore() != store || !bodyRef.isValid())) {
                bodyRef = null;
            }
            currentBodies.add(new BoundBody(body.bodyUuid(),
                bodyRef != null ? bodyRef : body.bodyRef(),
                body.backendRuntime()));
        }
        currentBodies.sort((first, second) -> Integer.compare(second.bodyRef().getIndex(),
            first.bodyRef().getIndex()));
        return currentBodies;
    }

    @Nonnull
    private static List<BoundJoint> collectDependentJoints(@Nonnull Store<PhysicsStore> store,
        @Nonnull Set<UUID> staleBodyUuids) {
        ConcurrentLinkedQueue<BoundJoint> joints = new ConcurrentLinkedQueue<>();
        store.forEachEntityParallel(UuidComponent.getComponentType(), (index, chunk, _) -> {
            JointComponent joint = chunk.getComponent(index, JointComponent.getComponentType());
            if (joint == null
                || (!staleBodyUuids.contains(joint.getBodyAUuid())
                && !staleBodyUuids.contains(joint.getBodyBUuid()))) {
                return;
            }
            UUID jointUuid = PhysicsStoreSystemSupport.rowUuid(chunk, index);
            if (PhysicsStoreSystemSupport.isNil(jointUuid)) {
                return;
            }
            boolean removeRow = shouldRemoveJointRow(store, joint, staleBodyUuids);
            joints.add(new BoundJoint(jointUuid, chunk.getReferenceTo(index), removeRow));
        });
        return new ArrayList<>(joints);
    }

    private static boolean shouldRemoveJointRow(@Nonnull Store<PhysicsStore> store,
        @Nonnull JointComponent joint,
        @Nonnull Set<UUID> staleBodyUuids) {
        return endpointRemoved(store, joint.getBodyAUuid(), joint.getBodyARef(), staleBodyUuids)
            || endpointRemoved(store, joint.getBodyBUuid(), joint.getBodyBRef(), staleBodyUuids);
    }

    private static boolean endpointRemoved(@Nonnull Store<PhysicsStore> store,
        @Nonnull UUID bodyUuid,
        Ref<PhysicsStore> currentRef,
        @Nonnull Set<UUID> staleBodyUuids) {
        if (!staleBodyUuids.contains(bodyUuid)) {
            return false;
        }
        UuidComponent currentUuid = PhysicsStoreSystemSupport.component(store,
            currentRef,
            UuidComponent.getComponentType());
        if (currentUuid != null && bodyUuid.equals(currentUuid.getUuid())) {
            return false;
        }
        Ref<PhysicsStore> indexed = store.getExternalData().getRefFromUUID(bodyUuid);
        return indexed == null || indexed.getStore() != store || !indexed.isValid();
    }

    private static void collectStaleBody(@Nonnull Store<PhysicsStore> store,
        @Nonnull PhysicsRuntimeResource runtime,
        @Nonnull PhysicsRestoreStatusResource restore,
        @Nonnull List<BoundBody> staleBodies,
        @Nonnull BackendId backendId,
        @Nonnull BackendSpaceHandle spaceHandle,
        @Nonnull PhysicsBackendRuntime backendRuntime,
        long bodyId) {
        BodySnapshotMetadata metadata = runtime.getBodySnapshotMetadata(backendId,
            spaceHandle,
            bodyId);
        if (metadata == null) {
            restore.markFailed("PhysicsStore backend body " + bodyId
                + " has no runtime snapshot metadata");
            return;
        }
        Ref<PhysicsStore> bodyRef = metadata.bodyRef();
        UuidComponent currentUuid = PhysicsStoreSystemSupport.component(store,
            bodyRef,
            UuidComponent.getComponentType());
        if (currentUuid == null || !metadata.bodyUuid().equals(currentUuid.getUuid())) {
            bodyRef = store.getExternalData().getRefFromUUID(metadata.bodyUuid());
        }
        if (bodyRef == null) {
            bodyRef = metadata.bodyRef();
        }
        BodyComponent body = PhysicsStoreSystemSupport.component(store,
            bodyRef,
            BodyComponent.getComponentType());
        if (body != null) {
            return;
        }
        staleBodies.add(new BoundBody(metadata.bodyUuid(),
            bodyRef,
            backendRuntime));
    }

    @Nonnull
    @Override
    public Set<Dependency<PhysicsStore>> getDependencies() {
        return DEPENDENCIES;
    }

    private record BoundBody(@Nonnull UUID bodyUuid,
                             @Nonnull Ref<PhysicsStore> bodyRef,
                             @Nonnull PhysicsBackendRuntime backendRuntime) {
    }

    private record BoundJoint(@Nonnull UUID jointUuid,
                              @Nonnull Ref<PhysicsStore> ref,
                              boolean removeRow) {
    }
}
