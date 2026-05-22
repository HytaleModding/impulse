package dev.hytalemodding.impulse.examples.commands;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.math.vector.Vector3dUtil;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.entity.entities.BlockEntity;
import com.hypixel.hytale.server.core.modules.entity.DespawnComponent;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.time.TimeResource;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.api.PhysicsBody;
import dev.hytalemodding.impulse.api.PhysicsSpace;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.plugin.components.ImpulseControllableComponent;
import dev.hytalemodding.impulse.core.plugin.components.PhysicsBodyAttachmentComponent;
import dev.hytalemodding.impulse.core.plugin.components.PhysicsBodyAttachmentComponent.AttachmentLifecycle;
import dev.hytalemodding.impulse.core.plugin.components.PhysicsBodyAttachmentComponent.TransformAuthority;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyId;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyKind;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyPersistenceMode;
import dev.hytalemodding.impulse.core.plugin.execution.PhysicsOwnerCallable;
import dev.hytalemodding.impulse.core.plugin.execution.PhysicsOwnerMutation;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsWorldResource;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3f;

public final class ExamplePhysicsUtils {

    private static final String DEFAULT_BLOCK_TYPE = "Rock_Stone";
    private static final ComponentType<EntityStore, TransformComponent> TRANSFORM_TYPE =
        TransformComponent.getComponentType();
    private static final ComponentType<EntityStore, DespawnComponent> DESPAWN_TYPE =
        DespawnComponent.getComponentType();
    private static final ComponentType<EntityStore, PhysicsBodyAttachmentComponent> ATTACHMENT_TYPE =
        PhysicsBodyAttachmentComponent.getComponentType();
    private static final ComponentType<EntityStore, ImpulseControllableComponent> IMPULSE_CONTROLLABLE_TYPE =
        ImpulseControllableComponent.getComponentType();
    private static final ComponentType<EntityStore, ModelComponent> MODEL_TYPE = ModelComponent.getComponentType();
    private static final ComponentType<EntityStore, HeadRotation> HEAD_ROTATION_TYPE = HeadRotation.getComponentType();

    private ExamplePhysicsUtils() {
    }

    @Nullable
    public static Vector3d playerPosition(@Nonnull CommandContext ctx,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref) {
        TransformComponent playerTransform = store.getComponent(ref, TRANSFORM_TYPE);
        if (playerTransform == null) {
            ctx.sender().sendMessage(Message.raw("Cannot determine player position."));
            return null;
        }
        return new Vector3d(playerTransform.getPosition());
    }

    @Nonnull
    public static PhysicsWorldResource resource(@Nonnull Store<EntityStore> store) {
        return store.getResource(PhysicsWorldResource.getResourceType());
    }

    @Nullable
    public static PhysicsSpace defaultSpace(@Nonnull CommandContext ctx,
        @Nonnull PhysicsWorldResource resource) {
        PhysicsSpace existing = resource.getDefaultSpace();
        if (existing != null) {
            return existing;
        }

        ctx.sender().sendMessage(Message.raw("No default physics space exists. Run "
            + "`/impulse space create --default=true` or select one with "
            + "`/impulse space default --space=<space-id>` before running Impulse example commands."));
        return null;
    }

    public static void physicsOwnerRun(@Nonnull Store<EntityStore> store,
        @Nonnull String operation,
        @Nonnull PhysicsOwnerMutation mutation) {
        resource(store).runOnPhysicsOwner(operation, mutation);
    }

    public static <T> T physicsOwnerCall(@Nonnull Store<EntityStore> store,
        @Nonnull String operation,
        @Nonnull PhysicsOwnerCallable<T> callable) {
        return resource(store).callOnPhysicsOwner(operation, callable);
    }

    @Nonnull
    static Ref<EntityStore> spawnBlockBody(@Nonnull Store<EntityStore> store,
        @Nonnull World world,
        @Nonnull PhysicsWorldResource resource,
        @Nonnull PhysicsSpace space,
        @Nonnull PhysicsBody body,
        @Nonnull Vector3d visualPosition) {
        TimeResource time = store.getResource(TimeResource.getResourceType());
        return spawnBlockBody(store, time, resource, space.getId(), space, body, visualPosition);
    }

    @Nonnull
    public static Ref<EntityStore> spawnBlockBody(@Nonnull Store<EntityStore> store,
        @Nonnull TimeResource time,
        @Nonnull PhysicsWorldResource resource,
        @Nonnull SpaceId spaceId,
        @Nonnull PhysicsSpace space,
        @Nonnull PhysicsBody body,
        @Nonnull Vector3d visualPosition) {
        Holder<EntityStore> holder = BlockEntity.assembleDefaultBlockEntity(
            time,
            DEFAULT_BLOCK_TYPE,
            new Vector3d(visualPosition)
        );
        holder.removeComponent(DESPAWN_TYPE);

        resource.runOnPhysicsOwner("position example physics body", () ->
            body.setPosition((float) visualPosition.x,
                (float) (visualPosition.y + body.getCenterOfMassOffsetY()),
                (float) visualPosition.z));

        return attachBlockBodyEntity(store, holder, resource, spaceId, body, true);
    }

    @Nonnull
    public static Ref<EntityStore> attachExistingBlockBody(@Nonnull Store<EntityStore> store,
        @Nonnull TimeResource time,
        @Nonnull PhysicsWorldResource resource,
        @Nonnull SpaceId spaceId,
        @Nonnull PhysicsBody body) {
        Holder<EntityStore> holder = BlockEntity.assembleDefaultBlockEntity(
            time,
            DEFAULT_BLOCK_TYPE,
            physicsOwnerCall(store, "read example physics body visual position",
                () -> visualPositionFromBody(body))
        );
        holder.removeComponent(DESPAWN_TYPE);

        return attachBlockBodyEntity(store, holder, resource, spaceId, body, false);
    }

    @Nonnull
    private static Ref<EntityStore> attachBlockBodyEntity(@Nonnull Store<EntityStore> store,
        @Nonnull Holder<EntityStore> holder,
        @Nonnull PhysicsWorldResource resource,
        @Nonnull SpaceId spaceId,
        @Nonnull PhysicsBody body,
        boolean addBodyToSpace) {

        PhysicsBodyId bodyId = resource.getBodyId(body);
        if (bodyId == null) {
            bodyId = resource.addBody(spaceId,
                body,
                PhysicsBodyKind.BODY,
                PhysicsBodyPersistenceMode.PERSISTENT);
        } else if (addBodyToSpace) {
            resource.addBody(bodyId,
                spaceId,
                body,
                PhysicsBodyKind.BODY,
                PhysicsBodyPersistenceMode.PERSISTENT);
        }
        holder.addComponent(ATTACHMENT_TYPE,
            new PhysicsBodyAttachmentComponent(bodyId,
                spaceId,
                TransformAuthority.BODY,
                AttachmentLifecycle.EXTERNAL_ENTITY));
        boolean dynamic = physicsOwnerCall(store, "read example physics body type",
            body::isDynamic);
        if (dynamic) {
            holder.addComponent(IMPULSE_CONTROLLABLE_TYPE, new ImpulseControllableComponent());
        }
        return store.addEntity(holder, AddReason.SPAWN);
    }

    @Nonnull
    static Vector3d eyePosition(@Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull TransformComponent transform) {
        return new Vector3d(transform.getPosition()).add(0.0, eyeHeight(store, ref), 0.0);
    }

    static double eyeHeight(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref) {
        ModelComponent modelComponent = store.getComponent(ref, MODEL_TYPE);
        if (modelComponent == null) {
            return 1.6;
        }

        Model model = modelComponent.getModel();
        if (model == null) {
            return 1.6;
        }
        return model.getEyeHeight(ref, store);
    }

    @Nonnull
    public static Vector3d lookDirection(@Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull TransformComponent transform) {
        HeadRotation headRotation = store.getComponent(ref, HEAD_ROTATION_TYPE);
        Rotation3f rotation = headRotation != null ? headRotation.getRotation() : transform.getRotation();
        Quaterniond quaternion = rotation.getQuaternion(new Quaterniond());
        Vector3d direction = new Vector3d(Vector3dUtil.FORWARD);
        quaternion.transform(direction);
        if (direction.lengthSquared() == 0.0) {
            return new Vector3d(Vector3dUtil.FORWARD);
        }
        return direction.normalize();
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

    static Vector3d bodyCenter(@Nonnull PhysicsBody body) {
        Vector3f position = body.getPosition();
        return new Vector3d(position.x, position.y, position.z);
    }

    @Nonnull
    static Vector3d visualPositionFromBody(@Nonnull PhysicsBody body) {
        Vector3f center = body.getPosition();
        return new Vector3d(center.x,
            center.y - body.getCenterOfMassOffsetY(),
            center.z);
    }

    static Vector3f toVector3f(@Nonnull Vector3d vector) {
        return new Vector3f((float) vector.x, (float) vector.y, (float) vector.z);
    }
}
