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
import dev.hytalemodding.impulse.api.PhysicsAxis;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsWorldResource;
import dev.hytalemodding.impulse.core.plugin.simulation.PhysicsShapeSpec;
import dev.hytalemodding.impulse.core.plugin.simulation.RigidBodySpawnSettings;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nonnull;
import org.joml.Vector3d;

public class ShapesCommand extends AbstractAsyncPlayerCommand {

    private final OptionalArg<Integer> spaceArg = this.withOptionalArg(
        "space",
        "Physics space id to target",
        ArgTypes.INTEGER);

    public ShapesCommand() {
        super("shapes", "Spawn a row of collider shape examples");
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

        Vector3d origin = new Vector3d(playerPos).add(-4.0, 3.0, 3.0);
        spawn(store, time, resource, spaceId, ShapeType.BOX, PhysicsAxis.Y,
            origin, 0);
        spawn(store, time, resource, spaceId, ShapeType.SPHERE, PhysicsAxis.Y, origin, 2);
        spawn(store, time, resource, spaceId, ShapeType.CAPSULE, PhysicsAxis.Y, origin, 4);
        spawn(store, time, resource, spaceId, ShapeType.CYLINDER, PhysicsAxis.Y, origin, 6);
        spawn(store, time, resource, spaceId, ShapeType.CONE, PhysicsAxis.Y, origin, 8);

        ctx.sender().sendMessage(Message.raw("Spawned shape demo."));
        return CompletableFuture.completedFuture(null);
    }

    private static void spawn(@Nonnull Store<EntityStore> store,
        @Nonnull TimeResource time,
        @Nonnull PhysicsWorldResource resource,
        @Nonnull SpaceId spaceId,
        @Nonnull ShapeType type,
        @Nonnull PhysicsAxis axis,
        @Nonnull Vector3d origin,
        int xOffset) {
        ExamplePhysicsUtils.spawnBlockBody(store,
            time,
            resource,
            spaceId,
            new Vector3d(origin).add(xOffset, 0.0, 0.0),
            shape(type, axis),
            1.0f,
            RigidBodySpawnSettings.material(0.7f, 0.35f));
    }

    @Nonnull
    private static PhysicsShapeSpec shape(@Nonnull ShapeType type,
        @Nonnull PhysicsAxis axis) {
        return switch (type) {
            case BOX -> PhysicsShapeSpec.box(0.5f, 0.5f, 0.5f);
            case SPHERE -> PhysicsShapeSpec.sphere(0.5f);
            case CAPSULE -> PhysicsShapeSpec.capsule(0.35f, 0.7f, axis);
            case CYLINDER -> PhysicsShapeSpec.cylinder(0.45f, 0.6f, axis);
            case CONE -> PhysicsShapeSpec.cone(0.5f, 0.7f, axis);
        };
    }

    private enum ShapeType {
        BOX,
        SPHERE,
        CAPSULE,
        CYLINDER,
        CONE
    }
}
