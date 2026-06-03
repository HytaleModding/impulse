package dev.hytalemodding.impulse.core.internal.modules.worldcollision;

import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import dev.hytalemodding.impulse.core.internal.modules.worldcollision.ShapeTemplateCache.ShapeTemplate;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Reads block collision templates around a chunk section, including adjacent sections.
 */
final class SectionBlockReader {

    private final World world;
    private final ShapeTemplateCache templates;
    private final BlockSection currentSection;
    private final int currentChunkX;
    private final int currentSectionY;
    private final int currentChunkZ;
    private final int baseX;
    private final int baseY;
    private final int baseZ;
    private final Long2ObjectMap<BlockSection> sectionCache = new Long2ObjectOpenHashMap<>();
    @Nullable
    private final WorldVoxelCollisionCache.SectionAccessCache accessCache;

    SectionBlockReader(@Nonnull World world,
        @Nonnull ShapeTemplateCache templates,
        @Nonnull BlockSection currentSection,
        int currentChunkX,
        int currentSectionY,
        int currentChunkZ) {
        this(world, templates, currentSection, currentChunkX, currentSectionY, currentChunkZ, null);
    }

    SectionBlockReader(@Nonnull World world,
        @Nonnull ShapeTemplateCache templates,
        @Nonnull BlockSection currentSection,
        int currentChunkX,
        int currentSectionY,
        int currentChunkZ,
        @Nullable WorldVoxelCollisionCache.SectionAccessCache accessCache) {
        this.world = world;
        this.templates = templates;
        this.currentSection = currentSection;
        this.currentChunkX = currentChunkX;
        this.currentSectionY = currentSectionY;
        this.currentChunkZ = currentChunkZ;
        this.baseX = currentChunkX << ChunkUtil.BITS;
        this.baseY = currentSectionY << ChunkUtil.BITS;
        this.baseZ = currentChunkZ << ChunkUtil.BITS;
        this.accessCache = accessCache;
    }

    @Nonnull
    ShapeTemplate localTemplate(int localX, int localY, int localZ) {
        return templateAt(baseX + localX, baseY + localY, baseZ + localZ);
    }

    boolean isFullCubeAt(int localX, int localY, int localZ) {
        return templateAt(baseX + localX, baseY + localY, baseZ + localZ).fullCube();
    }

    long neighborhoodSignature() {
        long signature = 0xCBF2_9CE4_8422_2325L;
        signature = mix(signature, sectionVersion(currentChunkX, currentSectionY, currentChunkZ));
        signature = mix(signature, sectionVersion(currentChunkX + 1, currentSectionY, currentChunkZ));
        signature = mix(signature, sectionVersion(currentChunkX - 1, currentSectionY, currentChunkZ));
        signature = mix(signature, sectionVersion(currentChunkX, currentSectionY + 1, currentChunkZ));
        signature = mix(signature, sectionVersion(currentChunkX, currentSectionY - 1, currentChunkZ));
        signature = mix(signature, sectionVersion(currentChunkX, currentSectionY, currentChunkZ + 1));
        signature = mix(signature, sectionVersion(currentChunkX, currentSectionY, currentChunkZ - 1));
        return signature;
    }

    private int sectionVersion(int chunkX, int sectionY, int chunkZ) {
        BlockSection section = sectionAt(chunkX, sectionY, chunkZ);
        return section != null ? section.getLocalChangeCounter() : Integer.MIN_VALUE;
    }

    private static long mix(long signature, int value) {
        return (signature ^ value) * 0x100_0000_01B3L;
    }

    @Nonnull
    private ShapeTemplate templateAt(int worldX, int worldY, int worldZ) {
        if (worldY < 0 || worldY > ChunkUtil.HEIGHT_MINUS_1) {
            return ShapeTemplate.EMPTY;
        }

        int chunkX = ChunkUtil.chunkCoordinate(worldX);
        int sectionY = ChunkUtil.indexSection(worldY);
        int chunkZ = ChunkUtil.chunkCoordinate(worldZ);
        BlockSection section = sectionAt(chunkX, sectionY, chunkZ);
        if (section == null) {
            return ShapeTemplate.EMPTY;
        }

        if (section.getFiller(worldX, worldY, worldZ) != 0) {
            return ShapeTemplate.EMPTY;
        }

        return templates.get(section.get(worldX, worldY, worldZ),
            section.getRotationIndex(worldX, worldY, worldZ));
    }

    @Nullable
    private BlockSection sectionAt(int chunkX, int sectionY, int chunkZ) {
        if (sectionY < 0 || sectionY > ChunkUtil.indexSection(ChunkUtil.HEIGHT_MINUS_1)) {
            return null;
        }
        if (chunkX == currentChunkX && sectionY == currentSectionY && chunkZ == currentChunkZ) {
            return currentSection;
        }

        long key = packSectionKey(chunkX, sectionY, chunkZ);
        if (sectionCache.containsKey(key)) {
            return sectionCache.get(key);
        }

        BlockSection section = accessCache != null
            ? accessCache.blockSection(world, chunkX, sectionY, chunkZ)
            : ChunkSectionAccess.blockSection(world, chunkX, sectionY, chunkZ);
        sectionCache.put(key, section);
        return section;
    }

    private static long packSectionKey(int chunkX, int sectionY, int chunkZ) {
        return ((long) chunkX & 0x3FF_FFFFL) << 38
            | ((long) chunkZ & 0x3FF_FFFFL) << 12
            | (sectionY & 0xFFFL);
    }

}
