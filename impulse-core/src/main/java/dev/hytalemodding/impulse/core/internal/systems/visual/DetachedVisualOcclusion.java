package dev.hytalemodding.impulse.core.internal.systems.visual;

import dev.hytalemodding.impulse.api.PhysicsBodySnapshot;
import dev.hytalemodding.impulse.api.PhysicsSpace;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsWorldRuntimeResource;
import dev.hytalemodding.impulse.core.internal.resources.profiling.PhysicsRuntimeProfilingResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsVisualRuntime;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsVisualRuntime.VisualInterest;
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsSpaceSettings;
import dev.hytalemodding.impulse.core.plugin.settings.VisualOcclusionMode;
import dev.hytalemodding.impulse.core.plugin.simulation.RaycastClosestQuery;
import dev.hytalemodding.impulse.core.plugin.simulation.RaycastHitView;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3f;

final class DetachedVisualOcclusion {

    private static final ThreadLocal<Vector3f> RAYCAST_TARGET =
        ThreadLocal.withInitial(Vector3f::new);

    private DetachedVisualOcclusion() {
    }

    @Nonnull
    static Result resolve(@Nonnull PhysicsWorldRuntimeResource resource,
        @Nonnull RigidBodyKey bodyKey,
        @Nullable PhysicsSpace space,
        @Nonnull PhysicsBodySnapshot snapshot,
        @Nonnull PhysicsSpaceSettings settings,
        @Nonnull List<VisualInterest> interests,
        float radius,
        long visualInterestTick,
        @Nonnull RaycastBudget raycastBudget,
        @Nullable PhysicsRuntimeProfilingResource.VisualCollector collector) {
        InterestProbe probe = probeNearestLikelyInterest(snapshot, settings, interests, radius);
        PhysicsVisualRuntime.BodyVisualInterestState state =
            resource.getOrCreateBodyVisualInterestState(bodyKey);
        if (!probe.inRange()) {
            state.clearPendingRaycast();
            state.recordInterest(Float.POSITIVE_INFINITY, false, false, false, visualInterestTick);
            return Result.notVisible();
        }

        VisualOcclusionMode occlusionMode = settings.getVisualSyncSettings().getVisualOcclusionMode();
        if (occlusionMode == VisualOcclusionMode.OFF || space == null) {
            state.clearPendingRaycast();
            state.recordInterest(probe.distanceSquared(), true, true, false, visualInterestTick);
            return Result.visible(probe.distanceSquared(), probe.distanceSquared());
        }

        boolean raycastFresh = state.hasFreshRaycast(settings.getVisualSyncSettings()
                .getVisualOcclusionCacheTicks(),
            visualInterestTick);
        boolean raycastDecisionKnown = raycastFresh;
        boolean raycastVisible = raycastFresh && state.isRaycastVisible();
        boolean raycastEvaluated = false;
        if (raycastFresh && collector != null) {
            collector.incrementRaycastCacheHits();
        }

        Optional<RaycastHitView> completedRaycast = state.pollCompletedRaycast();
        if (completedRaycast != null) {
            raycastVisible = completedRaycast
                .map(view -> bodyKey.equals(view.bodyKey()))
                .orElse(false);
            raycastDecisionKnown = true;
            raycastEvaluated = true;
        } else if (!raycastFresh) {
            if (state.hasRaycastResult()) {
                raycastVisible = state.isRaycastVisible();
                raycastDecisionKnown = true;
            }
            if (!state.hasPendingRaycast() && raycastBudget.tryUse(settings)) {
                submitRaycast(resource, space, state, probe, snapshot);
                if (collector != null) {
                    collector.incrementRaycasts();
                }
            }
        }

        state.recordInterest(probe.distanceSquared(),
            true,
            raycastVisible,
            raycastEvaluated,
            visualInterestTick);
        if (occlusionMode == VisualOcclusionMode.CULL && raycastDecisionKnown && !raycastVisible) {
            return Result.notVisible();
        }

        /*
         * PRIORITY only biases spawn order: visible bodies move forward in the
         * queue and occluded bodies move back. CULL above is the mode that skips
         * occluded candidates entirely.
         */
        float priorityDistanceSquared = probe.distanceSquared();
        if (occlusionMode == VisualOcclusionMode.PRIORITY && raycastDecisionKnown) {
            priorityDistanceSquared = raycastVisible
                ? probe.distanceSquared() * 0.25f
                : probe.distanceSquared() + radius * radius;
        }
        return Result.visible(probe.distanceSquared(), priorityDistanceSquared);
    }

    private static void submitRaycast(@Nonnull PhysicsWorldRuntimeResource resource,
        @Nonnull PhysicsSpace space,
        @Nonnull PhysicsVisualRuntime.BodyVisualInterestState state,
        @Nonnull InterestProbe probe,
        @Nonnull PhysicsBodySnapshot snapshot) {
        VisualInterest interest = Objects.requireNonNull(probe.interest(), "interest");
        Vector3f target = RAYCAST_TARGET.get()
            .set(snapshot.positionX(), snapshot.positionY(), snapshot.positionZ());
        state.startPendingRaycast(resource.query(new RaycastClosestQuery(space.id(),
            interest.position(),
            target)).completion());
    }

    @Nonnull
    private static InterestProbe probeNearestLikelyInterest(@Nonnull PhysicsBodySnapshot snapshot,
        @Nonnull PhysicsSpaceSettings settings,
        @Nonnull List<VisualInterest> interests,
        float radius) {
        return probeNearestLikelyInterest(snapshot.positionX(),
            snapshot.positionY(),
            snapshot.positionZ(),
            settings,
            interests,
            radius);
    }

    @Nonnull
    private static InterestProbe probeNearestLikelyInterest(float positionX,
        float positionY,
        float positionZ,
        @Nonnull PhysicsSpaceSettings settings,
        @Nonnull List<VisualInterest> interests,
        float radius) {
        float radiusSquared = radius * radius;
        float nearestDistanceSquared = Float.POSITIVE_INFINITY;
        VisualInterest nearestInterest = null;
        for (VisualInterest interest : interests) {
            float dx = positionX - interest.position().x;
            float dy = positionY - interest.position().y;
            float dz = positionZ - interest.position().z;
            float distanceSquared = dx * dx + dy * dy + dz * dz;
            if (distanceSquared <= radiusSquared
                && distanceSquared < nearestDistanceSquared
                && DetachedVisualGeometry.isInsideViewCone(settings,
                interest,
                dx,
                dy,
                dz,
                distanceSquared)) {
                nearestDistanceSquared = distanceSquared;
                nearestInterest = interest;
            }
        }
        return nearestInterest == null
            ? InterestProbe.notVisible()
            : new InterestProbe(nearestInterest, nearestDistanceSquared);
    }

    record Result(boolean shouldMaterialize,
                  float distanceSquared,
                  float priorityDistanceSquared) {

        static Result notVisible() {
            return new Result(false, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY);
        }

        static Result visible(float distanceSquared, float priorityDistanceSquared) {
            return new Result(true, distanceSquared, priorityDistanceSquared);
        }
    }

    static final class RaycastBudget {

        private int used;

        boolean tryUse(@Nonnull PhysicsSpaceSettings settings) {
            if (used >= settings.getVisualSyncSettings().getVisualOcclusionRaycastsPerTick()) {
                return false;
            }
            used++;
            return true;
        }
    }

    private record InterestProbe(@Nullable VisualInterest interest,
                                 float distanceSquared) {

        static InterestProbe notVisible() {
            return new InterestProbe(null, Float.POSITIVE_INFINITY);
        }

        boolean inRange() {
            return interest != null;
        }
    }
}
