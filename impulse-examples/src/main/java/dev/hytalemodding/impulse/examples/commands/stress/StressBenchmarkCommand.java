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
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyKind;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyPersistenceMode;
import dev.hytalemodding.impulse.core.plugin.physicsstore.PhysicsStoreDiagnostics;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsWorldResource;
import dev.hytalemodding.impulse.core.plugin.simulation.PhysicsShapeSpec;
import dev.hytalemodding.impulse.core.plugin.simulation.RigidBodySpawnSettings;
import dev.hytalemodding.impulse.examples.commands.ExamplePhysicsUtils;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
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
    private final OptionalArg<String> blockTypeArg = this.withOptionalArg(
        "blockType",
        "Hytale block type for entity-backed benchmark visuals",
        ArgTypes.STRING);
    private final OptionalArg<Integer> spaceArg = this.withOptionalArg(
        "space",
        "Physics space id to target",
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
        SpaceId spaceId = ExamplePhysicsUtils.spaceId(ctx, resource, spaceArg);
        if (spaceId == null) {
            return CompletableFuture.completedFuture(null);
        }
        BenchmarkLayout layout = BenchmarkLayout.around(playerPos, request.count());
        int beforeBodies = PhysicsStoreDiagnostics.bodyCountAsync(world, spaceId)
            .toCompletableFuture()
            .join();

        long serverTick = Math.max(0L, world.getTick());
        BenchmarkSpawnTiming timing = switch (request.mode()) {
            case RAW -> spawnRaw(world, spaceId, layout, request.count());
            case ENTITY -> spawnEntities(store,
                resource,
                spaceId,
                layout,
                request.count(),
                request.blockType(),
                serverTick);
        };

        if (timing.spawned() > 0) {
            ctx.sender().sendMessage(Message.raw("Added " + timing.spawned() + " "
                + request.mode().label() + " benchmark bodies: setupWallMs="
                + millis(timing.setupWallNanos())
                + " rowApplyMs=" + millis(timing.rowApplyNanos())
                + (timing.entityAttachNanos() > 0L
                    ? " entityAttachMs=" + millis(timing.entityAttachNanos())
                    : "")
                + " (" + microsPerBody(timing.setupWallNanos(), timing.spawned())
                + " us/body). Space bodies before add: " + beforeBodies
                + (request.mode() == BenchmarkMode.ENTITY ? ". blockType=" + request.blockType() : "")
                + ". Body-count updates are visible after PhysicsStore binds the new rows"
                + ". This command measures raw setup/entity attachment; use /impulse-examples stress bodies"
                + " for detached/detached-view scalability scenarios"
                + ". For clean comparisons run /impulse clean, /impulse-world-collision perf reset,"
                + " /impulse-world-collision perf toggle before spawning, then /impulse-world-collision perf report."));
        }
        return CompletableFuture.completedFuture(null);
    }

    @Nullable
    private BenchmarkRequest parseRequest(@Nonnull CommandContext ctx) {
        if (!modeArg.provided(ctx)) {
            return new BenchmarkRequest(BenchmarkMode.RAW, DEFAULT_COUNT, blockType(ctx));
        }

        String rawMode = modeArg.get(ctx).toLowerCase(Locale.ROOT);
        Integer countOnly = tryParseCount(rawMode);
        if (countOnly != null) {
            return new BenchmarkRequest(BenchmarkMode.RAW, clampCount(countOnly), blockType(ctx));
        }

        BenchmarkMode mode = BenchmarkMode.from(rawMode);
        if (mode == null) {
            ctx.sender().sendMessage(Message.raw("Unknown benchmark mode '" + rawMode
                + "'. Expected raw, entity, entities, hytale, or a count."));
            return null;
        }

        int count = ExamplePhysicsUtils.optionalInt(ctx, countArg, DEFAULT_COUNT, 1, MAX_COUNT);
        return new BenchmarkRequest(mode, count, blockType(ctx));
    }

    @Nonnull
    private static BenchmarkSpawnTiming spawnRaw(@Nonnull World world,
        @Nonnull SpaceId spaceId,
        @Nonnull BenchmarkLayout layout,
        int count) {
        PhysicsShapeSpec box = PhysicsShapeSpec.box(0.48f, 0.48f, 0.48f);
        RigidBodySpawnSettings spawnSettings = RigidBodySpawnSettings.material(0.65f, 0.15f);
        ExamplePhysicsUtils.BodyRowBatchTiming timing =
            ExamplePhysicsUtils.addDynamicBodyBatchMeasured(world,
                spaceId,
                count,
                box,
                1.0f,
                spawnSettings,
                PhysicsBodyKind.TEMPORARY,
                PhysicsBodyPersistenceMode.RUNTIME_ONLY,
                bodies -> {
                    for (int i = 0; i < count; i++) {
                        bodies.addBody(layout.positionX(i),
                            layout.positionY(i),
                            layout.positionZ(i));
                    }
                });
        return new BenchmarkSpawnTiming(timing.count(),
            timing.setupWallNanos(),
            timing.rowApplyNanos(),
            0L);
    }

    @Nonnull
    private static BenchmarkSpawnTiming spawnEntities(@Nonnull Store<EntityStore> store,
        @Nonnull PhysicsWorldResource resource,
        @Nonnull SpaceId spaceId,
        @Nonnull BenchmarkLayout layout,
        int count,
        @Nonnull String blockType,
        long serverTick) {
        TimeResource time = store.getResource(TimeResource.getResourceType());
        PhysicsShapeSpec box = PhysicsShapeSpec.box(0.48f, 0.48f, 0.48f);
        RigidBodySpawnSettings spawnSettings = RigidBodySpawnSettings.material(0.65f, 0.15f);
        ExamplePhysicsUtils.BlockBodyBatchTiming timing = ExamplePhysicsUtils.spawnBlockBodiesMeasured(store,
            time,
            resource,
            serverTick,
            spaceId,
            count,
            blockType,
            box,
            1.0f,
            spawnSettings,
            bodies -> {
                for (int i = 0; i < count; i++) {
                    bodies.addBody(layout.positionX(i),
                        layout.positionY(i),
                        layout.positionZ(i));
                }
            });
        return new BenchmarkSpawnTiming(timing.count(),
            timing.rowApplyNanos() + timing.entityAttachNanos(),
            timing.rowApplyNanos(),
            timing.entityAttachNanos());
    }

    @Nonnull
    private String blockType(@Nonnull CommandContext ctx) {
        return blockTypeArg.provided(ctx)
            ? ExamplePhysicsUtils.resolveBlockType(blockTypeArg.get(ctx))
            : ExamplePhysicsUtils.DEFAULT_BLOCK_TYPE;
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

    private record BenchmarkRequest(BenchmarkMode mode, int count, @Nonnull String blockType) {
    }

    private record BenchmarkSpawnTiming(int spawned,
                                        long setupWallNanos,
                                        long rowApplyNanos,
                                        long entityAttachNanos) {

        private BenchmarkSpawnTiming {
            setupWallNanos = Math.max(0L, setupWallNanos);
            rowApplyNanos = Math.max(0L, rowApplyNanos);
            entityAttachNanos = Math.max(0L, entityAttachNanos);
        }
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

        private float positionX(int index) {
            int x = index % side;
            return (float) (origin.x + x * SPACING);
        }

        private float positionY(int index) {
            int y = index / (side * side);
            return (float) (origin.y + y * SPACING);
        }

        private float positionZ(int index) {
            int z = (index / side) % side;
            return (float) (origin.z + z * SPACING);
        }
    }
}
