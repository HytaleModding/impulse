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

    public PhysicsSpaceSettings() {
    }

    public PhysicsSpaceSettings(@Nonnull PhysicsSpaceSettings settings) {
        worldCollisionMode = settings.worldCollisionMode;
        entityChunkBoundaryMode = settings.entityChunkBoundaryMode;
        worldCollisionRadius = settings.worldCollisionRadius;
        worldCollisionBodyRadius = settings.worldCollisionBodyRadius;
        worldCollisionTtlTicks = settings.worldCollisionTtlTicks;
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
}
