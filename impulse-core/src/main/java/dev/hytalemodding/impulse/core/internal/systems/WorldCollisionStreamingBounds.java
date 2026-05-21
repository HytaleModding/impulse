package dev.hytalemodding.impulse.core.internal.systems;

import javax.annotation.Nonnull;
import org.joml.Vector3f;

/**
 * Chunk/section neighborhood covered by a streamed world-collision target.
 *
 * <p>Two body targets with the same bounds would trigger the same section
 * collision requests, so the streaming system can deduplicate them.</p>
 */
record WorldCollisionStreamingBounds(int minChunkX,
                                     int maxChunkX,
                                     int minSectionY,
                                     int maxSectionY,
                                     int minChunkZ,
                                     int maxChunkZ) {

    private static final int CHUNK_BITS = 5;
    private static final int MAX_WORLD_BLOCK_Y = 319;

    @Nonnull
    static WorldCollisionStreamingBounds from(@Nonnull Vector3f center, int radius) {
        int minX = (int) Math.floor(center.x) - radius;
        int maxX = (int) Math.floor(center.x) + radius;
        int minY = Math.max(0, (int) Math.floor(center.y) - radius);
        int maxY = Math.clamp((int) Math.floor(center.y) + radius, 0, MAX_WORLD_BLOCK_Y);
        int minZ = (int) Math.floor(center.z) - radius;
        int maxZ = (int) Math.floor(center.z) + radius;
        return new WorldCollisionStreamingBounds(
            chunkCoordinate(minX),
            chunkCoordinate(maxX),
            sectionIndex(minY),
            sectionIndex(maxY),
            chunkCoordinate(minZ),
            chunkCoordinate(maxZ));
    }

    private static int chunkCoordinate(int block) {
        return block >> CHUNK_BITS;
    }

    private static int sectionIndex(int y) {
        return y >> CHUNK_BITS;
    }
}
