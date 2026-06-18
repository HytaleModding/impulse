package dev.hytalemodding.impulse.core.internal.modules.physicschunk;

/**
 * Runtime representation used for full-cube chunk collision.
 */
public enum ChunkCollisionMode {
    MERGED_BOXES,
    NATIVE_VOXELS_WHEN_SUPPORTED;

    public static ChunkCollisionMode fromNativeVoxelCollisionEnabled(boolean enabled) {
        return enabled ? NATIVE_VOXELS_WHEN_SUPPORTED : MERGED_BOXES;
    }

    public boolean nativeVoxelCollisionEnabled() {
        return this == NATIVE_VOXELS_WHEN_SUPPORTED;
    }
}
