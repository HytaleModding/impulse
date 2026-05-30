package dev.hytalemodding.impulse.core.internal.voxel;

import com.hypixel.hytale.math.util.ChunkUtil;
import javax.annotation.Nonnull;
import org.joml.Vector3f;

/**
 * Chunk/section neighborhood covered by a streamed world-collision target.
 *
 * <p>Two body targets with the same bounds would trigger the same section
 * collision requests, so the streaming system can deduplicate them.</p>
 */
public record WorldCollisionStreamingBounds(int minChunkX,
                                            int maxChunkX,
                                            int minSectionY,
                                            int maxSectionY,
                                            int minChunkZ,
                                            int maxChunkZ) {

    @Nonnull
    public static WorldCollisionStreamingBounds from(@Nonnull Vector3f center, int radius) {
        return from(center.x, center.y, center.z, radius);
    }

    @Nonnull
    public static WorldCollisionStreamingBounds from(float centerX,
        float centerY,
        float centerZ,
        int radius) {
        int minX = (int) Math.floor(centerX) - radius;
        int maxX = (int) Math.floor(centerX) + radius;
        int minY = Math.max(0, (int) Math.floor(centerY) - radius);
        int maxY = Math.clamp((int) Math.floor(centerY) + radius, 0, ChunkUtil.HEIGHT_MINUS_1);
        int minZ = (int) Math.floor(centerZ) - radius;
        int maxZ = (int) Math.floor(centerZ) + radius;
        return new WorldCollisionStreamingBounds(
            ChunkUtil.chunkCoordinate(minX),
            ChunkUtil.chunkCoordinate(maxX),
            ChunkUtil.indexSection(minY),
            ChunkUtil.indexSection(maxY),
            ChunkUtil.chunkCoordinate(minZ),
            ChunkUtil.chunkCoordinate(maxZ));
    }
}
