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
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyKind;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyPersistenceMode;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsWorldResource;
import dev.hytalemodding.impulse.core.plugin.simulation.PhysicsShapeSpec;
import dev.hytalemodding.impulse.core.plugin.simulation.RigidBodySpawnSettings;
import dev.hytalemodding.impulse.examples.commands.ExamplePhysicsUtils;
import dev.hytalemodding.impulse.examples.commands.ExamplePhysicsUtils.BodyRowBatchTiming;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nonnull;
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
    private final OptionalArg<Integer> spaceArg = this.withOptionalArg(
        "space",
        "Physics space id to target",
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
        Vector3d playerPos = new Vector3d(playerRef.getTransform().getPosition());

        int count = ExamplePhysicsUtils.optionalInt(ctx, countArg, DEFAULT_COUNT, 1, MAX_COUNT);
        PhysicsWorldResource resource = store.getResource(PhysicsWorldResource.getResourceType());
        SpaceId spaceId = ExamplePhysicsUtils.spaceId(ctx, resource, spaceArg);
        if (spaceId == null) {
            return CompletableFuture.completedFuture(null);
        }

        int side = (int) Math.ceil(Math.cbrt(count));
        double half = side * SPACING * 0.5;
        double originX = playerPos.x - half;
        double originY = playerPos.y + 5.0;
        double originZ = playerPos.z + 5.0 - half;

        PhysicsShapeSpec box = PhysicsShapeSpec.box(0.48f, 0.48f, 0.48f);
        RigidBodySpawnSettings spawnSettings = RigidBodySpawnSettings.material(0.65f, 0.15f);
        long totalStartNanos = System.nanoTime();
        BodyRowBatchTiming timing = ExamplePhysicsUtils.addDynamicBodyBatchMeasured(world,
            spaceId,
            count,
            box,
            1.0f,
            spawnSettings,
            PhysicsBodyKind.TEMPORARY,
            PhysicsBodyPersistenceMode.RUNTIME_ONLY,
            spawns -> {
                for (int i = 0; i < count; i++) {
                    int x = i % side;
                    int z = (i / side) % side;
                    int y = i / (side * side);

                    spawns.addBody((float) (originX + x * SPACING),
                        (float) (originY + y * SPACING),
                        (float) (originZ + z * SPACING));
                }
            });
        ctx.sender().sendMessage(Message.raw(successMessage(timing,
            System.nanoTime() - totalStartNanos)));
        return CompletableFuture.completedFuture(null);
    }

    private static String millis(long nanos) {
        return String.format(Locale.ROOT, "%.3f", nanos / 1_000_000.0);
    }

    @Nonnull
    private static String successMessage(@Nonnull BodyRowBatchTiming timing,
        long totalWallNanos) {
        return "PhysicsStore added raw body rows for " + timing.count()
            + " physics-only bodies: setupWallMs=" + millis(timing.setupWallNanos())
            + " rowApplyMs=" + millis(timing.rowApplyNanos())
            + " totalWallMs=" + millis(totalWallNanos)
            + ". Body-count updates are visible after PhysicsStore binds the new rows.";
    }

}
