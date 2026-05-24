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
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.api.PhysicsBody;
import dev.hytalemodding.impulse.api.PhysicsSpace;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodies;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyFactory;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyId;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodySpawnResult;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodySpawnSpec;
import dev.hytalemodding.impulse.core.plugin.components.ImpulseControllableComponent;
import dev.hytalemodding.impulse.core.plugin.components.PhysicsBodyAttachmentComponent;
import dev.hytalemodding.impulse.core.plugin.execution.PhysicsOwnerCallable;
import dev.hytalemodding.impulse.core.plugin.execution.PhysicsOwnerMutation;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsWorldResource;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsVisualMaterializationSettings;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3f;

public final class ExamplePhysicsUtils {

    public static final String DEFAULT_BLOCK_TYPE =
        PhysicsVisualMaterializationSettings.DEFAULT_DETACHED_VISUAL_BLOCK_TYPE;
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
    public static SpawnedBlockBody spawnBlockBody(@Nonnull Store<EntityStore> store,
        @Nonnull TimeResource time,
        @Nonnull PhysicsWorldResource resource,
        @Nonnull SpaceId spaceId,
        @Nonnull Vector3d visualPosition,
        @Nonnull PhysicsBodyFactory factory) {
        return spawnBlockBody(store, time, resource, spaceId, visualPosition, DEFAULT_BLOCK_TYPE,
            factory);
    }

    @Nonnull
    public static SpawnedBlockBody spawnBlockBody(@Nonnull Store<EntityStore> store,
        @Nonnull TimeResource time,
        @Nonnull PhysicsWorldResource resource,
        @Nonnull SpaceId spaceId,
        @Nonnull Vector3d visualPosition,
        @Nullable String blockType,
        @Nonnull PhysicsBodyFactory factory) {
        AtomicBoolean dynamic = new AtomicBoolean();
        PhysicsBodySpawnResult spawned = PhysicsBodies.spawn(resource,
            PhysicsBodySpawnSpec.persistentBody(
                spaceId,
                space -> {
                    PhysicsBody body = factory.create(space);
                    body.setPosition((float) visualPosition.x,
                        (float) (visualPosition.y + body.getCenterOfMassOffsetY()),
                        (float) visualPosition.z);
                    dynamic.set(body.isDynamic());
                    return body;
                }));
        Ref<EntityStore> entity = spawnAttachedBlockEntity(store,
            time,
            spawned.bodyId(),
            spawned.spaceId(),
            blockType,
            visualPosition,
            dynamic.get());
        return new SpawnedBlockBody(spawned.bodyId(), spawned.spaceId(), entity);
    }

    @Nonnull
    public static PhysicsBody requireLiveBody(@Nonnull PhysicsWorldResource resource,
        @Nonnull PhysicsBodyId bodyId) {
        PhysicsBody body = resource.getBody(bodyId);
        if (body == null) {
            throw new IllegalArgumentException("Physics body id=" + bodyId + " is not registered");
        }
        return body;
    }

    @Nonnull
    private static Ref<EntityStore> spawnAttachedBlockEntity(@Nonnull Store<EntityStore> store,
        @Nonnull TimeResource time,
        @Nonnull PhysicsBodyId bodyId,
        @Nonnull SpaceId spaceId,
        @Nullable String blockType,
        @Nonnull Vector3d visualPosition,
        boolean controllable) {
        Holder<EntityStore> holder = blockEntityHolder(time, blockType, visualPosition);
        holder.addComponent(ATTACHMENT_TYPE,
            PhysicsBodyAttachmentComponent.externalEntity(bodyId, spaceId));
        if (controllable) {
            holder.addComponent(IMPULSE_CONTROLLABLE_TYPE, new ImpulseControllableComponent());
        }
        return store.addEntity(holder, AddReason.SPAWN);
    }

    @Nonnull
    private static Holder<EntityStore> blockEntityHolder(@Nonnull TimeResource time,
        @Nullable String blockType,
        @Nonnull Vector3d visualPosition) {
        Holder<EntityStore> holder = BlockEntity.assembleDefaultBlockEntity(
            time,
            resolveBlockType(blockType),
            new Vector3d(visualPosition)
        );
        holder.removeComponent(DESPAWN_TYPE);
        return holder;
    }

    @Nonnull
    public static String resolveBlockType(@Nullable String blockType) {
        return blockType == null || blockType.isBlank()
            ? DEFAULT_BLOCK_TYPE
            : blockType.trim();
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

    static Vector3f toVector3f(@Nonnull Vector3d vector) {
        return new Vector3f((float) vector.x, (float) vector.y, (float) vector.z);
    }

    public record SpawnedBlockBody(@Nonnull PhysicsBodyId bodyId,
                                   @Nonnull SpaceId spaceId,
                                   @Nonnull Ref<EntityStore> entity) {
    }
}
