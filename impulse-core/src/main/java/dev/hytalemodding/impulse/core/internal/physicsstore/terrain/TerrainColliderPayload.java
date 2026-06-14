package dev.hytalemodding.impulse.core.internal.physicsstore.terrain;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nonnull;

/**
 * Copied terrain payload carried across the ChunkStore to PhysicsStore boundary.
 */
public record TerrainColliderPayload(float voxelSizeX,
                                     float voxelSizeY,
                                     float voxelSizeZ,
                                     @Nonnull int[] voxelCoordinates,
                                     @Nonnull List<BoxPayload> mergedFullCubeBoxes,
                                     @Nonnull List<BoxPayload> detailBoxes,
                                     boolean nativeVoxelTerrainEnabled,
                                     float friction,
                                     float restitution,
                                     int collisionGroup,
                                     int collisionMask,
                                     @Nonnull List<TerrainNeighbor> neighbors) {

    public TerrainColliderPayload {
        voxelCoordinates = voxelCoordinates != null
            ? Arrays.copyOf(voxelCoordinates, voxelCoordinates.length)
            : new int[0];
        mergedFullCubeBoxes = mergedFullCubeBoxes != null
            ? List.copyOf(mergedFullCubeBoxes)
            : List.of();
        detailBoxes = detailBoxes != null ? List.copyOf(detailBoxes) : List.of();
        neighbors = neighbors != null ? List.copyOf(neighbors) : List.of();
    }

    @Nonnull
    @Override
    public int[] voxelCoordinates() {
        return Arrays.copyOf(voxelCoordinates, voxelCoordinates.length);
    }

    public boolean hasFullCubeVoxels() {
        return voxelCoordinates.length > 0;
    }

    public boolean isEmpty() {
        return voxelCoordinates.length == 0 && mergedFullCubeBoxes.isEmpty()
            && detailBoxes.isEmpty();
    }

    /**
     * Axis-aligned static terrain box in world coordinates.
     */
    public record BoxPayload(double centerX,
                             double centerY,
                             double centerZ,
                             double halfX,
                             double halfY,
                             double halfZ) {
    }

    /**
     * Neighbor terrain source used for optional native-voxel stitching.
     */
    public record TerrainNeighbor(@Nonnull String sourceKey, int shiftX, int shiftY, int shiftZ) {

        public TerrainNeighbor {
            Objects.requireNonNull(sourceKey, "sourceKey");
        }
    }
}
