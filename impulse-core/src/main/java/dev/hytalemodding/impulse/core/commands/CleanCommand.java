package dev.hytalemodding.impulse.core.commands;

import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractWorldCommand;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.api.PhysicsBody;
import dev.hytalemodding.impulse.api.PhysicsJoint;
import dev.hytalemodding.impulse.api.PhysicsSpace;
import dev.hytalemodding.impulse.api.ShapeType;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.components.PhysicsControlSessionComponent;
import dev.hytalemodding.impulse.core.components.PhysicsBodyComponent;
import dev.hytalemodding.impulse.core.resources.PhysicsWorldResource;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nonnull;

/**
 * Removes entity-backed bodies, raw/orphan runtime bodies, and joints from the target world.
 *
 * <p>Static world scaffolding is preserved: default plane bodies and streamed world-collision
 * bodies stay registered so the space remains usable after cleanup.</p>
 */
public class CleanCommand extends AbstractWorldCommand {

    public CleanCommand() {
        super("clean", "Remove all Impulse physics entities from the world", true);
    }

    @Override
    protected void execute(@Nonnull CommandContext context,
        @Nonnull World world,
        @Nonnull Store<EntityStore> store) {
        AtomicInteger removedEntities = new AtomicInteger();
        store.forEachEntityParallel(PhysicsBodyComponent.getComponentType(),
            (index, archetypeChunk, commandBuffer) -> {
                removedEntities.incrementAndGet();
                commandBuffer.removeEntity(archetypeChunk.getReferenceTo(index), RemoveReason.REMOVE);
            });

        AtomicInteger removedSessions = new AtomicInteger();
        store.forEachEntityParallel(PhysicsControlSessionComponent.getComponentType(),
            (index, archetypeChunk, commandBuffer) -> {
                removedSessions.incrementAndGet();
                commandBuffer.removeComponent(archetypeChunk.getReferenceTo(index),
                    PhysicsControlSessionComponent.getComponentType());
            });

        PhysicsWorldResource resource = store.getResource(PhysicsWorldResource.getResourceType());
        int removedBodies = 0;
        int removedJoints = 0;
        for (PhysicsSpace space : resource.getSpaces()) {
            List<PhysicsJoint> joints = new ArrayList<>(space.getJoints());
            for (PhysicsJoint joint : joints) {
                space.removeJoint(joint);
                removedJoints++;
            }

            SpaceId spaceId = space.getId();
            List<PhysicsBody> bodies = new ArrayList<>(space.getBodies());
            for (PhysicsBody body : bodies) {
                if (body.getShapeType() == ShapeType.PLANE) {
                    continue;
                }
                if (resource.getWorldVoxelCollisionCache().containsBody(spaceId, body)) {
                    continue;
                }

                resource.clearBodyRuntimeState(body);
                space.removeBody(body);
                removedBodies++;
            }
        }

        context.sendMessage(Message.raw("Removed " + removedEntities.get()
            + " Impulse physics entities, " + removedBodies + " non-entity runtime bodies, "
            + removedJoints + " joints, and " + removedSessions.get()
            + " control sessions in world " + world.getName()
            + ". Preserved static plane and streamed world collision bodies."));
    }
}
