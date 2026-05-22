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
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsWorldResource;
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
        PhysicsSpace space = ExamplePhysicsUtils.defaultSpace(ctx, resource);
        if (space == null) {
            return CompletableFuture.completedFuture(null);
        }

        Vector3d origin = new Vector3d(playerPos).add(-4.0, 3.0, 3.0);
        spawn(store, world, resource, space,
            createBody(store, space, ShapeType.BOX, PhysicsAxis.Y),
            origin, 0);
        spawn(store, world, resource, space,
            createBody(store, space, ShapeType.SPHERE, PhysicsAxis.Y), origin, 2);
        spawn(store, world, resource, space,
            createBody(store, space, ShapeType.CAPSULE, PhysicsAxis.Y), origin, 4);
        spawn(store, world, resource, space,
            createBody(store, space, ShapeType.CYLINDER, PhysicsAxis.Y), origin, 6);
        spawn(store, world, resource, space,
            createBody(store, space, ShapeType.CONE, PhysicsAxis.Y), origin, 8);

        ctx.sender().sendMessage(Message.raw("Spawned shape demo."));
        return CompletableFuture.completedFuture(null);
    }

    private static void spawn(@Nonnull Store<EntityStore> store,
        @Nonnull World world,
        @Nonnull PhysicsWorldResource resource,
        @Nonnull PhysicsSpace space,
        @Nonnull PhysicsBody body,
        @Nonnull Vector3d origin,
        int xOffset) {
        ExamplePhysicsUtils.spawnBlockBody(store, world, resource, space, body,
            new Vector3d(origin).add(xOffset, 0.0, 0.0));
    }

    @Nonnull
    private static PhysicsBody createBody(@Nonnull Store<EntityStore> store,
        @Nonnull PhysicsSpace space,
        @Nonnull ShapeType type,
        @Nonnull PhysicsAxis axis) {
        return ExamplePhysicsUtils.physicsWorkerCall(store, "create shape demo physics body",
            () -> {
                PhysicsBody body = switch (type) {
                    case BOX -> space.createBox(0.5f, 0.5f, 0.5f, 1.0f);
                    case SPHERE -> space.createSphere(0.5f, 1.0f);
                    case CAPSULE -> space.createCapsule(0.35f, 0.7f, axis, 1.0f);
                    case CYLINDER -> space.createCylinder(0.45f, 0.6f, axis, 1.0f);
                    case CONE -> space.createCone(0.5f, 0.7f, axis, 1.0f);
                };
                body.setRestitution(0.35f);
                body.setFriction(0.7f);
                return body;
            });
    }

    private enum ShapeType {
        BOX,
        SPHERE,
        CAPSULE,
        CYLINDER,
        CONE
    }
}
