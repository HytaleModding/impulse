package dev.hytalemodding.impulse.core.plugin.modules.physicschunk.components;

import dev.hytalemodding.impulse.core.plugin.modules.physicschunk.PhysicsChunkTerrainMode;
import dev.hytalemodding.impulse.core.plugin.modules.physicschunk.WorldCollisionMode;
import dev.hytalemodding.impulse.core.plugin.settings.EntityChunkBoundaryMode;
import dev.hytalemodding.impulse.core.plugin.modules.physicschunk.settings.PhysicsChunkTerrainSettings;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsWorldCollisionSettings;
import javax.annotation.Nonnull;

/**
 * @deprecated Use {@link PhysicsChunkTerrainComponent}. The serialized component name remains
 * {@code WorldCollision} for saved-world compatibility.
 */
@Deprecated(forRemoval = false)
public class WorldCollisionComponent extends PhysicsChunkTerrainComponent {

    public WorldCollisionComponent() {
    }

    public WorldCollisionComponent(@Nonnull PhysicsChunkTerrainSettings settings) {
        super(settings);
    }

    public WorldCollisionComponent(@Nonnull PhysicsWorldCollisionSettings settings) {
        super(settings);
    }

    public WorldCollisionComponent(@Nonnull PhysicsChunkTerrainMode terrainMode,
        boolean nativeVoxelTerrainEnabled,
        int radius,
        int bodyRadius,
        int ttlTicks,
        float terrainFriction,
        float terrainRestitution) {
        super(terrainMode,
            nativeVoxelTerrainEnabled,
            radius,
            bodyRadius,
            ttlTicks,
            terrainFriction,
            terrainRestitution);
    }

    public WorldCollisionComponent(@Nonnull PhysicsChunkTerrainMode terrainMode,
        @Nonnull EntityChunkBoundaryMode entityChunkBoundaryMode,
        boolean nativeVoxelTerrainEnabled,
        int radius,
        int bodyRadius,
        int ttlTicks,
        float terrainFriction,
        float terrainRestitution) {
        super(terrainMode,
            entityChunkBoundaryMode,
            nativeVoxelTerrainEnabled,
            radius,
            bodyRadius,
            ttlTicks,
            terrainFriction,
            terrainRestitution);
    }

    public WorldCollisionComponent(@Nonnull WorldCollisionMode mode,
        boolean nativeVoxelTerrainEnabled,
        int radius,
        int bodyRadius,
        int ttlTicks,
        float terrainFriction,
        float terrainRestitution) {
        super(mode,
            nativeVoxelTerrainEnabled,
            radius,
            bodyRadius,
            ttlTicks,
            terrainFriction,
            terrainRestitution);
    }

    public WorldCollisionComponent(@Nonnull WorldCollisionMode mode,
        @Nonnull EntityChunkBoundaryMode entityChunkBoundaryMode,
        boolean nativeVoxelTerrainEnabled,
        int radius,
        int bodyRadius,
        int ttlTicks,
        float terrainFriction,
        float terrainRestitution) {
        super(mode,
            entityChunkBoundaryMode,
            nativeVoxelTerrainEnabled,
            radius,
            bodyRadius,
            ttlTicks,
            terrainFriction,
            terrainRestitution);
    }

    @Nonnull
    @Override
    public WorldCollisionComponent clone() {
        return new WorldCollisionComponent(getTerrainMode(),
            getEntityChunkBoundaryMode(),
            isNativeVoxelTerrainEnabled(),
            getRadius(),
            getBodyRadius(),
            getTtlTicks(),
            getTerrainFriction(),
            getTerrainRestitution());
    }
}
