package dev.hytalemodding.impulse.core.internal.modules.worldcollision;

import dev.hytalemodding.impulse.api.PhysicsCollisionFilters;
import dev.hytalemodding.impulse.core.internal.modules.worldcollision.SectionCollisionGeometry.BoxCollider;
import dev.hytalemodding.impulse.core.internal.physicsstore.terrain.TerrainColliderMutation;
import dev.hytalemodding.impulse.core.internal.physicsstore.terrain.TerrainColliderPayload;
import dev.hytalemodding.impulse.core.internal.physicsstore.terrain.TerrainColliderPayload.BoxPayload;
import dev.hytalemodding.impulse.core.internal.physicsstore.terrain.TerrainColliderPayload.TerrainNeighbor;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nonnull;

/**
 * Converts generated world-collision sections into copied PhysicsStore terrain mutations.
 */
public final class PhysicsStoreTerrainMutations {

    private static final int ADJACENT_SECTION_VOXEL_SHIFT = 16;

    private PhysicsStoreTerrainMutations() {
    }

    @Nonnull
    public static TerrainColliderMutation upsert(@Nonnull UUID spaceUuid,
        int chunkX,
        int sectionY,
        int chunkZ,
        long neighborhoodSignature,
        @Nonnull SectionCollisionGeometry geometry,
        @Nonnull WorldCollisionBuildOptions buildOptions) {
        String sourceKey = sourceKey(chunkX, sectionY, chunkZ);
        return TerrainColliderMutation.upsert(spaceUuid,
            sourceKey,
            chunkX,
            sectionY,
            chunkZ,
            payloadKey(sourceKey, neighborhoodSignature, buildOptions),
            payload(geometry, buildOptions, adjacentNeighbors(chunkX, sectionY, chunkZ)));
    }

    @Nonnull
    public static TerrainColliderMutation remove(@Nonnull UUID spaceUuid,
        int chunkX,
        int sectionY,
        int chunkZ) {
        return TerrainColliderMutation.remove(spaceUuid,
            sourceKey(chunkX, sectionY, chunkZ),
            chunkX,
            sectionY,
            chunkZ);
    }

    @Nonnull
    public static String sourceKey(int chunkX, int sectionY, int chunkZ) {
        return "chunk:" + chunkX + ":" + sectionY + ":" + chunkZ;
    }

    @Nonnull
    private static String payloadKey(@Nonnull String sourceKey,
        long neighborhoodSignature,
        @Nonnull WorldCollisionBuildOptions buildOptions) {
        return sourceKey + ":"
            + Long.toUnsignedString(neighborhoodSignature)
            + ":"
            + Integer.toUnsignedString(buildOptions.hashCode());
    }

    @Nonnull
    private static TerrainColliderPayload payload(@Nonnull SectionCollisionGeometry geometry,
        @Nonnull WorldCollisionBuildOptions buildOptions,
        @Nonnull List<TerrainNeighbor> neighbors) {
        return new TerrainColliderPayload(1.0f,
            1.0f,
            1.0f,
            geometry.fullCubeVoxels(),
            boxes(geometry.mergedFullCubeBoxes()),
            boxes(geometry.detailBoxes()),
            buildOptions.nativeVoxelTerrainEnabled(),
            buildOptions.terrainFriction(),
            buildOptions.terrainRestitution(),
            PhysicsCollisionFilters.TERRAIN,
            PhysicsCollisionFilters.ALL,
            neighbors);
    }

    @Nonnull
    private static List<BoxPayload> boxes(@Nonnull List<BoxCollider> boxes) {
        return boxes.stream()
            .map(box -> new BoxPayload(box.centerX(),
                box.centerY(),
                box.centerZ(),
                box.halfX(),
                box.halfY(),
                box.halfZ()))
            .toList();
    }

    @Nonnull
    private static List<TerrainNeighbor> adjacentNeighbors(int chunkX, int sectionY, int chunkZ) {
        return List.of(
            new TerrainNeighbor(sourceKey(chunkX - 1, sectionY, chunkZ),
                -ADJACENT_SECTION_VOXEL_SHIFT,
                0,
                0),
            new TerrainNeighbor(sourceKey(chunkX + 1, sectionY, chunkZ),
                ADJACENT_SECTION_VOXEL_SHIFT,
                0,
                0),
            new TerrainNeighbor(sourceKey(chunkX, sectionY - 1, chunkZ),
                0,
                -ADJACENT_SECTION_VOXEL_SHIFT,
                0),
            new TerrainNeighbor(sourceKey(chunkX, sectionY + 1, chunkZ),
                0,
                ADJACENT_SECTION_VOXEL_SHIFT,
                0),
            new TerrainNeighbor(sourceKey(chunkX, sectionY, chunkZ - 1),
                0,
                0,
                -ADJACENT_SECTION_VOXEL_SHIFT),
            new TerrainNeighbor(sourceKey(chunkX, sectionY, chunkZ + 1),
                0,
                0,
                ADJACENT_SECTION_VOXEL_SHIFT));
    }
}
