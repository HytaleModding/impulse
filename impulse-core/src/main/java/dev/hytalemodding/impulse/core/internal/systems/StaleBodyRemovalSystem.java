package dev.hytalemodding.impulse.core.internal.systems;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.api.runtime.PhysicsBackendRuntime;
import dev.hytalemodding.impulse.core.internal.physics.PhysicsStoreRowCleanup;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsIdentityIndexResource;
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
        PhysicsIdentityIndexResource identity =
            store.getResource(PhysicsIdentityIndexResource.getResourceType());
        removeStaleBodies(store, runtime, identity, restore);
    }

    private static void removeStaleBodies(@Nonnull Store<PhysicsStore> store,
        @Nonnull PhysicsRuntimeResource runtime,
        @Nonnull PhysicsIdentityIndexResource identity,
        @Nonnull PhysicsRestoreStatusResource restore) {
        List<BoundBody> staleBodies = new ArrayList<>();
        runtime.forEachRuntimeSpaceBinding((_, _, spaceHandle, backendRuntime) ->
            runtime.forEachBodyHandle(spaceHandle,
                bodyId -> collectStaleBody(store,
                    identity,
                    runtime,
                    restore,
                    staleBodies,
                    backendRuntime,
                    bodyId)));
        if (restore.isFailed()) {
            return;
        }
        Set<UUID> staleBodyUuids = new ObjectOpenHashSet<>();
        staleBodies.forEach(body -> staleBodyUuids.add(body.bodyUuid()));
        if (!removeDependentJoints(store, runtime, identity, restore, staleBodyUuids)) {
            return;
        }
        List<BoundBody> orderedBodies = currentBodyRefs(identity, staleBodies);
        boolean removedAny = false;
        for (BoundBody body : orderedBodies) {
            try {
                PhysicsStoreRowCleanup.removeRuntimeBody(runtime,
                    identity,
                    body.bodyUuid(),
                    body.bodyRef(),
                    body.backendRuntime());
            } catch (RuntimeException exception) {
                restore.markFailed("PhysicsStore body " + body.bodyUuid()
                    + " failed backend removal: " + exception.getMessage());
                return;
            }
            PhysicsStoreRowCleanup.removeBodyEntity(store, body.bodyUuid(), body.bodyRef(), null);
            removedAny = true;
        }
        if (removedAny) {
            PhysicsStoreRowCleanup.refreshIdentityAndRuntimeRefs(store);
        }
    }

    private static boolean removeDependentJoints(@Nonnull Store<PhysicsStore> store,
        @Nonnull PhysicsRuntimeResource runtime,
        @Nonnull PhysicsIdentityIndexResource identity,
        @Nonnull PhysicsRestoreStatusResource restore,
        @Nonnull Set<UUID> staleBodyUuids) {
        if (staleBodyUuids.isEmpty()) {
            return true;
        }
        List<BoundJoint> joints = collectDependentJoints(store, identity, staleBodyUuids);
        joints.sort((first, second) -> Integer.compare(second.ref().getIndex(),
            first.ref().getIndex()));
        boolean removedAny = false;
        for (BoundJoint joint : joints) {
            try {
                PhysicsStoreRowCleanup.removeRuntimeJoint(runtime,
                    identity,
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

    @Nonnull
    private static List<BoundBody> currentBodyRefs(@Nonnull PhysicsIdentityIndexResource identity,
        @Nonnull List<BoundBody> staleBodies) {
        List<BoundBody> currentBodies = new ArrayList<>(staleBodies.size());
        for (BoundBody body : staleBodies) {
            Ref<PhysicsStore> bodyRef = PhysicsStoreSystemSupport.refForUuid(identity,
                body.bodyUuid());
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
        @Nonnull PhysicsIdentityIndexResource identity,
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
            boolean removeRow = shouldRemoveJointRow(identity, joint, staleBodyUuids);
            joints.add(new BoundJoint(jointUuid, chunk.getReferenceTo(index), removeRow));
        });
        return new ArrayList<>(joints);
    }

    private static boolean shouldRemoveJointRow(@Nonnull PhysicsIdentityIndexResource identity,
        @Nonnull JointComponent joint,
        @Nonnull Set<UUID> staleBodyUuids) {
        return endpointRemoved(identity, joint.getBodyAUuid(), joint.getBodyARef(), staleBodyUuids)
            || endpointRemoved(identity, joint.getBodyBUuid(), joint.getBodyBRef(), staleBodyUuids);
    }

    private static boolean endpointRemoved(@Nonnull PhysicsIdentityIndexResource identity,
        @Nonnull UUID bodyUuid,
        Ref<PhysicsStore> currentRef,
        @Nonnull Set<UUID> staleBodyUuids) {
        if (!staleBodyUuids.contains(bodyUuid)) {
            return false;
        }
        return PhysicsStoreSystemSupport.resolvedRef(identity, bodyUuid, currentRef) == null;
    }

    private static void collectStaleBody(@Nonnull Store<PhysicsStore> store,
        @Nonnull PhysicsIdentityIndexResource identity,
        @Nonnull PhysicsRuntimeResource runtime,
        @Nonnull PhysicsRestoreStatusResource restore,
        @Nonnull List<BoundBody> staleBodies,
        @Nonnull PhysicsBackendRuntime backendRuntime,
        long bodyId) {
        BodySnapshotMetadata metadata = runtime.getBodySnapshotMetadata(bodyId);
        if (metadata == null) {
            restore.markFailed("PhysicsStore backend body " + bodyId
                + " has no runtime snapshot metadata");
            return;
        }
        Ref<PhysicsStore> bodyRef = PhysicsStoreSystemSupport.resolvedRef(identity,
            metadata.bodyUuid(),
            metadata.bodyRef());
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
