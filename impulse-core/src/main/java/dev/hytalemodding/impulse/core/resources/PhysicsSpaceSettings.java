package dev.hytalemodding.impulse.core.resources;

import dev.hytalemodding.impulse.core.voxel.WorldCollisionMode;
import lombok.Getter;
import lombok.Setter;
import javax.annotation.Nonnull;

/**
 * Per-space configuration that controls optional world collision behavior.
 *
 * <p>Settings are attached to a space at creation time via
 * {@link PhysicsWorldResource#createSpace} and can be changed later with
 * {@link PhysicsWorldResource#setSpaceSettings}.</p>
 *
 * <p>Default settings have world collision disabled ({@link WorldCollisionMode#NONE}),
 * which keeps Impulse fully opt-in: no terrain bodies are created unless the integrator
 * explicitly opts in.</p>
 */
public class PhysicsSpaceSettings {

    /**
     * Block radius around each tracked player for streaming collision.
     */
    public static final int DEFAULT_WORLD_COLLISION_RADIUS = 8;

    /**
     * Hard block-radius cap for player-centered world collision streaming.
     */
    public static final int MAX_WORLD_COLLISION_RADIUS = 128;

    /**
     * Block radius around each active dynamic physics body for streaming collision.
     * Smaller than the player radius because bodies should not pull collision
     * as far as players, but still need terrain to land on.
     */
    public static final int DEFAULT_WORLD_COLLISION_BODY_RADIUS = 4;

    /**
     * Hard block-radius cap for dynamic-body world collision streaming.
     */
    public static final int MAX_WORLD_COLLISION_BODY_RADIUS = 64;

    /**
     * Ticks before an unused section's collision bodies are pruned.
     */
    public static final int DEFAULT_WORLD_COLLISION_TTL_TICKS = 100;

    /**
     * Hard tick cap for retaining unused streamed collision sections.
     */
    public static final int MAX_WORLD_COLLISION_TTL_TICKS = 12_000;

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
     * Rapier default solver iterations. Lower values are faster but less stable.
     */
    public static final int DEFAULT_SOLVER_ITERATIONS = 4;

    /**
     * Rapier default internal PGS iterations.
     */
    public static final int DEFAULT_INTERNAL_PGS_ITERATIONS = 1;

    /**
     * Rapier default stabilization iterations.
     */
    public static final int DEFAULT_STABILIZATION_ITERATIONS = 1;

    /**
     * Rapier default minimum active-island size for parallel solver batching.
     */
    public static final int DEFAULT_MIN_ISLAND_SIZE = 128;

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
     * Default behavior when an entity-backed body reaches an unloaded chunk border.
     */
    @Nonnull
    public static final EntityChunkBoundaryMode DEFAULT_ENTITY_CHUNK_BOUNDARY_MODE =
        EntityChunkBoundaryMode.PAUSE_UNTIL_LOADED;

    /**
     * World collision mode for this space. Defaults to NONE so Impulse
     * is fully opt-in: no terrain collision is created unless explicitly requested.
     */
    @Getter
    @Setter
    @Nonnull
    private WorldCollisionMode worldCollisionMode = WorldCollisionMode.NONE;

    /**
     * How entity-backed bodies behave when they reach an unloaded chunk border.
     */
    @Getter
    @Setter
    @Nonnull
    private EntityChunkBoundaryMode entityChunkBoundaryMode = DEFAULT_ENTITY_CHUNK_BOUNDARY_MODE;

    /**
     * Block radius around tracked player positions for streaming or manual build.
     */
    @Getter
    private int worldCollisionRadius = DEFAULT_WORLD_COLLISION_RADIUS;

    /**
     * Block radius around active dynamic physics bodies for streaming collision.
     */
    @Getter
    private int worldCollisionBodyRadius = DEFAULT_WORLD_COLLISION_BODY_RADIUS;

    /**
     * How long a section stays loaded after its last use, in server ticks.
     */
    @Getter
    private int worldCollisionTtlTicks = DEFAULT_WORLD_COLLISION_TTL_TICKS;

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
    @Getter
    @Setter
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
    @Getter
    @Setter
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
     * Constraint solver iterations for tunable backends.
     */
    @Getter
    private int solverIterations = DEFAULT_SOLVER_ITERATIONS;

    /**
     * Internal PGS iterations per solver iteration for tunable backends.
     */
    @Getter
    private int internalPgsIterations = DEFAULT_INTERNAL_PGS_ITERATIONS;

    /**
     * Stabilization iterations per solver iteration for tunable backends.
     */
    @Getter
    private int stabilizationIterations = DEFAULT_STABILIZATION_ITERATIONS;

    /**
     * Minimum active island size used by tunable parallel solvers.
     */
    @Getter
    private int minIslandSize = DEFAULT_MIN_ISLAND_SIZE;

    /**
     * If enabled, entity-backed physics body transforms use the same player-interest culling
     * as follower visuals. Controlled bodies are always synced.
     */
    @Getter
    @Setter
    private boolean entityVisualSyncCullingEnabled = DEFAULT_ENTITY_VISUAL_SYNC_CULLING_ENABLED;

    /**
     * If enabled, near-range visual sync also requires an approximate player view-cone hit.
     * Keep disabled for custom cameras unless the server view direction matches the camera.
     */
    @Getter
    @Setter
    private boolean visualVisibilityCullingEnabled = DEFAULT_VISUAL_VISIBILITY_CULLING_ENABLED;

    /**
     * If enabled, detached physics bodies create disposable visual followers near players.
     */
    @Getter
    @Setter
    private boolean detachedVisualMaterializationEnabled =
        DEFAULT_DETACHED_VISUAL_MATERIALIZATION_ENABLED;

    /**
     * Radius where detached bodies become visual followers.
     */
    @Getter
    private int detachedVisualMaterializationRadius =
        DEFAULT_DETACHED_VISUAL_MATERIALIZATION_RADIUS;

    /**
     * Radius where detached visual followers are removed again.
     */
    @Getter
    private int detachedVisualDematerializationRadius =
        DEFAULT_DETACHED_VISUAL_DEMATERIALIZATION_RADIUS;

    /**
     * Per-tick cap for spawning detached visual followers.
     */
    @Getter
    private int detachedVisualMaxSpawnsPerTick =
        DEFAULT_DETACHED_VISUAL_MAX_SPAWNS_PER_TICK;

    /**
     * Total cap for detached visual followers in this space.
     */
    @Getter
    private int detachedVisualMaxMaterialized =
        DEFAULT_DETACHED_VISUAL_MAX_MATERIALIZED;

    /**
     * Hytale block type used for default detached visual proxies.
     */
    @Getter(onMethod_ = @__(@Nonnull))
    private String detachedVisualBlockType = DEFAULT_DETACHED_VISUAL_BLOCK_TYPE;

    public PhysicsSpaceSettings() {
    }

    public PhysicsSpaceSettings(@Nonnull PhysicsSpaceSettings settings) {
        worldCollisionMode = settings.worldCollisionMode;
        entityChunkBoundaryMode = settings.entityChunkBoundaryMode;
        worldCollisionRadius = settings.worldCollisionRadius;
        worldCollisionBodyRadius = settings.worldCollisionBodyRadius;
        worldCollisionTtlTicks = settings.worldCollisionTtlTicks;
        visualFullSyncRadius = settings.visualFullSyncRadius;
        visualMaxSyncRadius = settings.visualMaxSyncRadius;
        visualFarSyncCutoffEnabled = settings.visualFarSyncCutoffEnabled;
        visualMidSyncIntervalTicks = settings.visualMidSyncIntervalTicks;
        visualFarSyncIntervalTicks = settings.visualFarSyncIntervalTicks;
        visualOcclusionMode = settings.visualOcclusionMode;
        visualOcclusionRaycastsPerTick = settings.visualOcclusionRaycastsPerTick;
        visualOcclusionCacheTicks = settings.visualOcclusionCacheTicks;
        solverIterations = settings.solverIterations;
        internalPgsIterations = settings.internalPgsIterations;
        stabilizationIterations = settings.stabilizationIterations;
        minIslandSize = settings.minIslandSize;
        entityVisualSyncCullingEnabled = settings.entityVisualSyncCullingEnabled;
        visualVisibilityCullingEnabled = settings.visualVisibilityCullingEnabled;
        detachedVisualMaterializationEnabled = settings.detachedVisualMaterializationEnabled;
        detachedVisualMaterializationRadius = settings.detachedVisualMaterializationRadius;
        detachedVisualDematerializationRadius = settings.detachedVisualDematerializationRadius;
        detachedVisualMaxSpawnsPerTick = settings.detachedVisualMaxSpawnsPerTick;
        detachedVisualMaxMaterialized = settings.detachedVisualMaxMaterialized;
        detachedVisualBlockType = settings.detachedVisualBlockType;
    }

    @Nonnull
    public static PhysicsSpaceSettings defaults() {
        return new PhysicsSpaceSettings();
    }

    /**
     * Convenience factory for a space with streaming world collision enabled.
     */
    @Nonnull
    public static PhysicsSpaceSettings streamingWorldCollision() {
        PhysicsSpaceSettings settings = new PhysicsSpaceSettings();
        settings.setWorldCollisionMode(WorldCollisionMode.STREAMING);
        return settings;
    }

    public void setWorldCollisionRadius(int worldCollisionRadius) {
        this.worldCollisionRadius = requirePositiveAtMost(
            "World collision radius",
            worldCollisionRadius,
            MAX_WORLD_COLLISION_RADIUS);
    }

    public void setWorldCollisionBodyRadius(int worldCollisionBodyRadius) {
        this.worldCollisionBodyRadius = requirePositiveAtMost(
            "World collision body radius",
            worldCollisionBodyRadius,
            MAX_WORLD_COLLISION_BODY_RADIUS);
    }

    public void setWorldCollisionTtlTicks(int worldCollisionTtlTicks) {
        this.worldCollisionTtlTicks = requirePositiveAtMost(
            "World collision TTL",
            worldCollisionTtlTicks,
            MAX_WORLD_COLLISION_TTL_TICKS);
    }

    public void setVisualFullSyncRadius(int visualFullSyncRadius) {
        int boundedVisualFullSyncRadius = requirePositiveAtMost(
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
        int boundedVisualMaxSyncRadius = requirePositiveAtMost(
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
        int boundedVisualFullSyncRadius = requirePositiveAtMost(
            "Visual full sync radius",
            visualFullSyncRadius,
            MAX_VISUAL_FULL_SYNC_RADIUS);
        int boundedVisualMaxSyncRadius = requirePositiveAtMost(
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
        this.visualMidSyncIntervalTicks = requirePositiveAtMost(
            "Visual mid sync interval",
            visualMidSyncIntervalTicks,
            MAX_VISUAL_MID_SYNC_INTERVAL_TICKS);
    }

    public void setVisualFarSyncIntervalTicks(int visualFarSyncIntervalTicks) {
        this.visualFarSyncIntervalTicks = requirePositiveAtMost(
            "Visual far sync interval",
            visualFarSyncIntervalTicks,
            MAX_VISUAL_FAR_SYNC_INTERVAL_TICKS);
    }

    public void setVisualOcclusionRaycastsPerTick(int visualOcclusionRaycastsPerTick) {
        this.visualOcclusionRaycastsPerTick = requirePositiveAtMost(
            "Visual occlusion raycasts per tick",
            visualOcclusionRaycastsPerTick,
            MAX_VISUAL_OCCLUSION_RAYCASTS_PER_TICK);
    }

    public void setVisualOcclusionCacheTicks(int visualOcclusionCacheTicks) {
        this.visualOcclusionCacheTicks = requirePositiveAtMost(
            "Visual occlusion cache ticks",
            visualOcclusionCacheTicks,
            MAX_VISUAL_OCCLUSION_CACHE_TICKS);
    }

    public void setSolverIterations(int solverIterations) {
        if (solverIterations < 1) {
            throw new IllegalArgumentException("Solver iterations must be positive");
        }
        this.solverIterations = solverIterations;
    }

    public void setInternalPgsIterations(int internalPgsIterations) {
        if (internalPgsIterations < 1) {
            throw new IllegalArgumentException("Internal PGS iterations must be positive");
        }
        this.internalPgsIterations = internalPgsIterations;
    }

    public void setStabilizationIterations(int stabilizationIterations) {
        if (stabilizationIterations < 0) {
            throw new IllegalArgumentException("Stabilization iterations cannot be negative");
        }
        this.stabilizationIterations = stabilizationIterations;
    }

    public void setMinIslandSize(int minIslandSize) {
        if (minIslandSize < 1) {
            throw new IllegalArgumentException("Minimum island size must be positive");
        }
        this.minIslandSize = minIslandSize;
    }

    public void setDetachedVisualMaterializationRadius(int detachedVisualMaterializationRadius) {
        int boundedDetachedVisualMaterializationRadius = requirePositiveAtMost(
            "Detached visual materialization radius",
            detachedVisualMaterializationRadius,
            MAX_DETACHED_VISUAL_MATERIALIZATION_RADIUS);
        if (boundedDetachedVisualMaterializationRadius > detachedVisualDematerializationRadius) {
            throw new IllegalArgumentException(
                "Detached visual materialization radius cannot exceed dematerialization radius");
        }
        this.detachedVisualMaterializationRadius = boundedDetachedVisualMaterializationRadius;
    }

    public void setDetachedVisualDematerializationRadius(int detachedVisualDematerializationRadius) {
        int boundedDetachedVisualDematerializationRadius = requirePositiveAtMost(
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
        int boundedDetachedVisualMaterializationRadius = requirePositiveAtMost(
            "Detached visual materialization radius",
            detachedVisualMaterializationRadius,
            MAX_DETACHED_VISUAL_MATERIALIZATION_RADIUS);
        int boundedDetachedVisualDematerializationRadius = requirePositiveAtMost(
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

    public void setDetachedVisualMaxSpawnsPerTick(int detachedVisualMaxSpawnsPerTick) {
        this.detachedVisualMaxSpawnsPerTick = requirePositiveAtMost(
            "Detached visual max spawns per tick",
            detachedVisualMaxSpawnsPerTick,
            MAX_DETACHED_VISUAL_MAX_SPAWNS_PER_TICK);
    }

    public void setDetachedVisualMaxMaterialized(int detachedVisualMaxMaterialized) {
        this.detachedVisualMaxMaterialized = requirePositiveAtMost(
            "Detached visual max materialized",
            detachedVisualMaxMaterialized,
            MAX_DETACHED_VISUAL_MAX_MATERIALIZED);
    }

    public void setDetachedVisualBlockType(@Nonnull String detachedVisualBlockType) {
        if (detachedVisualBlockType.isBlank()) {
            throw new IllegalArgumentException("Detached visual block type cannot be blank");
        }
        this.detachedVisualBlockType = detachedVisualBlockType;
    }

    private static int requirePositiveAtMost(@Nonnull String label, int value, int maxValue) {
        if (value < 1 || value > maxValue) {
            throw new IllegalArgumentException(label + " must be between 1 and " + maxValue);
        }
        return value;
    }
}
