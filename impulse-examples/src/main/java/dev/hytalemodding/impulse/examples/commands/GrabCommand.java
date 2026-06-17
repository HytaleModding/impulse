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
import dev.hytalemodding.impulse.core.plugin.modules.control.ImpulseControllableComponent;
import dev.hytalemodding.impulse.core.plugin.projection.BodyAttachmentComponent;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyKind;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyPersistenceMode;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyRegistrationView;
import dev.hytalemodding.impulse.core.plugin.modules.control.PhysicsControlSessions;
import dev.hytalemodding.impulse.core.plugin.physicsstore.PhysicsBodyEntities;
import dev.hytalemodding.impulse.core.plugin.physicsstore.PhysicsJointEntities;
import dev.hytalemodding.impulse.core.plugin.physicsstore.PhysicsAsync;
import dev.hytalemodding.impulse.core.plugin.physicsstore.PhysicsBodies;
import dev.hytalemodding.impulse.core.plugin.physicsstore.PhysicsSpaces;
import dev.hytalemodding.impulse.core.plugin.physicsstore.PhysicsRaycasts;
import dev.hytalemodding.impulse.core.plugin.components.BodyCommandComponent;
import dev.hytalemodding.impulse.core.plugin.components.JointComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.BodyEntityDescriptor;
import dev.hytalemodding.impulse.core.plugin.projection.PhysicsAttachments;
import dev.hytalemodding.impulse.core.plugin.snapshots.PhysicsBodySnapshot;
import dev.hytalemodding.impulse.core.plugin.simulation.JointType;
import dev.hytalemodding.impulse.core.plugin.simulation.PhysicsShapeSpec;
import dev.hytalemodding.impulse.core.plugin.simulation.RigidBodySpawnSettings;
import dev.hytalemodding.impulse.core.plugin.simulation.view.RaycastHitView;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import dev.hytalemodding.impulse.examples.utils.ExamplePhysicsUtils;
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
        Ref<PhysicsStore> targetSpaceRef = ExamplePhysicsUtils.resolveSpaceRef(world,
            targetSpaceId);
        if (targetSpaceRef == null) {
            ctx.sender().sendMessage(Message.raw("PhysicsStore space id=" + targetSpaceId.value()
                + " is not bound yet."));
            return CompletableFuture.completedFuture(null);
        }
        Store<PhysicsStore> physicsStore = ((PhysicsStoreWorld) world).getPhysicsStore().getStore();

        Transform look = TargetUtil.getLook(ref, store);
        Vector3d start = new Vector3d(look.getPosition());
        Vector3d direction = new Vector3d(look.getDirection()).mul(RAY_LENGTH);
        Vector3d end = new Vector3d(start).add(direction);

        return PhysicsAsync.acceptOnWorldThread(world,
            PhysicsRaycasts.allAsync(world,
                targetSpaceRef,
                ExamplePhysicsUtils.toVector3f(start),
                ExamplePhysicsUtils.toVector3f(end)),
            hits -> finishGrab(ctx,
                world,
                store,
                ref,
                physicsStore,
                targetSpaceId,
                controllableType,
                hits));
    }

    private static void finishGrab(@Nonnull CommandContext ctx,
        @Nonnull World world,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<PhysicsStore> physicsStore,
        @Nonnull SpaceId targetSpaceId,
        @Nonnull ComponentType<EntityStore, ImpulseControllableComponent> controllableType,
        @Nonnull List<RaycastHitView> hits) {
        HitSelection selection = selectControllableHit(physicsStore,
            store,
            controllableType,
            hits);
        if (selection == null) {
            ctx.sender().sendMessage(Message.raw("No controllable physics body in sight."));
            return;
        }

        PhysicsControlSessions.releaseSession(store, ref);

        SpaceId selectedSpaceId = selection.spaceId() != null ? selection.spaceId() : targetSpaceId;
        if (!PhysicsSpaces.hasSpace(physicsStore, selectedSpaceId)) {
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
        PhysicsBodySnapshot selectedState = bodyState(world, selection.bodyRef());
        if (selectedState == null) {
            return null;
        }
        Ref<PhysicsStore> spaceRef;
        try {
            spaceRef = ExamplePhysicsUtils.resolveSpaceRef(world, selectedSpaceId);
        } catch (IllegalStateException exception) {
            return null;
        }
        if (spaceRef == null) {
            return null;
        }

        Vector3f hitPoint = new Vector3f(selection.point());
        Vector3f bodyLocalHit = new Vector3f(hitPoint).sub(selectedState.position());
        Quaternionf inverseBodyRotation = selectedState.rotation();
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
                anchorBodyEntity(spaceRef, anchorBodyUuid, hitPoint));
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
    private static BodyEntityDescriptor anchorBodyEntity(@Nonnull Ref<PhysicsStore> spaceRef,
        @Nonnull UUID bodyUuid,
        @Nonnull Vector3f hitPoint) {
        return PhysicsBodyEntities.body(spaceRef,
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
        return PhysicsJointEntities.joint(spaceRef,
            anchorBodyRef,
            bodyRef,
            JointType.POINT,
            new Vector3f(),
            bodyLocalHit,
            new Vector3f());
    }

    @Nullable
    private static HitSelection selectControllableHit(@Nonnull Store<PhysicsStore> physicsStore,
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
                PhysicsBodies.registrationView(physicsStore, hit.bodyRef());
            if (registration == null || registration.kind() != PhysicsBodyKind.BODY) {
                continue;
            }
            candidates.add(new HitCandidate(hit.bodyRef(),
                registration.spaceId(),
                hit.point(),
                hit.fraction(),
                hit.distance()));
        }
        HitSelection best = null;
        for (HitCandidate candidate : candidates) {
            AttachmentSelection attachments =
                inspectGameplayAttachments(store, controllableType, candidate.bodyRef());
            if (attachments.controllableAttachment() == null && attachments.hasGameplayAttachment()) {
                continue;
            }

            if (best == null || candidate.fraction() < best.fraction()) {
                best = new HitSelection(candidate.bodyRef(),
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
    private static PhysicsBodySnapshot bodyState(@Nonnull World world,
        @Nonnull Ref<PhysicsStore> bodyRef) {
        Store<PhysicsStore> store = ((PhysicsStoreWorld) world).getPhysicsStore().getStore();
        return PhysicsBodies.snapshot(store, bodyRef);
    }

    @Nonnull
    private static AttachmentSelection inspectGameplayAttachments(@Nonnull Store<EntityStore> store,
        @Nonnull ComponentType<EntityStore, ImpulseControllableComponent> controllableType,
        @Nonnull Ref<PhysicsStore> bodyRef) {
        boolean hasGameplayAttachment = false;
        for (Ref<EntityStore> attachmentRef : PhysicsAttachments.attachments(store, bodyRef)) {
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
                                @Nullable Ref<EntityStore> attachment,
                                @Nullable SpaceId spaceId,
                                @Nonnull Vector3f point,
                                float fraction,
                                float distance) {
    }

    private record HitCandidate(@Nonnull Ref<PhysicsStore> bodyRef,
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
