package dev.hytalemodding.impulse.core.internal.modules.physicschunk;

/**
 * Runtime representation used for full-cube terrain collision.
 */
public enum ChunkCollisionMode {
    MERGED_BOXES,
    NATIVE_VOXELS_WHEN_SUPPORTED;

    public static ChunkCollisionMode fromNativeVoxelTerrainEnabled(boolean enabled) {
        return enabled ? NATIVE_VOXELS_WHEN_SUPPORTED : MERGED_BOXES;
    }

    public boolean nativeVoxelTerrainEnabled() {
        return this == NATIVE_VOXELS_WHEN_SUPPORTED;
    }
}
