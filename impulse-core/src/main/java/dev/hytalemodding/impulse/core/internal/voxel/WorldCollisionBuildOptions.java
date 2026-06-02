package dev.hytalemodding.impulse.core.internal.voxel;

import dev.hytalemodding.impulse.core.plugin.settings.PhysicsWorldCollisionSettings;
import javax.annotation.Nonnull;

/**
 * Options that control generated world-collision backend geometry.
 */
public record WorldCollisionBuildOptions(@Nonnull TerrainColliderMode terrainColliderMode) {

    public static final WorldCollisionBuildOptions DEFAULT =
        fromNativeVoxelTerrainEnabled(PhysicsWorldCollisionSettings.DEFAULT_NATIVE_VOXEL_TERRAIN_ENABLED);

    public WorldCollisionBuildOptions {
        if (terrainColliderMode == null) {
            throw new IllegalArgumentException("Terrain collider mode cannot be null");
        }
    }

    @Nonnull
    public static WorldCollisionBuildOptions fromSettings(@Nonnull PhysicsWorldCollisionSettings settings) {
        return fromNativeVoxelTerrainEnabled(settings.isNativeVoxelTerrainEnabled());
    }

    @Nonnull
    public static WorldCollisionBuildOptions fromNativeVoxelTerrainEnabled(boolean enabled) {
        return new WorldCollisionBuildOptions(TerrainColliderMode.fromNativeVoxelTerrainEnabled(enabled));
    }

    public boolean nativeVoxelTerrainEnabled() {
        return terrainColliderMode.nativeVoxelTerrainEnabled();
    }
}
