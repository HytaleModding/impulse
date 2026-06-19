package dev.hytalemodding.impulse.core.internal.modules.physicschunk;

import dev.hytalemodding.impulse.core.plugin.modules.physicschunk.settings.PhysicsChunkTerrainSettings;
import java.util.Objects;
import javax.annotation.Nonnull;

/**
 * Options that control generated PhysicsChunk backend collision geometry.
 */
public record PhysicsChunkBuildOptions(@Nonnull ChunkCollisionMode chunkCollisionMode,
                                         float friction,
                                         float restitution,
                                         int collisionGroup,
                                         int collisionMask) {

    public static final PhysicsChunkBuildOptions DEFAULT =
        fromNativeVoxelCollisionEnabled(PhysicsChunkTerrainSettings.DEFAULT_NATIVE_VOXEL_COLLISION_ENABLED);

    public PhysicsChunkBuildOptions {
        Objects.requireNonNull(chunkCollisionMode, "chunkCollisionMode");
        if (!Float.isFinite(friction) || friction < 0.0f) {
            throw new IllegalArgumentException("friction must be finite and >= 0");
        }
        if (!Float.isFinite(restitution) || restitution < 0.0f) {
            throw new IllegalArgumentException("restitution must be finite and >= 0");
        }
    }

    @Nonnull
    public static PhysicsChunkBuildOptions fromSettings(@Nonnull PhysicsChunkTerrainSettings settings) {
        return new PhysicsChunkBuildOptions(
            ChunkCollisionMode.fromNativeVoxelCollisionEnabled(settings.isNativeVoxelCollisionEnabled()),
            PhysicsChunkCollisionDefaults.FRICTION,
            PhysicsChunkCollisionDefaults.RESTITUTION,
            PhysicsChunkCollisionDefaults.COLLISION_GROUP,
            PhysicsChunkCollisionDefaults.COLLISION_MASK);
    }

    @Nonnull
    public static PhysicsChunkBuildOptions fromNativeVoxelCollisionEnabled(boolean enabled) {
        return new PhysicsChunkBuildOptions(ChunkCollisionMode.fromNativeVoxelCollisionEnabled(enabled),
            PhysicsChunkCollisionDefaults.FRICTION,
            PhysicsChunkCollisionDefaults.RESTITUTION,
            PhysicsChunkCollisionDefaults.COLLISION_GROUP,
            PhysicsChunkCollisionDefaults.COLLISION_MASK);
    }

    public boolean nativeVoxelCollisionEnabled() {
        return chunkCollisionMode.nativeVoxelCollisionEnabled();
    }
}
