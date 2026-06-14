package dev.hytalemodding.impulse.core.plugin.physicsstore;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.api.BackendId;
import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.ShapeType;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.api.runtime.PhysicsBackendRuntime;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsRuntimeResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsRuntimeResource.BodyHitMetadata;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsSpaceCompatibilityIndexResource;
import dev.hytalemodding.impulse.core.internal.resources.BackendSpaceHandle;
import dev.hytalemodding.impulse.core.plugin.simulation.SpaceSummary;
import dev.hytalemodding.impulse.core.plugin.simulation.view.RaycastHitView;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

final class PhysicsStoreBackendAccess {

    private PhysicsStoreBackendAccess() {
    }

    @Nullable
    static SpaceContext space(@Nonnull Store<PhysicsStore> store, @Nonnull SpaceId spaceId) {
        PhysicsRuntimeResource runtime = store.getResource(PhysicsRuntimeResource.getResourceType());
        PhysicsSpaceCompatibilityIndexResource compatibility = store.getResource(
            PhysicsSpaceCompatibilityIndexResource.getResourceType());
        UUID spaceUuid = compatibility.getSpaceUuid(spaceId);
        return spaceUuid != null ? space(runtime, spaceUuid) : null;
    }

    @Nullable
    static SpaceContext space(@Nonnull Store<PhysicsStore> store, @Nonnull UUID spaceUuid) {
        PhysicsRuntimeResource runtime = store.getResource(PhysicsRuntimeResource.getResourceType());
        return space(runtime, spaceUuid);
    }

    @Nullable
    static SpaceContext space(@Nonnull PhysicsRuntimeResource runtime, @Nonnull UUID spaceUuid) {
        BackendSpaceHandle spaceHandle = runtime.getSpaceHandle(spaceUuid);
        BackendId backendId = runtime.getSpaceBackendId(spaceUuid);
        PhysicsBackendRuntime backendRuntime =
            backendId != null ? runtime.getRuntime(backendId) : null;
        if (spaceHandle == null || backendId == null || backendRuntime == null) {
            return null;
        }
        return new SpaceContext(spaceUuid, backendId, spaceHandle, backendRuntime);
    }

    @Nonnull
    static SpaceContext requireSpace(@Nonnull Store<PhysicsStore> store, @Nonnull SpaceId spaceId) {
        SpaceContext space = space(store, spaceId);
        if (space == null) {
            throw new IllegalArgumentException("Physics space id=" + spaceId + " is not registered");
        }
        return space;
    }

    @Nonnull
    static SpaceContext requireSpace(@Nonnull Store<PhysicsStore> store, @Nonnull UUID spaceUuid) {
        SpaceContext space = space(store, spaceUuid);
        if (space == null) {
            throw new IllegalArgumentException("Physics space uuid=" + spaceUuid
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
        return new RaycastHitView(metadata != null ? metadata.bodyKey() : null,
            metadata != null ? metadata.bodyType() : PhysicsBodyType.STATIC,
            pointX,
            pointY,
            pointZ,
            normalX,
            normalY,
            normalZ,
            metadata != null ? metadata.shapeType() : ShapeType.UNKNOWN,
            fraction,
            distance);
    }

    record SpaceContext(@Nonnull UUID spaceUuid,
                        @Nonnull BackendId backendId,
                        @Nonnull BackendSpaceHandle spaceHandle,
                        @Nonnull PhysicsBackendRuntime backendRuntime) {
    }
}
