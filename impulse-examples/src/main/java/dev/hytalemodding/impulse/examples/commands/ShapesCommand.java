package dev.hytalemodding.impulse.examples.commands;

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
import dev.hytalemodding.impulse.core.plugin.physics.PhysicsBodyEntities;
import dev.hytalemodding.impulse.core.plugin.physics.PhysicsThreading;
import dev.hytalemodding.impulse.core.plugin.physics.PhysicsShapeSpec;
import dev.hytalemodding.impulse.core.plugin.physics.RigidBodySpawnSettings;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nonnull;
import dev.hytalemodding.impulse.examples.utils.ExamplePhysicsUtils;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;

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
        Vector3d playerPos = new Vector3d(playerRef.getTransform().getPosition());

        ExamplePhysicsUtils.SpaceSelection space = ExamplePhysicsUtils.spaceSelection(ctx,
            world,
            spaceArg);
        if (space == null) {
            return CompletableFuture.completedFuture(null);
        }
        TimeResource time = store.getResource(TimeResource.getResourceType());
        Store<PhysicsStore> physicsStore = PhysicsThreading.store(world);
        PhysicsThreading.requireWorldThread(physicsStore,
            "spawn shape example PhysicsStore body entities");

        Vector3d origin = new Vector3d(playerPos).add(-4.0, 3.0, 3.0);
        spawn(store,
            physicsStore,
            time,
            space.spaceRef(),
            ShapeType.BOX,
            PhysicsAxis.Y,
            origin, 0);
        spawn(store,
            physicsStore,
            time,
            space.spaceRef(),
            ShapeType.SPHERE,
            PhysicsAxis.Y,
            origin, 2);
        spawn(store,
            physicsStore,
            time,
            space.spaceRef(),
            ShapeType.CAPSULE,
            PhysicsAxis.Y,
            origin, 4);
        spawn(store,
            physicsStore,
            time,
            space.spaceRef(),
            ShapeType.CYLINDER,
            PhysicsAxis.Y,
            origin, 6);
        spawn(store,
            physicsStore,
            time,
            space.spaceRef(),
            ShapeType.CONE,
            PhysicsAxis.Y,
            origin, 8);

        ctx.sender().sendMessage(Message.raw("Spawned shape demo."));
        return CompletableFuture.completedFuture(null);
    }

    private static void spawn(@Nonnull Store<EntityStore> store,
        @Nonnull Store<PhysicsStore> physicsStore,
        @Nonnull TimeResource time,
        @Nonnull Ref<PhysicsStore> spaceRef,
        @Nonnull ShapeType type,
        @Nonnull PhysicsAxis axis,
        @Nonnull Vector3d origin,
        int xOffset) {
        Vector3d position = new Vector3d(origin).add(xOffset, 0.0, 0.0);
        UUID bodyUuid = UUID.randomUUID();
        var bodyHolder = PhysicsBodyEntities.dynamicBodyHolder(spaceRef,
            bodyUuid,
            ExamplePhysicsUtils.toVector3f(position),
            shape(type, axis),
            1.0f,
            RigidBodySpawnSettings.material(0.7f, 0.35f),
            null);
        Ref<PhysicsStore> bodyRef = physicsStore.addEntity(bodyHolder, AddReason.SPAWN);
        assert bodyRef != null;
        store.addEntity(ExamplePhysicsUtils.attachedPhysicsBlockEntityHolder(
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
