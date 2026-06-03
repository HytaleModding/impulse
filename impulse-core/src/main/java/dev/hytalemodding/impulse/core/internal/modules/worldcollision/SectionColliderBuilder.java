package dev.hytalemodding.impulse.core.internal.modules.worldcollision;

import com.hypixel.hytale.math.shape.Box;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import dev.hytalemodding.impulse.core.internal.modules.worldcollision.SectionCollisionGeometry.BoxCollider;
import dev.hytalemodding.impulse.core.internal.modules.worldcollision.ShapeTemplateCache.ShapeTemplate;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Builds optimized collision geometry for a single chunk section.
 */
final class SectionColliderBuilder {

    private static final int SECTION_VOLUME = ChunkUtil.SIZE * ChunkUtil.SIZE * ChunkUtil.SIZE;

    private final ShapeTemplateCache templates;

    SectionColliderBuilder(@Nonnull ShapeTemplateCache templates) {
        this.templates = templates;
    }

    long neighborhoodSignature(@Nonnull World world,
        @Nonnull BlockSection section,
        int chunkX,
        int sectionY,
        int chunkZ) {
        return neighborhoodSignature(world, section, chunkX, sectionY, chunkZ, null);
    }

    long neighborhoodSignature(@Nonnull World world,
        @Nonnull BlockSection section,
        int chunkX,
        int sectionY,
        int chunkZ,
        @Nullable WorldVoxelCollisionCache.SectionAccessCache accessCache) {
        return new SectionBlockReader(world, templates, section, chunkX, sectionY, chunkZ, accessCache)
            .neighborhoodSignature();
    }

    @Nonnull
    SectionCollisionGeometry build(@Nonnull World world,
        @Nonnull BlockSection section,
        int chunkX,
        int sectionY,
        int chunkZ) {
        return build(world, section, chunkX, sectionY, chunkZ, null);
    }

    @Nonnull
    SectionCollisionGeometry build(@Nonnull World world,
        @Nonnull BlockSection section,
        int chunkX,
        int sectionY,
        int chunkZ,
        @Nullable WorldVoxelCollisionCache.SectionAccessCache accessCache) {
        SectionBlockReader reader = new SectionBlockReader(world, templates, section,
            chunkX, sectionY, chunkZ, accessCache);
        BitSet fullCubes = new BitSet(SECTION_VOLUME);
        IntArrayList voxelCoordinates = new IntArrayList();
        List<BoxCollider> detailBoxes = new ArrayList<>();
        int baseX = chunkX << ChunkUtil.BITS;
        int baseY = sectionY << ChunkUtil.BITS;
        int baseZ = chunkZ << ChunkUtil.BITS;
        int scanned = 0;
        int solid = 0;
        int culledInterior = 0;
        int detailBoxCount = 0;

        for (int localY = 0; localY < ChunkUtil.SIZE; localY++) {
            for (int localZ = 0; localZ < ChunkUtil.SIZE; localZ++) {
                for (int localX = 0; localX < ChunkUtil.SIZE; localX++) {
                    scanned++;
                    ShapeTemplate template = reader.localTemplate(localX, localY, localZ);
                    if (!template.collidable()) {
                        continue;
                    }

                    solid++;
                    if (template.fullCube()) {
                        if (isInteriorFullCube(reader, localX, localY, localZ)) {
                            culledInterior++;
                            continue;
                        }

                        fullCubes.set(ChunkUtil.indexBlock(localX, localY, localZ));
                        voxelCoordinates.add(localX);
                        voxelCoordinates.add(localY);
                        voxelCoordinates.add(localZ);
                        continue;
                    }

                    int worldX = baseX + localX;
                    int worldY = baseY + localY;
                    int worldZ = baseZ + localZ;
                    for (Box box : template.boxes()) {
                        detailBoxes.add(new BoxCollider(
                            worldX + (box.min.x + box.max.x) * 0.5,
                            worldY + (box.min.y + box.max.y) * 0.5,
                            worldZ + (box.min.z + box.max.z) * 0.5,
                            (box.max.x - box.min.x) * 0.5,
                            (box.max.y - box.min.y) * 0.5,
                            (box.max.z - box.min.z) * 0.5));
                        detailBoxCount++;
                    }
                }
            }
        }

        List<BoxCollider> mergedBoxes = mergeFullCubes(fullCubes, baseX, baseY, baseZ);
        return new SectionCollisionGeometry(voxelCoordinates.toIntArray(), mergedBoxes, detailBoxes,
            scanned, solid, culledInterior, detailBoxCount);
    }

    private static boolean isInteriorFullCube(@Nonnull SectionBlockReader reader,
        int localX,
        int localY,
        int localZ) {
        return reader.isFullCubeAt(localX + 1, localY, localZ)
            && reader.isFullCubeAt(localX - 1, localY, localZ)
            && reader.isFullCubeAt(localX, localY + 1, localZ)
            && reader.isFullCubeAt(localX, localY - 1, localZ)
            && reader.isFullCubeAt(localX, localY, localZ + 1)
            && reader.isFullCubeAt(localX, localY, localZ - 1);
    }

    @Nonnull
    private static List<BoxCollider> mergeFullCubes(@Nonnull BitSet source,
        int baseX,
        int baseY,
        int baseZ) {
        BitSet remaining = (BitSet) source.clone();
        List<BoxCollider> boxes = new ArrayList<>();
        int index = remaining.nextSetBit(0);
        while (index >= 0) {
            int startX = ChunkUtil.xFromIndex(index);
            int startY = ChunkUtil.yFromIndex(index);
            int startZ = ChunkUtil.zFromIndex(index);
            int width = widthFrom(remaining, startX, startY, startZ);
            int depth = depthFrom(remaining, startX, startY, startZ, width);
            int height = heightFrom(remaining, startX, startY, startZ, width, depth);

            clearVolume(remaining, startX, startY, startZ, width, height, depth);
            boxes.add(new BoxCollider(
                baseX + startX + width * 0.5,
                baseY + startY + height * 0.5,
                baseZ + startZ + depth * 0.5,
                width * 0.5,
                height * 0.5,
                depth * 0.5));
            index = remaining.nextSetBit(index + 1);
        }
        return boxes;
    }

    private static int widthFrom(@Nonnull BitSet remaining, int startX, int y, int z) {
        int width = 0;
        while (startX + width < ChunkUtil.SIZE
            && remaining.get(ChunkUtil.indexBlock(startX + width, y, z))) {
            width++;
        }
        return width;
    }

    private static int depthFrom(@Nonnull BitSet remaining, int startX, int y, int startZ, int width) {
        int depth = 1;
        while (startZ + depth < ChunkUtil.SIZE
            && rowFilled(remaining, startX, y, startZ + depth, width)) {
            depth++;
        }
        return depth;
    }

    private static int heightFrom(@Nonnull BitSet remaining,
        int startX,
        int startY,
        int startZ,
        int width,
        int depth) {
        int height = 1;
        while (startY + height < ChunkUtil.SIZE
            && layerFilled(remaining, startX, startY + height, startZ, width, depth)) {
            height++;
        }
        return height;
    }

    private static boolean layerFilled(@Nonnull BitSet remaining,
        int startX,
        int y,
        int startZ,
        int width,
        int depth) {
        for (int z = startZ; z < startZ + depth; z++) {
            if (!rowFilled(remaining, startX, y, z, width)) {
                return false;
            }
        }
        return true;
    }

    private static boolean rowFilled(@Nonnull BitSet remaining, int startX, int y, int z, int width) {
        for (int x = startX; x < startX + width; x++) {
            if (!remaining.get(ChunkUtil.indexBlock(x, y, z))) {
                return false;
            }
        }
        return true;
    }

    private static void clearVolume(@Nonnull BitSet remaining,
        int startX,
        int startY,
        int startZ,
        int width,
        int height,
        int depth) {
        for (int y = startY; y < startY + height; y++) {
            for (int z = startZ; z < startZ + depth; z++) {
                for (int x = startX; x < startX + width; x++) {
                    remaining.clear(ChunkUtil.indexBlock(x, y, z));
                }
            }
        }
    }
}
