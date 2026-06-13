package dev.hytalemodding.impulse.examples.commands.stress;

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
import dev.hytalemodding.impulse.examples.commands.ExamplePhysicsUtils;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nonnull;
import org.joml.Vector3d;

public class StressShapesCommand extends AbstractAsyncPlayerCommand {

    private static final int DEFAULT_SETS = 10;
    private static final int MAX_SETS = 1000;
    private static final PhysicsAxis[] AXES = {
        PhysicsAxis.X,
        PhysicsAxis.Y,
        PhysicsAxis.Z
    };

    private final OptionalArg<Integer> setsArg = this.withOptionalArg(
        "sets",
        "Number of mixed shape sets to spawn",
        ArgTypes.INTEGER);
    private final OptionalArg<Integer> spaceArg = this.withOptionalArg(
        "space",
        "Physics space id to target",
        ArgTypes.INTEGER);

    public StressShapesCommand() {
        super("shapes", "Spawn many mixed collider shapes");
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

        int sets = ExamplePhysicsUtils.optionalInt(ctx, setsArg, DEFAULT_SETS, 1, MAX_SETS);
        PhysicsWorldResource resource = ExamplePhysicsUtils.resource(store);
        SpaceId spaceId = ExamplePhysicsUtils.spaceId(ctx, resource, spaceArg);
        if (spaceId == null) {
            return CompletableFuture.completedFuture(null);
        }
        TimeResource time = store.getResource(TimeResource.getResourceType());

        Vector3d origin = new Vector3d(playerPos).add(-12.0, 5.0, 5.0);
        for (int set = 0; set < sets; set++) {
            PhysicsAxis axis = AXES[set % AXES.length];
            int row = set / 4;
            int col = set % 4;
            Vector3d base = new Vector3d(origin).add(col * 7.0, row * 2.2, row * 1.5);

            spawn(store, time, resource, spaceId, ShapeType.BOX, axis,
                base, 0.0);
            spawn(store, time, resource, spaceId, ShapeType.SPHERE, axis,
                base, 1.2);
            spawn(store, time, resource, spaceId, ShapeType.CAPSULE, axis,
                base, 2.4);
            spawn(store, time, resource, spaceId, ShapeType.CYLINDER, axis,
                base, 3.6);
            spawn(store, time, resource, spaceId, ShapeType.CONE, axis,
                base, 4.8);
        }

        ctx.sender().sendMessage(Message.raw("Queued " + sets + " mixed shape sets ("
            + (sets * 5) + " bodies)."));
        return CompletableFuture.completedFuture(null);
    }

    private static void spawn(@Nonnull Store<EntityStore> store,
        @Nonnull TimeResource time,
        @Nonnull PhysicsWorldResource resource,
        @Nonnull SpaceId spaceId,
        @Nonnull ShapeType type,
        @Nonnull PhysicsAxis axis,
        @Nonnull Vector3d base,
        double xOffset) {
        ExamplePhysicsUtils.spawnBlockBody(store,
            time,
            resource,
            spaceId,
            new Vector3d(base).add(xOffset, 0.0, 0.0),
            ExamplePhysicsUtils.DEFAULT_BLOCK_TYPE,
            shape(type, axis),
            1.0f,
            RigidBodySpawnSettings.material(0.6f, 0.25f),
            null);
    }

    @Nonnull
    private static PhysicsShapeSpec shape(@Nonnull ShapeType type,
        @Nonnull PhysicsAxis axis) {
        return switch (type) {
            case BOX -> PhysicsShapeSpec.box(0.45f, 0.55f, 0.35f);
            case SPHERE -> PhysicsShapeSpec.sphere(0.5f);
            case CAPSULE -> PhysicsShapeSpec.capsule(0.3f, 0.7f, axis);
            case CYLINDER -> PhysicsShapeSpec.cylinder(0.4f, 0.65f, axis);
            case CONE -> PhysicsShapeSpec.cone(0.45f, 0.7f, axis);
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
