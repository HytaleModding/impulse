package dev.hytalemodding.impulse.examples.commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncPlayerCommand;
import com.hypixel.hytale.server.core.modules.debug.DebugUtils;
import com.hypixel.hytale.server.core.modules.time.TimeResource;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsWorldResource;
import dev.hytalemodding.impulse.core.plugin.simulation.PhysicsRecipes;
import dev.hytalemodding.impulse.core.plugin.simulation.recorder.PhysicsCommandRecorder;
import dev.hytalemodding.impulse.core.plugin.simulation.PhysicsShapeSpec;
import dev.hytalemodding.impulse.core.plugin.simulation.RigidBodySpawnSettings;
import dev.hytalemodding.impulse.examples.commands.ExamplePhysicsUtils.PendingBlockBody;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nonnull;
import org.joml.Vector3d;
import org.joml.Vector3f;

public class ForcesCommand extends AbstractAsyncPlayerCommand {

    private final OptionalArg<Integer> spaceArg = this.withOptionalArg(
        "space",
        "Physics space id to target",
        ArgTypes.INTEGER);

    public ForcesCommand() {
        super("forces", "Spawn bodies that receive impulses, forces, and torque");
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

        PhysicsWorldResource resource = ExamplePhysicsUtils.resource(store);
        SpaceId spaceId = ExamplePhysicsUtils.spaceId(ctx, resource, spaceArg);
        if (spaceId == null) {
            return CompletableFuture.completedFuture(null);
        }
        TimeResource time = store.getResource(TimeResource.getResourceType());

        Vector3d origin = new Vector3d(playerPos).add(-2.0, 4.0, 4.0);
        Vector3d centralPosition = new Vector3d(origin);
        Vector3d offCenterPosition = new Vector3d(origin).add(2.0, 0.0, 0.0);
        Vector3d torquePosition = new Vector3d(origin).add(4.0, 0.0, 0.0);
        Vector3d forcePosition = new Vector3d(origin).add(6.0, 0.0, 0.0);
        ForceDemoBodies bodies = new ForceDemoBodies();
        ExamplePhysicsUtils.requireApplied(resource.submitCommands(Math.max(0L, world.getTick()), 8, commands -> {
            bodies.central = spawnBox(commands, spaceId, centralPosition);
            commands.compose(PhysicsRecipes.applyImpulse(bodies.central.bodyKey(),
                new Vector3f(4.0f, 2.0f, 0.0f)));
            bodies.offCenter = spawnBox(commands, spaceId, offCenterPosition);
            commands.applyBodyImpulse(bodies.offCenter.bodyKey(), 3.5f, 0.0f, 0.0f, 0.0f, 0.5f, 0.5f);
            bodies.torque = spawnBox(commands, spaceId, torquePosition);
            commands.applyBodyTorqueImpulse(bodies.torque.bodyKey(), 0.0f, 0.0f, 8.0f);
            bodies.force = spawnBox(commands, spaceId, forcePosition);
            commands.compose(PhysicsRecipes.applyForce(bodies.force.bodyKey(),
                new Vector3f(30.0f, 0.0f, 0.0f)));
        }), "apply force demo");
        PendingBlockBody central = bodies.requireCentral();
        PendingBlockBody offCenter = bodies.requireOffCenter();
        PendingBlockBody torque = bodies.requireTorque();
        PendingBlockBody force = bodies.requireForce();
        drawArrow(world, centralPosition, new Vector3d(2.0, 1.0, 0.0), DebugUtils.COLOR_GREEN);
        drawArrow(world, offCenterPosition, new Vector3d(2.0, 0.0, 0.0), DebugUtils.COLOR_YELLOW);
        drawArrow(world, torquePosition, new Vector3d(0.0, 0.0, 2.0), DebugUtils.COLOR_MAGENTA);
        drawArrow(world, forcePosition, new Vector3d(2.0, 0.0, 0.0), DebugUtils.COLOR_CYAN);
        ExamplePhysicsUtils.attachRecordedBlockBody(store, time, central);
        ExamplePhysicsUtils.attachRecordedBlockBody(store, time, offCenter);
        ExamplePhysicsUtils.attachRecordedBlockBody(store, time, torque);
        ExamplePhysicsUtils.attachRecordedBlockBody(store, time, force);

        ctx.sender().sendMessage(Message.raw(
            "Spawned force demo: central impulse, off-center impulse, torque, and force."));
        return CompletableFuture.completedFuture(null);
    }

    private static PendingBlockBody spawnBox(@Nonnull PhysicsCommandRecorder commandBuffer,
        @Nonnull SpaceId spaceId,
        @Nonnull Vector3d position) {
        return ExamplePhysicsUtils.recordBlockBodySpawn(commandBuffer,
            spaceId,
            position,
            PhysicsShapeSpec.box(0.5f, 0.5f, 0.5f),
            1.0f,
            RigidBodySpawnSettings.material(0.5f, 0.25f));
    }

    private static final class ForceDemoBodies {

        private PendingBlockBody central;
        private PendingBlockBody offCenter;
        private PendingBlockBody torque;
        private PendingBlockBody force;

        @Nonnull
        private PendingBlockBody requireCentral() {
            return require(central, "central");
        }

        @Nonnull
        private PendingBlockBody requireOffCenter() {
            return require(offCenter, "off-center");
        }

        @Nonnull
        private PendingBlockBody requireTorque() {
            return require(torque, "torque");
        }

        @Nonnull
        private PendingBlockBody requireForce() {
            return require(force, "force");
        }

        @Nonnull
        private static PendingBlockBody require(PendingBlockBody body, @Nonnull String name) {
            if (body == null) {
                throw new IllegalStateException("Missing " + name + " force demo body");
            }
            return body;
        }
    }

    private static void drawArrow(@Nonnull World world,
        @Nonnull Vector3d position,
        @Nonnull Vector3d direction,
        @Nonnull Vector3f color) {
        DebugUtils.addArrow(world, new Vector3d(position).add(0.0, 0.5, 0.0), direction,
            color, 0.9f, 4.0f, DebugUtils.FLAG_FADE);
    }
}
