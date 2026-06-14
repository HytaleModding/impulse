package dev.hytalemodding.impulse.core.internal.physicsstore.systems;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.api.runtime.PhysicsBackendRuntime;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsIdentityIndexResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsRestoreStatusResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsRuntimeResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsRuntimeResource.BodySnapshotMetadata;
import dev.hytalemodding.impulse.core.internal.resources.BackendBodyHandle;
import dev.hytalemodding.impulse.core.internal.resources.BackendSpaceHandle;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.BodyComponent;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
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
        for (BoundBody body : staleBodies) {
            try {
                body.backendRuntime().removeBody(body.spaceHandle().value(), body.bodyHandle().value());
            } catch (RuntimeException exception) {
                restore.markFailed("PhysicsStore body " + body.bodyUuid()
                    + " failed backend removal: " + exception.getMessage());
                return;
            }
            identity.removeBodyHandle(body.bodyHandle());
            runtime.removeBodyHandle(body.bodyUuid());
        }
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
}
