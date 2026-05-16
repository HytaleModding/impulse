package dev.hytalemodding.impulse.examples.commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.api.PhysicsBody;
import dev.hytalemodding.impulse.api.PhysicsSpace;
import dev.hytalemodding.impulse.core.resources.PhysicsWorldResource;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nonnull;
import org.joml.Vector3d;

public class MaterialsCommand extends AbstractAsyncPlayerCommand {

    public MaterialsCommand() {
        super("materials", "Spawn restitution and friction comparison bodies");
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

        Vector3d origin = new Vector3d(playerPos).add(-3.0, 5.0, 4.0);
        spawnSphere(store, world, resource, space, new Vector3d(origin), 0.05f, 0.9f, 3.0f);
        spawnSphere(store, world, resource, space, new Vector3d(origin).add(2.0, 0.0, 0.0),
            0.95f, 0.9f, 3.0f);
        spawnSphere(store, world, resource, space, new Vector3d(origin).add(4.0, 0.0, 0.0),
            0.5f, 0.0f, 2.0f);
        spawnSphere(store, world, resource, space, new Vector3d(origin).add(6.0, 0.0, 0.0),
            0.5f, 0.95f, 2.0f);

        ctx.sender().sendMessage(Message.raw(
            "Spawned material demo: bouncy, slick, dead, and sticky spheres."));
        return CompletableFuture.completedFuture(null);
    }

    private static void spawnSphere(@Nonnull Store<EntityStore> store,
        @Nonnull World world,
        @Nonnull PhysicsWorldResource resource,
        @Nonnull PhysicsSpace space,
        @Nonnull Vector3d position,
        float restitution,
        float friction,
        float speed) {
        PhysicsBody body = space.createSphere(0.5f, 1.0f);
        body.setRestitution(restitution);
        body.setFriction(friction);
        body.setLinearVelocity(speed, 0.0f, 0.0f);
        ExamplePhysicsUtils.spawnBlockBody(store, world, resource, space, body, position);
    }
}
