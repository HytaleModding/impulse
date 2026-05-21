package dev.hytalemodding.impulse.examples.commands.stress;

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
import javax.annotation.Nullable;

import dev.hytalemodding.impulse.examples.commands.ExamplePhysicsUtils;
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
        BenchmarkRequest request = parseRequest(ctx);
        if (request == null) {
            return CompletableFuture.completedFuture(null);
        }

        Vector3d playerPos = ExamplePhysicsUtils.playerPosition(ctx, store, ref);
        if (playerPos == null) {
            return CompletableFuture.completedFuture(null);
        }

        PhysicsWorldResource resource = ExamplePhysicsUtils.resource(store);
        PhysicsSpace space = ExamplePhysicsUtils.defaultSpace(ctx, resource);
        if (space == null) {
            return CompletableFuture.completedFuture(null);
        }
        BenchmarkLayout layout = BenchmarkLayout.around(playerPos, request.count());
        int beforeBodies = space.bodyCount();

        long startNanos = System.nanoTime();
        int spawned = switch (request.mode()) {
            case RAW -> spawnRaw(space, layout, request.count());
            case ENTITY -> spawnEntities(store, resource, space, layout, request.count());
        };
        long elapsedNanos = System.nanoTime() - startNanos;
        int afterBodies = space.bodyCount();

        if (spawned > 0) {
            ctx.sender().sendMessage(Message.raw("Spawned " + spawned + " "
                + request.mode().label() + " benchmark bodies in " + millis(elapsedNanos)
                + " ms (" + microsPerBody(elapsedNanos, spawned)
                + " us/body). Space bodies: " + beforeBodies + " -> " + afterBodies
                + ". For clean comparisons run /impulse clean, /impulse perf reset,"
                + " /impulse perf toggle before spawning, then /impulse perf report."));
        }
        return CompletableFuture.completedFuture(null);
    }

    @Nullable
    private BenchmarkRequest parseRequest(@Nonnull CommandContext ctx) {
        if (!modeArg.provided(ctx)) {
            return new BenchmarkRequest(BenchmarkMode.RAW, DEFAULT_COUNT);
        }

        String rawMode = modeArg.get(ctx).toLowerCase(Locale.ROOT);
        Integer countOnly = tryParseCount(rawMode);
        if (countOnly != null) {
            return new BenchmarkRequest(BenchmarkMode.RAW, clampCount(countOnly));
        }

        BenchmarkMode mode = BenchmarkMode.from(rawMode);
        if (mode == null) {
            ctx.sender().sendMessage(Message.raw("Unknown benchmark mode '" + rawMode
                + "'. Expected raw, entity, entities, hytale, or a count."));
            return null;
        }

        int count = ExamplePhysicsUtils.optionalInt(ctx, countArg, DEFAULT_COUNT, 1, MAX_COUNT);
        return new BenchmarkRequest(mode, count);
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
        @Nonnull PhysicsWorldResource resource,
        @Nonnull PhysicsSpace space,
        @Nonnull BenchmarkLayout layout,
        int count) {
        TimeResource time = store.getResource(TimeResource.getResourceType());
        for (int i = 0; i < count; i++) {
            PhysicsBody body = createBenchmarkBody(space);
            ExamplePhysicsUtils.spawnBlockBody(store,
                time,
                resource,
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

    @Nonnull
    private static String microsPerBody(long nanos, int bodies) {
        return String.format(Locale.ROOT, "%.3f", nanos / 1_000.0 / bodies);
    }

    private static int clampCount(int count) {
        if (count < 1) {
            return 1;
        }
        return Math.min(count, MAX_COUNT);
    }

    @Nullable
    private static Integer tryParseCount(@Nonnull String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private enum BenchmarkMode {
        RAW("backend-only raw"),
        ENTITY("entity-backed Hytale");

        private final String label;

        BenchmarkMode(@Nonnull String label) {
            this.label = label;
        }

        @Nonnull
        private String label() {
            return label;
        }

        @Nullable
        private static BenchmarkMode from(@Nonnull String value) {
            return switch (value) {
                case "raw" -> RAW;
                case "entity", "entities", "hytale", "hytale-entities", "entity-backed" -> ENTITY;
                default -> null;
            };
        }
    }

    private record BenchmarkRequest(BenchmarkMode mode, int count) {
    }

    private record BenchmarkLayout(Vector3d origin, int side) {

        @Nonnull
        private static BenchmarkLayout around(@Nonnull Vector3d playerPos, int count) {
            int side = (int) Math.ceil(Math.cbrt(count));
            double half = (side - 1) * SPACING * 0.5;
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
