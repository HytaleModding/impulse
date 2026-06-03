package dev.hytalemodding.impulse.core.internal.commands.perf;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractWorldCommand;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsWorldRuntimeResource;
import dev.hytalemodding.impulse.core.internal.simulation.query.PhysicsSpaceRuntimeStatsQuery;
import dev.hytalemodding.impulse.core.internal.simulation.view.PhysicsSpaceRuntimeStatsView;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsWorldResource;
import dev.hytalemodding.impulse.core.plugin.simulation.SpaceSummary;
import dev.hytalemodding.impulse.core.plugin.simulation.query.SpaceSummaryQuery;
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
        PhysicsWorldResource resource = store.getResource(PhysicsWorldResource.getResourceType());
        PhysicsWorldRuntimeResource runtime = PhysicsWorldRuntimeResource.require(resource);
        List<SpaceSummary> spaces = resource.query(new SpaceSummaryQuery(null))
            .completion()
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
            SpaceStats stats = SpaceStats.from(runtime.queryInternal(
                    new PhysicsSpaceRuntimeStatsQuery(space.spaceId()))
                .toCompletableFuture()
                .join());
            totals.add(stats);
            ctx.sender().sendMessage(Message.raw("Space " + space.spaceId().value()
                + " backend=" + space.backendId().value()
                + " bodies=" + stats.bodies
                + " dynamic=" + stats.dynamicBodies
                + " awake=" + stats.awakeDynamicBodies
                + " sleeping=" + stats.sleepingDynamicBodies
                + " static=" + stats.staticBodies
                + " kinematic=" + stats.kinematicBodies
                + " owned=" + stats.entityOwnedBodies
                + " detached=" + stats.detachedBodies
                + " worldCollision=" + stats.worldCollisionBodies
                + " planes=" + stats.planeBodies
                + " raw=" + stats.rawBodies
                + " joints=" + stats.joints
                + " contacts=" + stats.contacts));
        }
        ctx.sender().sendMessage(Message.raw("Totals: bodies=" + totals.bodies
            + " dynamic=" + totals.dynamicBodies
            + " awake=" + totals.awakeDynamicBodies
            + " sleeping=" + totals.sleepingDynamicBodies
            + " static=" + totals.staticBodies
            + " kinematic=" + totals.kinematicBodies
            + " owned=" + totals.entityOwnedBodies
            + " detached=" + totals.detachedBodies
            + " worldCollision=" + totals.worldCollisionBodies
            + " planes=" + totals.planeBodies
            + " raw=" + totals.rawBodies
            + " joints=" + totals.joints
            + " contacts=" + totals.contacts));
    }

    private static final class SpaceStats {

        private int bodies;
        private int dynamicBodies;
        private int awakeDynamicBodies;
        private int sleepingDynamicBodies;
        private int staticBodies;
        private int kinematicBodies;
        private int entityOwnedBodies;
        private int detachedBodies;
        private int worldCollisionBodies;
        private int planeBodies;
        private int rawBodies;
        private int joints;
        private int contacts;

        @Nonnull
        private static SpaceStats from(@Nonnull PhysicsSpaceRuntimeStatsView view) {
            SpaceStats stats = new SpaceStats();
            stats.bodies = view.bodies();
            stats.dynamicBodies = view.dynamicBodies();
            stats.awakeDynamicBodies = view.awakeDynamicBodies();
            stats.sleepingDynamicBodies = view.sleepingDynamicBodies();
            stats.staticBodies = view.staticBodies();
            stats.kinematicBodies = view.kinematicBodies();
            stats.entityOwnedBodies = view.entityOwnedBodies();
            stats.detachedBodies = view.detachedBodies();
            stats.worldCollisionBodies = view.worldCollisionBodies();
            stats.planeBodies = view.planeBodies();
            stats.rawBodies = view.rawBodies();
            stats.joints = view.joints();
            stats.contacts = view.contacts();
            return stats;
        }

        private void add(@Nonnull SpaceStats stats) {
            bodies += stats.bodies;
            dynamicBodies += stats.dynamicBodies;
            awakeDynamicBodies += stats.awakeDynamicBodies;
            sleepingDynamicBodies += stats.sleepingDynamicBodies;
            staticBodies += stats.staticBodies;
            kinematicBodies += stats.kinematicBodies;
            entityOwnedBodies += stats.entityOwnedBodies;
            detachedBodies += stats.detachedBodies;
            worldCollisionBodies += stats.worldCollisionBodies;
            planeBodies += stats.planeBodies;
            rawBodies += stats.rawBodies;
            joints += stats.joints;
            contacts += stats.contacts;
        }
    }
}
