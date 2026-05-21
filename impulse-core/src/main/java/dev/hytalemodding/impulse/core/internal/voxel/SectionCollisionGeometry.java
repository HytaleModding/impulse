package dev.hytalemodding.impulse.core.internal.voxel;

import java.util.List;
import javax.annotation.Nonnull;

/**
 * Backend-neutral collision geometry generated for one Hytale chunk section.
 */
public record SectionCollisionGeometry(int[] fullCubeVoxels, List<BoxCollider> mergedFullCubeBoxes,
                                       List<BoxCollider> detailBoxes, int scannedBlocks,
                                       int solidBlocks, int culledInteriorBlocks,
                                       int detailBoxCount) {

    private static final int[] EMPTY_VOXELS = new int[0];

    public SectionCollisionGeometry(@Nonnull int[] fullCubeVoxels,
        @Nonnull List<BoxCollider> mergedFullCubeBoxes,
        @Nonnull List<BoxCollider> detailBoxes,
        int scannedBlocks,
        int solidBlocks,
        int culledInteriorBlocks,
        int detailBoxCount) {
        this.fullCubeVoxels = fullCubeVoxels.length == 0 ? EMPTY_VOXELS : fullCubeVoxels.clone();
        this.mergedFullCubeBoxes = List.copyOf(mergedFullCubeBoxes);
        this.detailBoxes = List.copyOf(detailBoxes);
        this.scannedBlocks = scannedBlocks;
        this.solidBlocks = solidBlocks;
        this.culledInteriorBlocks = culledInteriorBlocks;
        this.detailBoxCount = detailBoxCount;
    }

    @Override
    @Nonnull
    public int[] fullCubeVoxels() {
        return fullCubeVoxels.clone();
    }

    public boolean hasFullCubeVoxels() {
        return fullCubeVoxels.length > 0;
    }

    @Override
    @Nonnull
    public List<BoxCollider> mergedFullCubeBoxes() {
        return mergedFullCubeBoxes;
    }

    @Override
    @Nonnull
    public List<BoxCollider> detailBoxes() {
        return detailBoxes;
    }

    /**
     * Axis-aligned box collider described in world coordinates.
     */
    public record BoxCollider(double centerX,
                              double centerY,
                              double centerZ,
                              double halfX,
                              double halfY,
                              double halfZ) {

    }
}
