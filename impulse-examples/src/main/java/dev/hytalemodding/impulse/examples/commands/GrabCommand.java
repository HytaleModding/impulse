package dev.hytalemodding.impulse.examples.commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncPlayerCommand;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.api.PhysicsBody;
import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.PhysicsJoint;
import dev.hytalemodding.impulse.api.PhysicsRayHit;
import dev.hytalemodding.impulse.api.PhysicsSpace;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.plugin.components.ImpulseControllableComponent;
import dev.hytalemodding.impulse.core.plugin.components.PhysicsBodyAttachmentComponent;
import dev.hytalemodding.impulse.core.internal.components.PhysicsControlSessionComponent;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsBodyId;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsBodyKind;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsBodyPersistenceMode;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsWorldResource;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsWorldResource.BodyRegistration;
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
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) {
            ctx.sender().sendMessage(Message.raw("Cannot determine player position."));
            return CompletableFuture.completedFuture(null);
        }

        PhysicsWorldResource resource = ExamplePhysicsUtils.resource(store);
        PhysicsSpace space = ExamplePhysicsUtils.defaultSpace(ctx, resource);
        if (space == null) {
            return CompletableFuture.completedFuture(null);
        }

        Vector3d start = ExamplePhysicsUtils.eyePosition(store, ref, transform);
        Vector3d direction = ExamplePhysicsUtils.lookDirection(store, ref, transform).mul(RAY_LENGTH);
        Vector3d end = new Vector3d(start).add(direction);

        HitSelection selection = findControllableHit(resource, store, space, start, end);
        if (selection == null) {
            ctx.sender().sendMessage(Message.raw("No controllable physics body in sight."));
            return CompletableFuture.completedFuture(null);
        }

        releaseExisting(store, ref);

        SpaceId selectedSpaceId = selection.spaceId() != null ? selection.spaceId() : space.getId();
        PhysicsSpace selectedSpace = resource.getSpace(selectedSpaceId);
        if (selectedSpace == null) {
            ctx.sender().sendMessage(Message.raw("Selected physics space no longer exists."));
            return CompletableFuture.completedFuture(null);
        }

        GrabPhysicsState physicsState = ExamplePhysicsUtils.physicsWorkerCall(store,
            "create grabbed physics anchor",
            () -> {
                PhysicsBody body = selection.hit.body();
                PhysicsBodyType originalBodyType = body.getBodyType();
                Vector3f bodyPosition = body.getPosition();
                Vector3f hitPoint = new Vector3f(selection.hit.point());
                Vector3f bodyLocalHit = new Vector3f(hitPoint).sub(bodyPosition);
                new Quaternionf(body.getRotation()).invert().transform(bodyLocalHit);

                PhysicsBody anchorBody = selectedSpace.createSphere(0.08f, 1.0f);
                anchorBody.setBodyType(PhysicsBodyType.KINEMATIC);
                anchorBody.setSensor(true);
                anchorBody.setCollisionFilter(1, 0);
                anchorBody.setPosition(hitPoint);
                PhysicsBodyId anchorBodyId = resource.addBody(selectedSpaceId,
                    anchorBody,
                    PhysicsBodyKind.TEMPORARY,
                    PhysicsBodyPersistenceMode.RUNTIME_ONLY);

                PhysicsJoint joint = selectedSpace.createPointJoint(anchorBody, body,
                    new Vector3f(), bodyLocalHit);
                body.activate();
                return new GrabPhysicsState(originalBodyType, anchorBodyId, joint, hitPoint);
            });

        store.putComponent(ref,
            PhysicsControlSessionComponent.getComponentType(),
            new PhysicsControlSessionComponent(
                selection.bodyId(),
                physicsState.anchorBodyId(),
                physicsState.joint(),
                selection.attachment(),
                selectedSpaceId,
                physicsState.originalBodyType(),
                Math.max((float) selection.hit.distance(), MIN_HOLD_DISTANCE),
                VIEW_OFFSET,
                physicsState.hitPoint()
            ));
        resource.markBodyControlled(selection.bodyId());

        ctx.sender().sendMessage(Message.raw("Grabbed physics body at distance "
            + selection.hit.distance()));
        return CompletableFuture.completedFuture(null);
    }

    @Nullable
    private static HitSelection findControllableHit(@Nonnull PhysicsWorldResource resource,
        @Nonnull Store<EntityStore> store,
        @Nonnull PhysicsSpace space,
        @Nonnull Vector3d start,
        @Nonnull Vector3d end) {
        List<HitCandidate> candidates = ExamplePhysicsUtils.physicsWorkerCall(store,
            "raycast controllable physics bodies",
            () -> {
                List<PhysicsRayHit> hits = space.raycastAll(ExamplePhysicsUtils.toVector3f(start),
                    ExamplePhysicsUtils.toVector3f(end));
                List<HitCandidate> found = new ArrayList<>(hits.size());
                for (PhysicsRayHit hit : hits) {
                    if (hit.body().getBodyType() != PhysicsBodyType.DYNAMIC) {
                        continue;
                    }

                    BodyRegistration registration = resource.getBodyRegistration(hit.body());
                    if (registration == null || registration.kind() != PhysicsBodyKind.BODY) {
                        continue;
                    }
                    found.add(new HitCandidate(hit, registration.id(), registration.spaceId()));
                }
                return found;
            });
        HitSelection best = null;
        for (HitCandidate candidate : candidates) {
            Ref<EntityStore> attachment = findControllableAttachment(resource, store, candidate.bodyId());
            if (attachment == null && hasGameplayAttachment(resource, store, candidate.bodyId())) {
                continue;
            }

            if (best == null || candidate.hit().fraction() < best.hit.fraction()) {
                best = new HitSelection(candidate.hit(), candidate.bodyId(), attachment,
                    candidate.spaceId());
            }
        }
        return best;
    }

    @Nullable
    private static Ref<EntityStore> findControllableAttachment(@Nonnull PhysicsWorldResource resource,
        @Nonnull Store<EntityStore> store,
        @Nonnull PhysicsBodyId bodyId) {
        for (Ref<EntityStore> attachmentRef : resource.getBodyAttachments(bodyId)) {
            PhysicsBodyAttachmentComponent attachment = store.getComponent(attachmentRef,
                PhysicsBodyAttachmentComponent.getComponentType());
            if (attachment == null
                || attachment.getLifecycle() == PhysicsBodyAttachmentComponent.AttachmentLifecycle.GENERATED_PROXY) {
                continue;
            }
            ImpulseControllableComponent controllable = store.getComponent(attachmentRef,
                ImpulseControllableComponent.getComponentType());
            if (controllable != null) {
                return attachmentRef;
            }
        }
        return null;
    }

    private static boolean hasGameplayAttachment(@Nonnull PhysicsWorldResource resource,
        @Nonnull Store<EntityStore> store,
        @Nonnull PhysicsBodyId bodyId) {
        for (Ref<EntityStore> attachmentRef : resource.getBodyAttachments(bodyId)) {
            PhysicsBodyAttachmentComponent attachment = store.getComponent(attachmentRef,
                PhysicsBodyAttachmentComponent.getComponentType());
            if (attachment != null
                && attachment.getLifecycle() != PhysicsBodyAttachmentComponent.AttachmentLifecycle.GENERATED_PROXY) {
                return true;
            }
        }
        return false;
    }

    private static void releaseExisting(@Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> playerRef) {
        PhysicsControlSessionComponent existing = store.getComponent(playerRef,
            PhysicsControlSessionComponent.getComponentType());
        if (existing == null) {
            return;
        }
        ReleaseCommand.release(store, playerRef, existing);
    }

    private record HitSelection(@Nonnull PhysicsRayHit hit,
                                @Nonnull PhysicsBodyId bodyId,
                                @Nullable Ref<EntityStore> attachment,
                                @Nullable SpaceId spaceId) {
    }

    private record HitCandidate(@Nonnull PhysicsRayHit hit,
                                @Nonnull PhysicsBodyId bodyId,
                                @Nullable SpaceId spaceId) {
    }

    private record GrabPhysicsState(@Nonnull PhysicsBodyType originalBodyType,
                                    @Nonnull PhysicsBodyId anchorBodyId,
                                    @Nonnull PhysicsJoint joint,
                                    @Nonnull Vector3f hitPoint) {
    }
}
