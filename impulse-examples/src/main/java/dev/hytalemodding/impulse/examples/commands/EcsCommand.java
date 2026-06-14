package dev.hytalemodding.impulse.examples.commands;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Transform;
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
import dev.hytalemodding.impulse.core.plugin.physicsstore.PhysicsStoreAsync;
import dev.hytalemodding.impulse.core.plugin.physicsstore.PhysicsStoreRaycasts;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.BodyCommandComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.DynamicsComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.TargetComponent;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsWorldResource;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsEventCollectionMode;
import dev.hytalemodding.impulse.core.plugin.simulation.PhysicsShapeSpec;
import dev.hytalemodding.impulse.core.plugin.simulation.RigidBodySpawnSettings;
import dev.hytalemodding.impulse.core.plugin.simulation.view.RaycastHitView;
import dev.hytalemodding.impulse.examples.explosive.ExplosiveBlockComponent;
import dev.hytalemodding.impulse.examples.explosive.ExplosiveBlockPolicy;
import dev.hytalemodding.impulse.examples.explosive.ExplosiveFuseComponent;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
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
            return PhysicsStoreAsync.acceptOnWorldThread(world,
                raycastAsync(ctx, store, ref, spaceId),
                hit -> applyImpulse(ctx, store, ref, world, hit));
        }

        private void applyImpulse(@Nonnull CommandContext ctx,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull World world,
            @Nullable RaycastHitView hit) {
            if (hit == null || hit.bodyKey() == null) {
                ctx.sender().sendMessage(Message.raw("No rigid body in view."));
                return;
            }

            TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
            if (transform == null) {
                ctx.sender().sendMessage(Message.raw("Cannot determine player direction."));
                return;
            }
            int strength = ExamplePhysicsUtils.optionalInt(ctx, strengthArg, 8, 1, 64);
            Vector3d impulse = new Vector3d(ExamplePhysicsUtils.lookTransform(store, ref)
                .getDirection()).mul(strength);
            boolean applied = ExamplePhysicsUtils.appendPhysicsStoreBodyCommand(world,
                hit.bodyKey().value(),
                BodyCommandComponent.vector(BodyCommandComponent.Kind.IMPULSE,
                    (float) impulse.x,
                    (float) impulse.y,
                    (float) impulse.z,
                    false,
                    0.0f,
                    0.0f,
                    0.0f));
            if (!applied) {
                ctx.sender().sendMessage(Message.raw("Rigid body " + hit.bodyKey()
                    + " is not bound in PhysicsStore."));
                return;
            }

            ctx.sender().sendMessage(Message.raw("Queued ECS impulse command for "
                + hit.bodyKey() + "."));
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
            UUID spaceUuid = ExamplePhysicsUtils.resolvePhysicsStoreSpaceUuid(world, spaceId);
            if (spaceUuid == null) {
                ctx.sender().sendMessage(Message.raw("PhysicsStore space id=" + spaceId.value()
                    + " is not bound yet."));
                return CompletableFuture.completedFuture(null);
            }
            Vector3f targetPosition = vector(spawn);
            ExamplePhysicsUtils.addPhysicsStoreBody(world,
                ExamplePhysicsUtils.bodyRow(spaceUuid,
                    bodyUuid,
                    targetPosition,
                    PhysicsShapeSpec.box(0.5f, 0.5f, 0.5f),
                    0.0f,
                    RigidBodySpawnSettings.material(0.5f, 0.2f),
                    null),
                new DynamicsComponent(PhysicsBodyType.KINEMATIC,
                    0.0f,
                    0.0f,
                    0.0f,
                    false),
                target(targetPosition));

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
            return PhysicsStoreAsync.acceptOnWorldThread(world,
                raycastAsync(ctx, store, ref, spaceId),
                hit -> attachView(ctx, store, hit));
        }

        private static void attachView(@Nonnull CommandContext ctx,
            @Nonnull Store<EntityStore> store,
            @Nullable RaycastHitView hit) {
            if (hit == null || hit.bodyKey() == null) {
                ctx.sender().sendMessage(Message.raw("No rigid body in view."));
                return;
            }

            Vector3d point = new Vector3d(hit.point().x, hit.point().y, hit.point().z);
            TimeResource time = store.getResource(TimeResource.getResourceType());
            ExamplePhysicsUtils.spawnExternalBodyViewBlockEntity(store,
                time,
                hit.bodyKey(),
                point,
                ExamplePhysicsUtils.DEFAULT_BLOCK_TYPE);

            ctx.sender().sendMessage(Message.raw("Attached view-only ECS entity to "
                + hit.bodyKey() + "."));
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
            WorldCollisionPrewarmStats stats = ExamplePhysicsUtils.ensurePhysicsStoreWorldCollisionAround(store,
                world,
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
            UUID spaceUuid = ExamplePhysicsUtils.resolvePhysicsStoreSpaceUuid(world, spaceId);
            if (spaceUuid == null) {
                ctx.sender().sendMessage(Message.raw("PhysicsStore space id=" + spaceId.value()
                    + " is not bound yet."));
                return CompletableFuture.completedFuture(null);
            }
            WorldCollisionPrewarmStats stats = ExamplePhysicsUtils.ensurePhysicsStoreWorldCollisionAround(store,
                world,
                spaceId,
                List.of(spawn),
                Math.max(8, radius + 6),
                Math.max(0L, world.getTick()));

            RigidBodyKey bodyKey = RigidBodyKey.random();
            UUID bodyUuid = bodyKey.value();
            ExamplePhysicsUtils.addPhysicsStoreBody(world,
                ExamplePhysicsUtils.bodyRow(spaceUuid,
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
            Transform look = ExamplePhysicsUtils.lookTransform(store, ref);
            Vector3d eye = new Vector3d(look.getPosition());
            Vector3d direction = new Vector3d(look.getDirection());
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

    @Nonnull
    private static CompletionStage<RaycastHitView> raycastAsync(@Nonnull CommandContext ctx,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull SpaceId spaceId) {
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) {
            ctx.sender().sendMessage(Message.raw("Cannot determine player position."));
            return CompletableFuture.completedFuture(null);
        }

        Transform look = ExamplePhysicsUtils.lookTransform(store, ref);
        Vector3d start = new Vector3d(look.getPosition());
        Vector3d end = new Vector3d(start)
            .add(new Vector3d(look.getDirection()).mul(RAY_LENGTH));
        return PhysicsStoreRaycasts.closestAsync(store.getExternalData().getWorld(),
                spaceId,
                vector(start),
                vector(end))
            .thenApply(hit -> hit.orElse(null));
    }

    @Nonnull
    private static TargetComponent target(@Nonnull Vector3f targetPosition) {
        TargetComponent target = new TargetComponent();
        target.setActive(true);
        target.setPosition(targetPosition);
        target.setRotation(new Quaternionf());
        target.setLinearVelocity(new Vector3f());
        target.setAngularVelocity(new Vector3f());
        target.setTransformEnabled(true);
        target.setVelocityEnabled(false);
        target.setActivate(true);
        return target;
    }

    @Nonnull
    private static Vector3f vector(@Nonnull Vector3d value) {
        return new Vector3f((float) value.x, (float) value.y, (float) value.z);
    }
}
