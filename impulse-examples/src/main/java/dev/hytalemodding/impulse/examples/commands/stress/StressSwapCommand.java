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
import dev.hytalemodding.impulse.api.BackendId;
import dev.hytalemodding.impulse.api.PhysicsSpace;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.resources.PhysicsWorldResource;
import dev.hytalemodding.impulse.core.services.PhysicsSpaceMigrationService;
import dev.hytalemodding.impulse.examples.commands.ExamplePhysicsUtils;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nonnull;

public class StressSwapCommand extends AbstractAsyncPlayerCommand {

    private static final BackendId BULLET = new BackendId("impulse:bullet");
    private static final BackendId RAPIER = new BackendId("impulse:rapier");
    private static final int DEFAULT_CYCLES = 2;
    private static final int MAX_CYCLES = 20;

    private final OptionalArg<Integer> cyclesArg = this.withOptionalArg(
        "cycles",
        "Number of backend swaps to run",
        ArgTypes.INTEGER);

    public StressSwapCommand() {
        super("swap", "Repeatedly swap the main physics space backend");
    }

    @Nonnull
    @Override
    protected CompletableFuture<Void> executeAsync(@Nonnull CommandContext ctx,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull PlayerRef playerRef,
        @Nonnull World world) {
        int cycles = ExamplePhysicsUtils.optionalInt(ctx, cyclesArg, DEFAULT_CYCLES, 1, MAX_CYCLES);
        PhysicsWorldResource resource = ExamplePhysicsUtils.resource(store);
        SpaceId spaceId = resource.getDefaultSpaceId();
        if (spaceId == null) {
            ctx.sender().sendMessage(Message.raw("No default example physics space exists yet."));
            return CompletableFuture.completedFuture(null);
        }
        PhysicsSpace space = resource.getSpace(spaceId);
        if (space == null) {
            ctx.sender().sendMessage(Message.raw("Default physics space is missing."));
            return CompletableFuture.completedFuture(null);
        }

        BackendId currentBackendId = space.getBackendId();
        int changed = 0;
        int bodies = 0;
        int joints = 0;
        long startNanos = System.nanoTime();
        try {
            for (int i = 0; i < cycles; i++) {
                BackendId targetBackendId = currentBackendId.equals(BULLET) ? RAPIER : BULLET;
                PhysicsSpaceMigrationService.MigrationResult result =
                    PhysicsSpaceMigrationService.migrateSpace(store,
                        resource,
                        spaceId,
                        targetBackendId,
                        world.getName());
                currentBackendId = result.targetBackendId();
                bodies = result.migratedBodies();
                joints = result.migratedJoints();
                if (result.changed()) {
                    changed++;
                }
            }
        } catch (Exception exception) {
            ctx.sender().sendMessage(Message.raw("Stress swap failed after " + changed
                + " swaps: " + exception.getMessage()));
            return CompletableFuture.completedFuture(null);
        }

        long elapsedNanos = System.nanoTime() - startNanos;
        ctx.sender().sendMessage(Message.raw("Completed " + changed + " backend swaps for space id="
            + spaceId.value() + " finalBackend=" + currentBackendId.value() + " bodies="
            + bodies + " joints=" + joints + " time=" + millis(elapsedNanos) + " ms."));
        return CompletableFuture.completedFuture(null);
    }

    private static String millis(long nanos) {
        return String.format(Locale.ROOT, "%.3f", nanos / 1_000_000.0);
    }
}
