package dev.hytalemodding.impulse.examples.commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncPlayerCommand;
import com.hypixel.hytale.server.core.modules.time.TimeResource;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsWorldResource;
import dev.hytalemodding.impulse.core.plugin.simulation.PhysicsShapeSpec;
import dev.hytalemodding.impulse.core.plugin.simulation.RigidBodySpawnSettings;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nonnull;
import org.joml.Vector3d;
import org.joml.Vector3f;

public class MaterialsCommand extends AbstractAsyncPlayerCommand {

    private final OptionalArg<Integer> spaceArg = this.withOptionalArg(
        "space",
        "Physics space id to target",
        ArgTypes.INTEGER);

    public MaterialsCommand() {
        super("materials", "Spawn restitution and friction comparison bodies");
    }

    @Nonnull
    @Override
    protected CompletableFuture<Void> executeAsync(@Nonnull CommandContext ctx,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull PlayerRef playerRef,
        @Nonnull World world) {
        Vector3d playerPos = ExamplePhysicsUtils.playerPosition(ctx, store, ref);
        if (playerPos == null) {
            return CompletableFuture.completedFuture(null);
        }

        PhysicsWorldResource resource = ExamplePhysicsUtils.resource(store);
        SpaceId spaceId = ExamplePhysicsUtils.spaceId(ctx, resource, spaceArg);
        if (spaceId == null) {
            return CompletableFuture.completedFuture(null);
        }
        TimeResource time = store.getResource(TimeResource.getResourceType());

        Vector3d origin = new Vector3d(playerPos).add(-3.0, 5.0, 4.0);
        spawnSphere(store, time, resource, spaceId, new Vector3d(origin), 0.05f, 0.9f, 3.0f);
        spawnSphere(store, time, resource, spaceId, new Vector3d(origin).add(2.0, 0.0, 0.0),
            0.95f, 0.9f, 3.0f);
        spawnSphere(store, time, resource, spaceId, new Vector3d(origin).add(4.0, 0.0, 0.0),
            0.5f, 0.0f, 2.0f);
        spawnSphere(store, time, resource, spaceId, new Vector3d(origin).add(6.0, 0.0, 0.0),
            0.5f, 0.95f, 2.0f);

        ctx.sender().sendMessage(Message.raw(
            "Spawned material demo: bouncy, slick, dead, and sticky spheres."));
        return CompletableFuture.completedFuture(null);
    }

    private static void spawnSphere(@Nonnull Store<EntityStore> store,
        @Nonnull TimeResource time,
        @Nonnull PhysicsWorldResource resource,
        @Nonnull SpaceId spaceId,
        @Nonnull Vector3d position,
        float restitution,
        float friction,
        float speed) {
        ExamplePhysicsUtils.spawnBlockBody(store,
            time,
            resource,
            spaceId,
            position,
            ExamplePhysicsUtils.DEFAULT_BLOCK_TYPE,
            PhysicsShapeSpec.sphere(0.5f),
            1.0f,
            RigidBodySpawnSettings.material(friction, restitution),
            new Vector3f(speed, 0.0f, 0.0f));
    }
}
