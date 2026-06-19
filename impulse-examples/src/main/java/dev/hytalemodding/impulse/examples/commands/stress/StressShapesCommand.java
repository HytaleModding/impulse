package dev.hytalemodding.impulse.examples.commands.stress;

import com.hypixel.hytale.component.AddReason;
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
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.api.PhysicsAxis;
import dev.hytalemodding.impulse.core.plugin.physicsstore.BodyEntityDescriptor;
import dev.hytalemodding.impulse.core.plugin.physicsstore.PhysicsThreading;
import dev.hytalemodding.impulse.core.plugin.simulation.PhysicsShapeSpec;
import dev.hytalemodding.impulse.core.plugin.simulation.RigidBodySpawnSettings;
import dev.hytalemodding.impulse.examples.utils.ExamplePhysicsUtils;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nonnull;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;

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
        Vector3d playerPos = new Vector3d(playerRef.getTransform().getPosition());

        int sets = ExamplePhysicsUtils.optionalInt(ctx, setsArg, DEFAULT_SETS, 1, MAX_SETS);
        ExamplePhysicsUtils.SpaceSelection space = ExamplePhysicsUtils.spaceSelection(ctx,
            world,
            spaceArg);
        if (space == null) {
            return CompletableFuture.completedFuture(null);
        }
        TimeResource time = store.getResource(TimeResource.getResourceType());
        Store<PhysicsStore> physicsStore = PhysicsThreading.store(world);
        PhysicsThreading.requireWorldThread(physicsStore,
            "spawn stress shape PhysicsStore body entities");

        Vector3d origin = new Vector3d(playerPos).add(-12.0, 5.0, 5.0);
        for (int set = 0; set < sets; set++) {
            PhysicsAxis axis = AXES[set % AXES.length];
            int row = set / 4;
            int col = set % 4;
            Vector3d base = new Vector3d(origin).add(col * 7.0, row * 2.2, row * 1.5);

            spawn(store,
                physicsStore,
                time,
                space.spaceRef(),
                ShapeType.BOX,
                axis,
                base, 0.0);
            spawn(store,
                physicsStore,
                time,
                space.spaceRef(),
                ShapeType.SPHERE,
                axis,
                base, 1.2);
            spawn(store,
                physicsStore,
                time,
                space.spaceRef(),
                ShapeType.CAPSULE,
                axis,
                base, 2.4);
            spawn(store,
                physicsStore,
                time,
                space.spaceRef(),
                ShapeType.CYLINDER,
                axis,
                base, 3.6);
            spawn(store,
                physicsStore,
                time,
                space.spaceRef(),
                ShapeType.CONE,
                axis,
                base, 4.8);
        }

        ctx.sender().sendMessage(Message.raw("Queued " + sets + " mixed shape sets ("
            + (sets * 5) + " bodies)."));
        return CompletableFuture.completedFuture(null);
    }

    private static void spawn(@Nonnull Store<EntityStore> store,
        @Nonnull Store<PhysicsStore> physicsStore,
        @Nonnull TimeResource time,
        @Nonnull Ref<PhysicsStore> spaceRef,
        @Nonnull ShapeType type,
        @Nonnull PhysicsAxis axis,
        @Nonnull Vector3d base,
        double xOffset) {
        Vector3d position = new Vector3d(base).add(xOffset, 0.0, 0.0);
        UUID bodyUuid = UUID.randomUUID();
        BodyEntityDescriptor descriptor = ExamplePhysicsUtils.bodyEntity(spaceRef,
            bodyUuid,
            ExamplePhysicsUtils.toVector3f(position),
            shape(type, axis),
            1.0f,
            RigidBodySpawnSettings.material(0.6f, 0.25f),
            null);
        Ref<PhysicsStore> bodyRef = physicsStore.addEntity(
            ExamplePhysicsUtils.bodyHolder(physicsStore, descriptor),
            AddReason.SPAWN);
        store.addEntity(ExamplePhysicsUtils.attachedPhysicsStoreBlockEntityHolder(
            time,
            bodyRef,
            bodyUuid,
            ExamplePhysicsUtils.DEFAULT_BLOCK_TYPE,
            position,
            new Vector3f(),
            new Quaternionf(),
            Float.NaN,
            true),
            AddReason.SPAWN);
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
