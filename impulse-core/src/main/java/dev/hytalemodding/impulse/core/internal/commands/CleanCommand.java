package dev.hytalemodding.impulse.core.internal.commands;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractWorldCommand;
import com.hypixel.hytale.server.core.entity.entities.BlockEntity;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.core.plugin.components.PhysicsControlSessionComponent;
import dev.hytalemodding.impulse.core.plugin.components.PhysicsBodyAttachmentComponent;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsSpaceSettings;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsWorldResource;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nonnull;

/**
 * Clears Impulse-owned runtime state from the target world.
 *
 * <p>This removes Hytale adapter entities, visual proxies, runtime bodies, joints,
 * and current world-collision cache bodies. Explicit physics spaces are kept, including
 * their world-collision settings. Spaces with streaming world collision enabled may
 * build fresh backend terrain bodies again on the next streaming tick.</p>
 */
public class CleanCommand extends AbstractWorldCommand {

    private static final ComponentType<EntityStore, PhysicsBodyAttachmentComponent> ATTACHMENT_TYPE =
        PhysicsBodyAttachmentComponent.getComponentType();
    private static final ComponentType<EntityStore, BlockEntity> BLOCK_ENTITY_TYPE = BlockEntity.getComponentType();
    private static final ComponentType<EntityStore, PhysicsControlSessionComponent> CONTROL_SESSION_TYPE =
        PhysicsControlSessionComponent.getComponentType();

    public CleanCommand() {
        super("clean", "Remove all Impulse physics runtime state from the world", true);
    }

    @Override
    protected void execute(@Nonnull CommandContext context,
        @Nonnull World world,
        @Nonnull Store<EntityStore> store) {
        AtomicInteger removedBodyEntities = new AtomicInteger();
        store.forEachEntityParallel(ATTACHMENT_TYPE,
            (index, archetypeChunk, commandBuffer) -> {
                removedBodyEntities.incrementAndGet();
                commandBuffer.removeEntity(archetypeChunk.getReferenceTo(index), RemoveReason.REMOVE);
            });

        AtomicInteger removedOrphanVisualEntities = new AtomicInteger();
        store.forEachEntityParallel(BLOCK_ENTITY_TYPE,
            (index, archetypeChunk, commandBuffer) -> {
                if (archetypeChunk.getComponent(index, ATTACHMENT_TYPE) != null) {
                    return;
                }

                BlockEntity blockEntity = archetypeChunk.getComponent(index, BLOCK_ENTITY_TYPE);
                if (blockEntity == null
                    || !PhysicsSpaceSettings.DEFAULT_DETACHED_VISUAL_BLOCK_TYPE.equals(blockEntity.getBlockTypeKey())) {
                    return;
                }

                removedOrphanVisualEntities.incrementAndGet();
                commandBuffer.removeEntity(archetypeChunk.getReferenceTo(index), RemoveReason.REMOVE);
            });

        AtomicInteger removedSessions = new AtomicInteger();
        store.forEachEntityParallel(CONTROL_SESSION_TYPE,
            (index, archetypeChunk, commandBuffer) -> {
                removedSessions.incrementAndGet();
                commandBuffer.removeComponent(archetypeChunk.getReferenceTo(index), CONTROL_SESSION_TYPE);
            });

        PhysicsWorldResource resource = store.getResource(PhysicsWorldResource.getResourceType());
        PhysicsWorldResource.RuntimeResetResult reset =
            resource.resetRuntimeStateKeepingSpaces(world.getName());

        context.sendMessage(Message.raw("Removed " + removedBodyEntities.get()
            + " Impulse attachment entities, " + removedOrphanVisualEntities.get()
            + " orphan visual proxy entities, " + reset.removedBodies() + " runtime bodies, "
            + reset.removedJoints() + " joints, and "
            + removedSessions.get() + " control sessions in world " + world.getName()
            + ". Kept " + reset.keptSpaces() + " explicit physics spaces."));
    }
}
