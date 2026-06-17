package dev.hytalemodding.impulse.core.internal.systems.debug;

import dev.hytalemodding.impulse.core.internal.modules.physicschunk.SectionCollisionGeometry.BoxCollider;
import java.util.List;
import javax.annotation.Nonnull;

/**
 * Copied terrain debug section used by the internal debug renderer.
 */
public record PhysicsDebugWorldCollisionSectionView(int chunkX,
                                                    int sectionY,
                                                    int chunkZ,
                                                    boolean voxelTerrain,
                                                    @Nonnull List<BoxCollider> fullCubeBoxes,
                                                    @Nonnull List<BoxCollider> detailBoxes) {

    public PhysicsDebugWorldCollisionSectionView {
        fullCubeBoxes = List.copyOf(fullCubeBoxes);
        detailBoxes = List.copyOf(detailBoxes);
    }
}
