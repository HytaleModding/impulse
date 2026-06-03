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
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import dev.hytalemodding.impulse.core.plugin.joint.JointKey;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsWorldResource;
import dev.hytalemodding.impulse.core.plugin.simulation.recorder.PhysicsCommandRecorder;
import dev.hytalemodding.impulse.core.plugin.simulation.PhysicsShapeSpec;
import dev.hytalemodding.impulse.core.plugin.simulation.RigidBodySpawnSettings;
import dev.hytalemodding.impulse.examples.commands.ExamplePhysicsUtils.PendingBlockBody;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nonnull;
import org.joml.Vector3d;
import org.joml.Vector3f;

public class JointsCommand extends AbstractAsyncPlayerCommand {

    private static final float HALF_SIZE = 0.45f;
    private static final float TOUCHING_SPACING = HALF_SIZE * 2.0f;
    private static final float SPRING_REST_LENGTH = 1.2f;
    private final OptionalArg<Integer> spaceArg = this.withOptionalArg(
        "space",
        "Physics space id to target",
        ArgTypes.INTEGER);

    public JointsCommand() {
        super("joints", "Spawn fixed, point, hinge, slider, and spring joint examples");
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

        Vector3d origin = new Vector3d(playerPos).add(-5.0, 5.0, 5.0);
        List<PendingBlockBody> pendingBodies = new ArrayList<>(10);
        ExamplePhysicsUtils.requireApplied(resource.submitCommands(Math.max(0L, world.getTick()), 16, commands -> {
            createFixed(pendingBodies, commands, spaceId, new Vector3d(origin));
            createPoint(pendingBodies, commands, spaceId, new Vector3d(origin).add(2.5, 0.0, 0.0));
            createHinge(pendingBodies, commands, spaceId, new Vector3d(origin).add(5.0, 0.0, 0.0));
            createSlider(pendingBodies, commands, spaceId, new Vector3d(origin).add(7.5, 0.0, 0.0));
            createSpring(pendingBodies, commands, spaceId, new Vector3d(origin).add(10.0, 0.0, 0.0));
        }), "create joint demo");
        for (PendingBlockBody pending : pendingBodies) {
            ExamplePhysicsUtils.attachRecordedBlockBody(store, time, pending);
        }

        ctx.sender().sendMessage(Message.raw(
            "Spawned joint demo: fixed, point, hinge, slider, and spring."));
        return CompletableFuture.completedFuture(null);
    }

    private static void createFixed(@Nonnull List<PendingBlockBody> pendingBodies,
        @Nonnull PhysicsCommandRecorder commands,
        @Nonnull SpaceId spaceId,
        @Nonnull Vector3d origin) {
        RigidBodyKey anchorKey = spawnBox(pendingBodies, commands, spaceId, origin, 0.0f);
        RigidBodyKey childKey = spawnBox(pendingBodies, commands, spaceId,
            new Vector3d(origin).add(0.0, -TOUCHING_SPACING, 0.0), 1.0f);
        commands.joint(JointKey.random(), joint -> joint
            .space(spaceId)
            .bodies(anchorKey, childKey)
            .fixed(new Vector3f(0.0f, -HALF_SIZE, 0.0f),
                new Vector3f(0.0f, HALF_SIZE, 0.0f)));
    }

    private static void createPoint(@Nonnull List<PendingBlockBody> pendingBodies,
        @Nonnull PhysicsCommandRecorder commands,
        @Nonnull SpaceId spaceId,
        @Nonnull Vector3d origin) {
        RigidBodyKey anchorKey = spawnBox(pendingBodies, commands, spaceId, origin, 0.0f);
        RigidBodyKey bobKey = spawnBox(pendingBodies, commands, spaceId,
            new Vector3d(origin).add(0.0, -TOUCHING_SPACING, 0.0), 1.0f);
        commands.setBodyVelocity(bobKey, 1.5f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, true);
        commands.joint(JointKey.random(), joint -> joint
            .space(spaceId)
            .bodies(anchorKey, bobKey)
            .point(new Vector3f(0.0f, -HALF_SIZE, 0.0f),
                new Vector3f(0.0f, HALF_SIZE, 0.0f)));
    }

    private static void createHinge(@Nonnull List<PendingBlockBody> pendingBodies,
        @Nonnull PhysicsCommandRecorder commands,
        @Nonnull SpaceId spaceId,
        @Nonnull Vector3d origin) {
        RigidBodyKey anchorKey = spawnBox(pendingBodies, commands, spaceId, origin, 0.0f);
        RigidBodyKey armKey = spawnBox(pendingBodies, commands, spaceId,
            new Vector3d(origin).add(0.0, -TOUCHING_SPACING, 0.0), 1.0f);
        commands.joint(JointKey.random(), joint -> joint
            .space(spaceId)
            .bodies(anchorKey, armKey)
            .hinge(new Vector3f(0.0f, -HALF_SIZE, 0.0f),
                new Vector3f(0.0f, HALF_SIZE, 0.0f),
                new Vector3f(0.0f, 0.0f, 1.0f))
            .limits(-1.2f, 1.2f)
            .motor(1.5f, 3.0f));
    }

    private static void createSlider(@Nonnull List<PendingBlockBody> pendingBodies,
        @Nonnull PhysicsCommandRecorder commands,
        @Nonnull SpaceId spaceId,
        @Nonnull Vector3d origin) {
        RigidBodyKey anchorKey = spawnBox(pendingBodies, commands, spaceId, origin, 0.0f);
        RigidBodyKey blockKey = spawnBox(pendingBodies, commands, spaceId,
            new Vector3d(origin).add(TOUCHING_SPACING, 0.0, 0.0), 1.0f);
        commands.joint(JointKey.random(), joint -> joint
            .space(spaceId)
            .bodies(anchorKey, blockKey)
            .slider(new Vector3f(HALF_SIZE, 0.0f, 0.0f),
                new Vector3f(-HALF_SIZE, 0.0f, 0.0f),
                new Vector3f(1.0f, 0.0f, 0.0f))
            .limits(-1.0f, 1.0f)
            .motor(1.0f, 4.0f));
    }

    private static void createSpring(@Nonnull List<PendingBlockBody> pendingBodies,
        @Nonnull PhysicsCommandRecorder commands,
        @Nonnull SpaceId spaceId,
        @Nonnull Vector3d origin) {
        RigidBodyKey anchorKey = spawnBox(pendingBodies, commands, spaceId, origin, 0.0f);
        RigidBodyKey bobKey = spawnBox(pendingBodies, commands, spaceId,
            new Vector3d(origin).add(0.0, -(TOUCHING_SPACING + SPRING_REST_LENGTH), 0.0),
            1.0f);
        commands.setBodyVelocity(bobKey, 1.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, true);
        commands.joint(JointKey.random(), joint -> joint
            .space(spaceId)
            .bodies(anchorKey, bobKey)
            .spring(new Vector3f(0.0f, -HALF_SIZE, 0.0f),
                new Vector3f(0.0f, HALF_SIZE, 0.0f),
                SPRING_REST_LENGTH,
                20.0f,
                2.0f));
    }

    private static RigidBodyKey spawnBox(@Nonnull List<PendingBlockBody> pendingBodies,
        @Nonnull PhysicsCommandRecorder commands,
        @Nonnull SpaceId spaceId,
        @Nonnull Vector3d position,
        float mass) {
        PendingBlockBody pending = ExamplePhysicsUtils.recordBlockBodySpawn(commands,
            spaceId,
            position,
            PhysicsShapeSpec.box(HALF_SIZE, HALF_SIZE, HALF_SIZE),
            mass,
            RigidBodySpawnSettings.material(0.6f, 0.15f));
        pendingBodies.add(pending);
        return pending.bodyKey();
    }
}
