package dev.hytalemodding.impulse.core.plugin.physics;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.api.BackendId;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.api.runtime.PhysicsBackendRuntime;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsRuntimeResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsRuntimeResource.BodyHitMetadata;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsSpaceCompatibilityIndexResource;
import dev.hytalemodding.impulse.core.internal.resources.BackendSpaceHandle;
import dev.hytalemodding.impulse.core.plugin.simulation.SpaceSummary;
import dev.hytalemodding.impulse.core.plugin.simulation.view.RaycastHitView;
import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

final class PhysicsBackendAccess {

    private PhysicsBackendAccess() {
    }

    @Nullable
    static SpaceContext space(@Nonnull Store<PhysicsStore> store,
        @Nonnull Ref<PhysicsStore> spaceRef) {
        PhysicsThreading.requireBackendIdle(store, "read live PhysicsStore backend state");
        Objects.requireNonNull(spaceRef, "spaceRef");
        if (spaceRef.getStore() != store || !spaceRef.isValid()) {
            return null;
        }
        PhysicsRuntimeResource runtime = store.getResource(PhysicsRuntimeResource.getResourceType());
        return space(runtime, spaceRef);
    }

    @Nullable
    static SpaceContext space(@Nonnull PhysicsRuntimeResource runtime,
        @Nonnull Ref<PhysicsStore> spaceRef) {
        UUID spaceUuid = runtime.getSpaceUuid(spaceRef);
        BackendSpaceHandle spaceHandle = runtime.getSpaceHandle(spaceRef);
        BackendId backendId = runtime.getSpaceBackendId(spaceRef);
        PhysicsBackendRuntime backendRuntime =
            backendId != null ? runtime.getRuntime(backendId) : null;
        if (spaceUuid == null || spaceHandle == null || backendId == null || backendRuntime == null) {
            return null;
        }
        return new SpaceContext(spaceUuid, backendId, spaceHandle, backendRuntime);
    }

    @Nonnull
    static SpaceContext requireSpace(@Nonnull Store<PhysicsStore> store,
        @Nonnull Ref<PhysicsStore> spaceRef) {
        SpaceContext space = space(store, spaceRef);
        if (space == null) {
            throw new IllegalArgumentException("Physics space ref=" + spaceRef
                + " is not registered");
        }
        return space;
    }

    @Nonnull
    static SpaceSummary summary(@Nonnull PhysicsSpaceCompatibilityIndexResource compatibility,
        @Nonnull SpaceContext space) {
        SpaceId spaceId = compatibility.getSpaceId(space.spaceUuid());
        if (spaceId == null) {
            throw new IllegalStateException("PhysicsStore space has no compatibility SpaceId: "
                + space.spaceUuid());
        }
        return new SpaceSummary(spaceId,
            space.backendId(),
            space.backendRuntime().bodyCount(space.spaceHandle().value()),
            space.backendRuntime().jointCount(space.spaceHandle().value()));
    }

    @Nonnull
    static RaycastHitView toView(@Nonnull PhysicsRuntimeResource runtime,
        long bodyId,
        float pointX,
        float pointY,
        float pointZ,
        float normalX,
        float normalY,
        float normalZ,
        float fraction,
        float distance) {
        BodyHitMetadata metadata = runtime.getBodyHitMetadata(bodyId);
        return new RaycastHitView(metadata != null ? metadata.bodyRef() : null,
            pointX,
            pointY,
            pointZ,
            normalX,
            normalY,
            normalZ,
            fraction,
            distance);
    }

    record SpaceContext(@Nonnull UUID spaceUuid,
                        @Nonnull BackendId backendId,
                        @Nonnull BackendSpaceHandle spaceHandle,
                        @Nonnull PhysicsBackendRuntime backendRuntime) {
    }
}
