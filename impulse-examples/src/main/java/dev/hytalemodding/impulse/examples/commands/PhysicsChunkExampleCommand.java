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
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.plugin.modules.physicschunk.PhysicsChunkTerrain;
import dev.hytalemodding.impulse.core.plugin.modules.physicschunk.PhysicsChunkTerrainBuildStats;
import dev.hytalemodding.impulse.core.plugin.modules.physicschunk.PhysicsChunkTerrainPrewarmStats;
import dev.hytalemodding.impulse.core.plugin.modules.physicschunk.PhysicsChunkTerrainStats;
import dev.hytalemodding.impulse.core.plugin.physicsstore.PhysicsThreading;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nonnull;

import dev.hytalemodding.impulse.examples.utils.ExamplePhysicsUtils;
import org.joml.Vector3d;

/**
 * Debug commands for manually building/clearing PhysicsChunk terrain collision.
 */
public class PhysicsChunkExampleCommand extends AbstractCommandCollection {

    public PhysicsChunkExampleCommand() {
        super("physicschunk", "Build static Impulse chunk collision from nearby world blocks");
        addSubCommand(new BuildCommand());
        addSubCommand(new EnsureCommand());
        addSubCommand(new ClearCommand());
        addSubCommand(new StatsCommand());
    }

    @Nonnull
    private static Store<PhysicsStore> physicsStore(@Nonnull World world) {
        return PhysicsThreading.store(world);
    }

    private static final class BuildCommand extends AbstractAsyncPlayerCommand {

        private static final int DEFAULT_RADIUS = 8;
        private static final int MAX_RADIUS = 24;

        private final OptionalArg<Integer> radiusArg = withOptionalArg(
            "radius",
            "Block radius around the player to scan",
            ArgTypes.INTEGER);
        private final OptionalArg<Integer> spaceArg = withOptionalArg(
            "space",
            "Physics space id to target",
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
            Vector3d playerPos = new Vector3d(playerRef.getTransform().getPosition());

            int radius = ExamplePhysicsUtils.optionalInt(ctx, radiusArg, DEFAULT_RADIUS, 1, MAX_RADIUS);
            SpaceId spaceId = ExamplePhysicsUtils.spaceId(ctx, world, spaceArg);
            if (spaceId == null) {
                return CompletableFuture.completedFuture(null);
            }
            Store<PhysicsStore> physicsStore = physicsStore(world);
            PhysicsChunkTerrainBuildStats stats = PhysicsChunkTerrain.rebuildAround(world,
                physicsStore,
                spaceId,
                playerPos,
                radius);

            ctx.sender().sendMessage(Message.raw("Built PhysicsChunk terrain collision: scanned "
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

    private static final class EnsureCommand extends AbstractAsyncPlayerCommand {

        private static final int DEFAULT_RADIUS = 8;
        private static final int MAX_RADIUS = 24;

        private final OptionalArg<Integer> radiusArg = withOptionalArg(
            "radius",
            "Block radius around the player to scan",
            ArgTypes.INTEGER);
        private final OptionalArg<Integer> spaceArg = withOptionalArg(
            "space",
            "Physics space id to target",
            ArgTypes.INTEGER);

        private EnsureCommand() {
            super("ensure", "Ensure nearby static voxel collision is available");
        }

        @Nonnull
        @Override
        protected CompletableFuture<Void> executeAsync(@Nonnull CommandContext ctx,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world) {
            Vector3d playerPos = new Vector3d(playerRef.getTransform().getPosition());
            int radius = ExamplePhysicsUtils.optionalInt(ctx, radiusArg, DEFAULT_RADIUS, 1, MAX_RADIUS);
            SpaceId spaceId = ExamplePhysicsUtils.spaceId(ctx, world, spaceArg);
            if (spaceId == null) {
                return CompletableFuture.completedFuture(null);
            }

            Store<PhysicsStore> physicsStore = physicsStore(world);
            PhysicsChunkTerrainPrewarmStats stats = PhysicsChunkTerrain.ensureAround(world,
                physicsStore,
                spaceId,
                List.of(playerPos),
                radius,
                Math.max(0L, world.getTick()));

            ctx.sender().sendMessage(Message.raw("Ensured PhysicsChunk terrain collision: targets "
                + stats.sectionTargets()
                + ", bodies "
                + stats.buildStats().colliderBodies()
                + ", removed "
                + stats.buildStats().removedBodies()
                + "."));
            return CompletableFuture.completedFuture(null);
        }
    }

    private static final class ClearCommand extends AbstractAsyncPlayerCommand {

        private final OptionalArg<Integer> spaceArg = withOptionalArg(
            "space",
            "Physics space id to target",
            ArgTypes.INTEGER);

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
            SpaceId spaceId = ExamplePhysicsUtils.spaceId(ctx, world, spaceArg);
            if (spaceId == null) {
                return CompletableFuture.completedFuture(null);
            }
            Store<PhysicsStore> physicsStore = physicsStore(world);
            int removed = PhysicsChunkTerrain.clearSpace(world, physicsStore, spaceId);
            ctx.sender().sendMessage(Message.raw("Removed " + removed
                + " PhysicsChunk terrain bodies."));
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
            PhysicsChunkTerrainStats stats = PhysicsChunkTerrain.stats(world);
            ctx.sender().sendMessage(Message.raw("World voxel collision: "
                + stats.spaces() + " spaces, "
                + stats.sections() + " sections, "
                + stats.bodies() + " bodies, "
                + stats.shapeTemplates() + " shape templates."));
            return CompletableFuture.completedFuture(null);
        }
    }
}
