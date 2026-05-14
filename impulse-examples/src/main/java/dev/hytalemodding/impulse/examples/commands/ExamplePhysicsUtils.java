package dev.hytalemodding.impulse.examples.commands;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.entity.entities.BlockEntity;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.time.TimeResource;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.api.PhysicsBody;
import dev.hytalemodding.impulse.api.PhysicsSpace;
import dev.hytalemodding.impulse.core.components.PhysicsBodyComponent;
import dev.hytalemodding.impulse.core.resources.PhysicsWorldResource;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
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

    @Nonnull
    static PhysicsSpace mainSpace(@Nonnull PhysicsWorldResource resource, @Nonnull World world) {
        return resource.getMainSpace(world.getName());
    }

    static void enableDebug(@Nonnull PhysicsWorldResource resource) {
        resource.setDebugEnabled(true);
        resource.setDebugShapesEnabled(true);
    }

    static void spawnBlockBody(@Nonnull Store<EntityStore> store,
        @Nonnull World world,
        @Nonnull PhysicsWorldResource resource,
        @Nonnull PhysicsSpace space,
        @Nonnull PhysicsBody body,
        @Nonnull Vector3d visualPosition) {
        TimeResource time = store.getResource(TimeResource.getResourceType());
        Holder<EntityStore> holder = BlockEntity.assembleDefaultBlockEntity(
            time,
            DEFAULT_BLOCK_TYPE,
            new Vector3d(visualPosition)
        );

        body.setPosition((float) visualPosition.x,
            (float) (visualPosition.y + body.getCenterOfMassOffsetY()),
            (float) visualPosition.z);
        space.addBody(body);

        holder.addComponent(PhysicsBodyComponent.getComponentType(),
            new PhysicsBodyComponent(body, resource.getMainSpaceId()));
        store.addEntity(holder, AddReason.SPAWN);
    }

    static Vector3d bodyCenter(@Nonnull PhysicsBody body) {
        Vector3f position = body.getPosition();
        return new Vector3d(position.x, position.y, position.z);
    }

    static Vector3f toVector3f(@Nonnull Vector3d vector) {
        return new Vector3f((float) vector.x, (float) vector.y, (float) vector.z);
    }
}
