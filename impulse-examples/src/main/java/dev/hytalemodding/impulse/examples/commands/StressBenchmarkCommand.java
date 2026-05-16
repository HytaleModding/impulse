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

/**
 * Builds repeatable high-count stress scenes for separating backend cost from
 * Hytale entity and visual update cost.
 */
public class StressBenchmarkCommand extends AbstractAsyncPlayerCommand {

    private static final int DEFAULT_COUNT = 1000;
    private static final int MAX_COUNT = 10000;
    private static final double SPACING = 1.05;

    private final OptionalArg<String> modeArg = this.withOptionalArg(
        "mode",
        "Benchmark mode: raw or entity",
        ArgTypes.STRING);
    private final OptionalArg<Integer> countArg = this.withOptionalArg(
        "count",
        "Number of bodies to spawn",
        ArgTypes.INTEGER);

    public StressBenchmarkCommand() {
        super("benchmark", "Spawn repeatable raw or entity-backed benchmark body grids");
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

        String mode = modeArg.provided(ctx) ? modeArg.get(ctx).toLowerCase(Locale.ROOT) : "raw";
        int count = ExamplePhysicsUtils.optionalInt(ctx, countArg, DEFAULT_COUNT, 1, MAX_COUNT);
        PhysicsWorldResource resource = ExamplePhysicsUtils.resource(store);
        PhysicsSpace space = ExamplePhysicsUtils.defaultSpace(resource, world);
        BenchmarkLayout layout = BenchmarkLayout.around(playerPos, count);

        long startNanos = System.nanoTime();
        int spawned = switch (mode) {
            case "raw" -> spawnRaw(space, layout, count);
            case "entity", "entities" -> spawnEntities(store, space, layout, count);
            default -> {
                ctx.sender().sendMessage(Message.raw("Unknown benchmark mode '" + mode
                    + "'. Expected raw or entity."));
                yield 0;
            }
        };
        long elapsedNanos = System.nanoTime() - startNanos;

        if (spawned > 0) {
            ctx.sender().sendMessage(Message.raw("Spawned " + spawned + " " + mode
                + " benchmark bodies in " + millis(elapsedNanos)
                + " ms. Run /impulse perf toggle before spawning, then /impulse perf report."));
        }
        return CompletableFuture.completedFuture(null);
    }

    private static int spawnRaw(@Nonnull PhysicsSpace space,
        @Nonnull BenchmarkLayout layout,
        int count) {
        for (int i = 0; i < count; i++) {
            PhysicsBody body = createBenchmarkBody(space);
            Vector3d position = layout.position(i);
            body.setPosition((float) position.x, (float) position.y, (float) position.z);
            space.addBody(body);
        }
        return count;
    }

    private static int spawnEntities(@Nonnull Store<EntityStore> store,
        @Nonnull PhysicsSpace space,
        @Nonnull BenchmarkLayout layout,
        int count) {
        TimeResource time = store.getResource(TimeResource.getResourceType());
        for (int i = 0; i < count; i++) {
            PhysicsBody body = createBenchmarkBody(space);
            ExamplePhysicsUtils.spawnBlockBody(store,
                time,
                space.getId(),
                space,
                body,
                layout.position(i));
        }
        return count;
    }

    @Nonnull
    private static PhysicsBody createBenchmarkBody(@Nonnull PhysicsSpace space) {
        PhysicsBody body = space.createBox(0.48f, 0.48f, 0.48f, 1.0f);
        body.setFriction(0.65f);
        body.setRestitution(0.15f);
        return body;
    }

    @Nonnull
    private static String millis(long nanos) {
        return String.format(Locale.ROOT, "%.3f", nanos / 1_000_000.0);
    }

    private record BenchmarkLayout(Vector3d origin, int side) {

        @Nonnull
        private static BenchmarkLayout around(@Nonnull Vector3d playerPos, int count) {
            int side = (int) Math.ceil(Math.cbrt(count));
            double half = side * SPACING * 0.5;
            return new BenchmarkLayout(new Vector3d(
                playerPos.x - half,
                playerPos.y + 5.0,
                playerPos.z + 5.0 - half), side);
        }

        @Nonnull
        private Vector3d position(int index) {
            int x = index % side;
            int z = (index / side) % side;
            int y = index / (side * side);
            return new Vector3d(origin).add(x * SPACING, y * SPACING, z * SPACING);
        }
    }
}
