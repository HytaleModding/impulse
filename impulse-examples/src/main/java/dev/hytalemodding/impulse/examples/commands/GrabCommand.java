package dev.hytalemodding.impulse.examples.commands;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncPlayerCommand;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.plugin.modules.control.ImpulseControllableComponent;
import dev.hytalemodding.impulse.core.plugin.components.PhysicsBodyAttachmentComponent;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyKind;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyRegistrationView;
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import dev.hytalemodding.impulse.core.plugin.modules.control.PhysicsControlSessions;
import dev.hytalemodding.impulse.core.plugin.joint.JointKey;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsWorldResource;
import dev.hytalemodding.impulse.core.plugin.simulation.RaycastAllQuery;
import dev.hytalemodding.impulse.core.plugin.simulation.RaycastHitView;
import dev.hytalemodding.impulse.core.plugin.simulation.RigidBodySpawnSettings;
import dev.hytalemodding.impulse.core.plugin.simulation.RigidBodyStateQuery;
import dev.hytalemodding.impulse.core.plugin.simulation.RigidBodyStateView;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;

public class GrabCommand extends AbstractAsyncPlayerCommand {

    private static final double RAY_LENGTH = 24.0;
    private static final float MIN_HOLD_DISTANCE = 4.0f;
    private static final Vector3f VIEW_OFFSET = new Vector3f(0.85f, -0.35f, 0.0f);
    private static final ComponentType<EntityStore, TransformComponent> TRANSFORM_TYPE =
        TransformComponent.getComponentType();
    private static final ComponentType<EntityStore, PhysicsBodyAttachmentComponent> ATTACHMENT_TYPE =
        PhysicsBodyAttachmentComponent.getComponentType();
    private static final ComponentType<EntityStore, ImpulseControllableComponent> IMPULSE_CONTROLLABLE_TYPE =
        ImpulseControllableComponent.getComponentType();
    private final OptionalArg<Integer> spaceArg = this.withOptionalArg(
        "space",
        "Physics space id to target",
        ArgTypes.INTEGER);

    public GrabCommand() {
        super("grab", "Grab a controllable physics body from your view");
    }

    @Nonnull
    @Override
    protected CompletableFuture<Void> executeAsync(@Nonnull CommandContext ctx,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull PlayerRef playerRef,
        @Nonnull World world) {
        TransformComponent transform = store.getComponent(ref, TRANSFORM_TYPE);
        if (transform == null) {
            ctx.sender().sendMessage(Message.raw("Cannot determine player position."));
            return CompletableFuture.completedFuture(null);
        }

        PhysicsWorldResource resource = ExamplePhysicsUtils.resource(store);
        SpaceId targetSpaceId = ExamplePhysicsUtils.spaceId(ctx, resource, spaceArg);
        if (targetSpaceId == null) {
            return CompletableFuture.completedFuture(null);
        }

        Vector3d start = ExamplePhysicsUtils.eyePosition(store, ref, transform);
        Vector3d direction = ExamplePhysicsUtils.lookDirection(store, ref, transform).mul(RAY_LENGTH);
        Vector3d end = new Vector3d(start).add(direction);

        HitSelection selection = findControllableHit(resource, store, targetSpaceId, start, end);
        if (selection == null) {
            ctx.sender().sendMessage(Message.raw("No controllable physics body in sight."));
            return CompletableFuture.completedFuture(null);
        }

        PhysicsControlSessions.releaseSession(store, ref);

        SpaceId selectedSpaceId = selection.spaceId() != null ? selection.spaceId() : targetSpaceId;
        if (!resource.hasSpace(selectedSpaceId)) {
            ctx.sender().sendMessage(Message.raw("Selected physics space no longer exists."));
            return CompletableFuture.completedFuture(null);
        }

        GrabPhysicsState physicsState = createGrabControl(resource,
            selectedSpaceId,
            selection,
            Math.max(0L, world.getTick()));
        if (physicsState == null) {
            ctx.sender().sendMessage(Message.raw("Selected physics body no longer exists."));
            return CompletableFuture.completedFuture(null);
        }

        PhysicsControlSessions.startSession(store,
            ref,
            selection.bodyKey(),
            physicsState.anchorBodyKey(),
            physicsState.controlJointKey(),
            selection.attachment(),
            selectedSpaceId,
            physicsState.originalBodyType(),
            Math.max(selection.distance(), MIN_HOLD_DISTANCE),
            VIEW_OFFSET,
            physicsState.hitPoint());

        ctx.sender().sendMessage(Message.raw("Grabbed physics body at distance "
            + selection.distance()));
        return CompletableFuture.completedFuture(null);
    }

    @Nullable
    private static GrabPhysicsState createGrabControl(@Nonnull PhysicsWorldResource resource,
        @Nonnull SpaceId selectedSpaceId,
        @Nonnull HitSelection selection,
        long serverTick) {
        RigidBodyStateView selectedState = resource.query(new RigidBodyStateQuery(selection.bodyKey()))
            .completion()
            .toCompletableFuture()
            .join()
            .orElse(null);
        if (selectedState == null) {
            return null;
        }

        Vector3f hitPoint = new Vector3f(selection.point());
        Vector3f bodyLocalHit = new Vector3f(hitPoint).sub(selectedState.pose().position());
        Quaternionf inverseBodyRotation = selectedState.pose().rotation();
        inverseBodyRotation.invert().transform(bodyLocalHit);

        RigidBodyKey anchorBodyKey = RigidBodyKey.random();
        JointKey controlJointKey = JointKey.random();
        boolean rejected = resource.submitCommands(serverTick, commands -> {
            commands.spawnBody(anchorBodyKey, spawn -> spawn
                .space(selectedSpaceId)
                .sphere(0.08f)
                .mass(1.0f)
                .kinematic()
                .position(hitPoint)
                .settings(RigidBodySpawnSettings.defaults().withSensor(true).withCollisionFilter(1, 0))
                .temporary()
                .runtimeOnly());
            commands.joint(controlJointKey, joint -> joint
                .space(selectedSpaceId)
                .bodies(anchorBodyKey, selection.bodyKey())
                .point(new Vector3f(), bodyLocalHit));
            commands.activateBody(selection.bodyKey());
        })
            .firstRejected()
            .toCompletableFuture()
            .join()
            .isPresent();
        if (rejected) {
            return null;
        }
        return new GrabPhysicsState(selectedState.bodyType(), anchorBodyKey, controlJointKey, hitPoint);
    }

    @Nullable
    private static HitSelection findControllableHit(@Nonnull PhysicsWorldResource resource,
        @Nonnull Store<EntityStore> store,
        @Nonnull SpaceId spaceId,
        @Nonnull Vector3d start,
        @Nonnull Vector3d end) {
        List<RaycastHitView> hits = resource.query(new RaycastAllQuery(spaceId,
                ExamplePhysicsUtils.toVector3f(start),
                ExamplePhysicsUtils.toVector3f(end)))
            .completion()
            .toCompletableFuture()
            .join();
        List<HitCandidate> candidates = new ArrayList<>(hits.size());
        for (RaycastHitView hit : hits) {
            if (hit.bodyType() != PhysicsBodyType.DYNAMIC || hit.bodyKey() == null) {
                continue;
            }
            PhysicsBodyRegistrationView registration =
                resource.getBodyRegistrationView(hit.bodyKey());
            if (registration == null || registration.kind() != PhysicsBodyKind.BODY) {
                continue;
            }
            candidates.add(new HitCandidate(registration.bodyKey(),
                registration.spaceId(),
                hit.point(),
                hit.fraction(),
                hit.distance()));
        }
        HitSelection best = null;
        for (HitCandidate candidate : candidates) {
            AttachmentSelection attachments =
                inspectGameplayAttachments(resource, store, candidate.bodyKey());
            if (attachments.controllableAttachment() == null && attachments.hasGameplayAttachment()) {
                continue;
            }

            if (best == null || candidate.fraction() < best.fraction()) {
                best = new HitSelection(candidate.bodyKey(),
                    attachments.controllableAttachment(),
                    candidate.spaceId(),
                    candidate.point(),
                    candidate.fraction(),
                    candidate.distance());
            }
        }
        return best;
    }

    @Nonnull
    private static AttachmentSelection inspectGameplayAttachments(@Nonnull PhysicsWorldResource resource,
        @Nonnull Store<EntityStore> store,
        @Nonnull RigidBodyKey bodyKey) {
        boolean hasGameplayAttachment = false;
        for (Ref<EntityStore> attachmentRef : resource.getBodyAttachments(bodyKey)) {
            PhysicsBodyAttachmentComponent attachment = store.getComponent(attachmentRef, ATTACHMENT_TYPE);
            if (attachment == null
                || attachment.getLifecycle() == PhysicsBodyAttachmentComponent.AttachmentLifecycle.GENERATED_PROXY) {
                continue;
            }
            hasGameplayAttachment = true;
            ImpulseControllableComponent controllable = store.getComponent(attachmentRef, IMPULSE_CONTROLLABLE_TYPE);
            if (controllable != null) {
                return new AttachmentSelection(attachmentRef, true);
            }
        }
        return new AttachmentSelection(null, hasGameplayAttachment);
    }

    private record HitSelection(@Nonnull RigidBodyKey bodyKey,
                                @Nullable Ref<EntityStore> attachment,
                                @Nullable SpaceId spaceId,
                                @Nonnull Vector3f point,
                                float fraction,
                                float distance) {
    }

    private record HitCandidate(@Nonnull RigidBodyKey bodyKey,
                                @Nullable SpaceId spaceId,
                                @Nonnull Vector3f point,
                                float fraction,
                                float distance) {
    }

    private record AttachmentSelection(@Nullable Ref<EntityStore> controllableAttachment,
                                       boolean hasGameplayAttachment) {
    }

    private record GrabPhysicsState(@Nonnull PhysicsBodyType originalBodyType,
                                    @Nonnull RigidBodyKey anchorBodyKey,
                                    @Nonnull JointKey controlJointKey,
                                    @Nonnull Vector3f hitPoint) {
    }
}
