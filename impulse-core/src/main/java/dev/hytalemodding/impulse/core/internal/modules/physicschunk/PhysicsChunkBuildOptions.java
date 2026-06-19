package dev.hytalemodding.impulse.core.internal.modules.physicschunk;

import dev.hytalemodding.impulse.core.plugin.modules.physicschunk.settings.PhysicsChunkTerrainSettings;
import java.util.Objects;
import javax.annotation.Nonnull;

/**
 * Options that control generated PhysicsChunk backend collision geometry.
 */
public record PhysicsChunkBuildOptions(@Nonnull ChunkCollisionMode chunkCollisionMode) {

    public static final PhysicsChunkBuildOptions DEFAULT =
        fromNativeVoxelCollisionEnabled(PhysicsChunkTerrainSettings.DEFAULT_NATIVE_VOXEL_COLLISION_ENABLED);

    public PhysicsChunkBuildOptions {
        Objects.requireNonNull(chunkCollisionMode, "chunkCollisionMode");
    }

    @Nonnull
    public static PhysicsChunkBuildOptions fromSettings(@Nonnull PhysicsChunkTerrainSettings settings) {
        return fromNativeVoxelCollisionEnabled(settings.isNativeVoxelCollisionEnabled());
    }

    @Nonnull
    public static PhysicsChunkBuildOptions fromNativeVoxelCollisionEnabled(boolean enabled) {
        return new PhysicsChunkBuildOptions(
            ChunkCollisionMode.fromNativeVoxelCollisionEnabled(enabled));
    }

    public boolean nativeVoxelCollisionEnabled() {
        return chunkCollisionMode.nativeVoxelCollisionEnabled();
    }
}
