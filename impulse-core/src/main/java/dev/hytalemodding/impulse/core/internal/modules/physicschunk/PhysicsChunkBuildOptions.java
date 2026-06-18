package dev.hytalemodding.impulse.core.internal.modules.physicschunk;

import dev.hytalemodding.impulse.core.plugin.modules.physicschunk.settings.PhysicsChunkTerrainSettings;
import java.util.Objects;
import javax.annotation.Nonnull;

/**
 * Options that control generated PhysicsChunk terrain backend geometry.
 */
public record PhysicsChunkBuildOptions(@Nonnull TerrainColliderMode terrainColliderMode,
                                         float terrainFriction,
                                         float terrainRestitution) {

    public static final PhysicsChunkBuildOptions DEFAULT =
        fromNativeVoxelTerrainEnabled(PhysicsChunkTerrainSettings.DEFAULT_NATIVE_VOXEL_TERRAIN_ENABLED);

    public PhysicsChunkBuildOptions {
        Objects.requireNonNull(terrainColliderMode, "terrainColliderMode");
        if (!Float.isFinite(terrainFriction) || terrainFriction < 0.0f) {
            throw new IllegalArgumentException("terrainFriction must be finite and >= 0");
        }
        if (!Float.isFinite(terrainRestitution) || terrainRestitution < 0.0f) {
            throw new IllegalArgumentException("terrainRestitution must be finite and >= 0");
        }
    }

    @Nonnull
    public static PhysicsChunkBuildOptions fromSettings(@Nonnull PhysicsChunkTerrainSettings settings) {
        return new PhysicsChunkBuildOptions(
            TerrainColliderMode.fromNativeVoxelTerrainEnabled(settings.isNativeVoxelTerrainEnabled()),
            settings.getTerrainFriction(),
            settings.getTerrainRestitution());
    }

    @Nonnull
    public static PhysicsChunkBuildOptions fromNativeVoxelTerrainEnabled(boolean enabled) {
        return new PhysicsChunkBuildOptions(TerrainColliderMode.fromNativeVoxelTerrainEnabled(enabled),
            PhysicsChunkTerrainSettings.DEFAULT_TERRAIN_FRICTION,
            PhysicsChunkTerrainSettings.DEFAULT_TERRAIN_RESTITUTION);
    }

    public boolean nativeVoxelTerrainEnabled() {
        return terrainColliderMode.nativeVoxelTerrainEnabled();
    }
}
