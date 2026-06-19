package dev.hytalemodding.impulse.core.plugin.modules.physicschunk.settings;

import dev.hytalemodding.impulse.core.plugin.modules.physicschunk.PhysicsChunkTerrainMode;
import dev.hytalemodding.impulse.core.plugin.settings.EntityChunkBoundaryMode;
import lombok.Getter;
import lombok.Setter;
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

    @Nonnull
    private PhysicsChunkTerrainMode terrainMode = PhysicsChunkTerrainMode.NONE;
    @Nonnull
    private EntityChunkBoundaryMode entityChunkBoundaryMode = DEFAULT_ENTITY_CHUNK_BOUNDARY_MODE;
    @Setter
    @Getter
    private boolean nativeVoxelCollisionEnabled = DEFAULT_NATIVE_VOXEL_COLLISION_ENABLED;
    @Getter
    private int terrainRadius = DEFAULT_TERRAIN_RADIUS;
    @Getter
    private int bodyTerrainRadius = DEFAULT_BODY_TERRAIN_RADIUS;
    @Getter
    private int terrainTtlTicks = DEFAULT_TERRAIN_TTL_TICKS;

    public PhysicsChunkTerrainSettings() {
    }

    public PhysicsChunkTerrainSettings(@Nonnull PhysicsChunkTerrainSettings settings) {
        terrainMode = settings.terrainMode;
        entityChunkBoundaryMode = settings.entityChunkBoundaryMode;
        nativeVoxelCollisionEnabled = settings.nativeVoxelCollisionEnabled;
        terrainRadius = settings.terrainRadius;
        bodyTerrainRadius = settings.bodyTerrainRadius;
        terrainTtlTicks = settings.terrainTtlTicks;
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

    public void setTerrainRadius(int terrainRadius) {
        this.terrainRadius = PhysicsChunkSettingsValidation.requirePositiveAtMost(
            "PhysicsChunk terrain radius",
            terrainRadius,
            MAX_TERRAIN_RADIUS);
    }

    public void setBodyTerrainRadius(int bodyTerrainRadius) {
        this.bodyTerrainRadius = PhysicsChunkSettingsValidation.requirePositiveAtMost(
            "PhysicsChunk terrain body radius",
            bodyTerrainRadius,
            MAX_BODY_TERRAIN_RADIUS);
    }

    public void setTerrainTtlTicks(int terrainTtlTicks) {
        this.terrainTtlTicks = PhysicsChunkSettingsValidation.requirePositiveAtMost(
            "PhysicsChunk terrain TTL",
            terrainTtlTicks,
            MAX_TERRAIN_TTL_TICKS);
    }

}
