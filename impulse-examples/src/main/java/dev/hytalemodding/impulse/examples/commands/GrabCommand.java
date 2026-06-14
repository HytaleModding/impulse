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
import dev.hytalemodding.impulse.early.PhysicsStoreWorld;
import dev.hytalemodding.impulse.api.PhysicsAxis;
import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.PhysicsCollisionFilters;
import dev.hytalemodding.impulse.api.ShapeType;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsSnapshotResource;
import dev.hytalemodding.impulse.core.plugin.modules.control.ImpulseControllableComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.projection.BodyAttachmentComponent;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyKind;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyPersistenceMode;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyRegistrationView;
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import dev.hytalemodding.impulse.core.plugin.modules.control.PhysicsControlSessions;
import dev.hytalemodding.impulse.core.plugin.joint.JointKey;
import dev.hytalemodding.impulse.core.plugin.physicsstore.PhysicsStoreAsync;
import dev.hytalemodding.impulse.core.plugin.physicsstore.PhysicsStoreRaycasts;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.BodyCommandComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.BodyComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.ColliderComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.CollisionFilterComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.DynamicsComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.JointComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.MaterialComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.ShapeComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.TargetComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.BodyRowDescriptor;
import dev.hytalemodding.impulse.core.plugin.physicsstore.snapshots.PhysicsStoreBodySnapshot;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsWorldResource;
import dev.hytalemodding.impulse.core.plugin.simulation.JointType;
import dev.hytalemodding.impulse.core.plugin.simulation.RigidBodyPose;
import dev.hytalemodding.impulse.core.plugin.simulation.view.RaycastHitView;
import dev.hytalemodding.impulse.core.plugin.simulation.view.RigidBodyStateView;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
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
    private static final ComponentType<EntityStore, BodyAttachmentComponent> ATTACHMENT_TYPE =
        BodyAttachmentComponent.getComponentType();
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

        if (!PhysicsControlSessions.isAvailable()) {
            ctx.sender().sendMessage(Message.raw(
                "Impulse control is disabled. Enable HytaleModding:ImpulseControl to use grab."));
            return CompletableFuture.completedFuture(null);
        }
        ComponentType<EntityStore, ImpulseControllableComponent> controllableType =
            ImpulseControllableComponent.getComponentType();

        PhysicsWorldResource resource = ExamplePhysicsUtils.resource(store);
        SpaceId targetSpaceId = ExamplePhysicsUtils.spaceId(ctx, resource, spaceArg);
        if (targetSpaceId == null) {
            return CompletableFuture.completedFuture(null);
        }

        Vector3d start = ExamplePhysicsUtils.eyePosition(store, ref, transform);
        Vector3d direction = ExamplePhysicsUtils.lookDirection(store, ref, transform).mul(RAY_LENGTH);
        Vector3d end = new Vector3d(start).add(direction);

        return PhysicsStoreAsync.acceptOnWorldThread(world,
            PhysicsStoreRaycasts.allAsync(world,
                targetSpaceId,
                ExamplePhysicsUtils.toVector3f(start),
                ExamplePhysicsUtils.toVector3f(end)),
            hits -> finishGrab(ctx,
                world,
                store,
                ref,
                resource,
                targetSpaceId,
                controllableType,
                hits));
    }

    private static void finishGrab(@Nonnull CommandContext ctx,
        @Nonnull World world,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull PhysicsWorldResource resource,
        @Nonnull SpaceId targetSpaceId,
        @Nonnull ComponentType<EntityStore, ImpulseControllableComponent> controllableType,
        @Nonnull List<RaycastHitView> hits) {
        HitSelection selection = selectControllableHit(resource,
            store,
            controllableType,
            hits);
        if (selection == null) {
            ctx.sender().sendMessage(Message.raw("No controllable physics body in sight."));
            return;
        }

        PhysicsControlSessions.releaseSession(store, ref);

        SpaceId selectedSpaceId = selection.spaceId() != null ? selection.spaceId() : targetSpaceId;
        if (!resource.hasSpace(selectedSpaceId)) {
            ctx.sender().sendMessage(Message.raw("Selected physics space no longer exists."));
            return;
        }

        GrabPhysicsState physicsState = createGrabControl(world,
            selectedSpaceId,
            selection);
        if (physicsState == null) {
            ctx.sender().sendMessage(Message.raw("Selected physics body no longer exists."));
            return;
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
    }

    @Nullable
    private static GrabPhysicsState createGrabControl(@Nonnull World world,
        @Nonnull SpaceId selectedSpaceId,
        @Nonnull HitSelection selection) {
        RigidBodyStateView selectedState = bodyState(world, selection.bodyKey());
        if (selectedState == null) {
            return null;
        }
        UUID spaceUuid;
        try {
            spaceUuid = ExamplePhysicsUtils.resolvePhysicsStoreSpaceUuid(world, selectedSpaceId);
        } catch (IllegalStateException exception) {
            return null;
        }
        if (spaceUuid == null) {
            return null;
        }

        Vector3f hitPoint = new Vector3f(selection.point());
        Vector3f bodyLocalHit = new Vector3f(hitPoint).sub(selectedState.pose().position());
        Quaternionf inverseBodyRotation = selectedState.pose().rotation();
        inverseBodyRotation.invert().transform(bodyLocalHit);

        RigidBodyKey anchorBodyKey = RigidBodyKey.random();
        JointKey controlJointKey = JointKey.random();
        boolean selectedBound = ExamplePhysicsUtils.appendPhysicsStoreBodyCommand(world,
            selection.bodyKey().value(),
            BodyCommandComponent.wake());
        if (!selectedBound) {
            return null;
        }
        try {
            ExamplePhysicsUtils.addPhysicsStoreBody(world,
                anchorBodyRow(spaceUuid, anchorBodyKey.value(), hitPoint));
            ExamplePhysicsUtils.addPhysicsStoreJoint(world,
                controlJointKey.value(),
                controlJoint(spaceUuid, anchorBodyKey, selection.bodyKey(), bodyLocalHit));
        } catch (IllegalStateException exception) {
            return null;
        }
        return new GrabPhysicsState(selectedState.bodyType(), anchorBodyKey, controlJointKey, hitPoint);
    }

    @Nonnull
    private static BodyRowDescriptor anchorBodyRow(@Nonnull UUID spaceUuid,
        @Nonnull UUID bodyUuid,
        @Nonnull Vector3f hitPoint) {
        return BodyRowDescriptor.of(bodyUuid,
            new BodyComponent(spaceUuid,
                PhysicsBodyKind.TEMPORARY,
                PhysicsBodyPersistenceMode.RUNTIME_ONLY),
            new DynamicsComponent(PhysicsBodyType.KINEMATIC,
                1.0f,
                0.0f,
                0.0f,
                false),
            initialAnchorTarget(hitPoint),
            bodyUuid,
            new ColliderComponent(new Vector3f(),
                new Quaternionf(),
                true),
            bodyUuid,
            new ShapeComponent(ShapeType.SPHERE,
                0.0f,
                0.0f,
                0.0f,
                0.08f,
                0.0f,
                PhysicsAxis.Y,
                0.0f,
                ""),
            bodyUuid,
            new MaterialComponent(0.5f, 0.0f),
            bodyUuid,
            new CollisionFilterComponent(PhysicsCollisionFilters.TERRAIN, 0));
    }

    @Nonnull
    private static TargetComponent initialAnchorTarget(@Nonnull Vector3f hitPoint) {
        TargetComponent target = new TargetComponent();
        target.setActive(false);
        target.setPosition(hitPoint);
        target.setRotation(new Quaternionf());
        target.setLinearVelocity(new Vector3f());
        target.setAngularVelocity(new Vector3f());
        target.setTransformEnabled(true);
        target.setVelocityEnabled(false);
        target.setActivate(true);
        return target;
    }

    @Nonnull
    private static JointComponent controlJoint(@Nonnull UUID spaceUuid,
        @Nonnull RigidBodyKey anchorBodyKey,
        @Nonnull RigidBodyKey bodyKey,
        @Nonnull Vector3f bodyLocalHit) {
        JointComponent joint = new JointComponent();
        joint.setSpaceUuid(spaceUuid);
        joint.setBodyAUuid(anchorBodyKey.value());
        joint.setBodyBUuid(bodyKey.value());
        joint.setType(JointType.POINT);
        joint.setAnchorA(new Vector3f());
        joint.setAnchorB(bodyLocalHit);
        joint.setAxis(new Vector3f());
        joint.setEnabled(true);
        return joint;
    }

    @Nullable
    private static HitSelection selectControllableHit(@Nonnull PhysicsWorldResource resource,
        @Nonnull Store<EntityStore> store,
        @Nonnull ComponentType<EntityStore, ImpulseControllableComponent> controllableType,
        @Nonnull List<RaycastHitView> hits) {
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
                inspectGameplayAttachments(resource, store, controllableType, candidate.bodyKey());
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

    @Nullable
    private static RigidBodyStateView bodyState(@Nonnull World world,
        @Nonnull RigidBodyKey bodyKey) {
        PhysicsStoreBodySnapshot body = ((PhysicsStoreWorld) world).getPhysicsStore()
            .getStore()
            .getResource(PhysicsSnapshotResource.getResourceType())
            .getBody(bodyKey.value());
        return body != null
            ? new RigidBodyStateView(bodyKey,
                body.bodyType(),
                RigidBodyPose.of(body.position(), body.rotation()))
            : null;
    }

    @Nonnull
    private static AttachmentSelection inspectGameplayAttachments(@Nonnull PhysicsWorldResource resource,
        @Nonnull Store<EntityStore> store,
        @Nonnull ComponentType<EntityStore, ImpulseControllableComponent> controllableType,
        @Nonnull RigidBodyKey bodyKey) {
        boolean hasGameplayAttachment = false;
        for (Ref<EntityStore> attachmentRef : resource.getBodyAttachments(bodyKey)) {
            BodyAttachmentComponent attachment = store.getComponent(attachmentRef, ATTACHMENT_TYPE);
            if (attachment == null
                || attachment.getLifecycle() == BodyAttachmentComponent.AttachmentLifecycle.GENERATED_PROXY) {
                continue;
            }
            hasGameplayAttachment = true;
            ImpulseControllableComponent controllable = store.getComponent(attachmentRef,
                controllableType);
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
