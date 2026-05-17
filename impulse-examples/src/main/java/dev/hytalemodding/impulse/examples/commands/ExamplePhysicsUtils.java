package dev.hytalemodding.impulse.examples.commands;

import com.hypixel.hytale.component.AddReason;
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
import dev.hytalemodding.impulse.core.components.ImpulseControllableComponent;
import dev.hytalemodding.impulse.core.components.PersistentPhysicsBodyComponent;
import dev.hytalemodding.impulse.core.components.PhysicsBodyComponent;
import dev.hytalemodding.impulse.core.resources.PhysicsWorldResource;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3f;

final class ExamplePhysicsUtils {

    private static final String DEFAULT_BLOCK_TYPE = "Rock_Stone";

    private ExamplePhysicsUtils() {
    }

    @Nullable
    static Vector3d playerPosition(@Nonnull CommandContext ctx,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref) {
        TransformComponent playerTransform = store.getComponent(ref,
            TransformComponent.getComponentType());
        if (playerTransform == null) {
            ctx.sender().sendMessage(Message.raw("Cannot determine player position."));
            return null;
        }
        return new Vector3d(playerTransform.getPosition());
    }

    @Nonnull
    static PhysicsWorldResource resource(@Nonnull Store<EntityStore> store) {
        return store.getResource(PhysicsWorldResource.getResourceType());
    }

    @Nullable
    static PhysicsSpace defaultSpace(@Nonnull CommandContext ctx,
        @Nonnull PhysicsWorldResource resource) {
        PhysicsSpace existing = resource.getDefaultSpace();
        if (existing != null) {
            return existing;
        }

        ctx.sender().sendMessage(Message.raw("No default physics space exists. Create or select "
            + "one explicitly before running Impulse example commands."));
        return null;
    }

    @Nonnull
    static Ref<EntityStore> spawnBlockBody(@Nonnull Store<EntityStore> store,
        @Nonnull World world,
        @Nonnull PhysicsWorldResource resource,
        @Nonnull PhysicsSpace space,
        @Nonnull PhysicsBody body,
        @Nonnull Vector3d visualPosition) {
        TimeResource time = store.getResource(TimeResource.getResourceType());
        return spawnBlockBody(store, time, space.getId(), space, body, visualPosition);
    }

    @Nonnull
    static Ref<EntityStore> spawnBlockBody(@Nonnull Store<EntityStore> store,
        @Nonnull TimeResource time,
        @Nonnull SpaceId spaceId,
        @Nonnull PhysicsSpace space,
        @Nonnull PhysicsBody body,
        @Nonnull Vector3d visualPosition) {
        Holder<EntityStore> holder = BlockEntity.assembleDefaultBlockEntity(
            time,
            DEFAULT_BLOCK_TYPE,
            new Vector3d(visualPosition)
        );
        holder.removeComponent(DespawnComponent.getComponentType());

        body.setPosition((float) visualPosition.x,
            (float) (visualPosition.y + body.getCenterOfMassOffsetY()),
            (float) visualPosition.z);

        return attachBlockBodyEntity(store, holder, spaceId, space, body, true);
    }

    @Nonnull
    static Ref<EntityStore> attachExistingBlockBody(@Nonnull Store<EntityStore> store,
        @Nonnull TimeResource time,
        @Nonnull SpaceId spaceId,
        @Nonnull PhysicsBody body) {
        Holder<EntityStore> holder = BlockEntity.assembleDefaultBlockEntity(
            time,
            DEFAULT_BLOCK_TYPE,
            visualPositionFromBody(body)
        );
        holder.removeComponent(DespawnComponent.getComponentType());

        return attachBlockBodyEntity(store, holder, spaceId, null, body, false);
    }

    @Nonnull
    private static Ref<EntityStore> attachBlockBodyEntity(@Nonnull Store<EntityStore> store,
        @Nonnull Holder<EntityStore> holder,
        @Nonnull SpaceId spaceId,
        @Nullable PhysicsSpace space,
        @Nonnull PhysicsBody body,
        boolean addBodyToSpace) {

        holder.addComponent(PhysicsBodyComponent.getComponentType(),
            new PhysicsBodyComponent(body, spaceId));
        holder.addComponent(PersistentPhysicsBodyComponent.getComponentType(),
            PersistentPhysicsBodyComponent.fromBody(body, spaceId));
        if (body.isDynamic()) {
            holder.addComponent(ImpulseControllableComponent.getComponentType(),
                new ImpulseControllableComponent());
        }
        Ref<EntityStore> entityRef = store.addEntity(holder, AddReason.SPAWN);
        if (addBodyToSpace) {
            if (space == null) {
                throw new IllegalArgumentException("Space is required when adding a body to the backend");
            }
            space.addBody(body);
        }
        return entityRef;
    }

    @Nonnull
    static Vector3d eyePosition(@Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull TransformComponent transform) {
        return new Vector3d(transform.getPosition()).add(0.0, eyeHeight(store, ref), 0.0);
    }

    static double eyeHeight(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref) {
        ModelComponent modelComponent = store.getComponent(ref, ModelComponent.getComponentType());
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
    static Vector3d lookDirection(@Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull TransformComponent transform) {
        HeadRotation headRotation = store.getComponent(ref, HeadRotation.getComponentType());
        Rotation3f rotation = headRotation != null ? headRotation.getRotation() : transform.getRotation();
        Quaterniond quaternion = rotation.getQuaternion(new Quaterniond());
        Vector3d direction = new Vector3d(Vector3dUtil.FORWARD);
        quaternion.transform(direction);
        if (direction.lengthSquared() == 0.0) {
            return new Vector3d(Vector3dUtil.FORWARD);
        }
        return direction.normalize();
    }

    static int optionalInt(@Nonnull CommandContext ctx,
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
