package dev.hytalemodding.impulse.core.internal.modules.worldcollision;

import dev.hytalemodding.impulse.api.PhysicsCollisionFilters;
import dev.hytalemodding.impulse.core.internal.modules.worldcollision.SectionCollisionGeometry.BoxCollider;
import dev.hytalemodding.impulse.core.plugin.physicsstore.requests.TerrainColliderPayload;
import dev.hytalemodding.impulse.core.plugin.physicsstore.requests.TerrainColliderPayload.BoxPayload;
import dev.hytalemodding.impulse.core.plugin.physicsstore.requests.TerrainColliderPayload.TerrainNeighbor;
import dev.hytalemodding.impulse.core.plugin.physicsstore.requests.TerrainColliderRequest;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nonnull;

/**
 * Converts generated world-collision sections into copied PhysicsStore terrain requests.
 */
public final class PhysicsStoreTerrainRequests {

    private static final int ADJACENT_SECTION_VOXEL_SHIFT = 16;

    private PhysicsStoreTerrainRequests() {
    }

    @Nonnull
    public static TerrainColliderRequest upsert(@Nonnull UUID spaceUuid,
        int chunkX,
        int sectionY,
        int chunkZ,
        long neighborhoodSignature,
        @Nonnull SectionCollisionGeometry geometry,
        @Nonnull WorldCollisionBuildOptions buildOptions) {
        String sourceKey = sourceKey(chunkX, sectionY, chunkZ);
        return TerrainColliderRequest.upsert(spaceUuid,
            sourceKey,
            chunkX,
            sectionY,
            chunkZ,
            payloadKey(sourceKey, neighborhoodSignature, buildOptions),
            payload(geometry, buildOptions, adjacentNeighbors(chunkX, sectionY, chunkZ)));
    }

    @Nonnull
    public static TerrainColliderRequest remove(@Nonnull UUID spaceUuid,
        int chunkX,
        int sectionY,
        int chunkZ) {
        return TerrainColliderRequest.remove(spaceUuid,
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
