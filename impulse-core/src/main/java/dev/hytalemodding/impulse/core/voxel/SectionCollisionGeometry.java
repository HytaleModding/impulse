package dev.hytalemodding.impulse.core.voxel;

import java.util.List;
import javax.annotation.Nonnull;

/**
 * Backend-neutral collision geometry generated for one Hytale chunk section.
 */
public final class SectionCollisionGeometry {

    private static final int[] EMPTY_VOXELS = new int[0];

    private final int[] fullCubeVoxels;
    private final List<BoxCollider> mergedFullCubeBoxes;
    private final List<BoxCollider> detailBoxes;
    private final int scannedBlocks;
    private final int solidBlocks;
    private final int culledInteriorBlocks;
    private final int detailBoxCount;

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

    @Nonnull
    public int[] fullCubeVoxels() {
        return fullCubeVoxels.clone();
    }

    public boolean hasFullCubeVoxels() {
        return fullCubeVoxels.length > 0;
    }

    @Nonnull
    public List<BoxCollider> mergedFullCubeBoxes() {
        return mergedFullCubeBoxes;
    }

    @Nonnull
    public List<BoxCollider> detailBoxes() {
        return detailBoxes;
    }

    public int scannedBlocks() {
        return scannedBlocks;
    }

    public int solidBlocks() {
        return solidBlocks;
    }

    public int culledInteriorBlocks() {
        return culledInteriorBlocks;
    }

    public int detailBoxCount() {
        return detailBoxCount;
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
