package dev.hytalemodding.impulse.examples.commands;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Holder;
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
import dev.hytalemodding.impulse.core.plugin.physics.PhysicsThreading;
import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.PhysicsCollisionFilters;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.plugin.modules.control.ImpulseControllableComponent;
import dev.hytalemodding.impulse.core.plugin.modules.physicsentity.components.BodyAttachmentComponent;
import dev.hytalemodding.impulse.core.plugin.modules.control.PhysicsControlSessions;
import dev.hytalemodding.impulse.core.plugin.modules.physicschunk.PhysicsChunkCollision;
import dev.hytalemodding.impulse.core.plugin.physics.PhysicsBodyEntities;
import dev.hytalemodding.impulse.core.plugin.physics.PhysicsJointEntities;
import dev.hytalemodding.impulse.core.plugin.physics.PhysicsAsync;
import dev.hytalemodding.impulse.core.plugin.physics.PhysicsBodies;
import dev.hytalemodding.impulse.core.plugin.physics.PhysicsSpaces;
import dev.hytalemodding.impulse.core.plugin.physics.PhysicsRaycasts;
import dev.hytalemodding.impulse.core.plugin.components.BodyCommandComponent;
import dev.hytalemodding.impulse.core.plugin.components.JointComponent;
import dev.hytalemodding.impulse.core.plugin.modules.physicsentity.PhysicsEntityAttachments;
import dev.hytalemodding.impulse.core.plugin.snapshots.PhysicsBodySnapshot;
import dev.hytalemodding.impulse.core.plugin.components.JointType;
import dev.hytalemodding.impulse.core.plugin.physics.PhysicsShapeSpec;
import dev.hytalemodding.impulse.core.plugin.physics.RigidBodySpawnSettings;
import dev.hytalemodding.impulse.core.plugin.physics.RaycastHitView;
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
    private static final String PHYSICS_ENTITY_UNAVAILABLE_MESSAGE =
        "Impulse PhysicsEntity integration is not available. "
            + "Enable HytaleModding:ImpulsePhysicsEntity to grab entity-backed physics bodies.";
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
        if (!PhysicsEntityAttachments.isAvailable()) {
            ctx.sender().sendMessage(Message.raw(PHYSICS_ENTITY_UNAVAILABLE_MESSAGE));
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
        Store<PhysicsStore> physicsStore = PhysicsThreading.store(world);

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
        if (!PhysicsEntityAttachments.isAvailable()) {
            ctx.sender().sendMessage(Message.raw(PHYSICS_ENTITY_UNAVAILABLE_MESSAGE));
            return;
        }
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
        PhysicsBodies.appendCommand(selectedBodyRef.getStore(),
            selectedBodyRef,
            BodyCommandComponent.wake());
        try {
            Ref<PhysicsStore> anchorBodyRef = ExamplePhysicsUtils.addPhysicsStoreBody(world,
                anchorBodyEntity(spaceRef, anchorBodyUuid, hitPoint));
            Ref<PhysicsStore> controlJointRef = ExamplePhysicsUtils.addJoint(world,
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
    private static Holder<PhysicsStore> anchorBodyEntity(@Nonnull Ref<PhysicsStore> spaceRef,
        @Nonnull UUID bodyUuid,
        @Nonnull Vector3f hitPoint) {
        return PhysicsBodyEntities.bodyHolder(spaceRef,
            bodyUuid,
            hitPoint,
            PhysicsShapeSpec.sphere(0.08f),
            PhysicsBodyType.KINEMATIC,
            1.0f,
            RigidBodySpawnSettings.material(0.5f, 0.0f)
                .withSensor(true)
                .withCollisionFilter(PhysicsCollisionFilters.TERRAIN, 0),
            null);
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
            SpaceId bodySpaceId = PhysicsBodies.spaceId(physicsStore, hit.bodyRef());
            if (bodySpaceId == null
                || PhysicsChunkCollision.isChunkCollisionBody(physicsStore, hit.bodyRef())) {
                continue;
            }
            candidates.add(new HitCandidate(hit.bodyRef(),
                bodySpaceId,
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
        Store<PhysicsStore> store = PhysicsThreading.store(world);
        return PhysicsBodies.snapshot(store, bodyRef);
    }

    @Nonnull
    private static AttachmentSelection inspectGameplayAttachments(@Nonnull Store<EntityStore> store,
        @Nonnull ComponentType<EntityStore, ImpulseControllableComponent> controllableType,
        @Nonnull Ref<PhysicsStore> bodyRef) {
        boolean hasGameplayAttachment = false;
        PhysicsEntityAttachments.requireAvailable();
        ComponentType<EntityStore, BodyAttachmentComponent> attachmentType =
            BodyAttachmentComponent.getComponentType();
        for (Ref<EntityStore> attachmentRef : PhysicsEntityAttachments.attachments(store, bodyRef)) {
            BodyAttachmentComponent attachment = store.getComponent(attachmentRef, attachmentType);
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
