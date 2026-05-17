package dev.hytalemodding.impulse.examples.commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncPlayerCommand;
import com.hypixel.hytale.server.core.modules.time.TimeResource;
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

public class StressShapesCommand extends AbstractAsyncPlayerCommand {

    private static final int DEFAULT_SETS = 10;
    private static final int MAX_SETS = 1000;
    private static final PhysicsAxis[] AXES = {
        PhysicsAxis.X,
        PhysicsAxis.Y,
        PhysicsAxis.Z
    };

    private final OptionalArg<Integer> setsArg = this.withOptionalArg(
        "sets",
        "Number of mixed shape sets to spawn",
        ArgTypes.INTEGER);

    public StressShapesCommand() {
        super("shapes", "Spawn many mixed collider shapes");
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

        int sets = ExamplePhysicsUtils.optionalInt(ctx, setsArg, DEFAULT_SETS, 1, MAX_SETS);
        PhysicsWorldResource resource = ExamplePhysicsUtils.resource(store);
        PhysicsSpace space = ExamplePhysicsUtils.defaultSpace(ctx, resource);
        if (space == null) {
            return CompletableFuture.completedFuture(null);
        }
        TimeResource time = store.getResource(TimeResource.getResourceType());

        Vector3d origin = new Vector3d(playerPos).add(-12.0, 5.0, 5.0);
        for (int set = 0; set < sets; set++) {
            PhysicsAxis axis = AXES[set % AXES.length];
            int row = set / 4;
            int col = set % 4;
            Vector3d base = new Vector3d(origin).add(col * 7.0, row * 2.2, row * 1.5);

            spawn(store, time, space, space.createBox(0.45f, 0.55f, 0.35f, 1.0f), base, 0.0);
            spawn(store, time, space, space.createSphere(0.5f, 1.0f), base, 1.2);
            spawn(store, time, space, space.createCapsule(0.3f, 0.7f, axis, 1.0f), base, 2.4);
            spawn(store, time, space, space.createCylinder(0.4f, 0.65f, axis, 1.0f), base, 3.6);
            spawn(store, time, space, space.createCone(0.45f, 0.7f, axis, 1.0f), base, 4.8);
        }

        ctx.sender().sendMessage(Message.raw("Spawned " + sets + " mixed shape sets ("
            + (sets * 5) + " bodies)."));
        return CompletableFuture.completedFuture(null);
    }

    private static void spawn(@Nonnull Store<EntityStore> store,
        @Nonnull TimeResource time,
        @Nonnull PhysicsSpace space,
        @Nonnull PhysicsBody body,
        @Nonnull Vector3d base,
        double xOffset) {
        body.setFriction(0.6f);
        body.setRestitution(0.25f);
        ExamplePhysicsUtils.spawnBlockBody(store, time, space.getId(), space, body,
            new Vector3d(base).add(xOffset, 0.0, 0.0));
    }
}

