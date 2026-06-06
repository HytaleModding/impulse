package dev.hytalemodding.impulse.core.plugin.settings;

import lombok.Getter;
import lombok.Setter;
import javax.annotation.Nonnull;

/**
 * Entity and follower transform sync sampling settings for a physics space.
 */
public class PhysicsVisualSyncSettings {

    /**
     * Block radius around players where visual followers receive the full sync policy.
     */
    public static final int DEFAULT_VISUAL_FULL_SYNC_RADIUS = 64;

    /**
     * Hard block-radius cap for full-rate visual sync.
     */
    public static final int MAX_VISUAL_FULL_SYNC_RADIUS = 512;

    /**
     * Maximum block radius around players where visual followers receive any sync at all.
     * Followers beyond this range stop writing Hytale transforms until they come back into
     * interest.
     */
    public static final int DEFAULT_VISUAL_MAX_SYNC_RADIUS = 128;

    /**
     * Hard block-radius cap for any visual sync.
     */
    public static final int MAX_VISUAL_MAX_SYNC_RADIUS = 1_024;

    /**
     * Whether visuals beyond {@link #DEFAULT_VISUAL_MAX_SYNC_RADIUS} stop receiving transform sync.
     */
    public static final boolean DEFAULT_VISUAL_FAR_SYNC_CUTOFF_ENABLED = true;

    /**
     * Minimum ticks between mid-range visual sync writes. A value of 1 means every tick is allowed.
     */
    public static final int DEFAULT_VISUAL_MID_SYNC_INTERVAL_TICKS = 1;

    /**
     * Hard tick cap for mid-range visual sync intervals.
     */
    public static final int MAX_VISUAL_MID_SYNC_INTERVAL_TICKS = 1_200;

    /**
     * Minimum ticks between far-range visual sync writes when far cutoff is disabled.
     */
    public static final int DEFAULT_VISUAL_FAR_SYNC_INTERVAL_TICKS = 40;

    /**
     * Hard tick cap for far-range visual sync intervals.
     */
    public static final int MAX_VISUAL_FAR_SYNC_INTERVAL_TICKS = 1_200;

    /**
     * Whether visual prioritization should use backend raycasts for occlusion.
     */
    @Nonnull
    public static final VisualOcclusionMode DEFAULT_VISUAL_OCCLUSION_MODE =
        VisualOcclusionMode.OFF;

    /**
     * Maximum backend raycasts spent on visual occlusion per world tick.
     */
    public static final int DEFAULT_VISUAL_OCCLUSION_RAYCASTS_PER_TICK = 256;

    /**
     * Hard cap on visual occlusion backend raycasts spent per tick.
     */
    public static final int MAX_VISUAL_OCCLUSION_RAYCASTS_PER_TICK = 4_096;

    /**
     * Ticks a visual occlusion raycast result can be reused.
     */
    public static final int DEFAULT_VISUAL_OCCLUSION_CACHE_TICKS = 10;

    /**
     * Hard tick cap for reusing visual occlusion raycast results.
     */
    public static final int MAX_VISUAL_OCCLUSION_CACHE_TICKS = 1_200;

    /**
     * Whether visual sync may predict near dynamic poses between published physics snapshots.
     */
    public static final boolean DEFAULT_VISUAL_SNAPSHOT_PREDICTION_ENABLED = false;

    /**
     * Maximum seconds of visual pose prediction from the last published snapshot.
     */
    public static final float DEFAULT_VISUAL_SNAPSHOT_PREDICTION_MAX_SECONDS = 0.10f;

    /**
     * Hard cap for visual snapshot prediction.
     */
    public static final float MAX_VISUAL_SNAPSHOT_PREDICTION_MAX_SECONDS = 0.25f;

    /**
     * Whether near dynamic visuals should ease toward published snapshots instead of snapping.
     */
    public static final boolean DEFAULT_VISUAL_SNAPSHOT_SMOOTHING_ENABLED = false;

    /**
     * Per-second rate used by visual snapshot smoothing.
     */
    public static final float DEFAULT_VISUAL_SNAPSHOT_SMOOTHING_RATE = 14.0f;

    /**
     * Hard cap for visual snapshot smoothing rate.
     */
    public static final float MAX_VISUAL_SNAPSHOT_SMOOTHING_RATE = 120.0f;

    /**
     * Whether owner entity transforms should use player-interest culling.
     * Disabled by default because gameplay code may rely on server-side transforms even
     * when no player is currently near the body.
     */
    public static final boolean DEFAULT_ENTITY_VISUAL_SYNC_CULLING_ENABLED = false;

    /**
     * Whether visual sync should require the body to be inside a player's approximate view cone.
     * Disabled by default because custom/third-person cameras can diverge from head rotation.
     */
    public static final boolean DEFAULT_VISUAL_VISIBILITY_CULLING_ENABLED = false;

    /**
     * Full-rate visual sync radius for follower entities.
     */
    @Getter
    private int visualFullSyncRadius = DEFAULT_VISUAL_FULL_SYNC_RADIUS;

    /**
     * Maximum visual sync radius for follower entities.
     */
    @Getter
    private int visualMaxSyncRadius = DEFAULT_VISUAL_MAX_SYNC_RADIUS;

    /**
     * If enabled, visuals outside {@link #visualMaxSyncRadius} do not receive transform sync.
     * If disabled, far visuals stay alive but sync at {@link #visualFarSyncIntervalTicks}.
     */
    @Setter
    @Getter
    private boolean visualFarSyncCutoffEnabled = DEFAULT_VISUAL_FAR_SYNC_CUTOFF_ENABLED;

    /**
     * Minimum ticks between mid-range visual transform writes.
     */
    @Getter
    private int visualMidSyncIntervalTicks = DEFAULT_VISUAL_MID_SYNC_INTERVAL_TICKS;

    /**
     * Minimum ticks between far-range visual transform writes when hard cutoff is disabled.
     */
    @Getter
    private int visualFarSyncIntervalTicks = DEFAULT_VISUAL_FAR_SYNC_INTERVAL_TICKS;

    /**
     * Optional raycast-backed visual occlusion behavior.
     */
    @Nonnull
    private VisualOcclusionMode visualOcclusionMode = DEFAULT_VISUAL_OCCLUSION_MODE;

    /**
     * Backend raycast budget for visual occlusion checks.
     */
    @Getter
    private int visualOcclusionRaycastsPerTick =
        DEFAULT_VISUAL_OCCLUSION_RAYCASTS_PER_TICK;

    /**
     * Reuse window for visual occlusion raycast results.
     */
    @Getter
    private int visualOcclusionCacheTicks = DEFAULT_VISUAL_OCCLUSION_CACHE_TICKS;

    /**
     * If enabled, near dynamic visuals can dead-reckon briefly between snapshots.
     */
    @Setter
    @Getter
    private boolean visualSnapshotPredictionEnabled =
        DEFAULT_VISUAL_SNAPSHOT_PREDICTION_ENABLED;

    /**
     * Maximum dead-reckoning window for visual snapshot prediction.
     */
    @Getter
    private float visualSnapshotPredictionMaxSeconds =
        DEFAULT_VISUAL_SNAPSHOT_PREDICTION_MAX_SECONDS;

    /**
     * If enabled, near dynamic visuals ease toward the latest snapshot target.
     */
    @Getter
    @Setter
    private boolean visualSnapshotSmoothingEnabled =
        DEFAULT_VISUAL_SNAPSHOT_SMOOTHING_ENABLED;

    /**
     * Per-second convergence rate for visual snapshot smoothing.
     */
    @Getter
    private float visualSnapshotSmoothingRate =
        DEFAULT_VISUAL_SNAPSHOT_SMOOTHING_RATE;

    /**
     * If enabled, entity-backed physics body transforms use the same player-interest culling
     * as follower visuals. Controlled bodies are always synced.
     */
    @Setter
    @Getter
    private boolean entityVisualSyncCullingEnabled = DEFAULT_ENTITY_VISUAL_SYNC_CULLING_ENABLED;

    /**
     * If enabled, near-range visual sync also requires an approximate player view-cone hit.
     * Keep disabled for custom cameras unless the server view direction matches the camera.
     */
    @Setter
    @Getter
    private boolean visualVisibilityCullingEnabled = DEFAULT_VISUAL_VISIBILITY_CULLING_ENABLED;

    public PhysicsVisualSyncSettings() {
    }

    public PhysicsVisualSyncSettings(@Nonnull PhysicsVisualSyncSettings settings) {
        visualFullSyncRadius = settings.visualFullSyncRadius;
        visualMaxSyncRadius = settings.visualMaxSyncRadius;
        visualFarSyncCutoffEnabled = settings.visualFarSyncCutoffEnabled;
        visualMidSyncIntervalTicks = settings.visualMidSyncIntervalTicks;
        visualFarSyncIntervalTicks = settings.visualFarSyncIntervalTicks;
        visualOcclusionMode = settings.visualOcclusionMode;
        visualOcclusionRaycastsPerTick = settings.visualOcclusionRaycastsPerTick;
        visualOcclusionCacheTicks = settings.visualOcclusionCacheTicks;
        visualSnapshotPredictionEnabled = settings.visualSnapshotPredictionEnabled;
        visualSnapshotPredictionMaxSeconds = settings.visualSnapshotPredictionMaxSeconds;
        visualSnapshotSmoothingEnabled = settings.visualSnapshotSmoothingEnabled;
        visualSnapshotSmoothingRate = settings.visualSnapshotSmoothingRate;
        entityVisualSyncCullingEnabled = settings.entityVisualSyncCullingEnabled;
        visualVisibilityCullingEnabled = settings.visualVisibilityCullingEnabled;
    }

    public void setVisualFullSyncRadius(int visualFullSyncRadius) {
        int boundedVisualFullSyncRadius = PhysicsSettingsValidation.requirePositiveAtMost(
            "Visual full sync radius",
            visualFullSyncRadius,
            MAX_VISUAL_FULL_SYNC_RADIUS);
        if (boundedVisualFullSyncRadius > visualMaxSyncRadius) {
            throw new IllegalArgumentException(
                "Visual full sync radius cannot exceed visual max sync radius");
        }
        this.visualFullSyncRadius = boundedVisualFullSyncRadius;
    }

    public void setVisualMaxSyncRadius(int visualMaxSyncRadius) {
        int boundedVisualMaxSyncRadius = PhysicsSettingsValidation.requirePositiveAtMost(
            "Visual max sync radius",
            visualMaxSyncRadius,
            MAX_VISUAL_MAX_SYNC_RADIUS);
        if (boundedVisualMaxSyncRadius < visualFullSyncRadius) {
            throw new IllegalArgumentException(
                "Visual max sync radius cannot be lower than visual full sync radius");
        }
        this.visualMaxSyncRadius = boundedVisualMaxSyncRadius;
    }

    public void setVisualSyncRadii(int visualFullSyncRadius, int visualMaxSyncRadius) {
        int boundedVisualFullSyncRadius = PhysicsSettingsValidation.requirePositiveAtMost(
            "Visual full sync radius",
            visualFullSyncRadius,
            MAX_VISUAL_FULL_SYNC_RADIUS);
        int boundedVisualMaxSyncRadius = PhysicsSettingsValidation.requirePositiveAtMost(
            "Visual max sync radius",
            visualMaxSyncRadius,
            MAX_VISUAL_MAX_SYNC_RADIUS);
        if (boundedVisualFullSyncRadius > boundedVisualMaxSyncRadius) {
            throw new IllegalArgumentException(
                "Visual full sync radius cannot exceed visual max sync radius");
        }
        this.visualFullSyncRadius = boundedVisualFullSyncRadius;
        this.visualMaxSyncRadius = boundedVisualMaxSyncRadius;
    }

    public void setVisualMidSyncIntervalTicks(int visualMidSyncIntervalTicks) {
        this.visualMidSyncIntervalTicks = PhysicsSettingsValidation.requirePositiveAtMost(
            "Visual mid sync interval",
            visualMidSyncIntervalTicks,
            MAX_VISUAL_MID_SYNC_INTERVAL_TICKS);
    }

    public void setVisualFarSyncIntervalTicks(int visualFarSyncIntervalTicks) {
        this.visualFarSyncIntervalTicks = PhysicsSettingsValidation.requirePositiveAtMost(
            "Visual far sync interval",
            visualFarSyncIntervalTicks,
            MAX_VISUAL_FAR_SYNC_INTERVAL_TICKS);
    }

    @Nonnull
    public VisualOcclusionMode getVisualOcclusionMode() {
        return visualOcclusionMode;
    }

    public void setVisualOcclusionMode(@Nonnull VisualOcclusionMode visualOcclusionMode) {
        this.visualOcclusionMode = visualOcclusionMode;
    }

    public void setVisualOcclusionRaycastsPerTick(int visualOcclusionRaycastsPerTick) {
        this.visualOcclusionRaycastsPerTick = PhysicsSettingsValidation.requirePositiveAtMost(
            "Visual occlusion raycasts per tick",
            visualOcclusionRaycastsPerTick,
            MAX_VISUAL_OCCLUSION_RAYCASTS_PER_TICK);
    }

    public void setVisualOcclusionCacheTicks(int visualOcclusionCacheTicks) {
        this.visualOcclusionCacheTicks = PhysicsSettingsValidation.requirePositiveAtMost(
            "Visual occlusion cache ticks",
            visualOcclusionCacheTicks,
            MAX_VISUAL_OCCLUSION_CACHE_TICKS);
    }

    public void setVisualSnapshotPredictionMaxSeconds(float visualSnapshotPredictionMaxSeconds) {
        if (!Float.isFinite(visualSnapshotPredictionMaxSeconds)
            || visualSnapshotPredictionMaxSeconds < 0.0f
            || visualSnapshotPredictionMaxSeconds > MAX_VISUAL_SNAPSHOT_PREDICTION_MAX_SECONDS) {
            throw new IllegalArgumentException("Visual snapshot prediction max seconds must be between 0 and "
                + MAX_VISUAL_SNAPSHOT_PREDICTION_MAX_SECONDS);
        }
        this.visualSnapshotPredictionMaxSeconds = visualSnapshotPredictionMaxSeconds;
    }

    public void setVisualSnapshotSmoothingRate(float visualSnapshotSmoothingRate) {
        if (!Float.isFinite(visualSnapshotSmoothingRate)
            || visualSnapshotSmoothingRate <= 0.0f
            || visualSnapshotSmoothingRate > MAX_VISUAL_SNAPSHOT_SMOOTHING_RATE) {
            throw new IllegalArgumentException("Visual snapshot smoothing rate must be > 0 and <= "
                + MAX_VISUAL_SNAPSHOT_SMOOTHING_RATE);
        }
        this.visualSnapshotSmoothingRate = visualSnapshotSmoothingRate;
    }

}
