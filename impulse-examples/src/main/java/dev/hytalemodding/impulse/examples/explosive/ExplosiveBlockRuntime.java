package dev.hytalemodding.impulse.examples.explosive;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.ExplosionConfig;
import com.hypixel.hytale.server.core.entity.ExplosionUtils;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.time.TimeResource;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsWorldResource;
import dev.hytalemodding.impulse.core.plugin.simulation.PhysicsShapeSpec;
import dev.hytalemodding.impulse.core.plugin.simulation.RigidBodySpawnSettings;
import dev.hytalemodding.impulse.examples.commands.ExamplePhysicsUtils;
import dev.hytalemodding.impulse.examples.commands.ExamplePhysicsUtils.PendingBlockBody;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector3i;

public final class ExplosiveBlockRuntime {

    private static final int SET_BLOCK_SETTINGS = 256;
    private static final double SUPPORT_PROBE_BELOW_BOTTOM = 0.51;
    private static final PhysicsShapeSpec FRAGMENT_SHAPE =
        PhysicsShapeSpec.box(0.48f, 0.48f, 0.48f);
    private static final RigidBodySpawnSettings FRAGMENT_SETTINGS =
        RigidBodySpawnSettings.material(0.65f, 0.15f);
    private static final Damage.EnvironmentSource DAMAGE_SOURCE =
        new Damage.EnvironmentSource("impulse_explosion");

    private ExplosiveBlockRuntime() {
    }

    @Nonnull
    public static ExplosionResult explode(@Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull World world,
        @Nonnull TimeResource time,
        @Nonnull PhysicsWorldResource resource,
        @Nonnull SpaceId spaceId,
        @Nonnull Vector3d center,
        @Nonnull ExplosiveBlockComponent settings) {
        return explode(commandBuffer,
            holder -> commandBuffer.addEntity(holder, AddReason.SPAWN),
            world,
            time,
            resource,
            spaceId,
            center,
            settings);
    }

    @Nonnull
    public static ExplosionResult explode(@Nonnull Store<EntityStore> store,
        @Nonnull World world,
        @Nonnull TimeResource time,
        @Nonnull PhysicsWorldResource resource,
        @Nonnull SpaceId spaceId,
        @Nonnull Vector3d center,
        @Nonnull ExplosiveBlockComponent settings) {
        return explode(store,
            holder -> store.addEntity(holder, AddReason.SPAWN),
            world,
            time,
            resource,
            spaceId,
            center,
            settings);
    }

    @Nonnull
    private static ExplosionResult explode(@Nonnull ComponentAccessor<EntityStore> entityAccessor,
        @Nonnull Consumer<Holder<EntityStore>> fragmentSpawner,
        @Nonnull World world,
        @Nonnull TimeResource time,
        @Nonnull PhysicsWorldResource resource,
        @Nonnull SpaceId spaceId,
        @Nonnull Vector3d center,
        @Nonnull ExplosiveBlockComponent settings) {
        ExplosionUtils.performExplosion(DAMAGE_SOURCE,
            new Vector3d(center),
            new Rotation3f(0.0f, 0.0f, 0.0f),
            explosionConfig(settings.getRadius()),
            null,
            entityAccessor,
            world.getChunkStore().getStore());

        resource.ensureWorldCollisionAround(world,
            spaceId,
            List.of(new Vector3d(center)),
            Math.max(8, settings.getRadius() + 4),
            Math.max(0L, world.getTick()));

        List<FragmentBlock> fragments = collectFragments(world,
            center,
            settings.getRadius(),
            settings.getMaxFragments());
        if (fragments.isEmpty()) {
            return new ExplosionResult(0);
        }

        PendingBlockBody[] pending = new PendingBlockBody[fragments.size()];
        Vector3f centerF = toVector3f(center);
        ExamplePhysicsUtils.requireApplied(resource.submitCommands(
            Math.max(0L, world.getTick()),
            fragments.size() * 2,
            commands -> {
                for (int i = 0; i < fragments.size(); i++) {
                    FragmentBlock fragment = fragments.get(i);
                    pending[i] = ExamplePhysicsUtils.recordBlockBodySpawn(commands,
                        spaceId,
                        fragment.center(),
                        fragment.blockType(),
                        FRAGMENT_SHAPE,
                        1.0f,
                        FRAGMENT_SETTINGS);
                    Vector3f impulse = ExplosiveBlockPolicy.outwardImpulse(centerF,
                        toVector3f(fragment.center()),
                        settings.getImpulseStrength(),
                        settings.getVerticalLift());
                    commands.applyBodyImpulse(pending[i].bodyKey(), impulse.x, impulse.y, impulse.z);
                }
            }), "spawn explosive block fragments");

        for (PendingBlockBody body : pending) {
            Holder<EntityStore> holder = ExamplePhysicsUtils.attachedBlockEntityHolder(time,
                body.bodyKey(),
                body.spaceId(),
                body.blockType(),
                new Vector3d(body.positionX(), body.positionY(), body.positionZ()),
                body.controllable());
            holder.addComponent(ExplosiveBlockComponent.getComponentType(),
                nextGeneration(settings, body.blockType()));
            fragmentSpawner.accept(holder);
        }
        return new ExplosionResult(fragments.size());
    }

    @Nonnull
    public static SettlementResult settleAndMaybeChain(@Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull World world,
        @Nonnull TimeResource time,
        @Nonnull PhysicsWorldResource resource,
        @Nonnull SpaceId spaceId,
        @Nonnull RigidBodyKey bodyKey,
        @Nonnull ExplosiveBlockComponent settings,
        @Nonnull Vector3f contactPoint) {
        Vector3d center = new Vector3d(contactPoint.x, contactPoint.y, contactPoint.z);
        ExplosionResult chained = ExplosiveBlockPolicy.shouldChain(settings.getGeneration(),
            settings.getMaxGeneration())
            ? explode(commandBuffer, world, time, resource, spaceId, center, settings)
            : ExplosionResult.NONE;

        Vector3i blockPosition = ExplosiveBlockPolicy.landingBlockPosition(contactPoint);
        boolean placed = placeBlock(world, blockPosition, settings.getBlockType());
        resource.destroyBody(bodyKey);
        return new SettlementResult(placed, chained.spawnedFragments());
    }

    @Nonnull
    private static List<FragmentBlock> collectFragments(@Nonnull World world,
        @Nonnull Vector3d center,
        int radius,
        int maxFragments) {
        int clampedRadius = Math.max(1, radius);
        int clampedMaxFragments = Math.max(1, maxFragments);
        int centerX = (int) Math.floor(center.x);
        int centerY = (int) Math.floor(center.y);
        int centerZ = (int) Math.floor(center.z);
        int radiusSquared = clampedRadius * clampedRadius;
        List<FragmentBlock> fragments = new ArrayList<>(Math.min(clampedMaxFragments, 64));

        for (int y = centerY - clampedRadius; y <= centerY + clampedRadius; y++) {
            for (int x = centerX - clampedRadius; x <= centerX + clampedRadius; x++) {
                for (int z = centerZ - clampedRadius; z <= centerZ + clampedRadius; z++) {
                    if (fragments.size() >= clampedMaxFragments
                        || squaredDistance(centerX, centerY, centerZ, x, y, z) > radiusSquared) {
                        continue;
                    }
                    FragmentBlock fragment = removeFragmentCandidate(world, x, y, z);
                    if (fragment != null) {
                        fragments.add(fragment);
                    }
                }
            }
        }
        return fragments;
    }

    @Nullable
    private static FragmentBlock removeFragmentCandidate(@Nonnull World world,
        int x,
        int y,
        int z) {
        WorldChunk chunk = loadedChunk(world, x, z);
        if (chunk == null) {
            return null;
        }
        int localX = chunkBlockCoordinate(x);
        int localZ = chunkBlockCoordinate(z);
        int blockId = chunk.getBlock(localX, y, localZ);
        if (!ExplosiveBlockPolicy.isFragmentCandidate(blockId)) {
            return null;
        }
        BlockType blockType = BlockType.getAssetMap().getAsset(blockId);
        if (blockType == null) {
            return null;
        }
        if (!chunk.setBlock(localX, y, localZ, 0, BlockType.EMPTY, 0, 0, SET_BLOCK_SETTINGS)) {
            return null;
        }
        return new FragmentBlock(blockType.getId(), new Vector3d(x + 0.5, y + 0.5, z + 0.5));
    }

    @Nullable
    public static Vector3f terrainContactPoint(@Nonnull World world,
        @Nonnull Vector3d bodyCenter) {
        Vector3i support = supportBlockPosition(bodyCenter);
        if (!isFragmentCandidateAt(world, support.x, support.y, support.z)) {
            return null;
        }
        return new Vector3f((float) bodyCenter.x, support.y + 1.0f, (float) bodyCenter.z);
    }

    static Vector3i supportBlockPosition(@Nonnull Vector3d bodyCenter) {
        return new Vector3i((int) Math.floor(bodyCenter.x),
            (int) Math.floor(bodyCenter.y - SUPPORT_PROBE_BELOW_BOTTOM),
            (int) Math.floor(bodyCenter.z));
    }

    private static boolean isFragmentCandidateAt(@Nonnull World world,
        int x,
        int y,
        int z) {
        WorldChunk chunk = loadedChunk(world, x, z);
        if (chunk == null) {
            return false;
        }
        int blockId = chunk.getBlock(chunkBlockCoordinate(x), y, chunkBlockCoordinate(z));
        return ExplosiveBlockPolicy.isFragmentCandidate(blockId);
    }

    private static boolean placeBlock(@Nonnull World world,
        @Nonnull Vector3i position,
        @Nonnull String blockTypeId) {
        WorldChunk chunk = loadedChunk(world, position.x, position.z);
        if (chunk == null) {
            return false;
        }
        BlockType blockType = BlockType.getAssetMap().getAsset(blockTypeId);
        int blockId = blockType != null ? BlockType.getAssetMap().getIndex(blockTypeId) : Integer.MIN_VALUE;
        if (blockType == null || blockId == Integer.MIN_VALUE) {
            blockType = BlockType.getAssetMap().getAsset(ExplosiveBlockPolicy.DEFAULT_BLOCK_TYPE);
            blockId = BlockType.getAssetMap().getIndex(ExplosiveBlockPolicy.DEFAULT_BLOCK_TYPE);
        }
        if (blockType == null || blockId == Integer.MIN_VALUE) {
            return false;
        }
        return chunk.setBlock(chunkBlockCoordinate(position.x),
            position.y,
            chunkBlockCoordinate(position.z),
            blockId,
            blockType,
            0,
            0,
            SET_BLOCK_SETTINGS);
    }

    @Nullable
    private static WorldChunk loadedChunk(@Nonnull World world, int x, int z) {
        return world.getChunkIfLoaded(ChunkUtil.indexChunkFromBlock(x, z));
    }

    private static int squaredDistance(int ax,
        int ay,
        int az,
        int bx,
        int by,
        int bz) {
        int dx = ax - bx;
        int dy = ay - by;
        int dz = az - bz;
        return dx * dx + dy * dy + dz * dz;
    }

    @Nonnull
    private static Vector3f toVector3f(@Nonnull Vector3d vector) {
        return new Vector3f((float) vector.x, (float) vector.y, (float) vector.z);
    }

    @Nonnull
    private static ExplosiveBlockComponent nextGeneration(@Nonnull ExplosiveBlockComponent settings,
        @Nullable String blockType) {
        return new ExplosiveBlockComponent(blockType,
            settings.getGeneration() + 1,
            settings.getMaxGeneration(),
            settings.getRadius(),
            settings.getMaxFragments(),
            settings.getImpulseStrength(),
            settings.getVerticalLift());
    }

    public record ExplosionResult(int spawnedFragments) {
        public static final ExplosionResult NONE = new ExplosionResult(0);

        public ExplosionResult {
            spawnedFragments = Math.max(0, spawnedFragments);
        }
    }

    public record SettlementResult(boolean placedBlock, int spawnedFragments) {
    }

    @Nonnull
    static ExplosionConfig explosionConfig(int radius) {
        return new EntityDamageExplosionConfig(radius);
    }

    static int chunkBlockCoordinate(int worldCoordinate) {
        return ChunkUtil.localCoordinate(worldCoordinate);
    }

    private record FragmentBlock(@Nonnull String blockType,
                                 @Nonnull Vector3d center) {
    }

    private static final class EntityDamageExplosionConfig extends ExplosionConfig {

        private EntityDamageExplosionConfig(int radius) {
            int clampedRadius = Math.max(1, radius);
            damageBlocks = false;
            damageEntities = true;
            blockDamageRadius = 0;
            blockDamageFalloff = 1.0f;
            entityDamageRadius = clampedRadius;
            entityDamage = 12.0f;
            entityDamageFalloff = 1.0f;
            blockDropChance = 0.0f;
        }
    }
}
