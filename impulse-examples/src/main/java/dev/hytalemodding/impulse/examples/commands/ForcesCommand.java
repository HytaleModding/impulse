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
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import dev.hytalemodding.impulse.core.plugin.physicsstore.requests.BodyForceRequest;
import dev.hytalemodding.impulse.core.plugin.physicsstore.requests.PhysicsStoreRequest;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsWorldResource;
import dev.hytalemodding.impulse.core.plugin.simulation.PhysicsShapeSpec;
import dev.hytalemodding.impulse.core.plugin.simulation.RigidBodySpawnSettings;
import dev.hytalemodding.impulse.examples.commands.ExamplePhysicsUtils.PendingBlockBody;
import java.util.ArrayList;
import java.util.List;
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
        PendingBlockBody central = bodies.central();
        PendingBlockBody offCenter = bodies.offCenter();
        PendingBlockBody torque = bodies.torque();
        PendingBlockBody force = bodies.force();
        drawArrow(world, centralPosition, new Vector3d(2.0, 1.0, 0.0), DebugUtils.COLOR_GREEN);
        drawArrow(world, offCenterPosition, new Vector3d(2.0, 0.0, 0.0), DebugUtils.COLOR_YELLOW);
        drawArrow(world, torquePosition, new Vector3d(0.0, 0.0, 2.0), DebugUtils.COLOR_MAGENTA);
        drawArrow(world, forcePosition, new Vector3d(2.0, 0.0, 0.0), DebugUtils.COLOR_CYAN);
        ExamplePhysicsUtils.attachPhysicsStoreBlockBody(store, time, central);
        ExamplePhysicsUtils.attachPhysicsStoreBlockBody(store, time, offCenter);
        ExamplePhysicsUtils.attachPhysicsStoreBlockBody(store, time, torque);
        ExamplePhysicsUtils.attachPhysicsStoreBlockBody(store, time, force);

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
        UUID spaceUuid;
        try {
            spaceUuid = ExamplePhysicsUtils.resolvePhysicsStoreSpaceUuid(world, spaceId);
        } catch (IllegalStateException exception) {
            return null;
        }
        if (spaceUuid == null) {
            return null;
        }

        List<PhysicsStoreRequest> requests = new ArrayList<>(8);
        PendingBlockBody central = spawnBox(requests, spaceUuid, spaceId, centralPosition);
        requests.add(BodyForceRequest.impulse(central.bodyKey().value(), 4.0f, 2.0f, 0.0f));
        PendingBlockBody offCenter = spawnBox(requests, spaceUuid, spaceId, offCenterPosition);
        requests.add(BodyForceRequest.impulseAt(offCenter.bodyKey().value(),
            3.5f,
            0.0f,
            0.0f,
            0.0f,
            0.5f,
            0.5f));
        PendingBlockBody torque = spawnBox(requests, spaceUuid, spaceId, torquePosition);
        requests.add(BodyForceRequest.torqueImpulse(torque.bodyKey().value(), 0.0f, 0.0f, 8.0f));
        PendingBlockBody force = spawnBox(requests, spaceUuid, spaceId, forcePosition);
        requests.add(BodyForceRequest.force(force.bodyKey().value(), 30.0f, 0.0f, 0.0f));
        try {
            ExamplePhysicsUtils.enqueuePhysicsStoreRequests(world, requests);
        } catch (IllegalStateException exception) {
            return null;
        }
        return new ForceDemoBodies(central, offCenter, torque, force);
    }

    private static PendingBlockBody spawnBox(@Nonnull List<PhysicsStoreRequest> requests,
        @Nonnull UUID spaceUuid,
        @Nonnull SpaceId spaceId,
        @Nonnull Vector3d position) {
        RigidBodyKey bodyKey = RigidBodyKey.random();
        requests.add(ExamplePhysicsUtils.bodyUpsertRequest(spaceUuid,
            bodyKey.value(),
            ExamplePhysicsUtils.toVector3f(position),
            PhysicsShapeSpec.box(0.5f, 0.5f, 0.5f),
            1.0f,
            RigidBodySpawnSettings.material(0.5f, 0.25f),
            null));
        return new PendingBlockBody(bodyKey,
            spaceId,
            ExamplePhysicsUtils.DEFAULT_BLOCK_TYPE,
            (float) position.x,
            (float) position.y,
            (float) position.z,
            true);
    }

    private record ForceDemoBodies(@Nonnull PendingBlockBody central,
                                   @Nonnull PendingBlockBody offCenter,
                                   @Nonnull PendingBlockBody torque,
                                   @Nonnull PendingBlockBody force) {
    }

    private static void drawArrow(@Nonnull World world,
        @Nonnull Vector3d position,
        @Nonnull Vector3d direction,
        @Nonnull Vector3f color) {
        DebugUtils.addArrow(world, new Vector3d(position).add(0.0, 0.5, 0.0), direction,
            color, 0.9f, 4.0f, DebugUtils.FLAG_FADE);
    }
}
