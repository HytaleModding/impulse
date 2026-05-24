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
import com.hypixel.hytale.server.core.modules.time.TimeResource;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.api.PhysicsSpace;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsWorldResource;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nonnull;
import org.joml.Vector3d;

/**
 * Spawn a visible block entity that falls under Bullet physics.
 */
public class DropCommand extends AbstractAsyncPlayerCommand {

    private static final ComponentType<EntityStore, TransformComponent> TRANSFORM_TYPE =
        TransformComponent.getComponentType();
    private final OptionalArg<String> blockTypeArg = this.withOptionalArg(
        "blockType",
        "Hytale block type used for the attached visual entity",
        ArgTypes.STRING);

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
        TransformComponent playerTransform = store.getComponent(ref, TRANSFORM_TYPE);

        if (playerTransform == null) {
            ctx.sender().sendMessage(Message.raw("Cannot determine player position."));
            return CompletableFuture.completedFuture(null);
        }

        Vector3d playerPos = playerTransform.getPosition();
        float spawnX = (float) playerPos.x();
        float spawnY = (float) playerPos.y() + 5f;
        float spawnZ = (float) playerPos.z();

        PhysicsWorldResource resource = store.getResource(PhysicsWorldResource.getResourceType());
        PhysicsSpace space = ExamplePhysicsUtils.defaultSpace(ctx, resource);
        if (space == null) {
            return CompletableFuture.completedFuture(null);
        }

        TimeResource time = store.getResource(TimeResource.getResourceType());
        ExamplePhysicsUtils.spawnBlockBody(store,
            time,
            resource,
            space.getId(),
            new Vector3d(spawnX, spawnY, spawnZ),
            blockType(ctx),
            bodySpace -> {
                var created = bodySpace.createBox(0.5f, 0.5f, 0.5f, 1.0f);
                created.setRestitution(0.5f);
                created.setFriction(0.5f);
                return created;
            });

        ctx.sender()
            .sendMessage(Message.raw("Dropped box at " + spawnX + ", " + spawnY + ", " + spawnZ));

        return CompletableFuture.completedFuture(null);
    }

    @Nonnull
    private String blockType(@Nonnull CommandContext ctx) {
        return blockTypeArg.provided(ctx)
            ? ExamplePhysicsUtils.resolveBlockType(blockTypeArg.get(ctx))
            : ExamplePhysicsUtils.DEFAULT_BLOCK_TYPE;
    }
}
