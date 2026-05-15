package dev.hytalemodding.impulse.examples.commands;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.BlockEntity;
import com.hypixel.hytale.server.core.modules.entity.DespawnComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.time.TimeResource;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.api.PhysicsBody;
import dev.hytalemodding.impulse.api.PhysicsSpace;
import dev.hytalemodding.impulse.core.components.ImpulseControllableComponent;
import dev.hytalemodding.impulse.core.components.PersistentPhysicsBodyComponent;
import dev.hytalemodding.impulse.core.components.PhysicsBodyComponent;
import dev.hytalemodding.impulse.core.resources.PhysicsWorldResource;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nonnull;
import org.joml.Vector3d;

/**
 * Spawn a visible block entity that falls under Bullet physics.
 */
public class DropCommand extends AbstractAsyncPlayerCommand {

    public DropCommand() {
        super("drop", "Spawn a physics box that falls under gravity");
    }

    @Nonnull
    @Override
    protected CompletableFuture<Void> executeAsync(@Nonnull CommandContext ctx,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull PlayerRef playerRef,
        @Nonnull World world) {
        TransformComponent playerTransform = store.getComponent(ref,
            TransformComponent.getComponentType());

        if (playerTransform == null) {
            ctx.sender().sendMessage(Message.raw("Cannot determine player position."));
            return CompletableFuture.completedFuture(null);
        }

        Vector3d playerPos = playerTransform.getPosition();
        float spawnX = (float) playerPos.x();
        float spawnY = (float) playerPos.y() + 5f;
        float spawnZ = (float) playerPos.z();

        TimeResource time = store.getResource(TimeResource.getResourceType());

        Holder<EntityStore> holder = BlockEntity.assembleDefaultBlockEntity(
            time,
            /*
             * FIXME: Replace this hardcoded block type with a command argument or config value.
             */
            "Rock_Stone",
            new Vector3d(spawnX, spawnY, spawnZ)
        );
        holder.removeComponent(DespawnComponent.getComponentType());

        PhysicsWorldResource resource = store.getResource(PhysicsWorldResource.getResourceType());
        PhysicsSpace space = ExamplePhysicsUtils.defaultSpace(resource, world);

        PhysicsBody box = space.createBox(0.5f, 0.5f, 0.5f, 1.0f);
        box.setPosition(spawnX, spawnY + box.getCenterOfMassOffsetY(), spawnZ);
        box.setRestitution(0.5f);
        box.setFriction(0.5f);

        space.addBody(box);

        holder.addComponent(PhysicsBodyComponent.getComponentType(),
            new PhysicsBodyComponent(box, space.getId()));
        holder.addComponent(PersistentPhysicsBodyComponent.getComponentType(),
            PersistentPhysicsBodyComponent.fromBody(box, space.getId()));
        holder.addComponent(ImpulseControllableComponent.getComponentType(),
            new ImpulseControllableComponent());

        Ref<EntityStore> bodyRef = store.addEntity(holder, AddReason.SPAWN);
        resource.registerBodyOwner(box, bodyRef);

        ctx.sender()
            .sendMessage(Message.raw("Dropped box at " + spawnX + ", " + spawnY + ", " + spawnZ));

        return CompletableFuture.completedFuture(null);
    }
}
