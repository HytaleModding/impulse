package dev.hytalemodding.impulse.examples.explosive;

import com.hypixel.hytale.math.shape.Box;
import com.hypixel.hytale.protocol.BlockMaterial;
import com.hypixel.hytale.server.core.asset.type.blockhitbox.BlockBoundingBoxes;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import dev.hytalemodding.impulse.examples.commands.ExamplePhysicsUtils;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3f;

public final class ExplosiveBlockPolicy {

    public static final String DEFAULT_BLOCK_TYPE = ExamplePhysicsUtils.DEFAULT_BLOCK_TYPE;
    private static final int EMPTY_BLOCK_ID = 0;
    private static final int UNKNOWN_BLOCK_ID = 1;

    private ExplosiveBlockPolicy() {
    }

    @Nonnull
    public static Vector3f outwardImpulse(@Nonnull Vector3f center,
        @Nonnull Vector3f blockCenter,
        float strength,
        float verticalLift) {
        Vector3f direction = new Vector3f(blockCenter).sub(center);
        direction.y = 0.0f;
        float clampedStrength = Math.max(0.0f, strength);
        if (direction.lengthSquared() == 0.0f) {
            return new Vector3f(0.0f, clampedStrength, 0.0f);
        }
        direction.normalize().mul(clampedStrength);
        direction.y = clampedStrength * Math.max(0.0f, verticalLift);
        return direction;
    }

    public static boolean isFragmentCandidate(int blockId) {
        return blockId != EMPTY_BLOCK_ID && blockId != UNKNOWN_BLOCK_ID;
    }

    public static boolean isSimpleFullCubeFragmentBlock(int blockId) {
        var assetStore = BlockType.getAssetStore();
        if (assetStore == null) {
            return false;
        }
        BlockType blockType = assetStore.getAssetMap().getAsset(blockId);
        return isSimpleFullCubeFragmentBlock(blockId, blockType, 0);
    }

    public static boolean isSimpleFullCubeFragmentBlock(int blockId,
        @Nullable BlockType blockType,
        int rotation) {
        if (!isFragmentCandidate(blockId) || blockType == null) {
            return false;
        }
        if (blockType.isUnknown()) {
            return false;
        }

        var hitboxStore = BlockBoundingBoxes.getAssetStore();
        if (hitboxStore == null) {
            return false;
        }
        BlockBoundingBoxes hitbox = hitboxStore.getAssetMap().getAsset(blockType.getHitboxTypeIndex());
        Box[] detailBoxes = hitbox != null ? hitbox.get(rotation).getDetailBoxes() : null;
        return isSimpleFullCubeFragmentBlock(blockId,
            false,
            blockType.getMaterial(),
            detailBoxes);
    }

    static boolean isSimpleFullCubeFragmentBlock(int blockId,
        boolean unknown,
        @Nullable BlockMaterial material,
        @Nullable Box[] detailBoxes) {
        return isFragmentCandidate(blockId)
            && !unknown
            && material == BlockMaterial.Solid
            && isFullUnitCollisionBox(detailBoxes);
    }

    private static boolean isFullUnitCollisionBox(@Nullable Box[] detailBoxes) {
        if (detailBoxes == null || detailBoxes.length != 1) {
            return false;
        }

        Box box = detailBoxes[0];
        return box.min.x == 0.0 && box.min.y == 0.0 && box.min.z == 0.0
            && box.max.x == 1.0 && box.max.y == 1.0 && box.max.z == 1.0;
    }
}
