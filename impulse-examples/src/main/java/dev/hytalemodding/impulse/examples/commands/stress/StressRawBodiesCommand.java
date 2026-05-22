package dev.hytalemodding.impulse.examples.commands.stress;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.api.PhysicsBody;
import dev.hytalemodding.impulse.api.PhysicsSpace;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsWorldResource;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nonnull;

import dev.hytalemodding.impulse.examples.commands.ExamplePhysicsUtils;
import org.joml.Vector3d;

/**
 * Creates backend bodies without entity components.
 * Compare this with the visible body stress test to separate physics cost from Hytale entity,
 * networking, and rendering cost.
 */
public class StressRawBodiesCommand extends AbstractAsyncPlayerCommand {

    private static final int DEFAULT_COUNT = 1000;
    private static final int MAX_COUNT = 10000;
    private static final double SPACING = 1.05;

    private final OptionalArg<Integer> countArg = this.withOptionalArg(
        "count",
        "Number of physics-only boxes to spawn",
        ArgTypes.INTEGER);

    public StressRawBodiesCommand() {
        super("raw-bodies", "Spawn physics bodies without Hytale entities");
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
        PhysicsSpace space = ExamplePhysicsUtils.defaultSpace(ctx, resource);
        if (space == null) {
            return CompletableFuture.completedFuture(null);
        }

        int side = (int) Math.ceil(Math.cbrt(count));
        double half = side * SPACING * 0.5;
        double originX = playerPos.x - half;
        double originY = playerPos.y + 5.0;
        double originZ = playerPos.z + 5.0 - half;

        long startNanos = System.nanoTime();
        ExamplePhysicsUtils.physicsOwnerRun(store, "spawn raw stress physics bodies", () -> {
            for (int i = 0; i < count; i++) {
                int x = i % side;
                int z = (i / side) % side;
                int y = i / (side * side);

                PhysicsBody body = space.createBox(0.48f, 0.48f, 0.48f, 1.0f);
                body.setPosition((float) (originX + x * SPACING),
                    (float) (originY + y * SPACING),
                    (float) (originZ + z * SPACING));
                body.setFriction(0.65f);
                body.setRestitution(0.15f);
                space.addBody(body);
            }
        });
        long elapsedNanos = System.nanoTime() - startNanos;

        ctx.sender().sendMessage(Message.raw("Spawned " + count
            + " raw physics bodies without entities in " + millis(elapsedNanos)
            + " ms. Use this to separate backend cost from entity/render cost."));
        return CompletableFuture.completedFuture(null);
    }

    private static String millis(long nanos) {
        return String.format(Locale.ROOT, "%.3f", nanos / 1_000_000.0);
    }
}
