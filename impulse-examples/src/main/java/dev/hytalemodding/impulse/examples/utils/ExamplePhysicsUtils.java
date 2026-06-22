package dev.hytalemodding.impulse.examples.utils;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.modules.time.TimeResource;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.plugin.components.BodyCommandComponent;
import dev.hytalemodding.impulse.core.plugin.components.JointComponent;
import dev.hytalemodding.impulse.core.plugin.modules.control.ImpulseControllableComponent;
import dev.hytalemodding.impulse.core.plugin.modules.control.PhysicsControlSessions;
import dev.hytalemodding.impulse.core.plugin.modules.physicsentity.PhysicsEntityAttachments;
import dev.hytalemodding.impulse.core.plugin.modules.physicsentity.components.BodyAttachmentComponent;
import dev.hytalemodding.impulse.core.plugin.physics.PhysicsBodies;
import dev.hytalemodding.impulse.core.plugin.physics.PhysicsBodyEntities;
import dev.hytalemodding.impulse.core.plugin.physics.PhysicsEntities;
import dev.hytalemodding.impulse.core.plugin.physics.PhysicsSpaces;
import dev.hytalemodding.impulse.core.plugin.physics.PhysicsThreading;
import dev.hytalemodding.impulse.core.plugin.modules.physicsentity.settings.PhysicsVisualMaterializationSettings;
import dev.hytalemodding.impulse.core.plugin.physics.PhysicsShapeSpec;
import dev.hytalemodding.impulse.core.plugin.physics.RigidBodySpawnSettings;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;

public final class ExamplePhysicsUtils {

    public static final String DEFAULT_BLOCK_TYPE =
        PhysicsVisualMaterializationSettings.DEFAULT_DETACHED_VISUAL_BLOCK_TYPE;

    private ExamplePhysicsUtils() {
    }

    @Nullable
    public static Ref<PhysicsStore> resolveSpaceRef(@Nonnull World world,
        @Nonnull SpaceId spaceId) {
        Store<PhysicsStore> store = PhysicsThreading.store(world);
        return PhysicsSpaces.resolveRef(store, spaceId);
    }

    @Nullable
    public static SpaceSelection spaceSelection(@Nonnull CommandContext ctx,
        @Nonnull World world,
        @Nonnull OptionalArg<Integer> spaceArg) {
        Store<PhysicsStore> store = PhysicsThreading.store(world);
        if (spaceArg.provided(ctx)) {
            int rawSpaceId = spaceArg.get(ctx);
            if (rawSpaceId <= 0) {
                ctx.sender().sendMessage(Message.raw("Space id must be a positive integer."));
                return null;
            }
            SpaceId spaceId = new SpaceId(rawSpaceId);
            Ref<PhysicsStore> spaceRef = PhysicsSpaces.resolveRef(store, spaceId);
            if (spaceRef != null) {
                return new SpaceSelection(spaceId, spaceRef);
            }
            if (PhysicsSpaces.hasSpace(store, spaceId)) {
                ctx.sender().sendMessage(Message.raw("PhysicsStore space id=" + rawSpaceId
                    + " is not bound yet."));
            } else {
                ctx.sender().sendMessage(Message.raw("No physics space id=" + rawSpaceId + " exists."));
            }
            return null;
        }

        SpaceId firstSpaceId = PhysicsSpaces.spaceIds(store)
            .stream()
            .min(Comparator.comparingInt(SpaceId::value))
            .orElse(null);
        if (firstSpaceId == null) {
            ctx.sender().sendMessage(Message.raw("No physics space exists. Run "
                + "`/impulse space create --backend=<id>` before running Impulse example commands."));
            return null;
        }
        Ref<PhysicsStore> spaceRef = PhysicsSpaces.resolveRef(store, firstSpaceId);
        if (spaceRef == null) {
            ctx.sender().sendMessage(Message.raw("PhysicsStore space id=" + firstSpaceId.value()
                + " is not bound yet."));
            return null;
        }
        return new SpaceSelection(firstSpaceId, spaceRef);
    }

    @Nonnull
    public static Ref<PhysicsStore> addPhysicsStoreBody(@Nonnull World world,
        @Nonnull Holder<PhysicsStore> holder) {
        Store<PhysicsStore> store = PhysicsThreading.store(world);
        return addPhysicsStoreBody(store, holder);
    }

    @Nonnull
    public static Ref<PhysicsStore> addPhysicsStoreBody(@Nonnull World world,
        @Nonnull Holder<PhysicsStore> holder,
        @Nonnull BodyCommandComponent command) {
        Store<PhysicsStore> store = PhysicsThreading.store(world);
        Ref<PhysicsStore> bodyRef = addPhysicsStoreBody(store, holder);
        PhysicsBodies.appendCommand(store, bodyRef, command);
        return bodyRef;
    }

    @Nonnull
    private static Ref<PhysicsStore> addPhysicsStoreBody(@Nonnull Store<PhysicsStore> store,
        @Nonnull Holder<PhysicsStore> holder) {
        PhysicsThreading.requireWorldThread(store, "add a PhysicsStore body entity");
        return store.addEntity(Objects.requireNonNull(holder, "holder"), AddReason.SPAWN);
    }

    @Nonnull
    public static Ref<PhysicsStore> addJoint(@Nonnull World world,
        @Nonnull UUID jointUuid,
        @Nonnull JointComponent joint) {
        Store<PhysicsStore> store = PhysicsThreading.store(world);
        PhysicsThreading.requireWorldThread(store, "add a PhysicsStore joint entity");
        return store.addEntity(PhysicsEntities.jointHolder(store,
            Objects.requireNonNull(jointUuid, "jointUuid"),
            joint), AddReason.SPAWN);
    }

    @Nullable
    public static SpaceId spaceId(@Nonnull CommandContext ctx,
        @Nonnull World world,
        @Nonnull OptionalArg<Integer> spaceArg) {
        Store<PhysicsStore> store = PhysicsThreading.store(world);
        if (spaceArg.provided(ctx)) {
            int rawSpaceId = spaceArg.get(ctx);
            if (rawSpaceId <= 0) {
                ctx.sender().sendMessage(Message.raw("Space id must be a positive integer."));
                return null;
            }
            SpaceId spaceId = new SpaceId(rawSpaceId);
            if (!PhysicsSpaces.hasSpace(store, spaceId)) {
                ctx.sender().sendMessage(Message.raw("No physics space id=" + rawSpaceId + " exists."));
                return null;
            }
            return spaceId;
        }

        SpaceId firstSpaceId = PhysicsSpaces.spaceIds(store)
            .stream()
            .min(Comparator.comparingInt(SpaceId::value))
            .orElse(null);
        if (firstSpaceId == null) {
            ctx.sender().sendMessage(Message.raw("No physics space exists. Run "
                + "`/impulse space create --backend=<id>` before running Impulse example commands."));
        }
        return firstSpaceId;
    }

    @Nonnull
    public static Holder<PhysicsStore> bodyEntity(@Nonnull Ref<PhysicsStore> spaceRef,
        @Nonnull UUID bodyUuid,
        @Nonnull Vector3f bodyCenter,
        @Nonnull PhysicsShapeSpec shape,
        float mass,
        @Nonnull RigidBodySpawnSettings settings,
        @Nullable Vector3f linearVelocity) {
        return PhysicsBodyEntities.dynamicBodyHolder(spaceRef,
            bodyUuid,
            bodyCenter,
            shape,
            mass,
            settings,
            linearVelocity);
    }

    public static void attachBlockBody(@Nonnull Store<EntityStore> store,
        @Nonnull TimeResource time,
        @Nonnull CreatedBlockBody created) {
        Ref<PhysicsStore> bodyRef = created.bodyRef();
        PhysicsThreading.requireWorldThread(bodyRef.getStore(),
            "attach a visual to a created PhysicsStore body entity");
        if (!bodyRef.isValid()) {
            throw new IllegalStateException("Cannot attach visual because PhysicsStore body entity "
                + "is no longer valid: " + created.bodyUuid());
        }
        spawnAttachedBlockEntity(store,
            time,
            bodyRef,
            created.bodyUuid(),
            created.blockType(),
            new Vector3d(created.positionX(), created.positionY(), created.positionZ()),
            created.visualOriginOffsetY(),
            created.controllable());
    }

    @Nullable
    public static Ref<EntityStore> spawnExternalBodyViewBlockEntity(@Nonnull Store<EntityStore> store,
        @Nonnull TimeResource time,
        @Nullable Ref<PhysicsStore> bodyRef,
        @Nonnull UUID bodyUuid,
        @Nonnull Vector3d visualPosition,
        @Nullable String blockType) {
        requirePhysicsEntityVisuals();

        Holder<EntityStore> holder =
            ExampleBlockEntityVisuals.impulseOwnedBlockVisual(time, blockType, visualPosition);

        holder.addComponent(BodyAttachmentComponent.getComponentType(),
            externalBodyAttachment(bodyUuid, bodyRef));
        return store.addEntity(holder, AddReason.SPAWN);
    }

    @Nullable
    private static Ref<EntityStore> spawnAttachedBlockEntity(@Nonnull Store<EntityStore> store,
        @Nonnull TimeResource time,
        @Nullable Ref<PhysicsStore> bodyRef,
        @Nonnull UUID physicsBodyUuid,
        @Nullable String blockType,
        @Nonnull Vector3d visualPosition,
        float visualOriginOffsetY,
        boolean controllable) {
        Holder<EntityStore> holder = attachedPhysicsBlockEntityHolder(time,
            bodyRef,
            physicsBodyUuid,
            blockType,
            visualPosition,
            new Vector3f(),
            new Quaternionf(),
            visualOriginOffsetY,
            controllable);
        return store.addEntity(holder, AddReason.SPAWN);
    }

    @Nonnull
    public static Holder<EntityStore> attachedPhysicsBlockEntityHolder(@Nonnull TimeResource time,
        @Nullable Ref<PhysicsStore> bodyRef,
        @Nonnull UUID physicsBodyUuid,
        @Nullable String blockType,
        @Nonnull Vector3d visualPosition,
        @Nonnull Vector3f localPositionOffset,
        @Nonnull Quaternionf localRotationOffset,
        float visualOriginOffsetY,
        boolean controllable) {
        requirePhysicsEntityVisuals();
        Holder<EntityStore> holder = blockEntityHolder(time, blockType, visualPosition);
        holder.addComponent(BodyAttachmentComponent.getComponentType(),
            impulseOwnedBodyAttachment(physicsBodyUuid,
                bodyRef,
                localPositionOffset,
                localRotationOffset,
                visualOriginOffsetY));
        if (controllable && PhysicsControlSessions.isAvailable()) {
            holder.addComponent(ImpulseControllableComponent.getComponentType(),
                new ImpulseControllableComponent());
        }
        return holder;
    }

    @Nonnull
    static BodyAttachmentComponent externalBodyAttachment(@Nonnull UUID bodyUuid,
        @Nullable Ref<PhysicsStore> bodyRef) {
        BodyAttachmentComponent attachment = BodyAttachmentComponent.externalEntity(bodyUuid);
        attachment.setBodyRef(bodyRef);
        return attachment;
    }

    @Nonnull
    static BodyAttachmentComponent impulseOwnedBodyAttachment(@Nonnull UUID bodyUuid,
        @Nullable Ref<PhysicsStore> bodyRef,
        @Nonnull Vector3f localPositionOffset,
        @Nonnull Quaternionf localRotationOffset,
        float visualOriginOffsetY) {
        BodyAttachmentComponent attachment = BodyAttachmentComponent.impulseOwnedVisual(bodyUuid,
            localPositionOffset,
            localRotationOffset,
            visualOriginOffsetY);
        attachment.setBodyRef(bodyRef);
        return attachment;
    }

    @Nonnull
    private static Holder<EntityStore> blockEntityHolder(@Nonnull TimeResource time,
        @Nullable String blockType,
        @Nonnull Vector3d visualPosition) {
        return ExampleBlockEntityVisuals.impulseOwnedBlockVisual(time, blockType, visualPosition);
    }

    private static void requirePhysicsEntityVisuals() {
        if (!PhysicsEntityAttachments.isAvailable()) {
            throw new IllegalStateException(
                "Impulse PhysicsEntity integration is not available. "
                    + "Enable HytaleModding:ImpulsePhysicsEntity to spawn entity-backed example visuals.");
        }
    }

    public static int optionalInt(@Nonnull CommandContext ctx,
        @Nonnull OptionalArg<Integer> arg,
        int defaultValue,
        int min,
        int max) {
        int value = arg.provided(ctx) ? arg.get(ctx) : defaultValue;
        if (value < min) {
            return min;
        }
        return Math.min(value, max);
    }

    public static Vector3f toVector3f(@Nonnull Vector3d vector) {
        return new Vector3f((float) vector.x, (float) vector.y, (float) vector.z);
    }

    public record SpaceSelection(@Nonnull SpaceId spaceId,
                                 @Nonnull Ref<PhysicsStore> spaceRef) {

        public SpaceSelection {
            Objects.requireNonNull(spaceId, "spaceId");
            Objects.requireNonNull(spaceRef, "spaceRef");
        }
    }

    public record CreatedBlockBody(@Nonnull UUID bodyUuid,
                                   @Nonnull Ref<PhysicsStore> bodyRef,
                                   @Nonnull SpaceId spaceId,
                                   @Nullable String blockType,
                                   float positionX,
                                   float positionY,
                                   float positionZ,
                                   boolean controllable,
                                   float visualOriginOffsetY) {

        public CreatedBlockBody(@Nonnull UUID bodyUuid,
            @Nonnull Ref<PhysicsStore> bodyRef,
            @Nonnull SpaceId spaceId,
            @Nullable String blockType,
            float positionX,
            float positionY,
            float positionZ,
            boolean controllable) {
            this(bodyUuid,
                bodyRef,
                spaceId,
                blockType,
                positionX,
                positionY,
                positionZ,
                controllable,
                Float.NaN);
        }

        public CreatedBlockBody {
            Objects.requireNonNull(bodyUuid, "bodyUuid");
            Objects.requireNonNull(bodyRef, "bodyRef");
            Objects.requireNonNull(spaceId, "spaceId");
        }
    }

}
