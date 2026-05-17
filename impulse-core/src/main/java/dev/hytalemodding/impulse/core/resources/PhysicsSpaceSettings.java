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
     * Block radius around each active dynamic physics body for streaming collision.
     * Smaller than the player radius because bodies should not pull collision
     * as far as players, but still need terrain to land on.
     */
    public static final int DEFAULT_WORLD_COLLISION_BODY_RADIUS = 4;

    /**
     * Ticks before an unused section's collision bodies are pruned.
     */
    public static final int DEFAULT_WORLD_COLLISION_TTL_TICKS = 100;

    /**
     * Block radius around players where visual followers receive the full sync policy.
     */
    public static final int DEFAULT_VISUAL_FULL_SYNC_RADIUS = 64;

    /**
     * Maximum block radius around players where visual followers receive any sync at all.
     * Followers beyond this range stop writing Hytale transforms until they come back into
     * interest.
     */
    public static final int DEFAULT_VISUAL_MAX_SYNC_RADIUS = 128;

    /**
     * Whether visuals beyond {@link #DEFAULT_VISUAL_MAX_SYNC_RADIUS} stop receiving transform sync.
     */
    public static final boolean DEFAULT_VISUAL_FAR_SYNC_CUTOFF_ENABLED = true;

    /**
     * Minimum ticks between mid-range visual sync writes. A value of 1 means every tick is allowed.
     */
    public static final int DEFAULT_VISUAL_MID_SYNC_INTERVAL_TICKS = 1;

    /**
     * Minimum ticks between far-range visual sync writes when far cutoff is disabled.
     */
    public static final int DEFAULT_VISUAL_FAR_SYNC_INTERVAL_TICKS = 40;

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
     * Ticks a visual occlusion raycast result can be reused.
     */
    public static final int DEFAULT_VISUAL_OCCLUSION_CACHE_TICKS = 10;

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
     * Larger radius used to avoid rapid visual proxy despawn/respawn at the edge.
     */
    public static final int DEFAULT_DETACHED_VISUAL_DEMATERIALIZATION_RADIUS = 80;

    /**
     * Maximum detached visual proxies spawned per world tick.
     */
    public static final int DEFAULT_DETACHED_VISUAL_MAX_SPAWNS_PER_TICK = 128;

    /**
     * Maximum detached visual proxies allowed in one physics space at once.
     */
    public static final int DEFAULT_DETACHED_VISUAL_MAX_MATERIALIZED = 1024;

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
        if (worldCollisionRadius < 1) {
            throw new IllegalArgumentException("World collision radius must be positive");
        }
        this.worldCollisionRadius = worldCollisionRadius;
    }

    public void setWorldCollisionBodyRadius(int worldCollisionBodyRadius) {
        if (worldCollisionBodyRadius < 1) {
            throw new IllegalArgumentException("World collision body radius must be positive");
        }
        this.worldCollisionBodyRadius = worldCollisionBodyRadius;
    }

    public void setWorldCollisionTtlTicks(int worldCollisionTtlTicks) {
        if (worldCollisionTtlTicks < 1) {
            throw new IllegalArgumentException("World collision TTL must be positive");
        }
        this.worldCollisionTtlTicks = worldCollisionTtlTicks;
    }

    public void setVisualFullSyncRadius(int visualFullSyncRadius) {
        if (visualFullSyncRadius < 1) {
            throw new IllegalArgumentException("Visual full sync radius must be positive");
        }
        if (visualFullSyncRadius > visualMaxSyncRadius) {
            throw new IllegalArgumentException(
                "Visual full sync radius cannot exceed visual max sync radius");
        }
        this.visualFullSyncRadius = visualFullSyncRadius;
    }

    public void setVisualMaxSyncRadius(int visualMaxSyncRadius) {
        if (visualMaxSyncRadius < 1) {
            throw new IllegalArgumentException("Visual max sync radius must be positive");
        }
        if (visualMaxSyncRadius < visualFullSyncRadius) {
            throw new IllegalArgumentException(
                "Visual max sync radius cannot be lower than visual full sync radius");
        }
        this.visualMaxSyncRadius = visualMaxSyncRadius;
    }

    public void setVisualMidSyncIntervalTicks(int visualMidSyncIntervalTicks) {
        if (visualMidSyncIntervalTicks < 1) {
            throw new IllegalArgumentException("Visual mid sync interval must be positive");
        }
        this.visualMidSyncIntervalTicks = visualMidSyncIntervalTicks;
    }

    public void setVisualFarSyncIntervalTicks(int visualFarSyncIntervalTicks) {
        if (visualFarSyncIntervalTicks < 1) {
            throw new IllegalArgumentException("Visual far sync interval must be positive");
        }
        this.visualFarSyncIntervalTicks = visualFarSyncIntervalTicks;
    }

    public void setVisualOcclusionRaycastsPerTick(int visualOcclusionRaycastsPerTick) {
        if (visualOcclusionRaycastsPerTick < 1) {
            throw new IllegalArgumentException("Visual occlusion raycasts per tick must be positive");
        }
        this.visualOcclusionRaycastsPerTick = visualOcclusionRaycastsPerTick;
    }

    public void setVisualOcclusionCacheTicks(int visualOcclusionCacheTicks) {
        if (visualOcclusionCacheTicks < 1) {
            throw new IllegalArgumentException("Visual occlusion cache ticks must be positive");
        }
        this.visualOcclusionCacheTicks = visualOcclusionCacheTicks;
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
        if (detachedVisualMaterializationRadius < 1) {
            throw new IllegalArgumentException("Detached visual materialization radius must be positive");
        }
        if (detachedVisualMaterializationRadius > detachedVisualDematerializationRadius) {
            throw new IllegalArgumentException(
                "Detached visual materialization radius cannot exceed dematerialization radius");
        }
        this.detachedVisualMaterializationRadius = detachedVisualMaterializationRadius;
    }

    public void setDetachedVisualDematerializationRadius(int detachedVisualDematerializationRadius) {
        if (detachedVisualDematerializationRadius < 1) {
            throw new IllegalArgumentException("Detached visual dematerialization radius must be positive");
        }
        if (detachedVisualDematerializationRadius < detachedVisualMaterializationRadius) {
            throw new IllegalArgumentException(
                "Detached visual dematerialization radius cannot be lower than materialization radius");
        }
        this.detachedVisualDematerializationRadius = detachedVisualDematerializationRadius;
    }

    public void setDetachedVisualMaxSpawnsPerTick(int detachedVisualMaxSpawnsPerTick) {
        if (detachedVisualMaxSpawnsPerTick < 1) {
            throw new IllegalArgumentException("Detached visual max spawns per tick must be positive");
        }
        this.detachedVisualMaxSpawnsPerTick = detachedVisualMaxSpawnsPerTick;
    }

    public void setDetachedVisualMaxMaterialized(int detachedVisualMaxMaterialized) {
        if (detachedVisualMaxMaterialized < 1) {
            throw new IllegalArgumentException("Detached visual max materialized must be positive");
        }
        this.detachedVisualMaxMaterialized = detachedVisualMaxMaterialized;
    }

    public void setDetachedVisualBlockType(@Nonnull String detachedVisualBlockType) {
        if (detachedVisualBlockType.isBlank()) {
            throw new IllegalArgumentException("Detached visual block type cannot be blank");
        }
        this.detachedVisualBlockType = detachedVisualBlockType;
    }
}
