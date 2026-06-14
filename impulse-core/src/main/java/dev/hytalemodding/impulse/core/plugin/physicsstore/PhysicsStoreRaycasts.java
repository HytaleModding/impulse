package dev.hytalemodding.impulse.core.plugin.physicsstore;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.api.runtime.BackendRayHitSink;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsRuntimeResource;
import dev.hytalemodding.impulse.core.plugin.simulation.RaycastClosestBatchResult;
import dev.hytalemodding.impulse.core.plugin.simulation.RaycastSegment;
import dev.hytalemodding.impulse.core.plugin.simulation.view.RaycastHitView;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import javax.annotation.Nonnull;
import org.joml.Vector3f;

/**
 * PhysicsStore live backend raycasts.
 *
 * <p>The synchronous methods read live backend state through {@link PhysicsRuntimeResource} and
 * must only be called from the PhysicsStore tick lane or explicitly scheduled PhysicsStore owner
 * work. Off-lane callers should use the {@code *Async} methods, which copy inputs, enqueue the
 * read on the PhysicsStore owner thread, and complete with copied hit views.</p>
 */
public final class PhysicsStoreRaycasts {

    private PhysicsStoreRaycasts() {
    }

    @Nonnull
    public static Optional<RaycastHitView> closest(@Nonnull Store<PhysicsStore> store,
        @Nonnull SpaceId spaceId,
        @Nonnull Vector3f from,
        @Nonnull Vector3f to) {
        PhysicsStoreBackendAccess.SpaceContext space =
            PhysicsStoreBackendAccess.space(store, Objects.requireNonNull(spaceId, "spaceId"));
        return space != null ? closest(store, space, from, to) : Optional.empty();
    }

    @Nonnull
    public static Optional<RaycastHitView> closest(@Nonnull Store<PhysicsStore> store,
        @Nonnull UUID spaceUuid,
        @Nonnull Vector3f from,
        @Nonnull Vector3f to) {
        PhysicsStoreBackendAccess.SpaceContext space =
            PhysicsStoreBackendAccess.space(store, Objects.requireNonNull(spaceUuid, "spaceUuid"));
        return space != null ? closest(store, space, from, to) : Optional.empty();
    }

    @Nonnull
    public static List<RaycastHitView> all(@Nonnull Store<PhysicsStore> store,
        @Nonnull SpaceId spaceId,
        @Nonnull Vector3f from,
        @Nonnull Vector3f to) {
        PhysicsStoreBackendAccess.SpaceContext space =
            PhysicsStoreBackendAccess.space(store, Objects.requireNonNull(spaceId, "spaceId"));
        return space != null ? all(store, space, from, to) : List.of();
    }

    @Nonnull
    public static List<RaycastHitView> all(@Nonnull Store<PhysicsStore> store,
        @Nonnull UUID spaceUuid,
        @Nonnull Vector3f from,
        @Nonnull Vector3f to) {
        PhysicsStoreBackendAccess.SpaceContext space =
            PhysicsStoreBackendAccess.space(store, Objects.requireNonNull(spaceUuid, "spaceUuid"));
        return space != null ? all(store, space, from, to) : List.of();
    }

    @Nonnull
    public static RaycastClosestBatchResult closestBatch(@Nonnull Store<PhysicsStore> store,
        @Nonnull SpaceId spaceId,
        @Nonnull List<RaycastSegment> rays) {
        PhysicsStoreBackendAccess.SpaceContext space =
            PhysicsStoreBackendAccess.space(store, Objects.requireNonNull(spaceId, "spaceId"));
        return closestBatch(store, space, rays);
    }

    @Nonnull
    public static RaycastClosestBatchResult closestBatch(@Nonnull Store<PhysicsStore> store,
        @Nonnull UUID spaceUuid,
        @Nonnull List<RaycastSegment> rays) {
        PhysicsStoreBackendAccess.SpaceContext space =
            PhysicsStoreBackendAccess.space(store, Objects.requireNonNull(spaceUuid, "spaceUuid"));
        return closestBatch(store, space, rays);
    }

    @Nonnull
    public static CompletionStage<Optional<RaycastHitView>> closestAsync(@Nonnull World world,
        @Nonnull SpaceId spaceId,
        @Nonnull Vector3f from,
        @Nonnull Vector3f to) {
        Vector3f copiedFrom = new Vector3f(Objects.requireNonNull(from, "from"));
        Vector3f copiedTo = new Vector3f(Objects.requireNonNull(to, "to"));
        return PhysicsStoreThreading.enqueueReadOnWorldThread(world,
            "queue PhysicsStore closest raycast read",
            physics -> closest(physics, spaceId, copiedFrom, copiedTo));
    }

    @Nonnull
    public static CompletionStage<Optional<RaycastHitView>> closestAsync(
        @Nonnull Store<PhysicsStore> store,
        @Nonnull SpaceId spaceId,
        @Nonnull Vector3f from,
        @Nonnull Vector3f to) {
        Vector3f copiedFrom = new Vector3f(Objects.requireNonNull(from, "from"));
        Vector3f copiedTo = new Vector3f(Objects.requireNonNull(to, "to"));
        return PhysicsStoreThreading.enqueueReadOnWorldThread(store,
            "queue PhysicsStore closest raycast read",
            physics -> closest(physics, spaceId, copiedFrom, copiedTo));
    }

    @Nonnull
    public static CompletionStage<List<RaycastHitView>> allAsync(@Nonnull World world,
        @Nonnull SpaceId spaceId,
        @Nonnull Vector3f from,
        @Nonnull Vector3f to) {
        Vector3f copiedFrom = new Vector3f(Objects.requireNonNull(from, "from"));
        Vector3f copiedTo = new Vector3f(Objects.requireNonNull(to, "to"));
        return PhysicsStoreThreading.enqueueReadOnWorldThread(world,
            "queue PhysicsStore all raycast read",
            physics -> all(physics, spaceId, copiedFrom, copiedTo));
    }

    @Nonnull
    public static CompletionStage<List<RaycastHitView>> allAsync(@Nonnull Store<PhysicsStore> store,
        @Nonnull SpaceId spaceId,
        @Nonnull Vector3f from,
        @Nonnull Vector3f to) {
        Vector3f copiedFrom = new Vector3f(Objects.requireNonNull(from, "from"));
        Vector3f copiedTo = new Vector3f(Objects.requireNonNull(to, "to"));
        return PhysicsStoreThreading.enqueueReadOnWorldThread(store,
            "queue PhysicsStore all raycast read",
            physics -> all(physics, spaceId, copiedFrom, copiedTo));
    }

    @Nonnull
    public static CompletionStage<RaycastClosestBatchResult> closestBatchAsync(
        @Nonnull World world,
        @Nonnull SpaceId spaceId,
        @Nonnull List<RaycastSegment> rays) {
        List<RaycastSegment> copied = List.copyOf(Objects.requireNonNull(rays, "rays"));
        return PhysicsStoreThreading.enqueueReadOnWorldThread(world,
            "queue PhysicsStore batch raycast read",
            physics -> closestBatch(physics, spaceId, copied));
    }

    @Nonnull
    public static CompletionStage<RaycastClosestBatchResult> closestBatchAsync(
        @Nonnull Store<PhysicsStore> store,
        @Nonnull SpaceId spaceId,
        @Nonnull List<RaycastSegment> rays) {
        List<RaycastSegment> copied = List.copyOf(Objects.requireNonNull(rays, "rays"));
        return PhysicsStoreThreading.enqueueReadOnWorldThread(store,
            "queue PhysicsStore batch raycast read",
            physics -> closestBatch(physics, spaceId, copied));
    }

    @Nonnull
    private static Optional<RaycastHitView> closest(@Nonnull Store<PhysicsStore> store,
        @Nonnull PhysicsStoreBackendAccess.SpaceContext space,
        @Nonnull Vector3f from,
        @Nonnull Vector3f to) {
        PhysicsRuntimeResource runtime = store.getResource(PhysicsRuntimeResource.getResourceType());
        RayHitCapture hit = new RayHitCapture(runtime);
        Vector3f copiedFrom = new Vector3f(Objects.requireNonNull(from, "from"));
        Vector3f copiedTo = new Vector3f(Objects.requireNonNull(to, "to"));
        boolean hitFound = space.backendRuntime().raycastClosest(space.spaceHandle().value(),
            copiedFrom.x,
            copiedFrom.y,
            copiedFrom.z,
            copiedTo.x,
            copiedTo.y,
            copiedTo.z,
            hit);
        return hitFound && hit.captured ? Optional.of(hit.view()) : Optional.empty();
    }

    @Nonnull
    private static List<RaycastHitView> all(@Nonnull Store<PhysicsStore> store,
        @Nonnull PhysicsStoreBackendAccess.SpaceContext space,
        @Nonnull Vector3f from,
        @Nonnull Vector3f to) {
        PhysicsRuntimeResource runtime = store.getResource(PhysicsRuntimeResource.getResourceType());
        Vector3f copiedFrom = new Vector3f(Objects.requireNonNull(from, "from"));
        Vector3f copiedTo = new Vector3f(Objects.requireNonNull(to, "to"));
        List<RaycastHitView> hits = new ArrayList<>();
        space.backendRuntime().raycastAll(space.spaceHandle().value(),
            copiedFrom.x,
            copiedFrom.y,
            copiedFrom.z,
            copiedTo.x,
            copiedTo.y,
            copiedTo.z,
            (bodyId,
                pointX,
                pointY,
                pointZ,
                normalX,
                normalY,
                normalZ,
                fraction,
                distance) -> hits.add(PhysicsStoreBackendAccess.toView(runtime,
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

    @Nonnull
    private static RaycastClosestBatchResult closestBatch(@Nonnull Store<PhysicsStore> store,
        PhysicsStoreBackendAccess.SpaceContext space,
        @Nonnull List<RaycastSegment> rays) {
        List<RaycastSegment> copiedRays = List.copyOf(Objects.requireNonNull(rays, "rays"));
        int rayCount = copiedRays.size();
        RaycastHitView[] hits = new RaycastHitView[rayCount];
        if (space == null) {
            return new RaycastClosestBatchResult(hits);
        }
        PhysicsRuntimeResource runtime = store.getResource(PhysicsRuntimeResource.getResourceType());
        Vector3f from = new Vector3f();
        Vector3f to = new Vector3f();
        for (int index = 0; index < rayCount; index++) {
            RaycastSegment ray = copiedRays.get(index);
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
                    distance) -> hits[rayIndex] = PhysicsStoreBackendAccess.toView(runtime,
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

    private static final class RayHitCapture implements BackendRayHitSink {

        @Nonnull
        private final PhysicsRuntimeResource runtime;
        private boolean captured;
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
            view = PhysicsStoreBackendAccess.toView(runtime,
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
