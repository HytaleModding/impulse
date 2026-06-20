package dev.hytalemodding.impulse.core.internal.commands;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
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
import dev.hytalemodding.impulse.core.internal.modules.control.components.PhysicsControlSessionComponent;
import dev.hytalemodding.impulse.core.internal.modules.control.systems.PhysicsControlSessionCleanup;
import dev.hytalemodding.impulse.core.internal.modules.physicsentity.PhysicsEntityProjectionCleanup;
import dev.hytalemodding.impulse.core.internal.modules.physicsentity.PhysicsEntityProjectionCleanup.Result;
import dev.hytalemodding.impulse.core.internal.physics.PhysicsTopologyMutations;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsRuntimeResetResult;
import dev.hytalemodding.impulse.core.plugin.components.UuidComponent;
import dev.hytalemodding.impulse.core.plugin.modules.control.ImpulseControllableComponent;
import dev.hytalemodding.impulse.core.plugin.modules.physicschunk.PhysicsChunkCollision;
import dev.hytalemodding.impulse.core.plugin.physics.PhysicsBodies;
import dev.hytalemodding.impulse.core.plugin.physics.PhysicsThreading;
import dev.hytalemodding.impulse.core.plugin.snapshots.PhysicsBodySnapshot;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3f;

/**
 * Clears Impulse-owned runtime state from the target world.
 *
 * <p>This removes Impulse-owned visual entities, detaches external physics attachments,
 * clears runtime bodies, joints, and current PhysicsChunk collision cache bodies. Explicit
 * physics spaces are kept, including their PhysicsChunk collision settings. Spaces with streaming
 * PhysicsChunk collision enabled may build fresh backend terrain bodies again on the next
 * streaming tick.</p>
 *
 * <p>When a radius is provided, cleanup is intentionally narrower: it selects
 * registered body snapshots near the player, removes those bodies and their
 * attachments/proxies, and leaves spaces plus the PhysicsChunk collision cache intact.</p>
 */
public class CleanCommand extends AbstractWorldCommand {

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
        Result projectionCleanup =
            PhysicsEntityProjectionCleanup.cleanAll(store, controllableTypeOrNull());

        AtomicInteger removedSessions = new AtomicInteger();
        ComponentType<EntityStore, PhysicsControlSessionComponent> controlSessionType =
            controlSessionTypeOrNull();
        if (controlSessionType != null) {
            store.forEachEntityParallel(controlSessionType,
                (index, archetypeChunk, commandBuffer) -> {
                    removedSessions.incrementAndGet();
                    commandBuffer.removeComponent(archetypeChunk.getReferenceTo(index),
                        controlSessionType);
                });
        }

        CompletionStage<PhysicsRuntimeResetResult> reset =
            PhysicsThreading.callWhenBackendIdleOnWorldThread(world,
                "clear PhysicsStore body entities",
                PhysicsTopologyMutations::clearBodiesKeepingSpaces);
        reset.whenComplete((result, failure) -> sendCleanAllResult(world,
            context,
            projectionCleanup,
            removedSessions.get(),
            result,
            failure));
    }

    private static void sendCleanAllResult(@Nonnull World world,
        @Nonnull CommandContext context,
        @Nonnull Result projectionCleanup,
        int removedSessions,
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
            sendCleanAllSuccess(context,
                projectionCleanup,
                removedSessions,
                reset,
                world.getName());
        };
        if (world.isInThread()) {
            sender.run();
            return;
        }
        world.execute(sender);
    }

    private static void sendCleanAllSuccess(@Nonnull CommandContext context,
        @Nonnull Result projectionCleanup,
        int removedSessions,
        @Nonnull PhysicsRuntimeResetResult reset,
        @Nonnull String worldName) {
        String prefix = projectionCleanup.skipped()
            ? "Impulse PhysicsEntity integration is not available; skipped EntityStore attachment/proxy cleanup. "
            : "";
        context.sendMessage(Message.raw(prefix + "Removed "
            + projectionCleanup.removedAttachmentEntities()
            + " Impulse-owned attachment entities, "
            + projectionCleanup.detachedExternalAttachments()
            + " detached external attachments, "
            + projectionCleanup.removedOrphanVisualEntities()
            + " orphan visual proxy entities, " + reset.removedBodies()
            + " runtime bodies, " + reset.removedJoints() + " joints, and "
            + removedSessions + " control sessions in world " + worldName
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

        Store<PhysicsStore> physicsStore = PhysicsThreading.store(world);
        SelectedBodies selectedBodies = selectBodiesNear(physicsStore, center, radius);
        double radiusSquared = (double) radius * radius;
        CompletionStage<RadiusCleanResult> clean = PhysicsThreading.callWhenBackendIdleOnWorldThread(world,
            "clean Impulse physics bodies within radius",
            backendStore -> cleanSelectedBodies(store,
                backendStore,
                selectedBodies,
                center,
                radiusSquared));
        clean.whenComplete((result, failure) -> sendCleanRadiusResult(world,
            context,
            radius,
            result,
            failure));
    }

    @Nonnull
    private static RadiusCleanResult cleanSelectedBodies(@Nonnull Store<EntityStore> store,
        @Nonnull Store<PhysicsStore> physicsStore,
        @Nonnull SelectedBodies selectedBodies,
        @Nonnull Vector3d center,
        double radiusSquared) {
        Result projectionCleanup = PhysicsEntityProjectionCleanup.cleanSelected(store,
            selectedBodies.bodyUuids(),
            center,
            radiusSquared,
            controllableTypeOrNull());

        AtomicInteger removedSessions = new AtomicInteger();
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

                    removedSessions.incrementAndGet();
                    PhysicsControlSessionCleanup.cleanup(store, session);
                    commandBuffer.removeComponent(archetypeChunk.getReferenceTo(index),
                        controlSessionType);
                });
        }

        int removedBodies = 0;
        for (UUID bodyUuid : selectedBodies.bodyUuids()) {
            PhysicsBodies.destroy(physicsStore, bodyUuid);
            removedBodies++;
        }

        return new RadiusCleanResult(projectionCleanup,
            removedSessions.get(),
            removedBodies);
    }

    private static void sendCleanRadiusResult(@Nonnull World world,
        @Nonnull CommandContext context,
        float radius,
        @Nullable RadiusCleanResult result,
        @Nullable Throwable failure) {
        Runnable sender = () -> {
            if (failure != null) {
                Throwable cause = unwrap(failure);
                String message = cause.getMessage() != null ? cause.getMessage() : cause.toString();
                context.sendMessage(Message.raw("Failed to clean Impulse physics bodies within radius: "
                    + message));
                return;
            }
            if (result == null) {
                context.sendMessage(Message.raw("Failed to clean Impulse physics bodies within radius."));
                return;
            }
            sendCleanRadiusSuccess(context, result, radius, world.getName());
        };
        if (world.isInThread()) {
            sender.run();
            return;
        }
        world.execute(sender);
    }

    private static void sendCleanRadiusSuccess(@Nonnull CommandContext context,
        @Nonnull RadiusCleanResult result,
        float radius,
        @Nonnull String worldName) {
        Result projectionCleanup = result.projectionCleanup();
        String prefix = projectionCleanup.skipped()
            ? "Impulse PhysicsEntity integration is not available; skipped EntityStore attachment/proxy cleanup. "
            : "";
        context.sendMessage(Message.raw(prefix + "Removed "
            + projectionCleanup.removedAttachmentEntities()
            + " Impulse-owned attachment entities, "
            + projectionCleanup.detachedExternalAttachments()
            + " detached external attachments, "
            + projectionCleanup.removedOrphanVisualEntities() + " orphan visual proxy entities, "
            + result.removedBodies() + " runtime bodies, and "
            + result.removedSessions()
            + " control sessions within radius " + radius + " in world " + worldName
            + ". Kept explicit physics spaces and PhysicsChunk collision cache."));
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
    private static SelectedBodies selectBodiesNear(@Nonnull Store<PhysicsStore> store,
        @Nonnull Vector3d center,
        float radius) {
        Set<UUID> bodyUuids = new ObjectOpenHashSet<>();
        double radiusSquared = (double) radius * radius;
        for (PhysicsBodySnapshot snapshot : PhysicsBodies.snapshotFrame(store).bodies()) {
            if (!PhysicsBodies.isRegistered(store, snapshot.bodyUuid())
                || PhysicsChunkCollision.isChunkCollisionBody(store, snapshot.bodyUuid())) {
                continue;
            }
            Vector3f position = snapshot.position();
            double dx = position.x - center.x;
            double dy = position.y - center.y;
            double dz = position.z - center.z;
            if (dx * dx + dy * dy + dz * dz <= radiusSquared) {
                bodyUuids.add(snapshot.bodyUuid());
            }
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

    @Nullable
    private static ComponentType<EntityStore, ImpulseControllableComponent> controllableTypeOrNull() {
        return ImpulseControllableComponent.isComponentTypeRegistered()
            ? ImpulseControllableComponent.getComponentType()
            : null;
    }

    private static boolean containsBody(@Nonnull Set<UUID> bodyUuids,
        @Nullable Ref<PhysicsStore> bodyRef) {
        UUID bodyUuid = rowUuid(bodyRef);
        return bodyUuid != null && bodyUuids.contains(bodyUuid);
    }

    private record SelectedBodies(@Nonnull Set<UUID> bodyUuids) {
    }

    private record RadiusCleanResult(@Nonnull Result projectionCleanup,
                                     int removedSessions,
                                     int removedBodies) {
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
