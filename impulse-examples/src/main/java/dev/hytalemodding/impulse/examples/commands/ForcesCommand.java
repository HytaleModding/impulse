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
        PendingBlockBody[] pending = new PendingBlockBody[4];
        ExamplePhysicsUtils.requireApplied(resource.submitCommands(Math.max(0L, world.getTick()), 8, commands -> {
            pending[0] = spawnBox(commands, spaceId, centralPosition);
            commands.applyBodyImpulse(pending[0].bodyKey(), 4.0f, 2.0f, 0.0f);
            pending[1] = spawnBox(commands, spaceId, offCenterPosition);
            commands.applyBodyImpulse(pending[1].bodyKey(), 3.5f, 0.0f, 0.0f, 0.0f, 0.5f, 0.5f);
            pending[2] = spawnBox(commands, spaceId, torquePosition);
            commands.applyBodyTorqueImpulse(pending[2].bodyKey(), 0.0f, 0.0f, 8.0f);
            pending[3] = spawnBox(commands, spaceId, forcePosition);
            commands.applyBodyForce(pending[3].bodyKey(), 30.0f, 0.0f, 0.0f);
        }), "apply force demo");
        PendingBlockBody central = pending[0];
        PendingBlockBody offCenter = pending[1];
        PendingBlockBody torque = pending[2];
        PendingBlockBody force = pending[3];
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

    private static void drawArrow(@Nonnull World world,
        @Nonnull Vector3d position,
        @Nonnull Vector3d direction,
        @Nonnull Vector3f color) {
        DebugUtils.addArrow(world, new Vector3d(position).add(0.0, 0.5, 0.0), direction,
            color, 0.9f, 4.0f, DebugUtils.FLAG_FADE);
    }
}
