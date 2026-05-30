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
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsWorldResource;
import dev.hytalemodding.impulse.core.plugin.simulation.RaycastClosestBatchQuery;
import dev.hytalemodding.impulse.core.plugin.simulation.RaycastSegment;
import dev.hytalemodding.impulse.examples.commands.ExamplePhysicsUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nonnull;
import org.joml.Vector3d;

public class StressRaycastCommand extends AbstractAsyncPlayerCommand {

    private static final int DEFAULT_RAYS = 256;
    private static final int MAX_RAYS = 5000;
    private static final double SPACING = 0.75;
    private static final double HEIGHT = 18.0;

    private final OptionalArg<Integer> raysArg = this.withOptionalArg(
        "rays",
        "Number of downward raycasts to run",
        ArgTypes.INTEGER);
    private final OptionalArg<Integer> spaceArg = this.withOptionalArg(
        "space",
        "Physics space id to target",
        ArgTypes.INTEGER);

    public StressRaycastCommand() {
        super("raycast", "Run many physics raycasts and report timing");
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

        int rays = ExamplePhysicsUtils.optionalInt(ctx, raysArg, DEFAULT_RAYS, 1, MAX_RAYS);
        PhysicsWorldResource resource = ExamplePhysicsUtils.resource(store);
        SpaceId spaceId = ExamplePhysicsUtils.spaceId(ctx, resource, spaceArg);
        if (spaceId == null) {
            return CompletableFuture.completedFuture(null);
        }

        int side = (int) Math.ceil(Math.sqrt(rays));
        List<RaycastSegment> segments = getRaycastSegments(side, rays, playerPos);

        long startNanos = System.nanoTime();
        long hits = resource.query(new RaycastClosestBatchQuery(spaceId, segments))
            .completion()
            .toCompletableFuture()
            .join()
            .hitCount();
        long elapsedNanos = System.nanoTime() - startNanos;

        ctx.sender().sendMessage(Message.raw("Ran " + rays + " raycasts: " + hits
            + " hits in " + millis(elapsedNanos) + " ms."));
        return CompletableFuture.completedFuture(null);
    }

    @Nonnull
    private static List<RaycastSegment> getRaycastSegments(int side, int rays, Vector3d playerPos) {
        double half = side * SPACING * 0.5;
        List<RaycastSegment> segments = new ArrayList<>(rays);
        for (int i = 0; i < rays; i++) {
            int x = i % side;
            int z = i / side;
            float rayX = (float) (playerPos.x + x * SPACING - half);
            float rayZ = (float) (playerPos.z + z * SPACING - half + 5.0);
            segments.add(new RaycastSegment(rayX,
                (float) (playerPos.y + HEIGHT),
                rayZ,
                rayX,
                (float) (playerPos.y - HEIGHT),
                rayZ));
        }
        return segments;
    }

    private static String millis(long nanos) {
        return String.format(Locale.ROOT, "%.3f", nanos / 1_000_000.0);
    }
}
