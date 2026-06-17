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
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.plugin.components.BodyCommandComponent;
import dev.hytalemodding.impulse.core.plugin.simulation.PhysicsShapeSpec;
import dev.hytalemodding.impulse.core.plugin.simulation.RigidBodySpawnSettings;
import dev.hytalemodding.impulse.examples.utils.ExamplePhysicsUtils;
import dev.hytalemodding.impulse.examples.utils.ExamplePhysicsUtils.CreatedBlockBody;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
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
        Vector3d playerPos = new Vector3d(playerRef.getTransform().getPosition());

        SpaceId spaceId = ExamplePhysicsUtils.spaceId(ctx, world, spaceArg);
        if (spaceId == null) {
            return CompletableFuture.completedFuture(null);
        }
        TimeResource time = store.getResource(TimeResource.getResourceType());

        Vector3d origin = new Vector3d(playerPos).add(-2.0, 4.0, 4.0);
        Vector3d centralPosition = new Vector3d(origin);
        Vector3d offCenterPosition = new Vector3d(origin).add(2.0, 0.0, 0.0);
        Vector3d torquePosition = new Vector3d(origin).add(4.0, 0.0, 0.0);
        Vector3d forcePosition = new Vector3d(origin).add(6.0, 0.0, 0.0);
        ForceDemoBodies bodies = tryCreatePhysicsStoreDemo(world,
            spaceId,
            centralPosition,
            offCenterPosition,
            torquePosition,
            forcePosition);
        if (bodies == null) {
            ctx.sender().sendMessage(Message.raw(
                "Cannot spawn force demo because the target space is not bound in PhysicsStore."));
            return CompletableFuture.completedFuture(null);
        }
        CreatedBlockBody central = bodies.central();
        CreatedBlockBody offCenter = bodies.offCenter();
        CreatedBlockBody torque = bodies.torque();
        CreatedBlockBody force = bodies.force();
        drawArrow(world, centralPosition, new Vector3d(2.0, 1.0, 0.0), DebugUtils.COLOR_GREEN);
        drawArrow(world, offCenterPosition, new Vector3d(2.0, 0.0, 0.0), DebugUtils.COLOR_YELLOW);
        drawArrow(world, torquePosition, new Vector3d(0.0, 0.0, 2.0), DebugUtils.COLOR_MAGENTA);
        drawArrow(world, forcePosition, new Vector3d(2.0, 0.0, 0.0), DebugUtils.COLOR_CYAN);
        ExamplePhysicsUtils.attachBlockBody(store, time, central);
        ExamplePhysicsUtils.attachBlockBody(store, time, offCenter);
        ExamplePhysicsUtils.attachBlockBody(store, time, torque);
        ExamplePhysicsUtils.attachBlockBody(store, time, force);

        ctx.sender().sendMessage(Message.raw(
            "Spawned force demo: central impulse, off-center impulse, torque, and force."));
        return CompletableFuture.completedFuture(null);
    }

    @Nullable
    private static ForceDemoBodies tryCreatePhysicsStoreDemo(@Nonnull World world,
        @Nonnull SpaceId spaceId,
        @Nonnull Vector3d centralPosition,
        @Nonnull Vector3d offCenterPosition,
        @Nonnull Vector3d torquePosition,
        @Nonnull Vector3d forcePosition) {
        Ref<PhysicsStore> spaceRef;
        try {
            spaceRef = ExamplePhysicsUtils.resolveSpaceRef(world, spaceId);
        } catch (IllegalStateException exception) {
            return null;
        }
        if (spaceRef == null) {
            return null;
        }

        try {
            CreatedBlockBody central = spawnBox(world,
                spaceRef,
                spaceId,
                centralPosition,
                BodyCommandComponent.vector(BodyCommandComponent.Kind.IMPULSE,
                    4.0f,
                    2.0f,
                    0.0f,
                    false,
                    0.0f,
                    0.0f,
                    0.0f));
            CreatedBlockBody offCenter = spawnBox(world,
                spaceRef,
                spaceId,
                offCenterPosition,
                BodyCommandComponent.vector(BodyCommandComponent.Kind.IMPULSE,
                    3.5f,
                    0.0f,
                    0.0f,
                    true,
                    0.0f,
                    0.5f,
                    0.5f));
            CreatedBlockBody torque = spawnBox(world,
                spaceRef,
                spaceId,
                torquePosition,
                BodyCommandComponent.vector(BodyCommandComponent.Kind.TORQUE_IMPULSE,
                    0.0f,
                    0.0f,
                    8.0f,
                    false,
                    0.0f,
                    0.0f,
                    0.0f));
            CreatedBlockBody force = spawnBox(world,
                spaceRef,
                spaceId,
                forcePosition,
                BodyCommandComponent.vector(BodyCommandComponent.Kind.FORCE,
                    30.0f,
                    0.0f,
                    0.0f,
                    false,
                    0.0f,
                    0.0f,
                    0.0f));
            return new ForceDemoBodies(central, offCenter, torque, force);
        } catch (IllegalStateException exception) {
            return null;
        }
    }

    private static CreatedBlockBody spawnBox(@Nonnull World world,
        @Nonnull Ref<PhysicsStore> spaceRef,
        @Nonnull SpaceId spaceId,
        @Nonnull Vector3d position,
        @Nonnull BodyCommandComponent command) {
        UUID bodyUuid = UUID.randomUUID();
        var bodyRef = ExamplePhysicsUtils.addPhysicsStoreBody(world,
            ExamplePhysicsUtils.bodyEntity(spaceRef,
                bodyUuid,
                ExamplePhysicsUtils.toVector3f(position),
                PhysicsShapeSpec.box(0.5f, 0.5f, 0.5f),
                1.0f,
                RigidBodySpawnSettings.material(0.5f, 0.25f),
                null),
            command);
        return new CreatedBlockBody(bodyUuid,
            bodyRef,
            spaceId,
            ExamplePhysicsUtils.DEFAULT_BLOCK_TYPE,
            (float) position.x,
            (float) position.y,
            (float) position.z,
            true);
    }

    private record ForceDemoBodies(@Nonnull CreatedBlockBody central,
                                   @Nonnull CreatedBlockBody offCenter,
                                   @Nonnull CreatedBlockBody torque,
                                   @Nonnull CreatedBlockBody force) {
    }

    private static void drawArrow(@Nonnull World world,
        @Nonnull Vector3d position,
        @Nonnull Vector3d direction,
        @Nonnull Vector3f color) {
        DebugUtils.addArrow(world, new Vector3d(position).add(0.0, 0.5, 0.0), direction,
            color, 0.9f, 4.0f, DebugUtils.FLAG_FADE);
    }
}
