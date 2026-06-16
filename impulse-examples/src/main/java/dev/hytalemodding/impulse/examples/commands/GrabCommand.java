package dev.hytalemodding.impulse.examples.commands;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import com.hypixel.hytale.server.core.util.TargetUtil;
import dev.hytalemodding.impulse.early.PhysicsStoreWorld;
import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.PhysicsCollisionFilters;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsBodyRegistrationResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsSnapshotResource;
import dev.hytalemodding.impulse.core.plugin.modules.control.ImpulseControllableComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.projection.BodyAttachmentComponent;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyKind;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyPersistenceMode;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyRegistrationView;
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import dev.hytalemodding.impulse.core.plugin.modules.control.PhysicsControlSessions;
import dev.hytalemodding.impulse.core.plugin.physicsstore.PhysicsBodyRows;
import dev.hytalemodding.impulse.core.plugin.physicsstore.PhysicsJointRows;
import dev.hytalemodding.impulse.core.plugin.physicsstore.PhysicsStoreAsync;
import dev.hytalemodding.impulse.core.plugin.physicsstore.PhysicsStoreRaycasts;
import dev.hytalemodding.impulse.core.plugin.physicsstore.PhysicsStoreThreading;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.BodyCommandComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.JointComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.BodyRowDescriptor;
import dev.hytalemodding.impulse.core.plugin.physicsstore.snapshots.PhysicsStoreBodySnapshot;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsWorldResource;
import dev.hytalemodding.impulse.core.plugin.simulation.JointType;
import dev.hytalemodding.impulse.core.plugin.simulation.PhysicsShapeSpec;
import dev.hytalemodding.impulse.core.plugin.simulation.RigidBodySpawnSettings;
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
        if (!PhysicsControlSessions.isAvailable()) {
            ctx.sender().sendMessage(Message.raw(
                "Impulse control is disabled. Enable HytaleModding:ImpulseControl to use grab."));
            return CompletableFuture.completedFuture(null);
        }
        ComponentType<EntityStore, ImpulseControllableComponent> controllableType =
            ImpulseControllableComponent.getComponentType();

        SpaceId targetSpaceId = ExamplePhysicsUtils.spaceId(ctx, world, spaceArg);
        if (targetSpaceId == null) {
            return CompletableFuture.completedFuture(null);
        }
        PhysicsWorldResource resource = store.getResource(PhysicsWorldResource.getResourceType());

        Transform look = TargetUtil.getLook(ref, store);
        Vector3d start = new Vector3d(look.getPosition());
        Vector3d direction = new Vector3d(look.getDirection()).mul(RAY_LENGTH);
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
            selection.bodyRef(),
            physicsState.anchorBodyRef(),
            physicsState.controlJointRef(),
            selection.attachment(),
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
        RigidBodyStateView selectedState = bodyState(world, selection.bodyRef(), selection.bodyKey());
        if (selectedState == null) {
            return null;
        }
        Ref<PhysicsStore> spaceRef;
        try {
            spaceRef = ExamplePhysicsUtils.resolvePhysicsStoreSpaceRef(world, selectedSpaceId);
        } catch (IllegalStateException exception) {
            return null;
        }
        if (spaceRef == null) {
            return null;
        }

        Vector3f hitPoint = new Vector3f(selection.point());
        Vector3f bodyLocalHit = new Vector3f(hitPoint).sub(selectedState.pose().position());
        Quaternionf inverseBodyRotation = selectedState.pose().rotation();
        inverseBodyRotation.invert().transform(bodyLocalHit);

        UUID anchorBodyUuid = UUID.randomUUID();
        UUID controlJointUuid = UUID.randomUUID();
        Ref<PhysicsStore> selectedBodyRef = selection.bodyRef();
        if (!selectedBodyRef.isValid()) {
            return null;
        }
        ExamplePhysicsUtils.appendPhysicsStoreBodyCommand(selectedBodyRef.getStore(),
            selectedBodyRef,
            BodyCommandComponent.wake());
        try {
            Ref<PhysicsStore> anchorBodyRef = ExamplePhysicsUtils.addPhysicsStoreBody(world,
                anchorBodyRow(spaceRef, anchorBodyUuid, hitPoint));
            Ref<PhysicsStore> controlJointRef = ExamplePhysicsUtils.addPhysicsStoreJoint(world,
                controlJointUuid,
                controlJoint(spaceRef, anchorBodyRef, selectedBodyRef, bodyLocalHit));
            return new GrabPhysicsState(selectedState.bodyType(),
                anchorBodyRef,
                controlJointRef,
                hitPoint);
        } catch (IllegalStateException exception) {
            return null;
        }
    }

    @Nonnull
    private static BodyRowDescriptor anchorBodyRow(@Nonnull Ref<PhysicsStore> spaceRef,
        @Nonnull UUID bodyUuid,
        @Nonnull Vector3f hitPoint) {
        return PhysicsBodyRows.body(spaceRef,
            bodyUuid,
            hitPoint,
            PhysicsShapeSpec.sphere(0.08f),
            PhysicsBodyType.KINEMATIC,
            1.0f,
            RigidBodySpawnSettings.material(0.5f, 0.0f)
                .withSensor(true)
                .withCollisionFilter(PhysicsCollisionFilters.TERRAIN, 0),
            null,
            PhysicsBodyKind.TEMPORARY,
            PhysicsBodyPersistenceMode.RUNTIME_ONLY);
    }

    @Nonnull
    private static JointComponent controlJoint(@Nonnull Ref<PhysicsStore> spaceRef,
        @Nonnull Ref<PhysicsStore> anchorBodyRef,
        @Nonnull Ref<PhysicsStore> bodyRef,
        @Nonnull Vector3f bodyLocalHit) {
        return PhysicsJointRows.joint(spaceRef,
            anchorBodyRef,
            bodyRef,
            JointType.POINT,
            new Vector3f(),
            bodyLocalHit,
            new Vector3f());
    }

    @Nullable
    private static HitSelection selectControllableHit(@Nonnull PhysicsWorldResource resource,
        @Nonnull Store<EntityStore> store,
        @Nonnull ComponentType<EntityStore, ImpulseControllableComponent> controllableType,
        @Nonnull List<RaycastHitView> hits) {
        List<HitCandidate> candidates = new ArrayList<>(hits.size());
        for (RaycastHitView hit : hits) {
            if (hit.bodyType() != PhysicsBodyType.DYNAMIC
                || hit.bodyRef() == null
                || !hit.bodyRef().isValid()) {
                continue;
            }
            PhysicsBodyRegistrationView registration =
                hit.bodyRef()
                    .getStore()
                    .getResource(PhysicsBodyRegistrationResource.getResourceType())
                    .getBodyRegistrationView(hit.bodyRef());
            if (registration == null || registration.kind() != PhysicsBodyKind.BODY) {
                continue;
            }
            candidates.add(new HitCandidate(hit.bodyRef(),
                registration.bodyKey(),
                registration.spaceId(),
                hit.point(),
                hit.fraction(),
                hit.distance()));
        }
        HitSelection best = null;
        for (HitCandidate candidate : candidates) {
            AttachmentSelection attachments =
                inspectGameplayAttachments(resource, store, controllableType, candidate.bodyRef());
            if (attachments.controllableAttachment() == null && attachments.hasGameplayAttachment()) {
                continue;
            }

            if (best == null || candidate.fraction() < best.fraction()) {
                best = new HitSelection(candidate.bodyRef(),
                    candidate.bodyKey(),
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
        @Nonnull Ref<PhysicsStore> bodyRef,
        @Nonnull RigidBodyKey bodyKey) {
        Store<PhysicsStore> store = ((PhysicsStoreWorld) world).getPhysicsStore().getStore();
        PhysicsStoreThreading.requireWorldThread(store,
            "read copied PhysicsStore grab body snapshot");
        PhysicsStoreBodySnapshot body = store
            .getResource(PhysicsSnapshotResource.getResourceType())
            .getBody(bodyRef);
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
        @Nonnull Ref<PhysicsStore> bodyRef) {
        boolean hasGameplayAttachment = false;
        for (Ref<EntityStore> attachmentRef : resource.getBodyAttachments(bodyRef)) {
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

    private record HitSelection(@Nonnull Ref<PhysicsStore> bodyRef,
                                @Nonnull RigidBodyKey bodyKey,
                                @Nullable Ref<EntityStore> attachment,
                                @Nullable SpaceId spaceId,
                                @Nonnull Vector3f point,
                                float fraction,
                                float distance) {
    }

    private record HitCandidate(@Nonnull Ref<PhysicsStore> bodyRef,
                                @Nonnull RigidBodyKey bodyKey,
                                @Nullable SpaceId spaceId,
                                @Nonnull Vector3f point,
                                float fraction,
                                float distance) {
    }

    private record AttachmentSelection(@Nullable Ref<EntityStore> controllableAttachment,
                                       boolean hasGameplayAttachment) {
    }

    private record GrabPhysicsState(@Nonnull PhysicsBodyType originalBodyType,
                                    @Nonnull Ref<PhysicsStore> anchorBodyRef,
                                    @Nonnull Ref<PhysicsStore> controlJointRef,
                                    @Nonnull Vector3f hitPoint) {
    }
}
