package dev.hytalemodding.impulse.core.internal.modules.worldcollision;

import dev.hytalemodding.impulse.core.plugin.settings.PhysicsWorldCollisionSettings;
import java.util.Objects;
import javax.annotation.Nonnull;

/**
 * Options that control generated world-collision backend geometry.
 */
public record WorldCollisionBuildOptions(@Nonnull TerrainColliderMode terrainColliderMode,
                                         float terrainFriction,
                                         float terrainRestitution) {

    public static final WorldCollisionBuildOptions DEFAULT =
        fromNativeVoxelTerrainEnabled(PhysicsWorldCollisionSettings.DEFAULT_NATIVE_VOXEL_TERRAIN_ENABLED);

    public WorldCollisionBuildOptions {
        Objects.requireNonNull(terrainColliderMode, "terrainColliderMode");
        if (!Float.isFinite(terrainFriction) || terrainFriction < 0.0f) {
            throw new IllegalArgumentException("terrainFriction must be finite and >= 0");
        }
        if (!Float.isFinite(terrainRestitution) || terrainRestitution < 0.0f) {
            throw new IllegalArgumentException("terrainRestitution must be finite and >= 0");
        }
    }

    @Nonnull
    public static WorldCollisionBuildOptions fromSettings(@Nonnull PhysicsWorldCollisionSettings settings) {
        return new WorldCollisionBuildOptions(
            TerrainColliderMode.fromNativeVoxelTerrainEnabled(settings.isNativeVoxelTerrainEnabled()),
            settings.getTerrainFriction(),
            settings.getTerrainRestitution());
    }

    @Nonnull
    public static WorldCollisionBuildOptions fromNativeVoxelTerrainEnabled(boolean enabled) {
        return new WorldCollisionBuildOptions(TerrainColliderMode.fromNativeVoxelTerrainEnabled(enabled),
            PhysicsWorldCollisionSettings.DEFAULT_TERRAIN_FRICTION,
            PhysicsWorldCollisionSettings.DEFAULT_TERRAIN_RESTITUTION);
    }

    public boolean nativeVoxelTerrainEnabled() {
        return terrainColliderMode.nativeVoxelTerrainEnabled();
    }
}
