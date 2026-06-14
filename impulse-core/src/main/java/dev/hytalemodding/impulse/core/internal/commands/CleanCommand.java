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
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.internal.modules.control.components.PhysicsControlSessionComponent;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsRuntimeResetResult;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsWorldRuntimeResource;
import dev.hytalemodding.impulse.core.internal.modules.control.systems.PhysicsControlSessionCleanup;
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import dev.hytalemodding.impulse.core.plugin.physicsstore.projection.BodyAttachmentComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.projection.GeneratedVisualProxyComponent;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicIntegerArray;
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

    private static final int REMOVED_BODY_ENTITIES = 0;
    private static final int REMOVED_ORPHAN_VISUAL_ENTITIES = 1;
    private static final int REMOVED_SESSIONS = 2;
    private static final int REMOVED_ENTITY_COUNTERS = 3;

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
        ComponentType<EntityStore, BodyAttachmentComponent> attachmentType =
            BodyAttachmentComponent.getComponentType();
        ComponentType<EntityStore, GeneratedVisualProxyComponent> generatedProxyType =
            GeneratedVisualProxyComponent.getComponentType();
        AtomicIntegerArray removedEntities = new AtomicIntegerArray(REMOVED_ENTITY_COUNTERS);
        store.forEachEntityParallel(attachmentType,
            (index, archetypeChunk, commandBuffer) -> {
                removedEntities.incrementAndGet(REMOVED_BODY_ENTITIES);
                commandBuffer.removeEntity(archetypeChunk.getReferenceTo(index), RemoveReason.REMOVE);
            });

        store.forEachEntityParallel(generatedProxyType,
            (index, archetypeChunk, commandBuffer) -> {
                if (archetypeChunk.getComponent(index, attachmentType) != null) {
                    return;
                }

                removedEntities.incrementAndGet(REMOVED_ORPHAN_VISUAL_ENTITIES);
                commandBuffer.removeEntity(archetypeChunk.getReferenceTo(index), RemoveReason.REMOVE);
            });

        ComponentType<EntityStore, PhysicsControlSessionComponent> controlSessionType =
            controlSessionTypeOrNull();
        if (controlSessionType != null) {
            store.forEachEntityParallel(controlSessionType,
                (index, archetypeChunk, commandBuffer) -> {
                    removedEntities.incrementAndGet(REMOVED_SESSIONS);
                    commandBuffer.removeComponent(archetypeChunk.getReferenceTo(index),
                        controlSessionType);
                });
        }

        PhysicsWorldRuntimeResource resource = PhysicsWorldRuntimeResource.require(store);
        PhysicsRuntimeResetResult reset =
            resource.resetRuntimeStateKeepingSpaces(world.getName());

        context.sendMessage(Message.raw("Removed " + removedEntities.get(REMOVED_BODY_ENTITIES)
            + " Impulse attachment entities, " + removedEntities.get(REMOVED_ORPHAN_VISUAL_ENTITIES)
            + " orphan visual proxy entities, " + reset.removedBodies() + " runtime bodies, "
            + reset.removedJoints() + " joints, and "
            + removedEntities.get(REMOVED_SESSIONS) + " control sessions in world " + world.getName()
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

        PhysicsWorldRuntimeResource resource = PhysicsWorldRuntimeResource.require(store);
        resource.refreshBodySnapshots();
        Set<RigidBodyKey> selectedBodyKeys = selectBodyKeysNear(resource, center, radius);
        double radiusSquared = (double) radius * radius;
        ComponentType<EntityStore, BodyAttachmentComponent> attachmentType =
            BodyAttachmentComponent.getComponentType();
        ComponentType<EntityStore, GeneratedVisualProxyComponent> generatedProxyType =
            GeneratedVisualProxyComponent.getComponentType();

        AtomicIntegerArray removedEntities = new AtomicIntegerArray(REMOVED_ENTITY_COUNTERS);
        store.forEachEntityParallel(attachmentType,
            (index, archetypeChunk, commandBuffer) -> {
                BodyAttachmentComponent attachment =
                    archetypeChunk.getComponent(index, attachmentType);
                assert attachment != null;
                if (!selectedBodyKeys.contains(RigidBodyKey.of(attachment.getBodyUuid()))) {
                    return;
                }

                removedEntities.incrementAndGet(REMOVED_BODY_ENTITIES);
                commandBuffer.removeEntity(archetypeChunk.getReferenceTo(index), RemoveReason.REMOVE);
            });

        store.forEachEntityParallel(generatedProxyType,
            (index, archetypeChunk, commandBuffer) -> {
                if (archetypeChunk.getComponent(index, attachmentType) != null
                    || !entityWithinRadius(archetypeChunk, index, center, radiusSquared)) {
                    return;
                }

                removedEntities.incrementAndGet(REMOVED_ORPHAN_VISUAL_ENTITIES);
                commandBuffer.removeEntity(archetypeChunk.getReferenceTo(index), RemoveReason.REMOVE);
            });

        ComponentType<EntityStore, PhysicsControlSessionComponent> controlSessionType =
            controlSessionTypeOrNull();
        if (controlSessionType != null) {
            store.forEachEntityParallel(controlSessionType,
                (index, archetypeChunk, commandBuffer) -> {
                    PhysicsControlSessionComponent session =
                        archetypeChunk.getComponent(index, controlSessionType);
                    assert session != null;
                    if (!controlSessionSelected(commandBuffer,
                        archetypeChunk,
                        index,
                        session,
                        selectedBodyKeys,
                        center,
                        radiusSquared)) {
                        return;
                    }

                    removedEntities.incrementAndGet(REMOVED_SESSIONS);
                    PhysicsControlSessionCleanup.cleanup(store, session);
                    commandBuffer.removeComponent(archetypeChunk.getReferenceTo(index),
                        controlSessionType);
                });
        }

        int removedBodies = 0;
        for (RigidBodyKey bodyKey : selectedBodyKeys) {
            resource.destroyBody(bodyKey);
            removedBodies++;
        }

        context.sendMessage(Message.raw("Removed " + removedEntities.get(REMOVED_BODY_ENTITIES)
            + " Impulse attachment entities, " + removedEntities.get(REMOVED_ORPHAN_VISUAL_ENTITIES)
            + " orphan visual proxy entities, " + removedBodies
            + " runtime bodies, and " + removedEntities.get(REMOVED_SESSIONS)
            + " control sessions within radius " + radius + " in world " + world.getName()
            + ". Kept explicit physics spaces and world-collision cache."));
    }

    @Nullable
    private static Vector3d playerPosition(@Nonnull CommandContext context,
        @Nonnull Store<EntityStore> store) {
        Ref<EntityStore> playerRef = context.senderAsPlayerRef();
        assert playerRef != null;
        TransformComponent transform =
            store.getComponent(playerRef, TransformComponent.getComponentType());
        return transform != null ? new Vector3d(transform.getPosition()) : null;
    }

    @Nonnull
    private static Set<RigidBodyKey> selectBodyKeysNear(@Nonnull PhysicsWorldRuntimeResource resource,
        @Nonnull Vector3d center,
        float radius) {
        Set<RigidBodyKey> bodyKeys = new ObjectOpenHashSet<>();
        Vector3f centerF = new Vector3f((float) center.x, (float) center.y, (float) center.z);
        for (SpaceId spaceId : resource.getSpaceIds()) {
            resource.forEachIndexedBodySnapshotNear(spaceId,
                centerF,
                radius,
                (bodyKey, snapshot, bodySpaceId, kind, persistenceMode) -> bodyKeys.add(bodyKey));
        }
        return bodyKeys;
    }

    private static boolean controlSessionSelected(
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
        int index,
        @Nonnull PhysicsControlSessionComponent session,
        @Nonnull Set<RigidBodyKey> selectedBodyKeys,
        @Nonnull Vector3d center,
        double radiusSquared) {
        if (containsBody(selectedBodyKeys, session.getBodyUuid())
            || containsBody(selectedBodyKeys, session.getAnchorBodyUuid())
            || entityWithinRadius(archetypeChunk, index, center, radiusSquared)) {
            return true;
        }

        Ref<EntityStore> targetRef = session.getTargetRef();
        if (targetRef == null || !targetRef.isValid()) {
            return false;
        }

        TransformComponent targetTransform =
            commandBuffer.getComponent(targetRef, TransformComponent.getComponentType());
        return targetTransform != null && positionWithinRadius(targetTransform.getPosition(),
            center,
            radiusSquared);
    }

    @Nullable
    private static ComponentType<EntityStore, PhysicsControlSessionComponent> controlSessionTypeOrNull() {
        return PhysicsControlSessionComponent.isComponentTypeRegistered()
            ? PhysicsControlSessionComponent.getComponentType()
            : null;
    }

    private static boolean containsBody(@Nonnull Set<RigidBodyKey> bodyKeys,
        @Nullable UUID bodyUuid) {
        return bodyUuid != null && bodyKeys.contains(RigidBodyKey.of(bodyUuid));
    }

    private static boolean entityWithinRadius(@Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
        int index,
        @Nonnull Vector3d center,
        double radiusSquared) {
        TransformComponent transform =
            archetypeChunk.getComponent(index, TransformComponent.getComponentType());
        return transform != null && positionWithinRadius(transform.getPosition(), center, radiusSquared);
    }

    private static boolean positionWithinRadius(@Nonnull Vector3d position,
        @Nonnull Vector3d center,
        double radiusSquared) {
        return position.distanceSquared(center) <= radiusSquared;
    }
}
