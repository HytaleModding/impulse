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
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.internal.components.GeneratedVisualProxyComponent;
import dev.hytalemodding.impulse.core.internal.modules.control.components.PhysicsControlSessionComponent;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsRuntimeResetResult;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsWorldRuntimeResource;
import dev.hytalemodding.impulse.core.internal.modules.control.systems.PhysicsControlSessionCleanup;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.UuidComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.projection.BodyAttachmentComponent;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
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
        CompletionStage<PhysicsRuntimeResetResult> reset =
            resource.resetRuntimeStateKeepingSpacesAsync(world.getName());
        reset.whenComplete((result, failure) -> sendCleanAllResult(world,
            context,
            removedEntities,
            result,
            failure));
    }

    private static void sendCleanAllResult(@Nonnull World world,
        @Nonnull CommandContext context,
        @Nonnull AtomicIntegerArray removedEntities,
        @Nullable PhysicsRuntimeResetResult reset,
        @Nullable Throwable failure) {
        Runnable sender = () -> {
            if (failure != null) {
                Throwable cause = unwrap(failure);
                String message = cause.getMessage() != null ? cause.getMessage() : cause.toString();
                context.sendMessage(Message.raw("Failed to clean Impulse physics runtime state: "
                    + message));
                return;
            }
            if (reset == null) {
                context.sendMessage(Message.raw("Failed to clean Impulse physics runtime state."));
                return;
            }
            sendCleanAllSuccess(context, removedEntities, reset, world.getName());
        };
        if (world.isInThread()) {
            sender.run();
            return;
        }
        world.execute(sender);
    }

    private static void sendCleanAllSuccess(@Nonnull CommandContext context,
        @Nonnull AtomicIntegerArray removedEntities,
        @Nonnull PhysicsRuntimeResetResult reset,
        @Nonnull String worldName) {
        context.sendMessage(Message.raw("Removed " + removedEntities.get(REMOVED_BODY_ENTITIES)
            + " Impulse attachment entities, " + removedEntities.get(REMOVED_ORPHAN_VISUAL_ENTITIES)
            + " orphan visual proxy entities, " + reset.removedBodies() + " runtime bodies, "
            + reset.removedJoints() + " joints, and "
            + removedEntities.get(REMOVED_SESSIONS) + " control sessions in world " + worldName
            + ". Kept " + reset.keptSpaces() + " explicit physics spaces."));
    }

    @Nonnull
    private static Throwable unwrap(@Nonnull Throwable failure) {
        if (failure instanceof CompletionException completionException
            && completionException.getCause() != null) {
            return completionException.getCause();
        }
        return failure;
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
        SelectedBodies selectedBodies = selectBodiesNear(resource, center, radius);
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
                if (!selectedBodies.bodyUuids().contains(attachment.getBodyUuid())) {
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
                        selectedBodies.bodyUuids(),
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
        for (UUID bodyUuid : selectedBodies.bodyUuids()) {
            resource.destroyBody(bodyUuid);
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
    private static SelectedBodies selectBodiesNear(@Nonnull PhysicsWorldRuntimeResource resource,
        @Nonnull Vector3d center,
        float radius) {
        Set<UUID> bodyUuids = new ObjectOpenHashSet<>();
        Vector3f centerF = new Vector3f((float) center.x, (float) center.y, (float) center.z);
        for (SpaceId spaceId : resource.getSpaceIds()) {
            resource.forEachIndexedBodySnapshotNear(spaceId,
                centerF,
                radius,
                (bodyUuid, snapshot, bodySpaceId, kind, persistenceMode) -> {
                    bodyUuids.add(bodyUuid);
                });
        }
        return new SelectedBodies(bodyUuids);
    }

    private static boolean controlSessionSelected(
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
        int index,
        @Nonnull PhysicsControlSessionComponent session,
        @Nonnull Set<UUID> selectedBodyUuids,
        @Nonnull Vector3d center,
        double radiusSquared) {
        if (containsBody(selectedBodyUuids, session.getBodyRef())
            || containsBody(selectedBodyUuids, session.getAnchorBodyRef())
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

    private static boolean containsBody(@Nonnull Set<UUID> bodyUuids,
        @Nullable Ref<PhysicsStore> bodyRef) {
        UUID bodyUuid = rowUuid(bodyRef);
        return bodyUuid != null && bodyUuids.contains(bodyUuid);
    }

    private record SelectedBodies(@Nonnull Set<UUID> bodyUuids) {
    }

    @Nullable
    private static UUID rowUuid(@Nullable Ref<PhysicsStore> bodyRef) {
        if (bodyRef == null || !bodyRef.isValid()) {
            return null;
        }
        UuidComponent uuid = bodyRef.getStore().getComponent(bodyRef, UuidComponent.getComponentType());
        return uuid != null ? uuid.getUuid() : null;
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
