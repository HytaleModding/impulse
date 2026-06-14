package dev.hytalemodding.impulse.core.internal.commands.perf;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractWorldCommand;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.core.plugin.physicsstore.PhysicsStoreDiagnostics;
import dev.hytalemodding.impulse.core.plugin.simulation.SpaceSummary;
import java.util.List;
import javax.annotation.Nonnull;

public class PerfStatsCommand extends AbstractWorldCommand {

    public PerfStatsCommand() {
        super("stats", "Show Impulse per-space runtime body and contact counts");
    }

    @Override
    protected void execute(@Nonnull CommandContext ctx,
        @Nonnull World world,
        @Nonnull Store<EntityStore> store) {
        List<SpaceSummary> spaces = PhysicsStoreDiagnostics.spaceSummariesAsync(world)
            .toCompletableFuture()
            .join();

        if (spaces.isEmpty()) {
            ctx.sender().sendMessage(Message.raw("Impulse runtime stats: no physics spaces in world "
                + world.getName() + "."));
            return;
        }

        SpaceStats totals = new SpaceStats();
        ctx.sender().sendMessage(Message.raw("Impulse runtime stats for world " + world.getName()
            + ": spaces=" + spaces.size()));
        for (SpaceSummary space : spaces) {
            SpaceStats stats = SpaceStats.from(space);
            totals.add(stats);
            ctx.sender().sendMessage(Message.raw("Space " + space.spaceId().value()
                + " backend=" + space.backendId().value()
                + " bodies=" + stats.bodies
                + " joints=" + stats.joints
                + " classification=unavailable"
                + " contacts=unavailable"));
        }
        ctx.sender().sendMessage(Message.raw("Totals: bodies=" + totals.bodies
            + " joints=" + totals.joints
            + " classification=unavailable"
            + " contacts=unavailable"));
    }

    private static final class SpaceStats {

        private int bodies;
        private int joints;

        @Nonnull
        private static SpaceStats from(@Nonnull SpaceSummary summary) {
            SpaceStats stats = new SpaceStats();
            stats.bodies = summary.bodyCount();
            stats.joints = summary.jointCount();
            return stats;
        }

        private void add(@Nonnull SpaceStats stats) {
            bodies += stats.bodies;
            joints += stats.joints;
        }
    }
}
