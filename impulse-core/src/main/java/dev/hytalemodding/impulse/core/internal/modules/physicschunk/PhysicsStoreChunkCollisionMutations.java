package dev.hytalemodding.impulse.core.internal.modules.physicschunk;

import dev.hytalemodding.impulse.api.PhysicsCollisionFilters;
import dev.hytalemodding.impulse.core.internal.modules.physicschunk.SectionCollisionGeometry.BoxCollider;
import dev.hytalemodding.impulse.core.internal.modules.physicschunk.ChunkCollisionMutation;
import dev.hytalemodding.impulse.core.internal.modules.physicschunk.ChunkCollisionPayload;
import dev.hytalemodding.impulse.core.internal.modules.physicschunk.ChunkCollisionPayload.BoxPayload;
import dev.hytalemodding.impulse.core.internal.modules.physicschunk.ChunkCollisionPayload.Neighbor;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nonnull;

/**
 * Converts generated PhysicsChunk terrain sections into copied PhysicsStore chunk collision mutations.
 */
public final class PhysicsStoreChunkCollisionMutations {

    private static final int ADJACENT_SECTION_VOXEL_SHIFT = 16;

    private PhysicsStoreChunkCollisionMutations() {
    }

    @Nonnull
    public static ChunkCollisionMutation upsert(@Nonnull UUID spaceUuid,
        int chunkX,
        int sectionY,
        int chunkZ,
        long neighborhoodSignature,
        @Nonnull SectionCollisionGeometry geometry,
        @Nonnull PhysicsChunkBuildOptions buildOptions) {
        String sourceKey = sourceKey(chunkX, sectionY, chunkZ);
        return ChunkCollisionMutation.upsert(spaceUuid,
            sourceKey,
            chunkX,
            sectionY,
            chunkZ,
            payloadKey(sourceKey, neighborhoodSignature, buildOptions),
            payload(geometry, buildOptions, adjacentNeighbors(chunkX, sectionY, chunkZ)));
    }

    @Nonnull
    public static ChunkCollisionMutation remove(@Nonnull UUID spaceUuid,
        int chunkX,
        int sectionY,
        int chunkZ) {
        return ChunkCollisionMutation.remove(spaceUuid,
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
        @Nonnull PhysicsChunkBuildOptions buildOptions) {
        return sourceKey + ":"
            + Long.toUnsignedString(neighborhoodSignature)
            + ":"
            + Integer.toUnsignedString(buildOptions.hashCode());
    }

    @Nonnull
    private static ChunkCollisionPayload payload(@Nonnull SectionCollisionGeometry geometry,
        @Nonnull PhysicsChunkBuildOptions buildOptions,
        @Nonnull List<Neighbor> neighbors) {
        return new ChunkCollisionPayload(1.0f,
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
    private static List<Neighbor> adjacentNeighbors(int chunkX, int sectionY, int chunkZ) {
        return List.of(
            new Neighbor(sourceKey(chunkX - 1, sectionY, chunkZ),
                -ADJACENT_SECTION_VOXEL_SHIFT,
                0,
                0),
            new Neighbor(sourceKey(chunkX + 1, sectionY, chunkZ),
                ADJACENT_SECTION_VOXEL_SHIFT,
                0,
                0),
            new Neighbor(sourceKey(chunkX, sectionY - 1, chunkZ),
                0,
                -ADJACENT_SECTION_VOXEL_SHIFT,
                0),
            new Neighbor(sourceKey(chunkX, sectionY + 1, chunkZ),
                0,
                ADJACENT_SECTION_VOXEL_SHIFT,
                0),
            new Neighbor(sourceKey(chunkX, sectionY, chunkZ - 1),
                0,
                0,
                -ADJACENT_SECTION_VOXEL_SHIFT),
            new Neighbor(sourceKey(chunkX, sectionY, chunkZ + 1),
                0,
                0,
                ADJACENT_SECTION_VOXEL_SHIFT));
    }
}
