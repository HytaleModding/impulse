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
import com.hypixel.hytale.server.core.modules.time.TimeResource;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import com.hypixel.hytale.server.core.util.TargetUtil;
import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.plugin.modules.physicschunk.PhysicsWorldCollision;
import dev.hytalemodding.impulse.core.plugin.modules.physicschunk.WorldCollisionPrewarmStats;
import dev.hytalemodding.impulse.core.plugin.physicsstore.PhysicsAsync;
import dev.hytalemodding.impulse.core.plugin.physicsstore.PhysicsRaycasts;
import dev.hytalemodding.impulse.core.plugin.components.BodyCommandComponent;
import dev.hytalemodding.impulse.core.plugin.components.DynamicsComponent;
import dev.hytalemodding.impulse.core.plugin.components.TargetComponent;
import dev.hytalemodding.impulse.core.plugin.components.UuidComponent;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsWorldResource;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsEventCollectionMode;
import dev.hytalemodding.impulse.core.plugin.simulation.PhysicsShapeSpec;
import dev.hytalemodding.impulse.core.plugin.simulation.RigidBodySpawnSettings;
import dev.hytalemodding.impulse.core.plugin.simulation.view.RaycastHitView;
import dev.hytalemodding.impulse.early.PhysicsStoreWorld;
import dev.hytalemodding.impulse.examples.explosive.ExplosiveBlockComponent;
import dev.hytalemodding.impulse.examples.explosive.ExplosiveBlockPolicy;
import dev.hytalemodding.impulse.examples.explosive.ExplosiveFuseComponent;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import dev.hytalemodding.impulse.examples.utils.ExampleBlockEntityVisuals;
import dev.hytalemodding.impulse.examples.utils.ExamplePhysicsUtils;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector3i;

final class PhysicsStoreExampleCommands {

    private static final double RAY_LENGTH = 24.0;

    private PhysicsStoreExampleCommands() {
    }

    abstract static class PhysicsStorePlayerCommand extends AbstractAsyncPlayerCommand {

        protected final OptionalArg<Integer> spaceArg = withOptionalArg(
            "space",
            "Physics space id to target",
            ArgTypes.INTEGER);

        protected PhysicsStorePlayerCommand(@Nonnull String name, @Nonnull String description) {
            super(name, description);
        }

        @Nullable
        protected SpaceId resolveSpace(@Nonnull CommandContext ctx,
            @Nonnull World world) {
            return ExamplePhysicsUtils.spaceId(ctx, world, spaceArg);
        }
    }

    static final class BumperCommand extends PhysicsStorePlayerCommand {

        private final OptionalArg<Integer> strengthArg = withOptionalArg(
            "strength",
            "Impulse strength",
            ArgTypes.INTEGER);

        BumperCommand() {
            super("bumper", "Apply an impulse to the rigid body in the player view");
        }

        @Nonnull
        @Override
        protected CompletableFuture<Void> executeAsync(@Nonnull CommandContext ctx,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world) {
            SpaceId spaceId = resolveSpace(ctx, world);
            if (spaceId == null) {
                return CompletableFuture.completedFuture(null);
            }
            Ref<PhysicsStore> spaceRef = ExamplePhysicsUtils.resolveSpaceRef(world,
                spaceId);
            if (spaceRef == null) {
                ctx.sender().sendMessage(Message.raw("PhysicsStore space id=" + spaceId.value()
                    + " is not bound yet."));
                return CompletableFuture.completedFuture(null);
            }
            return PhysicsAsync.acceptOnWorldThread(world,
                raycastAsync(store, ref, spaceRef),
                hit -> applyImpulse(ctx, store, ref, world, hit));
        }

        private void applyImpulse(@Nonnull CommandContext ctx,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull World world,
            @Nullable RaycastHitView hit) {
            if (hit == null || hit.bodyRef() == null || !hit.bodyRef().isValid()) {
                ctx.sender().sendMessage(Message.raw("No rigid body in view."));
                return;
            }

            int strength = ExamplePhysicsUtils.optionalInt(ctx, strengthArg, 8, 1, 64);
            Vector3d impulse = new Vector3d(TargetUtil.getLook(ref, store)
                .getDirection()).mul(strength);
            Ref<PhysicsStore> bodyRef = hit.bodyRef();
            Store<PhysicsStore> physicsStore = bodyRef.getStore();
            ExamplePhysicsUtils.appendPhysicsStoreBodyCommand(physicsStore,
                bodyRef,
                BodyCommandComponent.vector(BodyCommandComponent.Kind.IMPULSE,
                    (float) impulse.x,
                    (float) impulse.y,
                    (float) impulse.z,
                    false,
                    0.0f,
                    0.0f,
                    0.0f));

            ctx.sender().sendMessage(Message.raw("Queued PhysicsStore impulse command for "
                + bodyRef + "."));
        }
    }

    static final class PlatformCommand extends PhysicsStorePlayerCommand {

        PlatformCommand() {
            super("platform", "Spawn a kinematic PhysicsStore body target");
        }

        @Nonnull
        @Override
        protected CompletableFuture<Void> executeAsync(@Nonnull CommandContext ctx,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world) {
            Vector3d playerPos = new Vector3d(playerRef.getTransform().getPosition());
            SpaceId spaceId = resolveSpace(ctx, world);
            if (spaceId == null) {
                return CompletableFuture.completedFuture(null);
            }

            UUID bodyUuid = UUID.randomUUID();
            Vector3d spawn = new Vector3d(playerPos).add(0.0, 2.0, 0.0);
            Ref<PhysicsStore> spaceRef = ExamplePhysicsUtils.resolveSpaceRef(world,
                spaceId);
            if (spaceRef == null) {
                ctx.sender().sendMessage(Message.raw("PhysicsStore space id=" + spaceId.value()
                    + " is not bound yet."));
                return CompletableFuture.completedFuture(null);
            }
            Vector3f targetPosition = vector(spawn);
            var bodyRef = ExamplePhysicsUtils.addPhysicsStoreBody(world,
                ExamplePhysicsUtils.bodyEntity(spaceRef,
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
            ExamplePhysicsUtils.attachBlockBody(store,
                time,
                new ExamplePhysicsUtils.CreatedBlockBody(bodyUuid,
                    bodyRef,
                    spaceId,
                    ExamplePhysicsUtils.DEFAULT_BLOCK_TYPE,
                    (float) spawn.x,
                    (float) spawn.y,
                    (float) spawn.z,
                    false));

            ctx.sender().sendMessage(Message.raw("Queued PhysicsStore kinematic platform " + bodyUuid
                + " in space " + spaceId.value() + "."));
            return CompletableFuture.completedFuture(null);
        }
    }

    static final class PickupCommand extends PhysicsStorePlayerCommand {

        PickupCommand() {
            super("pickup", "Attach a view entity to the physics body in view");
        }

        @Nonnull
        @Override
        protected CompletableFuture<Void> executeAsync(@Nonnull CommandContext ctx,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world) {
            SpaceId spaceId = resolveSpace(ctx, world);
            if (spaceId == null) {
                return CompletableFuture.completedFuture(null);
            }
            Ref<PhysicsStore> spaceRef = ExamplePhysicsUtils.resolveSpaceRef(world,
                spaceId);
            if (spaceRef == null) {
                ctx.sender().sendMessage(Message.raw("PhysicsStore space id=" + spaceId.value()
                    + " is not bound yet."));
                return CompletableFuture.completedFuture(null);
            }
            return PhysicsAsync.acceptOnWorldThread(world,
                raycastAsync(store, ref, spaceRef),
                hit -> attachView(ctx, store, hit));
        }

        private static void attachView(@Nonnull CommandContext ctx,
            @Nonnull Store<EntityStore> store,
            @Nullable RaycastHitView hit) {
            if (hit == null || hit.bodyRef() == null || !hit.bodyRef().isValid()) {
                ctx.sender().sendMessage(Message.raw("No rigid body in view."));
                return;
            }
            UUID bodyUuid = physicsStoreBodyUuid(hit.bodyRef());
            if (bodyUuid == null) {
                ctx.sender().sendMessage(Message.raw("PhysicsStore body has no persistent UUID."));
                return;
            }

            Vector3d point = new Vector3d(hit.point().x, hit.point().y, hit.point().z);
            TimeResource time = store.getResource(TimeResource.getResourceType());
            ExamplePhysicsUtils.spawnExternalBodyViewBlockEntity(store,
                time,
                bodyUuid,
                point,
                ExamplePhysicsUtils.DEFAULT_BLOCK_TYPE);

            ctx.sender().sendMessage(Message.raw("Attached view-only entity to "
                + bodyUuid + "."));
        }

        @Nullable
        private static UUID physicsStoreBodyUuid(@Nonnull Ref<PhysicsStore> bodyRef) {
            UuidComponent uuid = bodyRef.getStore()
                .getComponent(bodyRef, UuidComponent.getComponentType());
            return uuid != null ? uuid.getUuid() : null;
        }
    }

    static final class ExplosiveCommand extends PhysicsStorePlayerCommand {

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

        ExplosiveCommand() {
            super("explosive", "Drop an explosive block that fragments terrain on impact");
        }

        @Nonnull
        @Override
        protected CompletableFuture<Void> executeAsync(@Nonnull CommandContext ctx,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world) {
            SpaceId spaceId = resolveSpace(ctx, world);
            if (spaceId == null) {
                return CompletableFuture.completedFuture(null);
            }

            String blockType = blockTypeArg.provided(ctx)
                ? ExampleBlockEntityVisuals.resolveBlockType(blockTypeArg.get(ctx))
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

            Vector3d spawn = spawnPosition(store, ref, world);
            if (blockType(blockType) == null) {
                ctx.sender().sendMessage(Message.raw("No valid explosive block type is available."));
                return CompletableFuture.completedFuture(null);
            }
            PhysicsWorldResource resource = store.getResource(PhysicsWorldResource.getResourceType());
            boolean contactEventsEnabled = contactEventsEnabled(resource);
            Ref<PhysicsStore> spaceRef = ExamplePhysicsUtils.resolveSpaceRef(world,
                spaceId);
            if (spaceRef == null) {
                ctx.sender().sendMessage(Message.raw("PhysicsStore space id=" + spaceId.value()
                    + " is not bound yet."));
                return CompletableFuture.completedFuture(null);
            }
            WorldCollisionPrewarmStats stats = PhysicsWorldCollision.ensureAround(world,
                ((PhysicsStoreWorld) world).getPhysicsStore().getStore(),
                spaceId,
                List.of(spawn),
                Math.max(8, radius + 6),
                Math.max(0L, world.getTick()));

            UUID bodyUuid = UUID.randomUUID();
            ExamplePhysicsUtils.addPhysicsStoreBody(world,
                ExamplePhysicsUtils.bodyEntity(spaceRef,
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

            ctx.sender().sendMessage(Message.raw("Queued Impulse explosive body " + bodyUuid
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
            @Nonnull World world) {
            Transform look = TargetUtil.getLook(ref, store);
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
    private static CompletionStage<RaycastHitView> raycastAsync(@Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Ref<PhysicsStore> spaceRef) {
        Transform look = TargetUtil.getLook(ref, store);
        Vector3d start = new Vector3d(look.getPosition());
        Vector3d end = new Vector3d(start)
            .add(new Vector3d(look.getDirection()).mul(RAY_LENGTH));
        return PhysicsRaycasts.closestAsync(store.getExternalData().getWorld(),
                spaceRef,
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
