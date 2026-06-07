package dev.hytalemodding.impulse.examples.explosive;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.protocol.EntityPart;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelParticle;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.entity.ExplosionConfig;
import com.hypixel.hytale.server.core.entity.ExplosionUtils;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.time.TimeResource;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsWorldResource;
import dev.hytalemodding.impulse.core.plugin.simulation.PhysicsShapeSpec;
import dev.hytalemodding.impulse.core.plugin.simulation.RigidBodySpawnSettings;
import dev.hytalemodding.impulse.examples.commands.ExamplePhysicsUtils;
import dev.hytalemodding.impulse.examples.commands.ExamplePhysicsUtils.PendingBlockBody;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;
import org.joml.Quaterniond;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;

public final class ExplosiveBlockRuntime {

    private static final int SET_BLOCK_SETTINGS = 256;
    private static final int MAX_FRAGMENTS_PER_EXPLOSION = 1024;
    static final int MAX_BLOCKS_PER_FRAGMENT_GROUP = 32;
    private static final int MID_BLOCKS_PER_FRAGMENT_GROUP = 16;
    private static final int INNER_BLOCKS_PER_FRAGMENT_GROUP = 8;
    // Explosion fragments represent removed terrain blocks, so their body bounds
    // should match Hytale's full-block block-entity visuals exactly.
    private static final float FRAGMENT_BLOCK_HALF_EXTENT = 0.5f;
    private static final float FRAGMENT_VISUAL_ORIGIN_OFFSET_Y = 0.5f;
    private static final double SOURCE_EXPLOSION_VERTICAL_BIAS = 1.0;
    private static final String EXPLOSION_PARTICLE_SYSTEM_ID = "Explosion_Medium";
    private static final String EXPLOSION_SOUND_EVENT_ID = "SFX_Goblin_Lobber_Bomb_Death";
    private static final RigidBodySpawnSettings FRAGMENT_SETTINGS =
        RigidBodySpawnSettings.material(0.65f, 0.15f);
    private static final Damage.EnvironmentSource DAMAGE_SOURCE =
        new Damage.EnvironmentSource("impulse_explosion");

    private ExplosiveBlockRuntime() {
    }

    public static void scheduleExplosion(@Nonnull Store<EntityStore> store,
        @Nonnull World world,
        @Nonnull SpaceId spaceId,
        @Nonnull Vector3d center,
        @Nonnull ExplosiveBlockComponent settings) {
        Vector3d centerCopy = new Vector3d(center);
        ExplosiveBlockComponent settingsCopy = settings.clone();
        world.execute(() -> {
            TimeResource time = store.getResource(TimeResource.getResourceType());
            PhysicsWorldResource resource = store.getResource(PhysicsWorldResource.getResourceType());
            explode(store,
                world,
                time,
                resource,
                spaceId,
                centerCopy,
                settingsCopy);
        });
    }

    @Nonnull
    private static ExplosionResult explode(@Nonnull Store<EntityStore> store,
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
            new Rotation3f(),
            explosionConfig(settings.getRadius()),
            null,
            entityAccessor,
            world.getChunkStore().getStore());

        List<FragmentBlock> fragments = collectFragments(world,
            center,
            settings.getRadius(),
            settings.getMaxFragments());
        if (fragments.isEmpty()) {
            return new ExplosionResult(0);
        }

        List<FragmentGroup> groups = groupFragments(fragments, center, settings.getRadius());
        resource.refreshWorldCollisionAround(world,
            spaceId,
            center,
            Math.max(8, settings.getRadius() + 4));
        resource.ensureWorldCollisionAround(world,
            spaceId,
            groupCenters(groups),
            Math.max(8, maxGroupCollisionRadius(groups) + 4),
            Math.max(0L, world.getTick()));

        List<PendingBlockBody> pending = new ArrayList<>(groups.size());
        Vector3f centerF = toVector3f(center);
        ExamplePhysicsUtils.requireApplied(resource.submitCommands(
            Math.max(0L, world.getTick()),
            groups.size() * 2,
            commands -> {
                for (int i = 0; i < groups.size(); i++) {
                    FragmentGroup group = groups.get(i);
                    PendingBlockBody body = ExamplePhysicsUtils.recordBlockBodySpawnAtBodyCenter(commands,
                        spaceId,
                        group.center(),
                        group.blockType(),
                        group.shape(),
                        group.mass(),
                        FRAGMENT_SETTINGS);
                    pending.add(body);
                    Vector3f impulse = ExplosiveBlockPolicy.outwardImpulse(centerF,
                        toVector3f(group.center()),
                        settings.getImpulseStrength(),
                        settings.getVerticalLift())
                        .mul(group.mass());
                    commands.applyBodyImpulse(body.bodyKey(), impulse.x, impulse.y, impulse.z);
                }
            }), "spawn explosive block fragments");

        for (int i = 0; i < groups.size(); i++) {
            spawnGroupVisuals(time, fragmentSpawner, settings, groups.get(i), pending.get(i));
        }
        return new ExplosionResult(groups.size());
    }

    private static void spawnGroupVisuals(@Nonnull TimeResource time,
        @Nonnull Consumer<Holder<EntityStore>> fragmentSpawner,
        @Nonnull ExplosiveBlockComponent settings,
        @Nonnull FragmentGroup group,
        @Nonnull PendingBlockBody body) {
        boolean controllableAssigned = false;
        for (FragmentVisual visual : group.visualBlocks()) {
            boolean controllable = body.controllable() && !controllableAssigned;
            Holder<EntityStore> holder = ExamplePhysicsUtils.attachedBlockEntityHolder(time,
                body.bodyKey(),
                body.spaceId(),
                visual.blockType(),
                visual.position(),
                visual.localPositionOffset(),
                visual.localRotationOffset(),
                visual.visualOriginOffsetY(),
                controllable);
            if (controllable) {
                controllableAssigned = true;
                ExplosiveBlockComponent fragmentState = fragmentLandingExplosionState();
                if (fragmentState != null) {
                    holder.addComponent(ExplosiveBlockComponent.getComponentType(), fragmentState);
                }
            }
            fragmentSpawner.accept(holder);
        }
    }

    @Nonnull
    private static List<FragmentBlock> collectFragments(@Nonnull World world,
        @Nonnull Vector3d center,
        int radius,
        int maxFragments) {
        int clampedRadius = Math.max(1, radius);
        int clampedMaxFragments = Math.clamp(maxFragments, 1, MAX_FRAGMENTS_PER_EXPLOSION);
        int centerX = (int) Math.floor(center.x);
        int centerY = (int) Math.floor(center.y);
        int centerZ = (int) Math.floor(center.z);
        List<FragmentBlock> fragments = new ArrayList<>(Math.min(clampedMaxFragments, 64));

        for (FragmentOffset offset : sphericalFragmentOffsets(clampedRadius)) {
            if (fragments.size() >= clampedMaxFragments) {
                break;
            }
            FragmentBlock fragment = removeFragmentCandidate(world,
                centerX + offset.dx(),
                centerY + offset.dy(),
                centerZ + offset.dz());
            if (fragment != null) {
                fragments.add(fragment);
            }
        }
        return fragments;
    }

    @Nullable
    @SuppressWarnings({"deprecation", "removal"})
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
        int rotation = chunk.getRotation(localX, y, localZ).index();
        var blockTypeStore = BlockType.getAssetStore();
        if (blockTypeStore == null) {
            return null;
        }
        BlockType blockType = blockTypeStore.getAssetMap().getAsset(blockId);
        if (!ExplosiveBlockPolicy.isSimpleFullCubeFragmentBlock(blockId, blockType, rotation)) {
            return null;
        }
        if (!chunk.setBlock(localX, y, localZ, 0, BlockType.EMPTY, 0, 0, SET_BLOCK_SETTINGS)) {
            return null;
        }
        return new FragmentBlock(blockType.getId(),
            x,
            y,
            z,
            blockTypeCenter(blockType, rotation),
            blockRotation(rotation));
    }

    @Nonnull
    private static Vector3d blockTypeCenter(@Nonnull BlockType blockType, int rotation) {
        Vector3d center = new Vector3d();
        blockType.getBlockCenter(rotation, center);
        if (center.lengthSquared() < 1.0e-6) {
            return new Vector3d(0.5, 0.5, 0.5);
        }
        return center;
    }

    @Nonnull
    static Quaternionf blockRotation(int rotationIndex) {
        Rotation3f rotation = new Rotation3f();
        RotationTuple.get(rotationIndex).applyRotationTo(rotation);
        Quaterniond quaternion = rotation.getQuaternion(new Quaterniond());
        return new Quaternionf((float) quaternion.x,
            (float) quaternion.y,
            (float) quaternion.z,
            (float) quaternion.w);
    }

    @Nonnull
    static List<FragmentGroup> groupFragments(@Nonnull List<FragmentBlock> fragments,
        @Nonnull Vector3d center,
        int radius) {
        if (fragments.isEmpty()) {
            return List.of();
        }

        Map<FragmentCell, FragmentBlock> remaining = new HashMap<>(fragments.size() * 2);
        for (FragmentBlock fragment : fragments) {
            remaining.put(fragment.cell(), fragment);
        }

        List<FragmentGroup> groups = new ArrayList<>();
        for (FragmentBlock seed : fragments) {
            if (!remaining.containsKey(seed.cell())) {
                continue;
            }
            groups.add(createGroup(collectSolidCuboidGroup(seed,
                remaining,
                maxBlocksForSeed(seed, center, radius))));
        }
        return List.copyOf(groups);
    }

    @Nonnull
    private static List<FragmentBlock> collectSolidCuboidGroup(@Nonnull FragmentBlock seed,
        @Nonnull Map<FragmentCell, FragmentBlock> remaining,
        int maxBlocks) {
        List<FragmentBlock> group = new ArrayList<>(Math.max(1, maxBlocks));
        remaining.remove(seed.cell());
        group.add(seed);

        FragmentBounds bounds = new FragmentBounds(seed.x(), seed.y(), seed.z());
        while (group.size() < maxBlocks) {
            CuboidExpansion expansion = bestExpansion(bounds, remaining, maxBlocks - group.size());
            if (expansion == null) {
                break;
            }
            bounds = expansion.bounds();
            for (FragmentCell cell : expansion.cells()) {
                FragmentBlock fragment = remaining.remove(cell);
                if (fragment != null) {
                    group.add(fragment);
                }
            }
        }
        return List.copyOf(group);
    }

    @Nullable
    private static CuboidExpansion bestExpansion(@Nonnull FragmentBounds bounds,
        @Nonnull Map<FragmentCell, FragmentBlock> remaining,
        int remainingCapacity) {
        CuboidExpansion best = null;
        for (ExpansionDirection direction : ExpansionDirection.values()) {
            CuboidExpansion candidate = expansion(bounds, direction, remaining, remainingCapacity);
            if (candidate == null) {
                continue;
            }
            if (best == null || candidate.cells().size() > best.cells().size()) {
                best = candidate;
            }
        }
        return best;
    }

    @Nullable
    private static CuboidExpansion expansion(@Nonnull FragmentBounds bounds,
        @Nonnull ExpansionDirection direction,
        @Nonnull Map<FragmentCell, FragmentBlock> remaining,
        int remainingCapacity) {
        FragmentBounds expanded = bounds.expand(direction);
        List<FragmentCell> layer = expanded.layer(direction);
        if (layer.isEmpty() || layer.size() > remainingCapacity) {
            return null;
        }
        for (FragmentCell cell : layer) {
            if (!remaining.containsKey(cell)) {
                return null;
            }
        }
        return new CuboidExpansion(expanded, layer);
    }

    @Nonnull
    private static FragmentGroup createGroup(@Nonnull List<FragmentBlock> fragments) {
        if (fragments.isEmpty()) {
            throw new IllegalArgumentException("fragment group cannot be empty");
        }

        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        Map<String, Integer> blockTypeCounts = new LinkedHashMap<>();
        for (FragmentBlock fragment : fragments) {
            minX = Math.min(minX, fragment.x());
            minY = Math.min(minY, fragment.y());
            minZ = Math.min(minZ, fragment.z());
            maxX = Math.max(maxX, fragment.x());
            maxY = Math.max(maxY, fragment.y());
            maxZ = Math.max(maxZ, fragment.z());
            blockTypeCounts.merge(fragment.blockType(), 1, Integer::sum);
        }

        String blockType = fragments.getFirst().blockType();
        int blockTypeCount = 0;
        for (Map.Entry<String, Integer> entry : blockTypeCounts.entrySet()) {
            if (entry.getValue() > blockTypeCount) {
                blockType = entry.getKey();
                blockTypeCount = entry.getValue();
            }
        }

        Vector3d groupCenter = new Vector3d(
            (minX + maxX + 1) * 0.5,
            (minY + maxY + 1) * 0.5,
            (minZ + maxZ + 1) * 0.5);
        return new FragmentGroup(blockType,
            fragments,
            groupCenter,
            ((maxX - minX) * 0.5f) + FRAGMENT_BLOCK_HALF_EXTENT,
            ((maxY - minY) * 0.5f) + FRAGMENT_BLOCK_HALF_EXTENT,
            ((maxZ - minZ) * 0.5f) + FRAGMENT_BLOCK_HALF_EXTENT);
    }

    private static int maxBlocksForSeed(@Nonnull FragmentBlock seed,
        @Nonnull Vector3d center,
        int radius) {
        double distance = seed.center().distance(center);
        double clampedRadius = Math.max(1, radius);
        if (distance <= clampedRadius * 0.35) {
            return INNER_BLOCKS_PER_FRAGMENT_GROUP;
        }
        if (distance <= clampedRadius * 0.7) {
            return MID_BLOCKS_PER_FRAGMENT_GROUP;
        }
        return MAX_BLOCKS_PER_FRAGMENT_GROUP;
    }

    @Nonnull
    private static List<Vector3d> groupCenters(@Nonnull List<FragmentGroup> groups) {
        List<Vector3d> centers = new ArrayList<>(groups.size());
        for (FragmentGroup group : groups) {
            centers.add(group.center());
        }
        return centers;
    }

    private static int maxGroupCollisionRadius(@Nonnull List<FragmentGroup> groups) {
        double maxRadius = 0.0;
        for (FragmentGroup group : groups) {
            maxRadius = Math.max(maxRadius, group.collisionRadius());
        }
        return (int) Math.ceil(maxRadius);
    }

    @Nullable
    private static WorldChunk loadedChunk(@Nonnull World world, int x, int z) {
        return world.getChunkIfLoaded(ChunkUtil.indexChunkFromBlock(x, z));
    }

    @Nonnull
    private static Vector3f toVector3f(@Nonnull Vector3d vector) {
        return new Vector3f((float) vector.x, (float) vector.y, (float) vector.z);
    }

    @Nullable
    static ExplosiveBlockComponent fragmentLandingExplosionState() {
        return null;
    }

    @Nonnull
    public static Vector3d sourceExplosionCenter(@Nonnull Vector3d sourceBodyCenter) {
        return new Vector3d(sourceBodyCenter).add(0.0, SOURCE_EXPLOSION_VERTICAL_BIAS, 0.0);
    }

    @Nonnull
    public static Vector3d contactExplosionCenter(@Nonnull Vector3d contactPoint) {
        return new Vector3d(contactPoint).add(0.0, 0.5, 0.0);
    }

    @Nonnull
    static List<FragmentOffset> sphericalFragmentOffsets(int radius) {
        int clampedRadius = Math.max(1, radius);
        List<FragmentOffset> offsets = getFragmentOffsets(clampedRadius);
        offsets.sort(Comparator
            .comparingInt(FragmentOffset::distanceSquared)
            .thenComparingInt(offset -> Math.abs(offset.dy()))
            .thenComparingInt(FragmentOffset::dx)
            .thenComparingInt(FragmentOffset::dz)
            .thenComparingInt(FragmentOffset::dy));
        return List.copyOf(offsets);
    }

    @NonNullDecl
    private static List<FragmentOffset> getFragmentOffsets(int clampedRadius) {
        int radiusSquared = clampedRadius * clampedRadius;
        List<FragmentOffset> offsets = new ArrayList<>();
        for (int dy = -clampedRadius; dy <= clampedRadius; dy++) {
            for (int dx = -clampedRadius; dx <= clampedRadius; dx++) {
                for (int dz = -clampedRadius; dz <= clampedRadius; dz++) {
                    int distanceSquared = dx * dx + dy * dy + dz * dz;
                    if (distanceSquared <= radiusSquared) {
                        offsets.add(new FragmentOffset(dx, dy, dz, distanceSquared));
                    }
                }
            }
        }
        return offsets;
    }

    public record ExplosionResult(int spawnedFragments) {
        public static final ExplosionResult NONE = new ExplosionResult(0);

        public ExplosionResult {
            spawnedFragments = Math.max(0, spawnedFragments);
        }
    }

    @Nonnull
    static ExplosionConfig explosionConfig(int radius) {
        return new EntityDamageExplosionConfig(radius);
    }

    static int chunkBlockCoordinate(int worldCoordinate) {
        return ChunkUtil.localCoordinate(worldCoordinate);
    }

    private static int soundEventIndex(@Nonnull String soundEventId) {
        var assetStore = SoundEvent.getAssetStore();
        if (assetStore == null) {
            return SoundEvent.EMPTY_ID;
        }
        return assetStore.getAssetMap().getIndexOrDefault(soundEventId, SoundEvent.EMPTY_ID);
    }

    record FragmentBlock(@Nonnull String blockType,
                         int x,
                         int y,
                         int z,
                         @Nonnull Vector3d visualCenter,
                         @Nonnull Quaternionf localRotation) {

        FragmentBlock(@Nonnull String blockType, int x, int y, int z) {
            this(blockType, x, y, z, new Vector3d(0.5, 0.5, 0.5));
        }

        FragmentBlock(@Nonnull String blockType,
            int x,
            int y,
            int z,
            @Nonnull Vector3d visualCenter) {
            this(blockType, x, y, z, visualCenter, new Quaternionf());
        }

        FragmentBlock {
            visualCenter = new Vector3d(visualCenter);
            localRotation = new Quaternionf(localRotation);
        }

        @Nonnull
        @Override
        public Vector3d visualCenter() {
            return new Vector3d(visualCenter);
        }

        @Nonnull
        @Override
        public Quaternionf localRotation() {
            return new Quaternionf(localRotation);
        }

        @Nonnull
        Vector3d center() {
            return new Vector3d(x + 0.5, y + 0.5, z + 0.5);
        }

        @Nonnull
        Vector3d visualBasePosition() {
            return new Vector3d(x + visualCenter.x, y, z + visualCenter.z);
        }

        @Nonnull
        FragmentCell cell() {
            return new FragmentCell(x, y, z);
        }
    }

    record FragmentGroup(@Nonnull String blockType,
                         @Nonnull List<FragmentBlock> blocks,
                         @Nonnull Vector3d center,
                         float halfExtentX,
                         float halfExtentY,
                         float halfExtentZ) {

        FragmentGroup {
            blocks = List.copyOf(blocks);
            if (blocks.isEmpty()) {
                throw new IllegalArgumentException("fragment group cannot be empty");
            }
            center = new Vector3d(center);
            halfExtentX = Math.max(FRAGMENT_BLOCK_HALF_EXTENT, halfExtentX);
            halfExtentY = Math.max(FRAGMENT_BLOCK_HALF_EXTENT, halfExtentY);
            halfExtentZ = Math.max(FRAGMENT_BLOCK_HALF_EXTENT, halfExtentZ);
        }

        @Nonnull
        @Override
        public Vector3d center() {
            return new Vector3d(center);
        }

        @Nonnull
        PhysicsShapeSpec shape() {
            return PhysicsShapeSpec.box(halfExtentX, halfExtentY, halfExtentZ);
        }

        int blockCount() {
            return blocks.size();
        }

        float mass() {
            return blockCount();
        }

        @Nonnull
        List<FragmentVisual> visualBlocks() {
            List<FragmentBlock> sorted = new ArrayList<>(blocks);
            sorted.sort(Comparator
                .comparingDouble((FragmentBlock block) -> block.center().distanceSquared(center))
                .thenComparingInt(FragmentBlock::x)
                .thenComparingInt(FragmentBlock::y)
                .thenComparingInt(FragmentBlock::z));

            List<FragmentVisual> visuals = new ArrayList<>(sorted.size());
            for (FragmentBlock block : sorted) {
                Vector3d position = block.visualBasePosition();
                Vector3d visualCenter = new Vector3d(position)
                    .add(0.0, FRAGMENT_VISUAL_ORIGIN_OFFSET_Y, 0.0);
                visuals.add(new FragmentVisual(block.blockType(),
                    position,
                    new Vector3f((float) (visualCenter.x - center.x),
                        (float) (visualCenter.y - center.y),
                        (float) (visualCenter.z - center.z)),
                    block.localRotation(),
                    FRAGMENT_VISUAL_ORIGIN_OFFSET_Y));
            }
            return List.copyOf(visuals);
        }

        double collisionRadius() {
            return Math.sqrt(halfExtentX * halfExtentX
                + halfExtentY * halfExtentY
                + halfExtentZ * halfExtentZ);
        }

        int aabbBlockVolume() {
            return spanBlocks(halfExtentX)
                * spanBlocks(halfExtentY)
                * spanBlocks(halfExtentZ);
        }

        private static int spanBlocks(float halfExtent) {
            return Math.max(1, Math.round((halfExtent - FRAGMENT_BLOCK_HALF_EXTENT) * 2.0f) + 1);
        }
    }

    record FragmentVisual(@Nonnull String blockType,
                          @Nonnull Vector3d position,
                          @Nonnull Vector3f localPositionOffset,
                          @Nonnull Quaternionf localRotationOffset,
                          float visualOriginOffsetY) {

        FragmentVisual {
            position = new Vector3d(position);
            localPositionOffset = new Vector3f(localPositionOffset);
            localRotationOffset = new Quaternionf(localRotationOffset);
            visualOriginOffsetY = Math.max(0.0f, visualOriginOffsetY);
        }

        @Nonnull
        @Override
        public Vector3d position() {
            return new Vector3d(position);
        }

        @Nonnull
        @Override
        public Vector3f localPositionOffset() {
            return new Vector3f(localPositionOffset);
        }

        @Nonnull
        @Override
        public Quaternionf localRotationOffset() {
            return new Quaternionf(localRotationOffset);
        }
    }

    private record FragmentCell(int x,
                                int y,
                                int z) {
    }

    private record FragmentBounds(int minX,
                                  int minY,
                                  int minZ,
                                  int maxX,
                                  int maxY,
                                  int maxZ) {

        private FragmentBounds(int x, int y, int z) {
            this(x, y, z, x, y, z);
        }

        @Nonnull
        private FragmentBounds expand(@Nonnull ExpansionDirection direction) {
            return switch (direction) {
                case NEG_X -> new FragmentBounds(minX - 1, minY, minZ, maxX, maxY, maxZ);
                case POS_X -> new FragmentBounds(minX, minY, minZ, maxX + 1, maxY, maxZ);
                case NEG_Y -> new FragmentBounds(minX, minY - 1, minZ, maxX, maxY, maxZ);
                case POS_Y -> new FragmentBounds(minX, minY, minZ, maxX, maxY + 1, maxZ);
                case NEG_Z -> new FragmentBounds(minX, minY, minZ - 1, maxX, maxY, maxZ);
                case POS_Z -> new FragmentBounds(minX, minY, minZ, maxX, maxY, maxZ + 1);
            };
        }

        @Nonnull
        private List<FragmentCell> layer(@Nonnull ExpansionDirection direction) {
            return switch (direction) {
                case NEG_X -> cells(minX, minX, minY, maxY, minZ, maxZ);
                case POS_X -> cells(maxX, maxX, minY, maxY, minZ, maxZ);
                case NEG_Y -> cells(minX, maxX, minY, minY, minZ, maxZ);
                case POS_Y -> cells(minX, maxX, maxY, maxY, minZ, maxZ);
                case NEG_Z -> cells(minX, maxX, minY, maxY, minZ, minZ);
                case POS_Z -> cells(minX, maxX, minY, maxY, maxZ, maxZ);
            };
        }

        @Nonnull
        private static List<FragmentCell> cells(int minX,
            int maxX,
            int minY,
            int maxY,
            int minZ,
            int maxZ) {
            List<FragmentCell> cells = new ArrayList<>();
            for (int x = minX; x <= maxX; x++) {
                for (int y = minY; y <= maxY; y++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        cells.add(new FragmentCell(x, y, z));
                    }
                }
            }
            return cells;
        }
    }

    private record CuboidExpansion(@Nonnull FragmentBounds bounds,
                                   @Nonnull List<FragmentCell> cells) {
    }

    private enum ExpansionDirection {
        POS_X,
        NEG_X,
        POS_Y,
        NEG_Y,
        POS_Z,
        NEG_Z
    }

    record FragmentOffset(int dx, int dy, int dz, int distanceSquared) {
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
            particles = new ModelParticle[] {
                new ModelParticle(EXPLOSION_PARTICLE_SYSTEM_ID,
                    EntityPart.Entity,
                    null,
                    null,
                    1.0f,
                    null,
                    null,
                    false)
            };
            soundEventId = EXPLOSION_SOUND_EVENT_ID;
            soundEventIndex = soundEventIndex(soundEventId);
        }
    }
}
