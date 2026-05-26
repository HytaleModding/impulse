package dev.hytalemodding.impulse.examples.commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncPlayerCommand;
import com.hypixel.hytale.server.core.modules.debug.DebugUtils;
import com.hypixel.hytale.server.core.modules.time.TimeResource;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.api.PhysicsBody;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyId;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsWorldResource;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nonnull;
import org.joml.Vector3d;
import org.joml.Vector3f;

public class ForcesCommand extends AbstractAsyncPlayerCommand {

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
        SpaceId spaceId = ExamplePhysicsUtils.defaultSpaceId(ctx, resource);
        if (spaceId == null) {
            return CompletableFuture.completedFuture(null);
        }
        TimeResource time = store.getResource(TimeResource.getResourceType());

        Vector3d origin = new Vector3d(playerPos).add(-2.0, 4.0, 4.0);
        Vector3d centralPosition = new Vector3d(origin);
        PhysicsBodyId central = spawnBox(store, time, resource, spaceId, centralPosition);
        ExamplePhysicsUtils.physicsOwnerRun(store, "apply central impulse demo",
            access -> ExamplePhysicsUtils.requireLiveBody(access, central)
                .applyCentralImpulse(4.0f, 2.0f, 0.0f));
        drawArrow(world, centralPosition, new Vector3d(2.0, 1.0, 0.0), DebugUtils.COLOR_GREEN);

        Vector3d offCenterPosition = new Vector3d(origin).add(2.0, 0.0, 0.0);
        PhysicsBodyId offCenter = spawnBox(store, time, resource, spaceId, offCenterPosition);
        ExamplePhysicsUtils.physicsOwnerRun(store, "apply off-center impulse demo",
            access -> ExamplePhysicsUtils.requireLiveBody(access, offCenter)
                .applyImpulse(new Vector3f(3.5f, 0.0f, 0.0f),
                    new Vector3f(0.0f, 0.5f, 0.5f)));
        drawArrow(world, offCenterPosition, new Vector3d(2.0, 0.0, 0.0), DebugUtils.COLOR_YELLOW);

        Vector3d torquePosition = new Vector3d(origin).add(4.0, 0.0, 0.0);
        PhysicsBodyId torque = spawnBox(store, time, resource, spaceId, torquePosition);
        ExamplePhysicsUtils.physicsOwnerRun(store, "apply torque impulse demo",
            access -> ExamplePhysicsUtils.requireLiveBody(access, torque)
                .applyTorqueImpulse(new Vector3f(0.0f, 0.0f, 8.0f)));
        drawArrow(world, torquePosition, new Vector3d(0.0, 0.0, 2.0), DebugUtils.COLOR_MAGENTA);

        Vector3d forcePosition = new Vector3d(origin).add(6.0, 0.0, 0.0);
        PhysicsBodyId force = spawnBox(store, time, resource, spaceId, forcePosition);
        ExamplePhysicsUtils.physicsOwnerRun(store, "apply central force demo",
            access -> ExamplePhysicsUtils.requireLiveBody(access, force)
                .applyCentralForce(30.0f, 0.0f, 0.0f));
        drawArrow(world, forcePosition, new Vector3d(2.0, 0.0, 0.0), DebugUtils.COLOR_CYAN);

        ctx.sender().sendMessage(Message.raw(
            "Spawned force demo: central impulse, off-center impulse, torque, and force."));
        return CompletableFuture.completedFuture(null);
    }

    private static PhysicsBodyId spawnBox(@Nonnull Store<EntityStore> store,
        @Nonnull TimeResource time,
        @Nonnull PhysicsWorldResource resource,
        @Nonnull SpaceId spaceId,
        @Nonnull Vector3d position) {
        return ExamplePhysicsUtils.spawnBlockBody(store,
            time,
            resource,
            spaceId,
            position,
            bodySpace -> {
                PhysicsBody created = bodySpace.createBox(0.5f, 0.5f, 0.5f, 1.0f);
                created.setFriction(0.5f);
                created.setRestitution(0.25f);
                return created;
            }).bodyId();
    }

    private static void drawArrow(@Nonnull World world,
        @Nonnull Vector3d position,
        @Nonnull Vector3d direction,
        @Nonnull Vector3f color) {
        DebugUtils.addArrow(world, new Vector3d(position).add(0.0, 0.5, 0.0), direction,
            color, 0.9f, 4.0f, DebugUtils.FLAG_FADE);
    }
}
