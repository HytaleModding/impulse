package dev.hytalemodding.impulse.core.internal.commands;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractWorldCommand;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.api.PhysicsSpace;
import dev.hytalemodding.impulse.core.internal.components.GeneratedVisualProxyComponent;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyId;
import dev.hytalemodding.impulse.core.plugin.components.PhysicsBodyAttachmentComponent;
import dev.hytalemodding.impulse.core.plugin.components.PhysicsControlSessionComponent;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsWorldResource;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3f;

/**
 * Clears Impulse-owned runtime state from the target world.
 *
 * <p>This removes Hytale adapter entities, visual proxies, runtime bodies, joints,
 * and current world-collision cache bodies. Explicit physics spaces are kept, including
 * their world-collision settings. Spaces with streaming world collision enabled may
 * build fresh backend terrain bodies again on the next streaming tick.</p>
 *
 * <p>When a radius is provided, cleanup is intentionally narrower: it selects
 * registered body snapshots near the player, removes those bodies and their
 * attachments/proxies, and leaves spaces plus the world-collision cache intact.</p>
 */
public class CleanCommand extends AbstractWorldCommand {

    private static final ComponentType<EntityStore, PhysicsBodyAttachmentComponent> ATTACHMENT_TYPE =
        PhysicsBodyAttachmentComponent.getComponentType();
    private static final ComponentType<EntityStore, GeneratedVisualProxyComponent> GENERATED_PROXY_TYPE =
        GeneratedVisualProxyComponent.getComponentType();
    private static final ComponentType<EntityStore, PhysicsControlSessionComponent> CONTROL_SESSION_TYPE =
        PhysicsControlSessionComponent.getComponentType();
    private static final ComponentType<EntityStore, TransformComponent> TRANSFORM_TYPE =
        TransformComponent.getComponentType();

    private final OptionalArg<Float> radiusArg = this.withOptionalArg(
        "radius",
        "Only clean Impulse bodies and visual entities within this radius of the player",
        ArgTypes.FLOAT);

    public CleanCommand() {
        super("clean", "Remove all Impulse physics runtime state from the world", true);
    }

    @Override
    protected void execute(@Nonnull CommandContext context,
        @Nonnull World world,
        @Nonnull Store<EntityStore> store) {
        if (radiusArg.provided(context)) {
            cleanWithinRadius(context, world, store);
            return;
        }

        cleanAll(context, world, store);
    }

    private static void cleanAll(@Nonnull CommandContext context,
        @Nonnull World world,
        @Nonnull Store<EntityStore> store) {
        AtomicInteger removedBodyEntities = new AtomicInteger();
        store.forEachEntityParallel(ATTACHMENT_TYPE,
            (index, archetypeChunk, commandBuffer) -> {
                removedBodyEntities.incrementAndGet();
                commandBuffer.removeEntity(archetypeChunk.getReferenceTo(index), RemoveReason.REMOVE);
            });

        AtomicInteger removedOrphanVisualEntities = new AtomicInteger();
        store.forEachEntityParallel(GENERATED_PROXY_TYPE,
            (index, archetypeChunk, commandBuffer) -> {
                if (archetypeChunk.getComponent(index, ATTACHMENT_TYPE) != null) {
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

    private void cleanWithinRadius(@Nonnull CommandContext context,
        @Nonnull World world,
        @Nonnull Store<EntityStore> store) {
        float radius = radiusArg.get(context);
        if (!Float.isFinite(radius) || radius <= 0.0f) {
            context.sendMessage(Message.raw("Clean radius must be finite and greater than 0."));
            return;
        }
        if (!context.isPlayer()) {
            context.sendMessage(Message.raw("Clean radius can only be used by a player."));
            return;
        }

        Vector3d center = playerPosition(context, store);
        if (center == null) {
            context.sendMessage(Message.raw("Cannot determine player position."));
            return;
        }

        PhysicsWorldResource resource = store.getResource(PhysicsWorldResource.getResourceType());
        resource.refreshBodySnapshots();
        Set<PhysicsBodyId> selectedBodies = selectBodyIdsNear(resource, center, radius);
        double radiusSquared = (double) radius * radius;

        AtomicInteger removedBodyEntities = new AtomicInteger();
        store.forEachEntityParallel(ATTACHMENT_TYPE,
            (index, archetypeChunk, commandBuffer) -> {
                PhysicsBodyAttachmentComponent attachment =
                    archetypeChunk.getComponent(index, ATTACHMENT_TYPE);
                if (!selectedBodies.contains(attachment.getBodyId())) {
                    return;
                }

                removedBodyEntities.incrementAndGet();
                commandBuffer.removeEntity(archetypeChunk.getReferenceTo(index), RemoveReason.REMOVE);
            });

        AtomicInteger removedOrphanVisualEntities = new AtomicInteger();
        store.forEachEntityParallel(GENERATED_PROXY_TYPE,
            (index, archetypeChunk, commandBuffer) -> {
                if (archetypeChunk.getComponent(index, ATTACHMENT_TYPE) != null
                    || !entityWithinRadius(archetypeChunk, index, center, radiusSquared)) {
                    return;
                }

                removedOrphanVisualEntities.incrementAndGet();
                commandBuffer.removeEntity(archetypeChunk.getReferenceTo(index), RemoveReason.REMOVE);
            });

        AtomicInteger removedSessions = new AtomicInteger();
        store.forEachEntityParallel(CONTROL_SESSION_TYPE,
            (index, archetypeChunk, commandBuffer) -> {
                PhysicsControlSessionComponent session =
                    archetypeChunk.getComponent(index, CONTROL_SESSION_TYPE);
                if (!controlSessionSelected(commandBuffer,
                    archetypeChunk,
                    index,
                    session,
                    selectedBodies,
                    center,
                    radiusSquared)) {
                    return;
                }

                removedSessions.incrementAndGet();
                commandBuffer.removeComponent(archetypeChunk.getReferenceTo(index), CONTROL_SESSION_TYPE);
            });

        AtomicInteger removedBodies = new AtomicInteger();
        for (PhysicsBodyId bodyId : selectedBodies) {
            resource.destroyBody(bodyId);
            removedBodies.incrementAndGet();
        }

        context.sendMessage(Message.raw("Removed " + removedBodyEntities.get()
            + " Impulse attachment entities, " + removedOrphanVisualEntities.get()
            + " orphan visual proxy entities, " + removedBodies.get()
            + " runtime bodies, and " + removedSessions.get()
            + " control sessions within radius " + radius + " in world " + world.getName()
            + ". Kept explicit physics spaces and world-collision cache."));
    }

    @Nullable
    private static Vector3d playerPosition(@Nonnull CommandContext context,
        @Nonnull Store<EntityStore> store) {
        Ref<EntityStore> playerRef = context.senderAsPlayerRef();
        TransformComponent transform = store.getComponent(playerRef, TRANSFORM_TYPE);
        return transform != null ? new Vector3d(transform.getPosition()) : null;
    }

    @Nonnull
    private static Set<PhysicsBodyId> selectBodyIdsNear(@Nonnull PhysicsWorldResource resource,
        @Nonnull Vector3d center,
        float radius) {
        Set<PhysicsBodyId> bodyIds = ConcurrentHashMap.newKeySet();
        Vector3f centerF = new Vector3f((float) center.x, (float) center.y, (float) center.z);
        for (PhysicsSpace space : resource.getSpaces()) {
            resource.forEachBodySnapshotNear(space.getId(), centerF, radius,
                entry -> bodyIds.add(entry.bodyId()));
        }
        return bodyIds;
    }

    private static boolean controlSessionSelected(
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
        int index,
        @Nonnull PhysicsControlSessionComponent session,
        @Nonnull Set<PhysicsBodyId> selectedBodies,
        @Nonnull Vector3d center,
        double radiusSquared) {
        if (containsBody(selectedBodies, session.getBodyId())
            || containsBody(selectedBodies, session.getAnchorBodyId())
            || entityWithinRadius(archetypeChunk, index, center, radiusSquared)) {
            return true;
        }

        Ref<EntityStore> targetRef = session.getTargetRef();
        if (targetRef == null || !targetRef.isValid()) {
            return false;
        }

        TransformComponent targetTransform = commandBuffer.getComponent(targetRef, TRANSFORM_TYPE);
        return targetTransform != null && positionWithinRadius(targetTransform.getPosition(),
            center,
            radiusSquared);
    }

    private static boolean containsBody(@Nonnull Set<PhysicsBodyId> bodyIds,
        @Nullable PhysicsBodyId bodyId) {
        return bodyId != null && bodyIds.contains(bodyId);
    }

    private static boolean entityWithinRadius(@Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
        int index,
        @Nonnull Vector3d center,
        double radiusSquared) {
        TransformComponent transform = archetypeChunk.getComponent(index, TRANSFORM_TYPE);
        return transform != null && positionWithinRadius(transform.getPosition(), center, radiusSquared);
    }

    private static boolean positionWithinRadius(@Nonnull Vector3d position,
        @Nonnull Vector3d center,
        double radiusSquared) {
        return position.distanceSquared(center) <= radiusSquared;
    }
}
