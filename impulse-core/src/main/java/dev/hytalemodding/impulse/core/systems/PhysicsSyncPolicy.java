package dev.hytalemodding.impulse.core.systems;

import dev.hytalemodding.impulse.core.resources.PhysicsSpaceSettings;
import dev.hytalemodding.impulse.core.resources.PhysicsWorldResource;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

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
    // Dot thresholds store angular tolerances without converting quaternions every tick.
    private static final float ROTATION_SYNC_DOT_THRESHOLD =
        (float) Math.cos(Math.toRadians(1.0));
    private static final float LOW_SPEED_ROTATION_SYNC_DOT_THRESHOLD =
        (float) Math.cos(Math.toRadians(3.0));
    private static final float MID_RANGE_ROTATION_SYNC_DOT_THRESHOLD =
        (float) Math.cos(Math.toRadians(8.0));
    // Keepalive updates bound how long an awake visual can stay below sync thresholds.
    private static final float ACTIVE_KEEPALIVE_SECONDS = 0.25f;
    private static final float LOW_SPEED_KEEPALIVE_SECONDS = 1.25f;
    private static final float MID_RANGE_KEEPALIVE_SECONDS = 2.5f;

    private PhysicsSyncPolicy() {
    }

    @Nonnull
    static SyncRangeTier resolveRangeTier(@Nullable PhysicsSpaceSettings settings,
        boolean rangeLimitedVisual,
        boolean controlled,
        @Nonnull List<Vector3f> playerInterestPositions,
        @Nonnull Vector3f visualPosition) {
        if (!rangeLimitedVisual || controlled) {
            return SyncRangeTier.NEAR;
        }
        if (playerInterestPositions.isEmpty()) {
            return SyncRangeTier.FAR;
        }
        if (settings == null) {
            return SyncRangeTier.NEAR;
        }

        float fullRadiusSquared = square(settings.getVisualFullSyncRadius());
        float maxRadiusSquared = square(settings.getVisualMaxSyncRadius());
        float nearestDistanceSquared = Float.MAX_VALUE;
        for (Vector3f playerPosition : playerInterestPositions) {
            float distanceSquared = playerPosition.distanceSquared(visualPosition);
            if (distanceSquared <= fullRadiusSquared) {
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
        if (rangeTier == SyncRangeTier.FAR) {
            return SyncDecision.SKIP_VISUAL_RANGE;
        }

        float positionThresholdSquared;
        float rotationDotThreshold;
        float keepaliveSeconds;
        if (rangeTier == SyncRangeTier.MID && !controlled) {
            positionThresholdSquared = MID_RANGE_POSITION_SYNC_THRESHOLD_SQUARED;
            rotationDotThreshold = MID_RANGE_ROTATION_SYNC_DOT_THRESHOLD;
            keepaliveSeconds = MID_RANGE_KEEPALIVE_SECONDS;
        } else {
            positionThresholdSquared = lowSpeed && !controlled
                ? LOW_SPEED_POSITION_SYNC_THRESHOLD_SQUARED : POSITION_SYNC_THRESHOLD_SQUARED;
            rotationDotThreshold = lowSpeed && !controlled
                ? LOW_SPEED_ROTATION_SYNC_DOT_THRESHOLD : ROTATION_SYNC_DOT_THRESHOLD;
            keepaliveSeconds = lowSpeed && !controlled
                ? LOW_SPEED_KEEPALIVE_SECONDS : ACTIVE_KEEPALIVE_SECONDS;
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

    private static float square(float value) {
        return value * value;
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
