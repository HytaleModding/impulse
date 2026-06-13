package dev.hytalemodding.impulse.core.internal.physicsstore.queries;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.api.BackendId;
import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.ShapeType;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.api.runtime.BackendRayHitSink;
import dev.hytalemodding.impulse.api.runtime.PhysicsBackendRuntime;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsRuntimeResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsRuntimeResource.BodyHitMetadata;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsSnapshotResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsSpaceCompatibilityIndexResource;
import dev.hytalemodding.impulse.core.internal.resources.BackendBodyHandle;
import dev.hytalemodding.impulse.core.internal.resources.BackendSpaceHandle;
import dev.hytalemodding.impulse.core.plugin.physicsstore.snapshots.PhysicsStoreBodySnapshot;
import dev.hytalemodding.impulse.core.plugin.simulation.RaycastClosestBatchResult;
import dev.hytalemodding.impulse.core.plugin.simulation.RaycastSegment;
import dev.hytalemodding.impulse.core.plugin.simulation.RigidBodyPose;
import dev.hytalemodding.impulse.core.plugin.simulation.SolverCapabilitySummary;
import dev.hytalemodding.impulse.core.plugin.simulation.SpaceSummary;
import dev.hytalemodding.impulse.core.plugin.simulation.query.CcdSupportQuery;
import dev.hytalemodding.impulse.core.plugin.simulation.query.PhysicsQuery;
import dev.hytalemodding.impulse.core.plugin.simulation.query.PhysicsQueryHandle;
import dev.hytalemodding.impulse.core.plugin.simulation.query.RaycastAllQuery;
import dev.hytalemodding.impulse.core.plugin.simulation.query.RaycastClosestBatchQuery;
import dev.hytalemodding.impulse.core.plugin.simulation.query.RaycastClosestQuery;
import dev.hytalemodding.impulse.core.plugin.simulation.query.RigidBodyStateQuery;
import dev.hytalemodding.impulse.core.plugin.simulation.query.RuntimeJointCountQuery;
import dev.hytalemodding.impulse.core.plugin.simulation.query.SolverCapabilityQuery;
import dev.hytalemodding.impulse.core.plugin.simulation.query.SpaceBodyCountQuery;
import dev.hytalemodding.impulse.core.plugin.simulation.query.SpaceSummaryQuery;
import dev.hytalemodding.impulse.core.plugin.simulation.query.UnsupportedCcdSpacesQuery;
import dev.hytalemodding.impulse.core.plugin.simulation.view.RaycastHitView;
import dev.hytalemodding.impulse.core.plugin.simulation.view.RigidBodyStateView;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3f;

/**
 * Internal compatibility adapter from legacy query DTOs to authoritative PhysicsStore state.
 */
public final class PhysicsStoreQueryBridge {

    private PhysicsStoreQueryBridge() {
    }

    @Nonnull
    public static <R> Optional<PhysicsQueryHandle<R>> tryQuery(@Nonnull Store<PhysicsStore> store,
        @Nonnull PhysicsQuery<R> query) {
        Objects.requireNonNull(store, "store");
        Objects.requireNonNull(query, "query");
        Object result;
        if (query instanceof RigidBodyStateQuery state) {
            result = rigidBodyState(store, state);
        } else if (query instanceof RaycastClosestQuery raycast) {
            result = raycastClosest(store, raycast);
        } else if (query instanceof RaycastClosestBatchQuery raycasts) {
            result = raycastClosestBatch(store, raycasts);
        } else if (query instanceof RaycastAllQuery raycast) {
            result = raycastAll(store, raycast);
        } else if (query instanceof SpaceBodyCountQuery count) {
            result = spaceBodyCount(store, count);
        } else if (query instanceof SpaceSummaryQuery summary) {
            result = spaceSummary(store, summary);
        } else if (query instanceof CcdSupportQuery ccd) {
            result = ccdSupported(store, ccd);
        } else if (query instanceof UnsupportedCcdSpacesQuery unsupportedCcd) {
            result = unsupportedCcdSpaces(store, unsupportedCcd);
        } else if (query instanceof SolverCapabilityQuery solver) {
            result = solverCapability(store, solver);
        } else if (query instanceof RuntimeJointCountQuery jointCount) {
            result = runtimeJointCount(store, jointCount);
        } else {
            return Optional.empty();
        }
        @SuppressWarnings("unchecked")
        R typed = (R) result;
        return Optional.of(PhysicsQueryHandle.completed(query, typed));
    }

    @Nonnull
    private static Optional<RigidBodyStateView> rigidBodyState(@Nonnull Store<PhysicsStore> store,
        @Nonnull RigidBodyStateQuery query) {
        PhysicsSnapshotResource snapshots = store.getResource(PhysicsSnapshotResource.getResourceType());
        PhysicsStoreBodySnapshot body = snapshots.getBody(query.bodyKey().value());
        if (body == null) {
            return Optional.empty();
        }
        return Optional.of(new RigidBodyStateView(query.bodyKey(),
            body.bodyType(),
            RigidBodyPose.of(body.position(), body.rotation())));
    }

    @Nonnull
    private static Optional<RaycastHitView> raycastClosest(@Nonnull Store<PhysicsStore> store,
        @Nonnull RaycastClosestQuery query) {
        SpaceQueryContext space = space(store, query.spaceId());
        if (space == null) {
            return Optional.empty();
        }
        PhysicsRuntimeResource runtime = store.getResource(PhysicsRuntimeResource.getResourceType());
        RayHitCapture hit = new RayHitCapture(runtime);
        Vector3f from = query.from();
        Vector3f to = query.to();
        boolean hitFound = space.backendRuntime().raycastClosest(space.spaceHandle().value(),
            from.x,
            from.y,
            from.z,
            to.x,
            to.y,
            to.z,
            hit);
        return hitFound && hit.captured ? Optional.of(hit.view()) : Optional.empty();
    }

    @Nonnull
    private static RaycastClosestBatchResult raycastClosestBatch(@Nonnull Store<PhysicsStore> store,
        @Nonnull RaycastClosestBatchQuery query) {
        SpaceQueryContext space = space(store, query.spaceId());
        if (space == null) {
            return new RaycastClosestBatchResult(new RaycastHitView[query.rayCount()]);
        }
        PhysicsRuntimeResource runtime = store.getResource(PhysicsRuntimeResource.getResourceType());
        int rayCount = query.rayCount();
        RaycastHitView[] hits = new RaycastHitView[rayCount];
        Vector3f from = new Vector3f();
        Vector3f to = new Vector3f();
        for (int index = 0; index < rayCount; index++) {
            RaycastSegment ray = query.ray(index);
            ray.copyFrom(from);
            ray.copyTo(to);
            int rayIndex = index;
            space.backendRuntime().raycastClosest(space.spaceHandle().value(),
                from.x,
                from.y,
                from.z,
                to.x,
                to.y,
                to.z,
                (bodyId,
                    pointX,
                    pointY,
                    pointZ,
                    normalX,
                    normalY,
                    normalZ,
                    fraction,
                    distance) -> hits[rayIndex] = toView(runtime,
                    bodyId,
                    pointX,
                    pointY,
                    pointZ,
                    normalX,
                    normalY,
                    normalZ,
                    fraction,
                    distance));
        }
        return new RaycastClosestBatchResult(hits);
    }

    @Nonnull
    private static List<RaycastHitView> raycastAll(@Nonnull Store<PhysicsStore> store,
        @Nonnull RaycastAllQuery query) {
        SpaceQueryContext space = space(store, query.spaceId());
        if (space == null) {
            return List.of();
        }
        PhysicsRuntimeResource runtime = store.getResource(PhysicsRuntimeResource.getResourceType());
        Vector3f from = query.from();
        Vector3f to = query.to();
        List<RaycastHitView> hits = new ArrayList<>();
        space.backendRuntime().raycastAll(space.spaceHandle().value(),
            from.x,
            from.y,
            from.z,
            to.x,
            to.y,
            to.z,
            (bodyId,
                pointX,
                pointY,
                pointZ,
                normalX,
                normalY,
                normalZ,
                fraction,
                distance) -> hits.add(toView(runtime,
                bodyId,
                pointX,
                pointY,
                pointZ,
                normalX,
                normalY,
                normalZ,
                fraction,
                distance)));
        return hits.isEmpty() ? List.of() : List.copyOf(hits);
    }

    private static int spaceBodyCount(@Nonnull Store<PhysicsStore> store,
        @Nonnull SpaceBodyCountQuery query) {
        SpaceQueryContext space = space(store, query.spaceId());
        return space != null ? space.backendRuntime().bodyCount(space.spaceHandle().value()) : 0;
    }

    @Nonnull
    private static List<SpaceSummary> spaceSummary(@Nonnull Store<PhysicsStore> store,
        @Nonnull SpaceSummaryQuery query) {
        PhysicsRuntimeResource runtime = store.getResource(PhysicsRuntimeResource.getResourceType());
        PhysicsSpaceCompatibilityIndexResource compatibility = store.getResource(
            PhysicsSpaceCompatibilityIndexResource.getResourceType());
        List<SpaceSummary> summaries = new ArrayList<>();
        if (query.spaceId() != null) {
            SpaceQueryContext space = space(runtime, compatibility, query.spaceId());
            if (space != null) {
                summaries.add(summary(compatibility, space));
            }
            return summaries.isEmpty() ? List.of() : List.copyOf(summaries);
        }
        runtime.forEachSpaceBinding((spaceUuid, backendId, spaceHandle, backendRuntime) -> {
            SpaceId spaceId = compatibility.getSpaceId(spaceUuid);
            if (spaceId != null) {
                summaries.add(new SpaceSummary(spaceId,
                    backendId,
                    backendRuntime.bodyCount(spaceHandle.value()),
                    backendRuntime.jointCount(spaceHandle.value())));
            }
        });
        return summaries.isEmpty() ? List.of() : List.copyOf(summaries);
    }

    private static boolean ccdSupported(@Nonnull Store<PhysicsStore> store,
        @Nonnull CcdSupportQuery query) {
        PhysicsRuntimeResource runtime = store.getResource(PhysicsRuntimeResource.getResourceType());
        boolean[] supported = {false};
        runtime.forEachSpaceBinding((spaceUuid, backendId, spaceHandle, backendRuntime) -> {
            if (backendRuntime.supportsContinuousCollision(spaceHandle.value())) {
                supported[0] = true;
            }
        });
        return supported[0];
    }

    @Nonnull
    private static List<SpaceSummary> unsupportedCcdSpaces(@Nonnull Store<PhysicsStore> store,
        @Nonnull UnsupportedCcdSpacesQuery query) {
        PhysicsRuntimeResource runtime = store.getResource(PhysicsRuntimeResource.getResourceType());
        PhysicsSpaceCompatibilityIndexResource compatibility = store.getResource(
            PhysicsSpaceCompatibilityIndexResource.getResourceType());
        List<SpaceSummary> spaces = new ArrayList<>();
        runtime.forEachSpaceBinding((spaceUuid, backendId, spaceHandle, backendRuntime) -> {
            if (backendRuntime.supportsContinuousCollision(spaceHandle.value())) {
                return;
            }
            spaces.add(summary(compatibility,
                new SpaceQueryContext(spaceUuid, backendId, spaceHandle, backendRuntime)));
        });
        return spaces.isEmpty() ? List.of() : List.copyOf(spaces);
    }

    @Nonnull
    private static SolverCapabilitySummary solverCapability(@Nonnull Store<PhysicsStore> store,
        @Nonnull SolverCapabilityQuery query) {
        SpaceQueryContext space = requireSpace(store, query.spaceId());
        return new SolverCapabilitySummary(query.spaceId(),
            space.backendId().value(),
            space.backendRuntime().supportsSolverTuning(space.spaceHandle().value()),
            space.backendRuntime().supportsActivationTuning(space.spaceHandle().value()));
    }

    private static int runtimeJointCount(@Nonnull Store<PhysicsStore> store,
        @Nonnull RuntimeJointCountQuery query) {
        PhysicsRuntimeResource runtime = store.getResource(PhysicsRuntimeResource.getResourceType());
        int[] count = {0};
        runtime.forEachSpaceBinding((spaceUuid, backendId, spaceHandle, backendRuntime) ->
            count[0] += backendRuntime.jointCount(spaceHandle.value()));
        return count[0];
    }

    @Nonnull
    private static SpaceQueryContext requireSpace(@Nonnull Store<PhysicsStore> store,
        @Nonnull SpaceId spaceId) {
        SpaceQueryContext space = space(store, spaceId);
        if (space == null) {
            throw new IllegalArgumentException("Physics space id=" + spaceId + " is not registered");
        }
        return space;
    }

    @Nonnull
    private static SpaceSummary summary(@Nonnull PhysicsSpaceCompatibilityIndexResource compatibility,
        @Nonnull SpaceQueryContext space) {
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

    @Nullable
    private static SpaceQueryContext space(@Nonnull Store<PhysicsStore> store,
        @Nonnull SpaceId spaceId) {
        PhysicsRuntimeResource runtime = store.getResource(PhysicsRuntimeResource.getResourceType());
        PhysicsSpaceCompatibilityIndexResource compatibility = store.getResource(
            PhysicsSpaceCompatibilityIndexResource.getResourceType());
        return space(runtime, compatibility, spaceId);
    }

    @Nullable
    private static SpaceQueryContext space(@Nonnull PhysicsRuntimeResource runtime,
        @Nonnull PhysicsSpaceCompatibilityIndexResource compatibility,
        @Nonnull SpaceId spaceId) {
        UUID spaceUuid = compatibility.getSpaceUuid(spaceId);
        if (spaceUuid == null) {
            return null;
        }
        BackendSpaceHandle spaceHandle = runtime.getSpaceHandle(spaceUuid);
        BackendId backendId = runtime.getSpaceBackendId(spaceUuid);
        PhysicsBackendRuntime backendRuntime = backendId != null ? runtime.getRuntime(backendId) : null;
        if (spaceHandle == null || backendId == null || backendRuntime == null) {
            return null;
        }
        return new SpaceQueryContext(spaceUuid, backendId, spaceHandle, backendRuntime);
    }

    @Nonnull
    private static RaycastHitView toView(@Nonnull PhysicsRuntimeResource runtime,
        long bodyId,
        float pointX,
        float pointY,
        float pointZ,
        float normalX,
        float normalY,
        float normalZ,
        float fraction,
        float distance) {
        BodyHitMetadata metadata = runtime.getBodyHitMetadata(new BackendBodyHandle(bodyId));
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

    private record SpaceQueryContext(@Nonnull UUID spaceUuid,
                                     @Nonnull BackendId backendId,
                                     @Nonnull BackendSpaceHandle spaceHandle,
                                     @Nonnull PhysicsBackendRuntime backendRuntime) {
    }

    private static final class RayHitCapture implements BackendRayHitSink {

        @Nonnull
        private final PhysicsRuntimeResource runtime;
        private boolean captured;
        @Nullable
        private RaycastHitView view;

        private RayHitCapture(@Nonnull PhysicsRuntimeResource runtime) {
            this.runtime = runtime;
        }

        @Override
        public void accept(long bodyId,
            float pointX,
            float pointY,
            float pointZ,
            float normalX,
            float normalY,
            float normalZ,
            float fraction,
            float distance) {
            view = toView(runtime,
                bodyId,
                pointX,
                pointY,
                pointZ,
                normalX,
                normalY,
                normalZ,
                fraction,
                distance);
            captured = true;
        }

        @Nonnull
        private RaycastHitView view() {
            return Objects.requireNonNull(view, "view");
        }
    }
}
