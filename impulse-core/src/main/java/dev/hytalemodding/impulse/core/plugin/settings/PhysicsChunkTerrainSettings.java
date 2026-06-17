package dev.hytalemodding.impulse.core.plugin.settings;

import dev.hytalemodding.impulse.core.plugin.modules.physicschunk.PhysicsChunkTerrainMode;
import dev.hytalemodding.impulse.core.plugin.modules.physicschunk.WorldCollisionMode;
import java.util.Objects;
import javax.annotation.Nonnull;

/**
 * Terrain collider streaming settings for a PhysicsStore space.
 */
public class PhysicsChunkTerrainSettings {

    /**
     * Block radius around each tracked player for streaming terrain colliders.
     */
    public static final int DEFAULT_TERRAIN_RADIUS = 8;

    /**
     * Hard block-radius cap for player-centered PhysicsChunk terrain streaming.
     */
    public static final int MAX_TERRAIN_RADIUS = 128;

    /**
     * Block radius around each active dynamic physics body for streaming terrain colliders.
     */
    public static final int DEFAULT_BODY_TERRAIN_RADIUS = 4;

    /**
     * Hard block-radius cap for dynamic-body PhysicsChunk terrain streaming.
     */
    public static final int MAX_BODY_TERRAIN_RADIUS = 64;

    /**
     * Ticks before an unused section's terrain colliders are pruned.
     */
    public static final int DEFAULT_TERRAIN_TTL_TICKS = 100;

    /**
     * Hard tick cap for retaining unused streamed terrain sections.
     */
    public static final int MAX_TERRAIN_TTL_TICKS = 12_000;

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
     * Default friction applied to generated terrain collider bodies.
     */
    public static final float DEFAULT_TERRAIN_FRICTION = 0.75f;

    /**
     * Default restitution applied to generated terrain collider bodies.
     */
    public static final float DEFAULT_TERRAIN_RESTITUTION = 0.0f;

    /**
     * @deprecated Use {@link #DEFAULT_TERRAIN_RADIUS}.
     */
    @Deprecated(forRemoval = false)
    public static final int DEFAULT_WORLD_COLLISION_RADIUS = DEFAULT_TERRAIN_RADIUS;

    /**
     * @deprecated Use {@link #MAX_TERRAIN_RADIUS}.
     */
    @Deprecated(forRemoval = false)
    public static final int MAX_WORLD_COLLISION_RADIUS = MAX_TERRAIN_RADIUS;

    /**
     * @deprecated Use {@link #DEFAULT_BODY_TERRAIN_RADIUS}.
     */
    @Deprecated(forRemoval = false)
    public static final int DEFAULT_WORLD_COLLISION_BODY_RADIUS = DEFAULT_BODY_TERRAIN_RADIUS;

    /**
     * @deprecated Use {@link #MAX_BODY_TERRAIN_RADIUS}.
     */
    @Deprecated(forRemoval = false)
    public static final int MAX_WORLD_COLLISION_BODY_RADIUS = MAX_BODY_TERRAIN_RADIUS;

    /**
     * @deprecated Use {@link #DEFAULT_TERRAIN_TTL_TICKS}.
     */
    @Deprecated(forRemoval = false)
    public static final int DEFAULT_WORLD_COLLISION_TTL_TICKS = DEFAULT_TERRAIN_TTL_TICKS;

    /**
     * @deprecated Use {@link #MAX_TERRAIN_TTL_TICKS}.
     */
    @Deprecated(forRemoval = false)
    public static final int MAX_WORLD_COLLISION_TTL_TICKS = MAX_TERRAIN_TTL_TICKS;

    @Nonnull
    private PhysicsChunkTerrainMode terrainMode = PhysicsChunkTerrainMode.NONE;
    @Nonnull
    private EntityChunkBoundaryMode entityChunkBoundaryMode = DEFAULT_ENTITY_CHUNK_BOUNDARY_MODE;
    private boolean nativeVoxelTerrainEnabled = DEFAULT_NATIVE_VOXEL_TERRAIN_ENABLED;
    private int terrainRadius = DEFAULT_TERRAIN_RADIUS;
    private int bodyTerrainRadius = DEFAULT_BODY_TERRAIN_RADIUS;
    private int terrainTtlTicks = DEFAULT_TERRAIN_TTL_TICKS;
    private float terrainFriction = DEFAULT_TERRAIN_FRICTION;
    private float terrainRestitution = DEFAULT_TERRAIN_RESTITUTION;

    public PhysicsChunkTerrainSettings() {
    }

    public PhysicsChunkTerrainSettings(@Nonnull PhysicsChunkTerrainSettings settings) {
        terrainMode = settings.terrainMode;
        entityChunkBoundaryMode = settings.entityChunkBoundaryMode;
        nativeVoxelTerrainEnabled = settings.nativeVoxelTerrainEnabled;
        terrainRadius = settings.terrainRadius;
        bodyTerrainRadius = settings.bodyTerrainRadius;
        terrainTtlTicks = settings.terrainTtlTicks;
        terrainFriction = settings.terrainFriction;
        terrainRestitution = settings.terrainRestitution;
    }

    @Nonnull
    public PhysicsChunkTerrainMode getTerrainMode() {
        return terrainMode;
    }

    public void setTerrainMode(@Nonnull PhysicsChunkTerrainMode terrainMode) {
        this.terrainMode = Objects.requireNonNull(terrainMode, "terrainMode");
    }

    @Nonnull
    public EntityChunkBoundaryMode getEntityChunkBoundaryMode() {
        return entityChunkBoundaryMode;
    }

    public void setEntityChunkBoundaryMode(
        @Nonnull EntityChunkBoundaryMode entityChunkBoundaryMode) {
        this.entityChunkBoundaryMode = Objects.requireNonNull(entityChunkBoundaryMode,
            "entityChunkBoundaryMode");
    }

    public boolean isNativeVoxelTerrainEnabled() {
        return nativeVoxelTerrainEnabled;
    }

    public void setNativeVoxelTerrainEnabled(boolean nativeVoxelTerrainEnabled) {
        this.nativeVoxelTerrainEnabled = nativeVoxelTerrainEnabled;
    }

    public int getTerrainRadius() {
        return terrainRadius;
    }

    public void setTerrainRadius(int terrainRadius) {
        this.terrainRadius = PhysicsSettingsValidation.requirePositiveAtMost(
            "PhysicsChunk terrain radius",
            terrainRadius,
            MAX_TERRAIN_RADIUS);
    }

    public int getBodyTerrainRadius() {
        return bodyTerrainRadius;
    }

    public void setBodyTerrainRadius(int bodyTerrainRadius) {
        this.bodyTerrainRadius = PhysicsSettingsValidation.requirePositiveAtMost(
            "PhysicsChunk terrain body radius",
            bodyTerrainRadius,
            MAX_BODY_TERRAIN_RADIUS);
    }

    public int getTerrainTtlTicks() {
        return terrainTtlTicks;
    }

    public void setTerrainTtlTicks(int terrainTtlTicks) {
        this.terrainTtlTicks = PhysicsSettingsValidation.requirePositiveAtMost(
            "PhysicsChunk terrain TTL",
            terrainTtlTicks,
            MAX_TERRAIN_TTL_TICKS);
    }

    public float getTerrainFriction() {
        return terrainFriction;
    }

    public void setTerrainFriction(float terrainFriction) {
        this.terrainFriction = PhysicsSettingsValidation.requireFiniteAtLeast(
            "Terrain friction",
            terrainFriction,
            0.0f);
    }

    public float getTerrainRestitution() {
        return terrainRestitution;
    }

    public void setTerrainRestitution(float terrainRestitution) {
        this.terrainRestitution = PhysicsSettingsValidation.requireFiniteAtLeast(
            "Terrain restitution",
            terrainRestitution,
            0.0f);
    }

    public void setTerrainMaterial(float terrainFriction, float terrainRestitution) {
        float validatedFriction = PhysicsSettingsValidation.requireFiniteAtLeast(
            "Terrain friction",
            terrainFriction,
            0.0f);
        float validatedRestitution = PhysicsSettingsValidation.requireFiniteAtLeast(
            "Terrain restitution",
            terrainRestitution,
            0.0f);
        this.terrainFriction = validatedFriction;
        this.terrainRestitution = validatedRestitution;
    }

    /**
     * @deprecated Use {@link #getTerrainMode()}.
     */
    @Deprecated(forRemoval = false)
    @Nonnull
    public WorldCollisionMode getWorldCollisionMode() {
        return terrainMode.toWorldCollisionMode();
    }

    /**
     * @deprecated Use {@link #setTerrainMode(PhysicsChunkTerrainMode)}.
     */
    @Deprecated(forRemoval = false)
    public void setWorldCollisionMode(@Nonnull WorldCollisionMode worldCollisionMode) {
        setTerrainMode(worldCollisionMode.toPhysicsChunkTerrainMode());
    }

    /**
     * @deprecated Use {@link #getTerrainRadius()}.
     */
    @Deprecated(forRemoval = false)
    public int getWorldCollisionRadius() {
        return getTerrainRadius();
    }

    /**
     * @deprecated Use {@link #setTerrainRadius(int)}.
     */
    @Deprecated(forRemoval = false)
    public void setWorldCollisionRadius(int worldCollisionRadius) {
        setTerrainRadius(worldCollisionRadius);
    }

    /**
     * @deprecated Use {@link #getBodyTerrainRadius()}.
     */
    @Deprecated(forRemoval = false)
    public int getWorldCollisionBodyRadius() {
        return getBodyTerrainRadius();
    }

    /**
     * @deprecated Use {@link #setBodyTerrainRadius(int)}.
     */
    @Deprecated(forRemoval = false)
    public void setWorldCollisionBodyRadius(int worldCollisionBodyRadius) {
        setBodyTerrainRadius(worldCollisionBodyRadius);
    }

    /**
     * @deprecated Use {@link #getTerrainTtlTicks()}.
     */
    @Deprecated(forRemoval = false)
    public int getWorldCollisionTtlTicks() {
        return getTerrainTtlTicks();
    }

    /**
     * @deprecated Use {@link #setTerrainTtlTicks(int)}.
     */
    @Deprecated(forRemoval = false)
    public void setWorldCollisionTtlTicks(int worldCollisionTtlTicks) {
        setTerrainTtlTicks(worldCollisionTtlTicks);
    }
}
