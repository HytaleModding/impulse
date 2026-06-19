package dev.hytalemodding.impulse.core.plugin.modules.physicschunk.settings;

import dev.hytalemodding.impulse.core.plugin.modules.physicschunk.PhysicsChunkTerrainMode;
import dev.hytalemodding.impulse.core.plugin.settings.EntityChunkBoundaryMode;
import lombok.Getter;
import lombok.Setter;
import java.util.Objects;
import javax.annotation.Nonnull;

/**
 * Chunk-collision streaming settings for a PhysicsStore space.
 */
public class PhysicsChunkCollisionSettings {

    /**
     * Block radius around each tracked player for streaming chunk collision bodies.
     */
    public static final int DEFAULT_RADIUS = 8;

    /**
     * Hard block-radius cap for player-centered PhysicsChunk collision streaming.
     */
    public static final int MAX_RADIUS = 128;

    /**
     * Block radius around each active dynamic physics body for streaming chunk collision bodies.
     */
    public static final int DEFAULT_BODY_RADIUS = 4;

    /**
     * Hard block-radius cap for dynamic-body PhysicsChunk collision streaming.
     */
    public static final int MAX_BODY_RADIUS = 64;

    /**
     * Ticks before an unused section's chunk collision bodies are pruned.
     */
    public static final int DEFAULT_TTL_TICKS = 100;

    /**
     * Hard tick cap for retaining unused streamed chunk-collision sections.
     */
    public static final int MAX_TTL_TICKS = 12_000;

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
    private PhysicsChunkTerrainMode mode = PhysicsChunkTerrainMode.NONE;
    @Nonnull
    private EntityChunkBoundaryMode entityChunkBoundaryMode = DEFAULT_ENTITY_CHUNK_BOUNDARY_MODE;
    @Setter
    @Getter
    private boolean nativeVoxelCollisionEnabled = DEFAULT_NATIVE_VOXEL_COLLISION_ENABLED;
    @Getter
    private int radius = DEFAULT_RADIUS;
    @Getter
    private int bodyRadius = DEFAULT_BODY_RADIUS;
    @Getter
    private int ttlTicks = DEFAULT_TTL_TICKS;

    public PhysicsChunkCollisionSettings() {
    }

    public PhysicsChunkCollisionSettings(@Nonnull PhysicsChunkCollisionSettings settings) {
        mode = settings.mode;
        entityChunkBoundaryMode = settings.entityChunkBoundaryMode;
        nativeVoxelCollisionEnabled = settings.nativeVoxelCollisionEnabled;
        radius = settings.radius;
        bodyRadius = settings.bodyRadius;
        ttlTicks = settings.ttlTicks;
    }

    @Nonnull
    public PhysicsChunkTerrainMode getMode() {
        return mode;
    }

    public void setMode(@Nonnull PhysicsChunkTerrainMode mode) {
        this.mode = Objects.requireNonNull(mode, "mode");
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

    public void setRadius(int radius) {
        this.radius = PhysicsChunkSettingsValidation.requirePositiveAtMost(
            "PhysicsChunk collision radius",
            radius,
            MAX_RADIUS);
    }

    public void setBodyRadius(int bodyRadius) {
        this.bodyRadius = PhysicsChunkSettingsValidation.requirePositiveAtMost(
            "PhysicsChunk collision body radius",
            bodyRadius,
            MAX_BODY_RADIUS);
    }

    public void setTtlTicks(int ttlTicks) {
        this.ttlTicks = PhysicsChunkSettingsValidation.requirePositiveAtMost(
            "PhysicsChunk collision TTL",
            ttlTicks,
            MAX_TTL_TICKS);
    }

}
