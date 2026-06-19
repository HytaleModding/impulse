package dev.hytalemodding.impulse.core.plugin.modules.physicschunk.settings;

import dev.hytalemodding.impulse.api.PhysicsCollisionFilters;
import dev.hytalemodding.impulse.core.plugin.modules.physicschunk.PhysicsChunkTerrainMode;
import dev.hytalemodding.impulse.core.plugin.settings.EntityChunkBoundaryMode;
import java.util.Objects;
import javax.annotation.Nonnull;

/**
 * Terrain collider streaming settings for a PhysicsStore space.
 */
public class PhysicsChunkTerrainSettings {

    /**
     * Block radius around each tracked player for streaming chunk collision bodies.
     */
    public static final int DEFAULT_TERRAIN_RADIUS = 8;

    /**
     * Hard block-radius cap for player-centered PhysicsChunk terrain streaming.
     */
    public static final int MAX_TERRAIN_RADIUS = 128;

    /**
     * Block radius around each active dynamic physics body for streaming chunk collision bodies.
     */
    public static final int DEFAULT_BODY_TERRAIN_RADIUS = 4;

    /**
     * Hard block-radius cap for dynamic-body PhysicsChunk terrain streaming.
     */
    public static final int MAX_BODY_TERRAIN_RADIUS = 64;

    /**
     * Ticks before an unused section's chunk collision bodies are pruned.
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
     * Whether full-cube world sections should use native backend voxel collision when available.
     */
    public static final boolean DEFAULT_NATIVE_VOXEL_COLLISION_ENABLED = false;

    /**
     * Default friction applied to generated chunk collision bodies.
     */
    public static final float DEFAULT_CHUNK_COLLISION_FRICTION = 0.75f;

    /**
     * Default restitution applied to generated chunk collision bodies.
     */
    public static final float DEFAULT_CHUNK_COLLISION_RESTITUTION = 0.0f;

    /**
     * Default collision group applied to generated chunk collision bodies.
     */
    public static final int DEFAULT_CHUNK_COLLISION_GROUP = PhysicsCollisionFilters.TERRAIN;

    /**
     * Default collision mask applied to generated chunk collision bodies.
     */
    public static final int DEFAULT_CHUNK_COLLISION_MASK = PhysicsCollisionFilters.ALL;

    @Nonnull
    private PhysicsChunkTerrainMode terrainMode = PhysicsChunkTerrainMode.NONE;
    @Nonnull
    private EntityChunkBoundaryMode entityChunkBoundaryMode = DEFAULT_ENTITY_CHUNK_BOUNDARY_MODE;
    private boolean nativeVoxelCollisionEnabled = DEFAULT_NATIVE_VOXEL_COLLISION_ENABLED;
    private int terrainRadius = DEFAULT_TERRAIN_RADIUS;
    private int bodyTerrainRadius = DEFAULT_BODY_TERRAIN_RADIUS;
    private int terrainTtlTicks = DEFAULT_TERRAIN_TTL_TICKS;
    private float chunkCollisionFriction = DEFAULT_CHUNK_COLLISION_FRICTION;
    private float chunkCollisionRestitution = DEFAULT_CHUNK_COLLISION_RESTITUTION;
    private int chunkCollisionGroup = DEFAULT_CHUNK_COLLISION_GROUP;
    private int chunkCollisionMask = DEFAULT_CHUNK_COLLISION_MASK;

    public PhysicsChunkTerrainSettings() {
    }

    public PhysicsChunkTerrainSettings(@Nonnull PhysicsChunkTerrainSettings settings) {
        terrainMode = settings.terrainMode;
        entityChunkBoundaryMode = settings.entityChunkBoundaryMode;
        nativeVoxelCollisionEnabled = settings.nativeVoxelCollisionEnabled;
        terrainRadius = settings.terrainRadius;
        bodyTerrainRadius = settings.bodyTerrainRadius;
        terrainTtlTicks = settings.terrainTtlTicks;
        chunkCollisionFriction = settings.chunkCollisionFriction;
        chunkCollisionRestitution = settings.chunkCollisionRestitution;
        chunkCollisionGroup = settings.chunkCollisionGroup;
        chunkCollisionMask = settings.chunkCollisionMask;
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

    public boolean isNativeVoxelCollisionEnabled() {
        return nativeVoxelCollisionEnabled;
    }

    public void setNativeVoxelCollisionEnabled(boolean nativeVoxelCollisionEnabled) {
        this.nativeVoxelCollisionEnabled = nativeVoxelCollisionEnabled;
    }

    public int getTerrainRadius() {
        return terrainRadius;
    }

    public void setTerrainRadius(int terrainRadius) {
        this.terrainRadius = PhysicsChunkSettingsValidation.requirePositiveAtMost(
            "PhysicsChunk terrain radius",
            terrainRadius,
            MAX_TERRAIN_RADIUS);
    }

    public int getBodyTerrainRadius() {
        return bodyTerrainRadius;
    }

    public void setBodyTerrainRadius(int bodyTerrainRadius) {
        this.bodyTerrainRadius = PhysicsChunkSettingsValidation.requirePositiveAtMost(
            "PhysicsChunk terrain body radius",
            bodyTerrainRadius,
            MAX_BODY_TERRAIN_RADIUS);
    }

    public int getTerrainTtlTicks() {
        return terrainTtlTicks;
    }

    public void setTerrainTtlTicks(int terrainTtlTicks) {
        this.terrainTtlTicks = PhysicsChunkSettingsValidation.requirePositiveAtMost(
            "PhysicsChunk terrain TTL",
            terrainTtlTicks,
            MAX_TERRAIN_TTL_TICKS);
    }

    public float getChunkCollisionFriction() {
        return chunkCollisionFriction;
    }

    public void setChunkCollisionFriction(float chunkCollisionFriction) {
        this.chunkCollisionFriction = PhysicsChunkSettingsValidation.requireFiniteAtLeast(
            "Chunk collision friction",
            chunkCollisionFriction,
            0.0f);
    }

    public float getChunkCollisionRestitution() {
        return chunkCollisionRestitution;
    }

    public void setChunkCollisionRestitution(float chunkCollisionRestitution) {
        this.chunkCollisionRestitution = PhysicsChunkSettingsValidation.requireFiniteAtLeast(
            "Chunk collision restitution",
            chunkCollisionRestitution,
            0.0f);
    }

    public void setChunkCollisionMaterial(float friction, float restitution) {
        float validatedFriction = PhysicsChunkSettingsValidation.requireFiniteAtLeast(
            "Chunk collision friction",
            friction,
            0.0f);
        float validatedRestitution = PhysicsChunkSettingsValidation.requireFiniteAtLeast(
            "Chunk collision restitution",
            restitution,
            0.0f);
        this.chunkCollisionFriction = validatedFriction;
        this.chunkCollisionRestitution = validatedRestitution;
    }

    public int getChunkCollisionGroup() {
        return chunkCollisionGroup;
    }

    public int getChunkCollisionMask() {
        return chunkCollisionMask;
    }

    public void setChunkCollisionFilter(int collisionGroup, int collisionMask) {
        this.chunkCollisionGroup = collisionGroup;
        this.chunkCollisionMask = collisionMask;
    }

}
