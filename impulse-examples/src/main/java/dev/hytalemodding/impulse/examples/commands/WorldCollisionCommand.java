package dev.hytalemodding.impulse.examples.commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncPlayerCommand;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.api.PhysicsSpace;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsWorldResource;
import dev.hytalemodding.impulse.core.plugin.collision.WorldCollisionBuildStats;
import dev.hytalemodding.impulse.core.plugin.collision.WorldCollisionStats;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nonnull;
import org.joml.Vector3d;

/**
 * Debug commands for manually building/clearing world voxel collision.
 */
public class WorldCollisionCommand extends AbstractCommandCollection {

    public WorldCollisionCommand() {
        super("world-collision", "Build static Impulse voxel collision from nearby world blocks");
        addSubCommand(new BuildCommand());
        addSubCommand(new ClearCommand());
        addSubCommand(new StatsCommand());
    }

    private static final class BuildCommand extends AbstractAsyncPlayerCommand {

        private static final int DEFAULT_RADIUS = 8;
        private static final int MAX_RADIUS = 24;

        private final OptionalArg<Integer> radiusArg = withOptionalArg(
            "radius",
            "Block radius around the player to scan",
            ArgTypes.INTEGER);

        private BuildCommand() {
            super("build", "Rebuild nearby static voxel collision");
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

            int radius = ExamplePhysicsUtils.optionalInt(ctx, radiusArg, DEFAULT_RADIUS, 1, MAX_RADIUS);
            PhysicsWorldResource resource = ExamplePhysicsUtils.resource(store);
            PhysicsSpace space = ExamplePhysicsUtils.defaultSpace(ctx, resource);
            if (space == null) {
                return CompletableFuture.completedFuture(null);
            }
            WorldCollisionBuildStats stats = resource.rebuildWorldCollisionAround(world,
                space.getId(),
                playerPos,
                radius);

            ctx.sender().sendMessage(Message.raw("Built world voxel collision: scanned "
                + stats.scannedBlocks()
                + " blocks, solid " + stats.solidBlocks()
                + ", culled " + stats.culledInteriorBlocks()
                + ", full runs " + stats.fullCubeRuns()
                + ", detail boxes " + stats.detailBoxes()
                + ", sections " + stats.sectionsBuilt()
                + ", rebuilt " + stats.sectionsRebuilt()
                + ", voxel bodies " + stats.voxelBodies()
                + ", bodies " + stats.colliderBodies()
                + ", removed " + stats.removedBodies()
                + "."));
            return CompletableFuture.completedFuture(null);
        }
    }

    private static final class ClearCommand extends AbstractAsyncPlayerCommand {

        private ClearCommand() {
            super("clear", "Remove generated static voxel collision");
        }

        @Nonnull
        @Override
        protected CompletableFuture<Void> executeAsync(@Nonnull CommandContext ctx,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world) {
            PhysicsWorldResource resource = ExamplePhysicsUtils.resource(store);
            PhysicsSpace space = ExamplePhysicsUtils.defaultSpace(ctx, resource);
            if (space == null) {
                return CompletableFuture.completedFuture(null);
            }
            int removed = resource.clearWorldCollision(space.getId());
            ctx.sender().sendMessage(Message.raw("Removed " + removed
                + " world voxel collision bodies."));
            return CompletableFuture.completedFuture(null);
        }
    }

    private static final class StatsCommand extends AbstractAsyncPlayerCommand {

        private StatsCommand() {
            super("stats", "Show generated static voxel collision stats");
        }

        @Nonnull
        @Override
        protected CompletableFuture<Void> executeAsync(@Nonnull CommandContext ctx,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world) {
            PhysicsWorldResource resource = ExamplePhysicsUtils.resource(store);
            WorldCollisionStats stats = resource.getWorldCollisionStats();
            ctx.sender().sendMessage(Message.raw("World voxel collision: "
                + stats.spaces() + " spaces, "
                + stats.sections() + " sections, "
                + stats.bodies() + " bodies, "
                + stats.shapeTemplates() + " shape templates."));
            return CompletableFuture.completedFuture(null);
        }
    }
}
