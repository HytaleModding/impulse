package dev.hytalemodding.impulse.core.internal.voxel;

import com.hypixel.hytale.math.shape.Box;
import com.hypixel.hytale.protocol.BlockMaterial;
import com.hypixel.hytale.server.core.asset.type.blockhitbox.BlockBoundingBoxes;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import javax.annotation.Nonnull;

/**
 * Caches resolved block hitbox templates by block id and rotation.
 */
final class ShapeTemplateCache {

    private final Long2ObjectMap<ShapeTemplate> templates = new Long2ObjectOpenHashMap<>();

    @Nonnull
    ShapeTemplate get(int blockId, int rotation) {
        if (blockId == 0) {
            return ShapeTemplate.EMPTY;
        }

        long key = key(blockId, rotation);
        ShapeTemplate cached = templates.get(key);
        if (cached != null) {
            return cached;
        }

        ShapeTemplate resolved = resolve(blockId, rotation);
        templates.put(key, resolved);
        return resolved;
    }

    int size() {
        return templates.size();
    }

    private static long key(int blockId, int rotation) {
        return ((long) blockId << Integer.SIZE) ^ (rotation & 0xFFFF_FFFFL);
    }

    @Nonnull
    private static ShapeTemplate resolve(int blockId, int rotation) {
        BlockType blockType = BlockType.getAssetMap().getAsset(blockId);
        if (blockType == null || blockType.isUnknown()
            || blockType.getMaterial() != BlockMaterial.Solid) {
            return ShapeTemplate.EMPTY;
        }

        BlockBoundingBoxes hitbox = BlockBoundingBoxes.getAssetMap()
            .getAsset(blockType.getHitboxTypeIndex());
        if (hitbox == null) {
            return ShapeTemplate.EMPTY;
        }

        Box[] boxes = hitbox.get(rotation).getDetailBoxes();
        if (boxes.length == 0) {
            return ShapeTemplate.EMPTY;
        }

        return new ShapeTemplate(boxes, isFullCube(boxes), hitbox.protrudesUnitBox());
    }

    private static boolean isFullCube(@Nonnull Box[] boxes) {
        if (boxes.length != 1) {
            return false;
        }

        Box box = boxes[0];
        return box.min.x == 0.0 && box.min.y == 0.0 && box.min.z == 0.0
            && box.max.x == 1.0 && box.max.y == 1.0 && box.max.z == 1.0;
    }

    /**
     * Immutable hitbox data used by section collision generation.
     */
    static final class ShapeTemplate {

        static final ShapeTemplate EMPTY = new ShapeTemplate(new Box[0], false, false);

        private final Box[] boxes;
        private final boolean fullCube;
        private final boolean protrudesUnitBox;

        private ShapeTemplate(@Nonnull Box[] boxes, boolean fullCube, boolean protrudesUnitBox) {
            this.boxes = boxes;
            this.fullCube = fullCube;
            this.protrudesUnitBox = protrudesUnitBox;
        }

        boolean collidable() {
            return boxes.length > 0;
        }

        boolean fullCube() {
            return fullCube;
        }

        boolean protrudesUnitBox() {
            return protrudesUnitBox;
        }

        @Nonnull
        Box[] boxes() {
            return boxes;
        }
    }
}
