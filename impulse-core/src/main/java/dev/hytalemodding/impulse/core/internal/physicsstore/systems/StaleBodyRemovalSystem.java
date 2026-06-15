package dev.hytalemodding.impulse.core.internal.physicsstore.systems;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.api.runtime.PhysicsBackendRuntime;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsBodyRegistrationResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsIdentityIndexResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsRestoreStatusResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsRuntimeResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsRuntimeResource.BodySnapshotMetadata;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsSnapshotResource;
import dev.hytalemodding.impulse.core.internal.resources.BackendBodyHandle;
import dev.hytalemodding.impulse.core.internal.resources.BackendJointHandle;
import dev.hytalemodding.impulse.core.internal.resources.BackendSpaceHandle;
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.BodyComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.JointComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.UuidComponent;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import javax.annotation.Nonnull;

/**
 * Removes backend bodies after their authoritative PhysicsStore body row is gone.
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
        runtime.forEachSpaceBinding((_, _, spaceHandle, backendRuntime) ->
            runtime.forEachBodyHandle(spaceHandle,
                bodyId -> collectStaleBody(store,
                    runtime,
                    identity,
                    restore,
                    staleBodies,
                    spaceHandle,
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
        PhysicsSnapshotResource snapshots = store.getResource(PhysicsSnapshotResource.getResourceType());
        PhysicsBodyRegistrationResource registrations =
            store.getResource(PhysicsBodyRegistrationResource.getResourceType());
        for (BoundBody body : staleBodies) {
            try {
                body.backendRuntime().removeBody(body.spaceHandle().value(), body.bodyHandle().value());
            } catch (RuntimeException exception) {
                restore.markFailed("PhysicsStore body " + body.bodyUuid()
                    + " failed backend removal: " + exception.getMessage());
                return;
            }
            identity.removeBodyHandle(body.bodyHandle());
            Ref<PhysicsStore> ref = identity.getByUuid(body.bodyUuid());
            if (ref != null) {
                identity.removeUuid(body.bodyUuid(), ref);
            }
            snapshots.removeBody(body.bodyUuid());
            registrations.removeBody(RigidBodyKey.of(body.bodyUuid()));
            runtime.removeBodyHandle(body.bodyUuid());
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
        for (BoundJoint joint : collectDependentJoints(store, staleBodyUuids)) {
            BackendJointHandle jointHandle = runtime.getJointHandle(joint.jointUuid());
            if (jointHandle != null) {
                BackendSpaceHandle spaceHandle = runtime.getJointSpaceHandle(joint.jointUuid());
                if (spaceHandle == null) {
                    spaceHandle = runtime.getSpaceHandle(joint.spaceUuid());
                }
                PhysicsBackendRuntime backendRuntime = runtime.runtimeForSpaceHandle(spaceHandle);
                if (spaceHandle != null && backendRuntime != null) {
                    try {
                        backendRuntime.removeJoint(spaceHandle.value(), jointHandle.value());
                    } catch (RuntimeException exception) {
                        restore.markFailed("PhysicsStore joint " + joint.jointUuid()
                            + " failed backend removal: " + exception.getMessage());
                        return false;
                    }
                }
                identity.removeJointHandle(jointHandle);
            }
            runtime.removeJointHandle(joint.jointUuid());
            if (joint.ref().isValid()) {
                identity.removeUuid(joint.jointUuid(), joint.ref());
                store.removeEntity(joint.ref(),
                    store.getRegistry().newHolder(),
                    RemoveReason.REMOVE);
            }
        }
        return true;
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
            joints.add(new BoundJoint(jointUuid,
                chunk.getReferenceTo(index),
                joint.getSpaceUuid()));
        });
        return new ArrayList<>(joints);
    }

    private static void collectStaleBody(@Nonnull Store<PhysicsStore> store,
        @Nonnull PhysicsRuntimeResource runtime,
        @Nonnull PhysicsIdentityIndexResource identity,
        @Nonnull PhysicsRestoreStatusResource restore,
        @Nonnull List<BoundBody> staleBodies,
        @Nonnull BackendSpaceHandle spaceHandle,
        @Nonnull PhysicsBackendRuntime backendRuntime,
        long bodyId) {
        BodySnapshotMetadata metadata = runtime.getBodySnapshotMetadata(bodyId);
        if (metadata == null) {
            restore.markFailed("PhysicsStore backend body " + bodyId
                + " has no runtime snapshot metadata");
            return;
        }
        Ref<PhysicsStore> ref = PhysicsStoreSystemSupport.refForUuid(identity, metadata.bodyUuid());
        BodyComponent body = PhysicsStoreSystemSupport.component(store,
            ref,
            BodyComponent.getComponentType());
        if (body != null) {
            return;
        }
        staleBodies.add(new BoundBody(metadata.bodyUuid(),
            spaceHandle,
            new BackendBodyHandle(bodyId),
            backendRuntime));
    }

    @Nonnull
    @Override
    public Set<Dependency<PhysicsStore>> getDependencies() {
        return DEPENDENCIES;
    }

    private record BoundBody(@Nonnull UUID bodyUuid,
                             @Nonnull BackendSpaceHandle spaceHandle,
                             @Nonnull BackendBodyHandle bodyHandle,
                             @Nonnull PhysicsBackendRuntime backendRuntime) {
    }

    private record BoundJoint(@Nonnull UUID jointUuid,
                              @Nonnull Ref<PhysicsStore> ref,
                              @Nonnull UUID spaceUuid) {
    }
}
