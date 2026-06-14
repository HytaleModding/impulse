package dev.hytalemodding.impulse.examples.commands;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncPlayerCommand;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.time.TimeResource;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.TargetUtil;
import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import dev.hytalemodding.impulse.core.plugin.modules.worldcollision.WorldCollisionPrewarmStats;
import dev.hytalemodding.impulse.core.plugin.physicsstore.PhysicsStoreAccess;
import dev.hytalemodding.impulse.core.plugin.physicsstore.PhysicsStoreRaycasts;
import dev.hytalemodding.impulse.core.plugin.physicsstore.requests.BodyForceRequest;
import dev.hytalemodding.impulse.core.plugin.physicsstore.requests.BodyTargetRequest;
import dev.hytalemodding.impulse.core.plugin.physicsstore.requests.BodyTypeRequest;
import dev.hytalemodding.impulse.core.plugin.physicsstore.requests.BodyUpsertRequest;
import dev.hytalemodding.impulse.core.plugin.physicsstore.requests.PhysicsStoreRequest;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsWorldResource;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsEventCollectionMode;
import dev.hytalemodding.impulse.core.plugin.simulation.PhysicsShapeSpec;
import dev.hytalemodding.impulse.core.plugin.simulation.RigidBodySpawnSettings;
import dev.hytalemodding.impulse.core.plugin.simulation.view.RaycastHitView;
import dev.hytalemodding.impulse.examples.explosive.ExplosiveBlockComponent;
import dev.hytalemodding.impulse.examples.explosive.ExplosiveBlockPolicy;
import dev.hytalemodding.impulse.examples.explosive.ExplosiveFuseComponent;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector3i;

/**
 * ECS-first examples for durable rigid body definitions.
 */
public class EcsCommand extends AbstractCommandCollection {

    private static final double RAY_LENGTH = 24.0;

    public EcsCommand() {
        super("ecs", "ECS rigid-body examples");
        addSubCommand(new DropCommand());
        addSubCommand(new BumperCommand());
        addSubCommand(new PlatformCommand());
        addSubCommand(new PickupCommand());
        addSubCommand(new WorldCollisionCommand());
        addSubCommand(new ExplosiveCommand());
    }

    private abstract static class EcsPlayerCommand extends AbstractAsyncPlayerCommand {

        protected final OptionalArg<Integer> spaceArg = withOptionalArg(
            "space",
            "Physics space id to target",
            ArgTypes.INTEGER);

        protected EcsPlayerCommand(@Nonnull String name, @Nonnull String description) {
            super(name, description);
        }

        @Nullable
        protected SpaceId resolveSpace(@Nonnull CommandContext ctx,
            @Nonnull Store<EntityStore> store) {
            return ExamplePhysicsUtils.spaceId(ctx,
                ExamplePhysicsUtils.resource(store),
                spaceArg);
        }
    }

    private static final class DropCommand extends EcsPlayerCommand {

        private final OptionalArg<String> blockTypeArg = withOptionalArg(
            "blockType",
            "Hytale block type used for the attached visual entity",
            ArgTypes.STRING);

        private DropCommand() {
            super("drop", "Spawn a dynamic physics body from split ECS components");
        }

        @Nonnull
        @Override
        protected CompletableFuture<Void> executeAsync(@Nonnull CommandContext ctx,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world) {
            Vector3d playerPos = ExamplePhysicsUtils.playerPosition(ctx, store, ref);
            if (playerPos == null) {
                return CompletableFuture.completedFuture(null);
            }
            SpaceId spaceId = resolveSpace(ctx, store);
            if (spaceId == null) {
                return CompletableFuture.completedFuture(null);
            }

            Vector3d spawn = new Vector3d(playerPos).add(0.0, 5.0, 0.0);
            TimeResource time = store.getResource(TimeResource.getResourceType());
            ExamplePhysicsUtils.SpawnedBlockBody body = ExamplePhysicsUtils.spawnBlockBody(store,
                time,
                ExamplePhysicsUtils.resource(store),
                spaceId,
                spawn,
                blockType(ctx),
                PhysicsShapeSpec.box(0.5f, 0.5f, 0.5f),
                1.0f,
                RigidBodySpawnSettings.material(0.5f, 0.2f));

            ctx.sender().sendMessage(Message.raw("Queued ECS-authored physics body " + body.bodyKey()
                + " in space " + spaceId.value() + "."));
            return CompletableFuture.completedFuture(null);
        }

        @Nonnull
        private String blockType(@Nonnull CommandContext ctx) {
            return blockTypeArg.provided(ctx)
                ? ExamplePhysicsUtils.resolveBlockType(blockTypeArg.get(ctx))
                : ExamplePhysicsUtils.DEFAULT_BLOCK_TYPE;
        }
    }

    private static final class BumperCommand extends EcsPlayerCommand {

        private final OptionalArg<Integer> strengthArg = withOptionalArg(
            "strength",
            "Impulse strength",
            ArgTypes.INTEGER);

        private BumperCommand() {
            super("bumper", "Apply an impulse to the rigid body in the player view");
        }

        @Nonnull
        @Override
        protected CompletableFuture<Void> executeAsync(@Nonnull CommandContext ctx,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world) {
            SpaceId spaceId = resolveSpace(ctx, store);
            if (spaceId == null) {
                return CompletableFuture.completedFuture(null);
            }
            RaycastHitView hit = raycast(ctx, store, ref, spaceId);
            if (hit == null || hit.bodyKey() == null) {
                ctx.sender().sendMessage(Message.raw("No rigid body in view."));
                return CompletableFuture.completedFuture(null);
            }

            TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
            if (transform == null) {
                ctx.sender().sendMessage(Message.raw("Cannot determine player direction."));
                return CompletableFuture.completedFuture(null);
            }
            int strength = ExamplePhysicsUtils.optionalInt(ctx, strengthArg, 8, 1, 64);
            Vector3d impulse = ExamplePhysicsUtils.lookDirection(store, ref, transform).mul(strength);
            PhysicsStoreAccess.enqueue(world,
                BodyForceRequest.impulse(hit.bodyKey().value(),
                    (float) impulse.x,
                    (float) impulse.y,
                    (float) impulse.z));

            ctx.sender().sendMessage(Message.raw("Queued ECS impulse request for "
                + hit.bodyKey() + "."));
            return CompletableFuture.completedFuture(null);
        }
    }

    private static final class PlatformCommand extends EcsPlayerCommand {

        private PlatformCommand() {
            super("platform", "Spawn a kinematic rigid body target from ECS data");
        }

        @Nonnull
        @Override
        protected CompletableFuture<Void> executeAsync(@Nonnull CommandContext ctx,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world) {
            Vector3d playerPos = ExamplePhysicsUtils.playerPosition(ctx, store, ref);
            if (playerPos == null) {
                return CompletableFuture.completedFuture(null);
            }
            SpaceId spaceId = resolveSpace(ctx, store);
            if (spaceId == null) {
                return CompletableFuture.completedFuture(null);
            }

            RigidBodyKey bodyKey = RigidBodyKey.random();
            UUID bodyUuid = bodyKey.value();
            Vector3d spawn = new Vector3d(playerPos).add(0.0, 2.0, 0.0);
            UUID spaceUuid = PhysicsStoreAccess.resolveSpaceUuid(world, spaceId);
            if (spaceUuid == null) {
                ctx.sender().sendMessage(Message.raw("PhysicsStore space id=" + spaceId.value()
                    + " is not bound yet."));
                return CompletableFuture.completedFuture(null);
            }
            Vector3f targetPosition = vector(spawn);
            BodyUpsertRequest bodyRequest = ExamplePhysicsUtils.bodyUpsertRequest(spaceUuid,
                bodyUuid,
                targetPosition,
                PhysicsShapeSpec.box(0.5f, 0.5f, 0.5f),
                0.0f,
                RigidBodySpawnSettings.material(0.5f, 0.2f),
                null);
            List<PhysicsStoreRequest> requests = List.of(bodyRequest,
                BodyTypeRequest.of(bodyUuid, PhysicsBodyType.KINEMATIC, true),
                BodyTargetRequest.of(bodyUuid,
                    targetPosition,
                    new Quaternionf(),
                    new Vector3f(),
                    new Vector3f(),
                    true,
                    false,
                    true));
            PhysicsStoreAccess.enqueueAll(world, requests);

            TimeResource time = store.getResource(TimeResource.getResourceType());
            ExamplePhysicsUtils.attachPhysicsStoreBlockBody(store,
                time,
                new ExamplePhysicsUtils.PendingBlockBody(bodyKey,
                    spaceId,
                    ExamplePhysicsUtils.DEFAULT_BLOCK_TYPE,
                    (float) spawn.x,
                    (float) spawn.y,
                    (float) spawn.z,
                    false));

            ctx.sender().sendMessage(Message.raw("Queued ECS kinematic platform " + bodyKey
                + " in space " + spaceId.value() + "."));
            return CompletableFuture.completedFuture(null);
        }
    }

    private static final class PickupCommand extends EcsPlayerCommand {

        private PickupCommand() {
            super("pickup", "Attach a view entity to the physics body in view");
        }

        @Nonnull
        @Override
        protected CompletableFuture<Void> executeAsync(@Nonnull CommandContext ctx,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world) {
            SpaceId spaceId = resolveSpace(ctx, store);
            if (spaceId == null) {
                return CompletableFuture.completedFuture(null);
            }
            RaycastHitView hit = raycast(ctx, store, ref, spaceId);
            if (hit == null || hit.bodyKey() == null) {
                ctx.sender().sendMessage(Message.raw("No rigid body in view."));
                return CompletableFuture.completedFuture(null);
            }

            Vector3d point = new Vector3d(hit.point().x, hit.point().y, hit.point().z);
            TimeResource time = store.getResource(TimeResource.getResourceType());
            ExamplePhysicsUtils.spawnExternalBodyViewBlockEntity(store,
                time,
                hit.bodyKey(),
                spaceId,
                point,
                ExamplePhysicsUtils.DEFAULT_BLOCK_TYPE);

            ctx.sender().sendMessage(Message.raw("Attached view-only ECS entity to "
                + hit.bodyKey() + "."));
            return CompletableFuture.completedFuture(null);
        }
    }

    private static final class WorldCollisionCommand extends EcsPlayerCommand {

        private final OptionalArg<Integer> radiusArg = withOptionalArg(
            "radius",
            "Block radius around the player to scan",
            ArgTypes.INTEGER);

        private WorldCollisionCommand() {
            super("world-collision", "Ensure voxel collision around the player for an explicit space");
        }

        @Nonnull
        @Override
        protected CompletableFuture<Void> executeAsync(@Nonnull CommandContext ctx,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world) {
            Vector3d playerPos = ExamplePhysicsUtils.playerPosition(ctx, store, ref);
            if (playerPos == null) {
                return CompletableFuture.completedFuture(null);
            }
            SpaceId spaceId = resolveSpace(ctx, store);
            if (spaceId == null) {
                return CompletableFuture.completedFuture(null);
            }

            int radius = ExamplePhysicsUtils.optionalInt(ctx, radiusArg, 8, 1, 24);
            PhysicsWorldResource resource = ExamplePhysicsUtils.resource(store);
            WorldCollisionPrewarmStats stats = resource.ensureWorldCollisionAround(world,
                spaceId,
                List.of(playerPos),
                radius,
                0L);

            ctx.sender().sendMessage(Message.raw("Ensured ECS world collision: targets "
                + stats.sectionTargets()
                + ", bodies "
                + stats.buildStats().colliderBodies()
                + ", removed "
                + stats.buildStats().removedBodies()
                + "."));
            return CompletableFuture.completedFuture(null);
        }
    }

    private static final class ExplosiveCommand extends EcsPlayerCommand {

        private static final double FALLBACK_SPAWN_DISTANCE = 5.0;
        private static final int DEFAULT_EXPLOSION_RADIUS = 8;
        private static final int MAX_EXPLOSION_RADIUS = 24;
        private static final int DEFAULT_EXPLOSION_FRAGMENTS = 256;
        private static final int MAX_EXPLOSION_FRAGMENTS = 1024;
        private static final float SOURCE_FRICTION = 0.35f;
        private static final float SOURCE_RESTITUTION = 0.7f;

        private final OptionalArg<String> blockTypeArg = withOptionalArg(
            "blockType",
            "Hytale block type used for the primed explosive block",
            ArgTypes.STRING);
        private final OptionalArg<Integer> radiusArg = withOptionalArg(
            "radius",
            "Block radius fragmented when the explosive block hits world collision",
            ArgTypes.INTEGER);
        private final OptionalArg<Integer> maxFragmentsArg = withOptionalArg(
            "maxFragments",
            "Maximum blocks converted to physics fragments per explosion",
            ArgTypes.INTEGER);
        private final OptionalArg<Float> strengthArg = withOptionalArg(
            "strength",
            "Impulse strength applied to spawned fragments",
            ArgTypes.FLOAT);
        private final OptionalArg<Float> verticalLiftArg = withOptionalArg(
            "verticalLift",
            "Upward lift fraction applied to spawned fragments",
            ArgTypes.FLOAT);

        private ExplosiveCommand() {
            super("explosive", "Drop an ECS explosive block that fragments terrain on impact");
        }

        @Nonnull
        @Override
        protected CompletableFuture<Void> executeAsync(@Nonnull CommandContext ctx,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world) {
            TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
            if (transform == null) {
                ctx.sender().sendMessage(Message.raw("Cannot determine player position."));
                return CompletableFuture.completedFuture(null);
            }
            SpaceId spaceId = resolveSpace(ctx, store);
            if (spaceId == null) {
                return CompletableFuture.completedFuture(null);
            }

            String blockType = blockTypeArg.provided(ctx)
                ? ExamplePhysicsUtils.resolveBlockType(blockTypeArg.get(ctx))
                : ExamplePhysicsUtils.DEFAULT_BLOCK_TYPE;
            int radius = ExamplePhysicsUtils.optionalInt(ctx,
                radiusArg,
                DEFAULT_EXPLOSION_RADIUS,
                1,
                MAX_EXPLOSION_RADIUS);
            int maxFragments = ExamplePhysicsUtils.optionalInt(ctx,
                maxFragmentsArg,
                DEFAULT_EXPLOSION_FRAGMENTS,
                1,
                MAX_EXPLOSION_FRAGMENTS);
            float strength = optionalFloat(ctx, strengthArg, 12.0f, 0.0f, 128.0f);
            float verticalLift = optionalFloat(ctx, verticalLiftArg, 0.35f, 0.0f, 2.0f);

            Vector3d spawn = spawnPosition(store, ref, transform, world);
            if (blockType(blockType) == null) {
                ctx.sender().sendMessage(Message.raw("No valid explosive block type is available."));
                return CompletableFuture.completedFuture(null);
            }
            PhysicsWorldResource resource = ExamplePhysicsUtils.resource(store);
            boolean contactEventsEnabled = contactEventsEnabled(resource);
            UUID spaceUuid = PhysicsStoreAccess.resolveSpaceUuid(world, spaceId);
            if (spaceUuid == null) {
                ctx.sender().sendMessage(Message.raw("PhysicsStore space id=" + spaceId.value()
                    + " is not bound yet."));
                return CompletableFuture.completedFuture(null);
            }
            WorldCollisionPrewarmStats stats = resource.ensureWorldCollisionAround(world,
                spaceId,
                List.of(spawn),
                Math.max(8, radius + 6),
                Math.max(0L, world.getTick()));

            RigidBodyKey bodyKey = RigidBodyKey.random();
            UUID bodyUuid = bodyKey.value();
            PhysicsStoreAccess.enqueue(world,
                ExamplePhysicsUtils.bodyUpsertRequest(spaceUuid,
                    bodyUuid,
                    vector(spawn),
                    PhysicsShapeSpec.box(0.5f, 0.5f, 0.5f),
                    1.0f,
                    RigidBodySpawnSettings.material(SOURCE_FRICTION, SOURCE_RESTITUTION),
                    null));
            TimeResource time = store.getResource(TimeResource.getResourceType());
            ExplosiveBlockComponent settings = new ExplosiveBlockComponent(blockType,
                0,
                0,
                radius,
                maxFragments,
                strength,
                verticalLift);
            Holder<EntityStore> holder = ExamplePhysicsUtils.attachedPhysicsStoreBlockEntityHolder(time,
                bodyUuid,
                spaceId,
                blockType,
                spawn,
                new Vector3f(),
                new Quaternionf(),
                Float.NaN,
                true);
            holder.addComponent(ExplosiveBlockComponent.getComponentType(), settings);
            holder.addComponent(ExplosiveFuseComponent.getComponentType(), new ExplosiveFuseComponent());
            store.addEntity(holder, AddReason.SPAWN);

            ctx.sender().sendMessage(Message.raw("Queued Impulse explosive body " + bodyKey
                + " in space " + spaceId.value()
                + " radius=" + radius
                + " maxFragments=" + maxFragments
                + " collisionBodies=" + stats.buildStats().colliderBodies()
                + " contactEvents=" + contactEventsEnabled
                + "."));
            return CompletableFuture.completedFuture(null);
        }

        @Nullable
        private static BlockType blockType(@Nonnull String blockTypeId) {
            BlockType blockType = BlockType.getAssetMap().getAsset(blockTypeId);
            if (blockType != null) {
                return blockType;
            }
            return BlockType.getAssetMap().getAsset(ExamplePhysicsUtils.DEFAULT_BLOCK_TYPE);
        }

        private static boolean contactEventsEnabled(@Nonnull PhysicsWorldResource resource) {
            return resource.getWorldSettings().getEventCollectionMode() == PhysicsEventCollectionMode.CONTACTS;
        }

        @Nonnull
        private static Vector3d spawnPosition(@Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull TransformComponent transform,
            @Nonnull World world) {
            Vector3d eye = ExamplePhysicsUtils.eyePosition(store, ref, transform);
            Vector3d direction = ExamplePhysicsUtils.lookDirection(store, ref, transform);
            Vector3i target = TargetUtil.getTargetBlock(world,
                ExplosiveBlockPolicy::isSimpleFullCubeFragmentBlock,
                eye.x,
                eye.y,
                eye.z,
                direction.x,
                direction.y,
                direction.z,
                RAY_LENGTH);
            if (target != null) {
                return new Vector3d(target.x + 0.5, target.y + 3.5, target.z + 0.5);
            }
            return new Vector3d(eye)
                .add(new Vector3d(direction).mul(FALLBACK_SPAWN_DISTANCE))
                .add(0.0, 1.0, 0.0);
        }

        private static float optionalFloat(@Nonnull CommandContext ctx,
            @Nonnull OptionalArg<Float> arg,
            float defaultValue,
            float min,
            float max) {
            float value = arg.provided(ctx) ? arg.get(ctx) : defaultValue;
            if (!Float.isFinite(value)) {
                return defaultValue;
            }
            if (value < min) {
                return min;
            }
            return Math.min(value, max);
        }
    }

    @Nullable
    private static RaycastHitView raycast(@Nonnull CommandContext ctx,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull SpaceId spaceId) {
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) {
            ctx.sender().sendMessage(Message.raw("Cannot determine player position."));
            return null;
        }

        Vector3d start = ExamplePhysicsUtils.eyePosition(store, ref, transform);
        Vector3d end = new Vector3d(start)
            .add(ExamplePhysicsUtils.lookDirection(store, ref, transform).mul(RAY_LENGTH));
        Optional<RaycastHitView> hit = PhysicsStoreRaycasts.closestAsync(
                store.getExternalData().getWorld(),
                spaceId,
                vector(start),
                vector(end))
            .toCompletableFuture()
            .join();
        return hit.orElse(null);
    }

    @Nonnull
    private static Vector3f vector(@Nonnull Vector3d value) {
        return new Vector3f((float) value.x, (float) value.y, (float) value.z);
    }
}
