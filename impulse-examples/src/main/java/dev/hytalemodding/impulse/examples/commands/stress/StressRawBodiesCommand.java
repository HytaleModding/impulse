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
import dev.hytalemodding.impulse.core.plugin.physicsstore.requests.PhysicsStoreRequestFenceResult;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsWorldResource;
import dev.hytalemodding.impulse.core.plugin.simulation.PhysicsShapeSpec;
import dev.hytalemodding.impulse.core.plugin.simulation.RigidBodySpawnSettings;
import dev.hytalemodding.impulse.examples.commands.ExamplePhysicsUtils;
import dev.hytalemodding.impulse.examples.commands.ExamplePhysicsUtils.FencedBodyRequestBatchTiming;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
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
        Vector3d playerPos = ExamplePhysicsUtils.playerPosition(ctx, store, ref);
        if (playerPos == null) {
            return CompletableFuture.completedFuture(null);
        }

        int count = ExamplePhysicsUtils.optionalInt(ctx, countArg, DEFAULT_COUNT, 1, MAX_COUNT);
        PhysicsWorldResource resource = ExamplePhysicsUtils.resource(store);
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
        long submittedServerTick = Math.max(0L, world.getTick());
        long commandStartNanos = System.nanoTime();
        FencedBodyRequestBatchTiming timing = ExamplePhysicsUtils.enqueueDynamicBodyBatchFencedMeasured(world,
            spaceId,
            count,
            box,
            1.0f,
            spawnSettings,
            PhysicsBodyKind.TEMPORARY,
            PhysicsBodyPersistenceMode.RUNTIME_ONLY,
            submittedServerTick,
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
        long fenceStartNanos = System.nanoTime();

        return timing.fence()
            .completion()
            .thenAccept(result -> ctx.sender().sendMessage(Message.raw(successMessage(timing,
                result,
                System.nanoTime() - fenceStartNanos,
                System.nanoTime() - commandStartNanos))))
            .exceptionally(failure -> {
                ctx.sender().sendMessage(Message.raw("Failed to drain raw physics body requests: "
                    + failureMessage(failure)));
                return null;
            })
            .toCompletableFuture();
    }

    private static String millis(long nanos) {
        return String.format(Locale.ROOT, "%.3f", nanos / 1_000_000.0);
    }

    @Nonnull
    private static String successMessage(@Nonnull FencedBodyRequestBatchTiming timing,
        @Nonnull PhysicsStoreRequestFenceResult result,
        long fenceWaitNanos,
        long totalWallNanos) {
        return "PhysicsStore drained raw body requests for " + timing.count()
            + " physics-only bodies: setupWallMs=" + millis(timing.setupWallNanos())
            + " requestEnqueueMs=" + millis(timing.requestEnqueueNanos())
            + " fenceWaitMs=" + millis(fenceWaitNanos)
            + " totalWallMs=" + millis(totalWallNanos)
            + " accepted=" + result.acceptedCount()
            + " applied=" + result.appliedCount()
            + " softSkipped=" + result.softSkippedCount()
            + " rejected=" + result.rejectedCount()
            + " failed=" + result.failedCount()
            + " submittedTick=" + result.submittedServerTick()
            + " consumedTick=" + result.consumedServerTick()
            + " consumedTickLatency=" + result.consumedServerTickLatency()
            + " allApplied=" + result.allApplied()
            + " hasProblems=" + result.hasProblems()
            + " fence=" + result.fenceUuid()
            + ". This reports request drain/application only.";
    }

    @Nonnull
    private static String failureMessage(@Nonnull Throwable failure) {
        Throwable unwrapped = failure instanceof CompletionException && failure.getCause() != null
            ? failure.getCause()
            : failure;
        return unwrapped.getMessage() != null
            ? unwrapped.getMessage()
            : unwrapped.getClass().getSimpleName();
    }

}
