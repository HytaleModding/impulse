package dev.hytalemodding.impulse.core.plugin.settings;

import dev.hytalemodding.impulse.core.plugin.modules.worldcollision.WorldCollisionMode;
import javax.annotation.Nonnull;

/**
 * Terrain collision streaming settings for a physics space.
 */
public class PhysicsWorldCollisionSettings {

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
     * Default behavior when an entity-backed body reaches an unloaded chunk border.
     */
    @Nonnull
    public static final EntityChunkBoundaryMode DEFAULT_ENTITY_CHUNK_BOUNDARY_MODE =
        EntityChunkBoundaryMode.PAUSE_UNTIL_LOADED;

    /**
     * Whether full-cube world sections should use native backend voxel terrain when available.
     */
    public static final boolean DEFAULT_NATIVE_VOXEL_TERRAIN_ENABLED = false;

    /**
     * World collision mode for this space. Defaults to NONE so Impulse
     * is fully opt-in: no terrain collision is created unless explicitly requested.
     */
    @Nonnull
    private WorldCollisionMode worldCollisionMode = WorldCollisionMode.NONE;

    /**
     * How entity-backed bodies behave when they reach an unloaded chunk border.
     */
    @Nonnull
    private EntityChunkBoundaryMode entityChunkBoundaryMode = DEFAULT_ENTITY_CHUNK_BOUNDARY_MODE;

    /**
     * Enables native backend voxel terrain for full-cube world collision.
     */
    private boolean nativeVoxelTerrainEnabled = DEFAULT_NATIVE_VOXEL_TERRAIN_ENABLED;

    /**
     * Block radius around tracked player positions for streaming or manual build.
     */
    private int worldCollisionRadius = DEFAULT_WORLD_COLLISION_RADIUS;

    /**
     * Block radius around active dynamic physics bodies for streaming collision.
     */
    private int worldCollisionBodyRadius = DEFAULT_WORLD_COLLISION_BODY_RADIUS;

    /**
     * How long a section stays loaded after its last use, in server ticks.
     */
    private int worldCollisionTtlTicks = DEFAULT_WORLD_COLLISION_TTL_TICKS;

    public PhysicsWorldCollisionSettings() {
    }

    public PhysicsWorldCollisionSettings(@Nonnull PhysicsWorldCollisionSettings settings) {
        worldCollisionMode = settings.worldCollisionMode;
        entityChunkBoundaryMode = settings.entityChunkBoundaryMode;
        nativeVoxelTerrainEnabled = settings.nativeVoxelTerrainEnabled;
        worldCollisionRadius = settings.worldCollisionRadius;
        worldCollisionBodyRadius = settings.worldCollisionBodyRadius;
        worldCollisionTtlTicks = settings.worldCollisionTtlTicks;
    }

    @Nonnull
    public WorldCollisionMode getWorldCollisionMode() {
        return worldCollisionMode;
    }

    public void setWorldCollisionMode(@Nonnull WorldCollisionMode worldCollisionMode) {
        this.worldCollisionMode = worldCollisionMode;
    }

    @Nonnull
    public EntityChunkBoundaryMode getEntityChunkBoundaryMode() {
        return entityChunkBoundaryMode;
    }

    public void setEntityChunkBoundaryMode(
        @Nonnull EntityChunkBoundaryMode entityChunkBoundaryMode) {
        this.entityChunkBoundaryMode = entityChunkBoundaryMode;
    }

    public boolean isNativeVoxelTerrainEnabled() {
        return nativeVoxelTerrainEnabled;
    }

    public void setNativeVoxelTerrainEnabled(boolean nativeVoxelTerrainEnabled) {
        this.nativeVoxelTerrainEnabled = nativeVoxelTerrainEnabled;
    }

    public int getWorldCollisionRadius() {
        return worldCollisionRadius;
    }

    public void setWorldCollisionRadius(int worldCollisionRadius) {
        this.worldCollisionRadius = PhysicsSettingsValidation.requirePositiveAtMost(
            "World collision radius",
            worldCollisionRadius,
            MAX_WORLD_COLLISION_RADIUS);
    }

    public int getWorldCollisionBodyRadius() {
        return worldCollisionBodyRadius;
    }

    public void setWorldCollisionBodyRadius(int worldCollisionBodyRadius) {
        this.worldCollisionBodyRadius = PhysicsSettingsValidation.requirePositiveAtMost(
            "World collision body radius",
            worldCollisionBodyRadius,
            MAX_WORLD_COLLISION_BODY_RADIUS);
    }

    public int getWorldCollisionTtlTicks() {
        return worldCollisionTtlTicks;
    }

    public void setWorldCollisionTtlTicks(int worldCollisionTtlTicks) {
        this.worldCollisionTtlTicks = PhysicsSettingsValidation.requirePositiveAtMost(
            "World collision TTL",
            worldCollisionTtlTicks,
            MAX_WORLD_COLLISION_TTL_TICKS);
    }
}
