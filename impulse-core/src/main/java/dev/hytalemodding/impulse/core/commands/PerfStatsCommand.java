package dev.hytalemodding.impulse.core.commands;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractWorldCommand;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.api.PhysicsBody;
import dev.hytalemodding.impulse.api.PhysicsSpace;
import dev.hytalemodding.impulse.api.ShapeType;
import dev.hytalemodding.impulse.core.resources.PhysicsWorldResource;
import dev.hytalemodding.impulse.core.voxel.WorldVoxelCollisionCache;
import java.util.Collection;
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
        Collection<PhysicsSpace> spaces = resource.getSpaces();
        if (spaces.isEmpty()) {
            ctx.sender().sendMessage(Message.raw("Impulse runtime stats: no physics spaces in world "
                + world.getName() + "."));
            return;
        }

        SpaceStats totals = new SpaceStats();
        WorldVoxelCollisionCache cache = resource.getWorldVoxelCollisionCache();
        ctx.sender().sendMessage(Message.raw("Impulse runtime stats for world " + world.getName()
            + ": spaces=" + spaces.size()));
        for (PhysicsSpace space : spaces) {
            SpaceStats stats = collectSpaceStats(resource, cache, space);
            totals.add(stats);
            ctx.sender().sendMessage(Message.raw("Space " + space.getId().value()
                + " backend=" + space.getBackendId().value()
                + " bodies=" + stats.bodies
                + " dynamic=" + stats.dynamicBodies
                + " awake=" + stats.awakeDynamicBodies
                + " sleeping=" + stats.sleepingDynamicBodies
                + " static=" + stats.staticBodies
                + " kinematic=" + stats.kinematicBodies
                + " owned=" + stats.entityOwnedBodies
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
            + " worldCollision=" + totals.worldCollisionBodies
            + " planes=" + totals.planeBodies
            + " raw=" + totals.rawBodies
            + " joints=" + totals.joints
            + " contacts=" + totals.contacts));
    }

    @Nonnull
    private static SpaceStats collectSpaceStats(@Nonnull PhysicsWorldResource resource,
        @Nonnull WorldVoxelCollisionCache cache,
        @Nonnull PhysicsSpace space) {
        SpaceStats stats = new SpaceStats();
        space.forEachBody(body -> classifyBody(resource, cache, space, body, stats));
        stats.joints = space.jointCount();
        stats.contacts = space.getContacts().size();
        return stats;
    }

    private static void classifyBody(@Nonnull PhysicsWorldResource resource,
        @Nonnull WorldVoxelCollisionCache cache,
        @Nonnull PhysicsSpace space,
        @Nonnull PhysicsBody body,
        @Nonnull SpaceStats stats) {
        stats.bodies++;
        if (body.isDynamic()) {
            stats.dynamicBodies++;
            if (body.isSleeping()) {
                stats.sleepingDynamicBodies++;
            } else {
                stats.awakeDynamicBodies++;
            }
        } else if (body.isKinematic()) {
            stats.kinematicBodies++;
        } else {
            stats.staticBodies++;
        }

        if (resource.getBodyOwner(body) != null) {
            stats.entityOwnedBodies++;
            return;
        }
        if (body.getShapeType() == ShapeType.PLANE) {
            stats.planeBodies++;
            return;
        }
        if (cache.containsBody(space.getId(), body)) {
            stats.worldCollisionBodies++;
            return;
        }
        stats.rawBodies++;
    }

    private static final class SpaceStats {

        private int bodies;
        private int dynamicBodies;
        private int awakeDynamicBodies;
        private int sleepingDynamicBodies;
        private int staticBodies;
        private int kinematicBodies;
        private int entityOwnedBodies;
        private int worldCollisionBodies;
        private int planeBodies;
        private int rawBodies;
        private int joints;
        private int contacts;

        private void add(@Nonnull SpaceStats stats) {
            bodies += stats.bodies;
            dynamicBodies += stats.dynamicBodies;
            awakeDynamicBodies += stats.awakeDynamicBodies;
            sleepingDynamicBodies += stats.sleepingDynamicBodies;
            staticBodies += stats.staticBodies;
            kinematicBodies += stats.kinematicBodies;
            entityOwnedBodies += stats.entityOwnedBodies;
            worldCollisionBodies += stats.worldCollisionBodies;
            planeBodies += stats.planeBodies;
            rawBodies += stats.rawBodies;
            joints += stats.joints;
            contacts += stats.contacts;
        }
    }
}
