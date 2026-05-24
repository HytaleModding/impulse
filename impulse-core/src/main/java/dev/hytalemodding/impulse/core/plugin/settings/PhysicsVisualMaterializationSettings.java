package dev.hytalemodding.impulse.core.plugin.settings;

import javax.annotation.Nonnull;

/**
 * Generated visual proxy settings for detached physics bodies.
 */
public class PhysicsVisualMaterializationSettings {

    /**
     * Whether detached bodies should automatically create disposable Hytale visual followers near players.
     * Disabled by default because integrators may provide their own render/materialization layer.
     */
    public static final boolean DEFAULT_DETACHED_VISUAL_MATERIALIZATION_ENABLED = false;

    /**
     * Block radius around players where detached bodies materialize visual followers.
     */
    public static final int DEFAULT_DETACHED_VISUAL_MATERIALIZATION_RADIUS = 64;

    /**
     * Hard block-radius cap for detached visual proxy materialization.
     */
    public static final int MAX_DETACHED_VISUAL_MATERIALIZATION_RADIUS = 512;

    /**
     * Larger radius used to avoid rapid visual proxy despawn/respawn at the edge.
     */
    public static final int DEFAULT_DETACHED_VISUAL_DEMATERIALIZATION_RADIUS = 80;

    /**
     * Hard block-radius cap for detached visual proxy dematerialization.
     */
    public static final int MAX_DETACHED_VISUAL_DEMATERIALIZATION_RADIUS = 1_024;

    /**
     * Maximum detached visual proxies spawned per world tick.
     */
    public static final int DEFAULT_DETACHED_VISUAL_MAX_SPAWNS_PER_TICK = 128;

    /**
     * Hard cap on detached visual proxy spawns per tick.
     */
    public static final int MAX_DETACHED_VISUAL_MAX_SPAWNS_PER_TICK = 512;

    /**
     * Maximum detached visual proxies allowed in one physics space at once.
     */
    public static final int DEFAULT_DETACHED_VISUAL_MAX_MATERIALIZED = 1024;

    /**
     * Hard cap on detached visual proxies materialized in one physics space.
     */
    public static final int MAX_DETACHED_VISUAL_MAX_MATERIALIZED = 16_384;

    /**
     * Default visual proxy block type. Integrators should override this for their own content.
     */
    @Nonnull
    public static final String DEFAULT_DETACHED_VISUAL_BLOCK_TYPE = "Rock_Stone";

    /**
     * Ticks between refreshing player/synthetic visual interests for detached materialization.
     */
    public static final int DEFAULT_DETACHED_VISUAL_INTEREST_REFRESH_INTERVAL_TICKS = 4;

    /**
     * Ticks between refreshing detached materialization near-query/raycast spawn candidates.
     */
    public static final int DEFAULT_DETACHED_VISUAL_CANDIDATE_REFRESH_INTERVAL_TICKS = 4;

    /**
     * Ticks between checking existing generated proxies for dematerialization.
     */
    public static final int DEFAULT_DETACHED_VISUAL_VISIBILITY_CHECK_INTERVAL_TICKS = 10;

    /**
     * Hard tick cap for detached visual materialization cache intervals.
     */
    public static final int MAX_DETACHED_VISUAL_CACHE_INTERVAL_TICKS = 1_200;

    /**
     * If enabled, detached physics bodies create disposable visual followers near players.
     */
    private boolean detachedVisualMaterializationEnabled =
        DEFAULT_DETACHED_VISUAL_MATERIALIZATION_ENABLED;

    /**
     * Radius where detached bodies become visual followers.
     */
    private int detachedVisualMaterializationRadius =
        DEFAULT_DETACHED_VISUAL_MATERIALIZATION_RADIUS;

    /**
     * Radius where detached visual followers are removed again.
     */
    private int detachedVisualDematerializationRadius =
        DEFAULT_DETACHED_VISUAL_DEMATERIALIZATION_RADIUS;

    /**
     * Per-tick cap for spawning detached visual followers.
     */
    private int detachedVisualMaxSpawnsPerTick =
        DEFAULT_DETACHED_VISUAL_MAX_SPAWNS_PER_TICK;

    /**
     * Total cap for detached visual followers in this space.
     */
    private int detachedVisualMaxMaterialized =
        DEFAULT_DETACHED_VISUAL_MAX_MATERIALIZED;

    /**
     * Refresh cadence for player/synthetic interests used by detached visual materialization.
     */
    private int detachedVisualInterestRefreshIntervalTicks =
        DEFAULT_DETACHED_VISUAL_INTEREST_REFRESH_INTERVAL_TICKS;

    /**
     * Refresh cadence for detached visual materialization near-query/raycast candidates.
     */
    private int detachedVisualCandidateRefreshIntervalTicks =
        DEFAULT_DETACHED_VISUAL_CANDIDATE_REFRESH_INTERVAL_TICKS;

    /**
     * Refresh cadence for existing generated-proxy visibility/dematerialization checks.
     */
    private int detachedVisualVisibilityCheckIntervalTicks =
        DEFAULT_DETACHED_VISUAL_VISIBILITY_CHECK_INTERVAL_TICKS;

    /**
     * Hytale block type used for default detached visual proxies.
     *
     * FIXME: this is temporary we cannot assume a specific blocktype since a physics body could be
     * composed by any general mix of blocks and entities
     */
    @Nonnull
    private String detachedVisualBlockType = DEFAULT_DETACHED_VISUAL_BLOCK_TYPE;

    public PhysicsVisualMaterializationSettings() {
    }

    public PhysicsVisualMaterializationSettings(
        @Nonnull PhysicsVisualMaterializationSettings settings) {
        detachedVisualMaterializationEnabled = settings.detachedVisualMaterializationEnabled;
        detachedVisualMaterializationRadius = settings.detachedVisualMaterializationRadius;
        detachedVisualDematerializationRadius = settings.detachedVisualDematerializationRadius;
        detachedVisualMaxSpawnsPerTick = settings.detachedVisualMaxSpawnsPerTick;
        detachedVisualMaxMaterialized = settings.detachedVisualMaxMaterialized;
        detachedVisualInterestRefreshIntervalTicks =
            settings.detachedVisualInterestRefreshIntervalTicks;
        detachedVisualCandidateRefreshIntervalTicks =
            settings.detachedVisualCandidateRefreshIntervalTicks;
        detachedVisualVisibilityCheckIntervalTicks =
            settings.detachedVisualVisibilityCheckIntervalTicks;
        detachedVisualBlockType = settings.detachedVisualBlockType;
    }

    public boolean isDetachedVisualMaterializationEnabled() {
        return detachedVisualMaterializationEnabled;
    }

    public void setDetachedVisualMaterializationEnabled(
        boolean detachedVisualMaterializationEnabled) {
        this.detachedVisualMaterializationEnabled = detachedVisualMaterializationEnabled;
    }

    public int getDetachedVisualMaterializationRadius() {
        return detachedVisualMaterializationRadius;
    }

    public void setDetachedVisualMaterializationRadius(
        int detachedVisualMaterializationRadius) {
        int boundedDetachedVisualMaterializationRadius =
            PhysicsSettingsValidation.requirePositiveAtMost(
                "Detached visual materialization radius",
                detachedVisualMaterializationRadius,
                MAX_DETACHED_VISUAL_MATERIALIZATION_RADIUS);
        if (boundedDetachedVisualMaterializationRadius > detachedVisualDematerializationRadius) {
            throw new IllegalArgumentException(
                "Detached visual materialization radius cannot exceed dematerialization radius");
        }
        this.detachedVisualMaterializationRadius = boundedDetachedVisualMaterializationRadius;
    }

    public int getDetachedVisualDematerializationRadius() {
        return detachedVisualDematerializationRadius;
    }

    public void setDetachedVisualDematerializationRadius(
        int detachedVisualDematerializationRadius) {
        int boundedDetachedVisualDematerializationRadius =
            PhysicsSettingsValidation.requirePositiveAtMost(
                "Detached visual dematerialization radius",
                detachedVisualDematerializationRadius,
                MAX_DETACHED_VISUAL_DEMATERIALIZATION_RADIUS);
        if (boundedDetachedVisualDematerializationRadius < detachedVisualMaterializationRadius) {
            throw new IllegalArgumentException(
                "Detached visual dematerialization radius cannot be lower than materialization radius");
        }
        this.detachedVisualDematerializationRadius = boundedDetachedVisualDematerializationRadius;
    }

    public void setDetachedVisualRadii(int detachedVisualMaterializationRadius,
        int detachedVisualDematerializationRadius) {
        int boundedDetachedVisualMaterializationRadius =
            PhysicsSettingsValidation.requirePositiveAtMost(
                "Detached visual materialization radius",
                detachedVisualMaterializationRadius,
                MAX_DETACHED_VISUAL_MATERIALIZATION_RADIUS);
        int boundedDetachedVisualDematerializationRadius =
            PhysicsSettingsValidation.requirePositiveAtMost(
                "Detached visual dematerialization radius",
                detachedVisualDematerializationRadius,
                MAX_DETACHED_VISUAL_DEMATERIALIZATION_RADIUS);
        if (boundedDetachedVisualMaterializationRadius
            > boundedDetachedVisualDematerializationRadius) {
            throw new IllegalArgumentException(
                "Detached visual materialization radius cannot exceed dematerialization radius");
        }
        this.detachedVisualMaterializationRadius = boundedDetachedVisualMaterializationRadius;
        this.detachedVisualDematerializationRadius = boundedDetachedVisualDematerializationRadius;
    }

    public int getDetachedVisualMaxSpawnsPerTick() {
        return detachedVisualMaxSpawnsPerTick;
    }

    public void setDetachedVisualMaxSpawnsPerTick(int detachedVisualMaxSpawnsPerTick) {
        this.detachedVisualMaxSpawnsPerTick =
            PhysicsSettingsValidation.requirePositiveAtMost(
                "Detached visual max spawns per tick",
                detachedVisualMaxSpawnsPerTick,
                MAX_DETACHED_VISUAL_MAX_SPAWNS_PER_TICK);
    }

    public int getDetachedVisualMaxMaterialized() {
        return detachedVisualMaxMaterialized;
    }

    public void setDetachedVisualMaxMaterialized(int detachedVisualMaxMaterialized) {
        this.detachedVisualMaxMaterialized =
            PhysicsSettingsValidation.requirePositiveAtMost(
                "Detached visual max materialized",
                detachedVisualMaxMaterialized,
                MAX_DETACHED_VISUAL_MAX_MATERIALIZED);
    }

    public int getDetachedVisualInterestRefreshIntervalTicks() {
        return detachedVisualInterestRefreshIntervalTicks;
    }

    public void setDetachedVisualInterestRefreshIntervalTicks(
        int detachedVisualInterestRefreshIntervalTicks) {
        this.detachedVisualInterestRefreshIntervalTicks =
            PhysicsSettingsValidation.requirePositiveAtMost(
                "Detached visual interest refresh interval",
                detachedVisualInterestRefreshIntervalTicks,
                MAX_DETACHED_VISUAL_CACHE_INTERVAL_TICKS);
    }

    public int getDetachedVisualCandidateRefreshIntervalTicks() {
        return detachedVisualCandidateRefreshIntervalTicks;
    }

    public void setDetachedVisualCandidateRefreshIntervalTicks(
        int detachedVisualCandidateRefreshIntervalTicks) {
        this.detachedVisualCandidateRefreshIntervalTicks =
            PhysicsSettingsValidation.requirePositiveAtMost(
                "Detached visual candidate refresh interval",
                detachedVisualCandidateRefreshIntervalTicks,
                MAX_DETACHED_VISUAL_CACHE_INTERVAL_TICKS);
    }

    public int getDetachedVisualVisibilityCheckIntervalTicks() {
        return detachedVisualVisibilityCheckIntervalTicks;
    }

    public void setDetachedVisualVisibilityCheckIntervalTicks(
        int detachedVisualVisibilityCheckIntervalTicks) {
        this.detachedVisualVisibilityCheckIntervalTicks =
            PhysicsSettingsValidation.requirePositiveAtMost(
                "Detached visual visibility check interval",
                detachedVisualVisibilityCheckIntervalTicks,
                MAX_DETACHED_VISUAL_CACHE_INTERVAL_TICKS);
    }

    @Nonnull
    public String getDetachedVisualBlockType() {
        return detachedVisualBlockType;
    }

    public void setDetachedVisualBlockType(@Nonnull String detachedVisualBlockType) {
        if (detachedVisualBlockType.isBlank()) {
            throw new IllegalArgumentException("Detached visual block type cannot be blank");
        }
        this.detachedVisualBlockType = detachedVisualBlockType;
    }
}
