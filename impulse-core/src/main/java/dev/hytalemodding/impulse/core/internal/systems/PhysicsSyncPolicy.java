package dev.hytalemodding.impulse.core.internal.systems;

import dev.hytalemodding.impulse.core.plugin.resources.PhysicsSpaceSettings;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsWorldResource;
import dev.hytalemodding.impulse.core.plugin.resources.VisualOcclusionMode;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Decides when a body pose should be written back to Hytale visual transforms.
 */
final class PhysicsSyncPolicy {

    // Near visuals sync after roughly one thirty-second of a block of movement.
    private static final float POSITION_SYNC_THRESHOLD = 1.0f / 32.0f;
    private static final float POSITION_SYNC_THRESHOLD_SQUARED =
        POSITION_SYNC_THRESHOLD * POSITION_SYNC_THRESHOLD;
    // Low-speed visuals use a wider deadzone for tiny awake-body solver jitter.
    private static final float LOW_SPEED_POSITION_SYNC_THRESHOLD = 1.0f / 8.0f;
    private static final float LOW_SPEED_POSITION_SYNC_THRESHOLD_SQUARED =
        LOW_SPEED_POSITION_SYNC_THRESHOLD * LOW_SPEED_POSITION_SYNC_THRESHOLD;
    // Mid-range visuals are outside the full-sync radius but still within visual range.
    private static final float MID_RANGE_POSITION_SYNC_THRESHOLD = 0.5f;
    private static final float MID_RANGE_POSITION_SYNC_THRESHOLD_SQUARED =
        MID_RANGE_POSITION_SYNC_THRESHOLD * MID_RANGE_POSITION_SYNC_THRESHOLD;
    /*
     * Quaternion dot thresholds avoid per-tick angle conversion. Since unit
     * quaternion dot is cos(delta / 2), these cos(1/3/8 degree) values trigger
     * after roughly 2/6/16 degrees of pose delta.
     */
    private static final float ROTATION_SYNC_DOT_THRESHOLD =
        (float) Math.cos(Math.toRadians(1.0));
    private static final float LOW_SPEED_ROTATION_SYNC_DOT_THRESHOLD =
        (float) Math.cos(Math.toRadians(3.0));
    private static final float MID_RANGE_ROTATION_SYNC_DOT_THRESHOLD =
        (float) Math.cos(Math.toRadians(8.0));
    // Optional visibility culling uses a broad forward cone and always keeps close visuals.
    private static final float VISUAL_CONE_DOT_THRESHOLD =
        (float) Math.cos(Math.toRadians(70.0));
    private static final float CLOSE_VISUAL_RADIUS = 8.0f;
    private static final float CLOSE_VISUAL_RADIUS_SQUARED =
        CLOSE_VISUAL_RADIUS * CLOSE_VISUAL_RADIUS;
    // Keepalive updates bound how long an awake visual can stay below sync thresholds.
    private static final float ACTIVE_KEEPALIVE_SECONDS = 0.25f;
    private static final float LOW_SPEED_KEEPALIVE_SECONDS = 1.25f;
    private static final float MID_RANGE_KEEPALIVE_SECONDS = 2.5f;
    private static final float SECONDS_PER_TICK = 0.05f;

    private PhysicsSyncPolicy() {
    }

    @Nonnull
    static SyncRangeTier resolveRangeTier(@Nullable PhysicsSpaceSettings settings,
        @Nullable PhysicsWorldResource.BodyVisualInterestState visualInterestState,
        boolean rangeLimitedVisual,
        boolean controlled,
        @Nonnull List<PlayerInterest> playerInterests,
        @Nonnull Vector3f visualPosition) {
        if (!rangeLimitedVisual || controlled) {
            return SyncRangeTier.NEAR;
        }
        if (playerInterests.isEmpty()) {
            return SyncRangeTier.FAR;
        }
        if (settings == null) {
            return SyncRangeTier.NEAR;
        }
        if (settings.getVisualOcclusionMode() == VisualOcclusionMode.CULL
            && visualInterestState != null
            && visualInterestState.hasFreshRaycast(settings.getVisualOcclusionCacheTicks())
            && !visualInterestState.isRaycastVisible()) {
            // Materialization owns the raycast budget; sync only consumes fresh CULL results.
            return SyncRangeTier.FAR;
        }

        float fullRadiusSquared = square(settings.getVisualFullSyncRadius());
        float maxRadiusSquared = square(settings.getVisualMaxSyncRadius());
        float nearestDistanceSquared = Float.MAX_VALUE;
        boolean visibilityCulling = settings.isVisualVisibilityCullingEnabled();
        for (PlayerInterest playerInterest : playerInterests) {
            float distanceSquared = playerInterest.position().distanceSquared(visualPosition);
            if (distanceSquared <= fullRadiusSquared
                && (!visibilityCulling || isLikelyVisible(playerInterest, visualPosition))) {
                return SyncRangeTier.NEAR;
            }
            if (distanceSquared < nearestDistanceSquared) {
                nearestDistanceSquared = distanceSquared;
            }
        }
        return nearestDistanceSquared <= maxRadiusSquared ? SyncRangeTier.MID : SyncRangeTier.FAR;
    }

    @Nonnull
    static SyncDecision resolveSyncDecision(@Nonnull PhysicsWorldResource.BodySyncState syncState,
        @Nullable PhysicsSpaceSettings settings,
        @Nonnull Vector3f position,
        @Nonnull Quaternionf rotation,
        boolean sleeping,
        boolean lowSpeed,
        boolean controlled,
        @Nonnull SyncRangeTier rangeTier) {
        if (!syncState.isInitialized()) {
            return SyncDecision.INITIAL;
        }
        if (sleeping != syncState.isSleeping()) {
            return SyncDecision.TRANSITION;
        }
        if (rangeTier == SyncRangeTier.FAR
            && (settings == null || settings.isVisualFarSyncCutoffEnabled())) {
            return SyncDecision.SKIP_VISUAL_RANGE;
        }
        if (sleeping && rangeTier != SyncRangeTier.NEAR) {
            return SyncDecision.SKIP_VISUAL_RANGE;
        }

        float positionThresholdSquared;
        float rotationDotThreshold;
        float keepaliveSeconds;
        int minimumIntervalTicks = 1;
        if (rangeTier == SyncRangeTier.FAR && !controlled) {
            positionThresholdSquared = MID_RANGE_POSITION_SYNC_THRESHOLD_SQUARED;
            rotationDotThreshold = MID_RANGE_ROTATION_SYNC_DOT_THRESHOLD;
            keepaliveSeconds = intervalSeconds(settings != null
                ? settings.getVisualFarSyncIntervalTicks()
                : PhysicsSpaceSettings.DEFAULT_VISUAL_FAR_SYNC_INTERVAL_TICKS);
            minimumIntervalTicks = settings != null
                ? settings.getVisualFarSyncIntervalTicks()
                : PhysicsSpaceSettings.DEFAULT_VISUAL_FAR_SYNC_INTERVAL_TICKS;
        } else if (rangeTier == SyncRangeTier.MID && !controlled) {
            positionThresholdSquared = MID_RANGE_POSITION_SYNC_THRESHOLD_SQUARED;
            rotationDotThreshold = MID_RANGE_ROTATION_SYNC_DOT_THRESHOLD;
            keepaliveSeconds = MID_RANGE_KEEPALIVE_SECONDS;
            minimumIntervalTicks = settings != null
                ? settings.getVisualMidSyncIntervalTicks()
                : PhysicsSpaceSettings.DEFAULT_VISUAL_MID_SYNC_INTERVAL_TICKS;
        } else {
            positionThresholdSquared = lowSpeed && !controlled
                ? LOW_SPEED_POSITION_SYNC_THRESHOLD_SQUARED : POSITION_SYNC_THRESHOLD_SQUARED;
            rotationDotThreshold = lowSpeed && !controlled
                ? LOW_SPEED_ROTATION_SYNC_DOT_THRESHOLD : ROTATION_SYNC_DOT_THRESHOLD;
            keepaliveSeconds = lowSpeed && !controlled
                ? LOW_SPEED_KEEPALIVE_SECONDS : ACTIVE_KEEPALIVE_SECONDS;
        }

        if (minimumIntervalTicks > 1
            && syncState.getSecondsSinceSync() < intervalSeconds(minimumIntervalTicks)) {
            return SyncDecision.SKIP_VISUAL_RANGE;
        }

        if (position.distanceSquared(syncState.getLastSyncedPosition()) >= positionThresholdSquared
            || rotationChangedEnough(rotation, syncState.getLastSyncedRotation(), rotationDotThreshold)) {
            return SyncDecision.THRESHOLD;
        }
        if (!sleeping && syncState.getSecondsSinceSync() >= keepaliveSeconds) {
            return SyncDecision.KEEPALIVE;
        }
        if (sleeping) {
            return SyncDecision.SKIP_SLEEPING;
        }
        if (rangeTier == SyncRangeTier.MID) {
            return SyncDecision.SKIP_VISUAL_RANGE;
        }
        return lowSpeed && !controlled ? SyncDecision.SKIP_VISUAL_DEADZONE
            : SyncDecision.SKIP_THRESHOLD;
    }

    private static boolean rotationChangedEnough(@Nonnull Quaternionf current,
        @Nonnull Quaternionf lastSynced,
        float dotThreshold) {
        float dot = Math.abs(current.x * lastSynced.x
            + current.y * lastSynced.y
            + current.z * lastSynced.z
            + current.w * lastSynced.w);
        return Math.min(dot, 1.0f) < dotThreshold;
    }

    private static boolean isLikelyVisible(@Nonnull PlayerInterest playerInterest,
        @Nonnull Vector3f visualPosition) {
        float dx = visualPosition.x - playerInterest.position().x;
        float dy = visualPosition.y - playerInterest.position().y;
        float dz = visualPosition.z - playerInterest.position().z;
        float distanceSquared = dx * dx + dy * dy + dz * dz;
        if (distanceSquared <= CLOSE_VISUAL_RADIUS_SQUARED) {
            return true;
        }

        Vector3f direction = playerInterest.direction();
        float dot = (dx * direction.x + dy * direction.y + dz * direction.z)
            / (float) Math.sqrt(distanceSquared);
        return dot >= VISUAL_CONE_DOT_THRESHOLD;
    }

    private static float square(float value) {
        return value * value;
    }

    private static float intervalSeconds(int ticks) {
        return ticks * SECONDS_PER_TICK;
    }

    record PlayerInterest(@Nonnull Vector3f position, @Nonnull Vector3f direction) {
    }

    enum SyncDecision {
        INITIAL,
        THRESHOLD,
        TRANSITION,
        KEEPALIVE,
        SKIP_SLEEPING,
        SKIP_THRESHOLD,
        SKIP_VISUAL_DEADZONE,
        SKIP_VISUAL_RANGE
    }

    enum SyncRangeTier {
        NEAR,
        MID,
        FAR
    }
}
