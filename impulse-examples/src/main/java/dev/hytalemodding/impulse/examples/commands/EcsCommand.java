package dev.hytalemodding.impulse.examples.commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
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
import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import dev.hytalemodding.impulse.core.plugin.components.RigidBodyComponent;
import dev.hytalemodding.impulse.core.plugin.components.RigidBodyKinematicTargetComponent;
import dev.hytalemodding.impulse.core.plugin.modules.worldcollision.WorldCollisionPrewarmStats;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsWorldResource;
import dev.hytalemodding.impulse.core.plugin.simulation.PhysicsCommandBuffer;
import dev.hytalemodding.impulse.core.plugin.simulation.view.RaycastHitView;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;

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
            super("drop", "Spawn an entity-owned dynamic rigid body from ECS components");
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
            Vector3d spawn = new Vector3d(playerPos).add(0.0, 5.0, 0.0);
            TimeResource time = store.getResource(TimeResource.getResourceType());
            ExamplePhysicsUtils.spawnRigidBodyComponentBlockEntity(store,
                time,
                bodyKey,
                spaceId,
                spawn,
                blockType(ctx),
                PhysicsBodyType.DYNAMIC,
                1.0f,
                RigidBodyComponent.Ownership.ENTITY_OWNED,
                null);

            ctx.sender().sendMessage(Message.raw("Queued ECS rigid body " + bodyKey
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
            PhysicsWorldResource resource = ExamplePhysicsUtils.resource(store);
            ExamplePhysicsUtils.requireApplied(PhysicsCommandBuffer.begin(resource, 0L, 1)
                .applyImpulse(hit.bodyKey(), impulse)
                .submit(), "ecs bumper impulse");

            ctx.sender().sendMessage(Message.raw("Applied ECS command-buffer impulse to "
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
            Vector3d spawn = new Vector3d(playerPos).add(0.0, 2.0, 0.0);
            RigidBodyKinematicTargetComponent target = ExamplePhysicsUtils.kinematicTargetAt(spawn);
            TimeResource time = store.getResource(TimeResource.getResourceType());
            ExamplePhysicsUtils.spawnRigidBodyComponentBlockEntity(store,
                time,
                bodyKey,
                spaceId,
                spawn,
                ExamplePhysicsUtils.DEFAULT_BLOCK_TYPE,
                PhysicsBodyType.KINEMATIC,
                0.0f,
                RigidBodyComponent.Ownership.ENTITY_OWNED,
                target);

            ctx.sender().sendMessage(Message.raw("Queued ECS kinematic platform " + bodyKey
                + " in space " + spaceId.value() + "."));
            return CompletableFuture.completedFuture(null);
        }
    }

    private static final class PickupCommand extends EcsPlayerCommand {

        private PickupCommand() {
            super("pickup", "Attach a detached-view entity to the rigid body in view");
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
            ExamplePhysicsUtils.spawnRigidBodyComponentBlockEntity(store,
                time,
                hit.bodyKey(),
                spaceId,
                point,
                ExamplePhysicsUtils.DEFAULT_BLOCK_TYPE,
                PhysicsBodyType.DYNAMIC,
                1.0f,
                RigidBodyComponent.Ownership.DETACHED_VIEW,
                null);

            ctx.sender().sendMessage(Message.raw("Attached detached-view ECS entity to "
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
        Optional<RaycastHitView> hit = PhysicsCommandBuffer.begin(ExamplePhysicsUtils.resource(store), 0L, 0)
            .raycastClosest(spaceId, start, end)
            .completion()
            .toCompletableFuture()
            .join();
        return hit.orElse(null);
    }
}
