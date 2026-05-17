package dev.hytalemodding.impulse.core.commands;

import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractWorldCommand;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.api.PhysicsSpace;
import dev.hytalemodding.impulse.core.components.PhysicsBodyComponent;
import dev.hytalemodding.impulse.core.components.PhysicsBodyVisualComponent;
import dev.hytalemodding.impulse.core.components.PhysicsControlSessionComponent;
import dev.hytalemodding.impulse.core.resources.PhysicsWorldResource;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nonnull;

/**
 * Clears Impulse-owned runtime state from the target world.
 *
 * <p>This removes Hytale adapter entities and visual proxies, closes physics spaces,
 * and clears registry state. No replacement/default space is created implicitly.</p>
 */
public class CleanCommand extends AbstractWorldCommand {

    public CleanCommand() {
        super("clean", "Remove all Impulse physics runtime state from the world", true);
    }

    @Override
    protected void execute(@Nonnull CommandContext context,
        @Nonnull World world,
        @Nonnull Store<EntityStore> store) {
        AtomicInteger removedBodyEntities = new AtomicInteger();
        store.forEachEntityParallel(PhysicsBodyComponent.getComponentType(),
            (index, archetypeChunk, commandBuffer) -> {
                removedBodyEntities.incrementAndGet();
                commandBuffer.removeEntity(archetypeChunk.getReferenceTo(index), RemoveReason.REMOVE);
            });

        AtomicInteger removedVisualEntities = new AtomicInteger();
        store.forEachEntityParallel(PhysicsBodyVisualComponent.getComponentType(),
            (index, archetypeChunk, commandBuffer) -> {
                if (archetypeChunk.getComponent(index, PhysicsBodyComponent.getComponentType()) != null) {
                    return;
                }
                removedVisualEntities.incrementAndGet();
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
        int removedSpaces = 0;
        for (PhysicsSpace space : resource.getSpaces()) {
            removedSpaces++;
            removedBodies += space.bodyCount();
            removedJoints += space.getJoints().size();
        }
        resource.clearAllSpaces(world.getName());
        resource.clearBodyOwners();

        context.sendMessage(Message.raw("Removed " + removedBodyEntities.get()
            + " Impulse body entities, " + removedVisualEntities.get()
            + " visual proxy entities, " + removedBodies + " runtime bodies, "
            + removedJoints + " joints, " + removedSpaces + " spaces, and "
            + removedSessions.get() + " control sessions in world " + world.getName()
            + ". No default physics space is recreated implicitly."));
    }
}
