package dev.hytalemodding.impulse.core.internal.modules.physicschunk;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nonnull;

/**
 * Copied chunk collision payload carried across the ChunkStore to PhysicsStore boundary.
 */
public record ChunkCollisionPayload(float voxelSizeX,
                                     float voxelSizeY,
                                     float voxelSizeZ,
                                     @Nonnull int[] voxelCoordinates,
                                     @Nonnull List<BoxPayload> mergedFullCubeBoxes,
                                     @Nonnull List<BoxPayload> detailBoxes,
                                     boolean nativeVoxelCollisionEnabled,
                                     float friction,
                                     float restitution,
                                     int collisionGroup,
                                     int collisionMask,
                                     @Nonnull List<Neighbor> neighbors) {

    public ChunkCollisionPayload {
        voxelCoordinates = Arrays.copyOf(voxelCoordinates, voxelCoordinates.length);
        mergedFullCubeBoxes = List.copyOf(mergedFullCubeBoxes);
        detailBoxes = List.copyOf(detailBoxes);
        neighbors = List.copyOf(neighbors);
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
    public record Neighbor(@Nonnull String sourceKey, int shiftX, int shiftY, int shiftZ) {

        public Neighbor {
            Objects.requireNonNull(sourceKey, "sourceKey");
        }
    }
}
