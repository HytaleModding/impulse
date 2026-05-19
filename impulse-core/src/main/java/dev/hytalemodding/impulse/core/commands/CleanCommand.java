package dev.hytalemodding.impulse.core.commands;

import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractWorldCommand;
import com.hypixel.hytale.server.core.entity.entities.BlockEntity;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.api.PhysicsBody;
import dev.hytalemodding.impulse.api.PhysicsSpace;
import dev.hytalemodding.impulse.core.components.PersistentPhysicsBodyComponent;
import dev.hytalemodding.impulse.core.components.PhysicsBodyComponent;
import dev.hytalemodding.impulse.core.components.PhysicsBodyVisualComponent;
import dev.hytalemodding.impulse.core.components.PhysicsControlSessionComponent;
import dev.hytalemodding.impulse.core.resources.PhysicsSpaceSettings;
import dev.hytalemodding.impulse.core.resources.PhysicsWorldResource;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nonnull;

/**
 * Clears Impulse-owned runtime state from the target world.
 *
 * <p>This removes Hytale adapter entities, visual proxies, runtime bodies, joints,
 * and world-collision cache bodies. Explicit physics spaces are kept.</p>
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

        AtomicInteger removedOrphanVisualEntities = new AtomicInteger();
        store.forEachEntityParallel(BlockEntity.getComponentType(),
            (index, archetypeChunk, commandBuffer) -> {
                if (archetypeChunk.getComponent(index, PhysicsBodyComponent.getComponentType()) != null
                    || archetypeChunk.getComponent(index, PhysicsBodyVisualComponent.getComponentType()) != null
                    || archetypeChunk.getComponent(index, PersistentPhysicsBodyComponent.getComponentType()) != null) {
                    return;
                }

                BlockEntity blockEntity = archetypeChunk.getComponent(index, BlockEntity.getComponentType());
                if (blockEntity == null
                    || !PhysicsSpaceSettings.DEFAULT_DETACHED_VISUAL_BLOCK_TYPE.equals(blockEntity.getBlockTypeKey())) {
                    return;
                }

                removedOrphanVisualEntities.incrementAndGet();
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
        int keptSpaces = 0;
        for (PhysicsSpace space : resource.getSpaces()) {
            keptSpaces++;
            removedBodies += space.bodyCount();
            removedJoints += space.getJoints().size();
            resource.getWorldVoxelCollisionCache().clear(space);
            for (PhysicsBody body : new ArrayList<>(space.getBodies())) {
                resource.unregisterBody(body, true);
            }
        }
        resource.clearBodyOwners();

        context.sendMessage(Message.raw("Removed " + removedBodyEntities.get()
            + " Impulse body entities, " + removedVisualEntities.get()
            + " visual proxy entities, " + removedOrphanVisualEntities.get()
            + " orphan visual proxy entities, " + removedBodies + " runtime bodies, "
            + removedJoints + " joints, and "
            + removedSessions.get() + " control sessions in world " + world.getName()
            + ". Kept " + keptSpaces + " explicit physics spaces."));
    }
}
