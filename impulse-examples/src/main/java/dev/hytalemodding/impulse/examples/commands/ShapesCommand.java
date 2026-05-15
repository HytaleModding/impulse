package dev.hytalemodding.impulse.examples.commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.api.PhysicsAxis;
import dev.hytalemodding.impulse.api.PhysicsBody;
import dev.hytalemodding.impulse.api.PhysicsSpace;
import dev.hytalemodding.impulse.core.resources.PhysicsWorldResource;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nonnull;
import org.joml.Vector3d;

public class ShapesCommand extends AbstractAsyncPlayerCommand {

    public ShapesCommand() {
        super("shapes", "Spawn a row of collider shape examples");
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
        PhysicsSpace space = ExamplePhysicsUtils.defaultSpace(resource, world);
        ExamplePhysicsUtils.enableDebug(store, playerRef);

        Vector3d origin = new Vector3d(playerPos).add(-4.0, 3.0, 3.0);
        spawn(store, world, resource, space, space.createBox(0.5f, 0.5f, 0.5f, 1.0f),
            origin, 0);
        spawn(store, world, resource, space, space.createSphere(0.5f, 1.0f), origin, 2);
        spawn(store, world, resource, space,
            space.createCapsule(0.35f, 0.7f, PhysicsAxis.Y, 1.0f), origin, 4);
        spawn(store, world, resource, space,
            space.createCylinder(0.45f, 0.6f, PhysicsAxis.Y, 1.0f), origin, 6);
        spawn(store, world, resource, space,
            space.createCone(0.5f, 0.7f, PhysicsAxis.Y, 1.0f), origin, 8);

        ctx.sender().sendMessage(Message.raw("Spawned shape demo. Debug overlays are enabled."));
        return CompletableFuture.completedFuture(null);
    }

    private static void spawn(@Nonnull Store<EntityStore> store,
        @Nonnull World world,
        @Nonnull PhysicsWorldResource resource,
        @Nonnull PhysicsSpace space,
        @Nonnull PhysicsBody body,
        @Nonnull Vector3d origin,
        int xOffset) {
        body.setRestitution(0.35f);
        body.setFriction(0.7f);
        ExamplePhysicsUtils.spawnBlockBody(store, world, resource, space, body,
            new Vector3d(origin).add(xOffset, 0.0, 0.0));
    }
}
