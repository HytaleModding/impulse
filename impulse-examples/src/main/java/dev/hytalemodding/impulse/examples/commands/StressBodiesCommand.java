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
import dev.hytalemodding.impulse.api.PhysicsBody;
import dev.hytalemodding.impulse.api.PhysicsSpace;
import dev.hytalemodding.impulse.core.resources.PhysicsWorldResource;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nonnull;
import org.joml.Vector3d;

public class StressBodiesCommand extends AbstractAsyncPlayerCommand {

    private static final int DEFAULT_COUNT = 100;
    private static final int MAX_COUNT = 5000;
    private static final double SPACING = 1.05;

    private final OptionalArg<Integer> countArg = this.withOptionalArg(
        "count",
        "Number of dynamic boxes to spawn",
        ArgTypes.INTEGER);

    public StressBodiesCommand() {
        super("bodies", "Spawn many dynamic box bodies");
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

        int count = ExamplePhysicsUtils.optionalInt(ctx, countArg, DEFAULT_COUNT, 1, MAX_COUNT);
        PhysicsWorldResource resource = ExamplePhysicsUtils.resource(store);
        PhysicsSpace space = ExamplePhysicsUtils.defaultSpace(resource, world);
        TimeResource time = store.getResource(TimeResource.getResourceType());

        int side = (int) Math.ceil(Math.cbrt(count));
        Vector3d origin = new Vector3d(playerPos).add(
            -side * SPACING * 0.5,
            5.0,
            4.0 - side * SPACING * 0.5);

        long startNanos = System.nanoTime();
        for (int i = 0; i < count; i++) {
            int x = i % side;
            int z = (i / side) % side;
            int y = i / (side * side);
            Vector3d position = new Vector3d(origin).add(x * SPACING, y * SPACING, z * SPACING);

            PhysicsBody body = space.createBox(0.48f, 0.48f, 0.48f, 1.0f);
            body.setFriction(0.65f);
            body.setRestitution(0.15f);
            ExamplePhysicsUtils.spawnBlockBody(store, time, space.getId(), space, body, position);
        }
        long elapsedNanos = System.nanoTime() - startNanos;

        ctx.sender().sendMessage(Message.raw("Spawned " + count + " stress bodies in "
            + millis(elapsedNanos) + " ms."));
        return CompletableFuture.completedFuture(null);
    }

    private static String millis(long nanos) {
        return String.format(Locale.ROOT, "%.3f", nanos / 1_000_000.0);
    }
}
