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
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.JointComponent;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsWorldResource;
import dev.hytalemodding.impulse.core.plugin.simulation.JointType;
import dev.hytalemodding.impulse.core.plugin.simulation.PhysicsShapeSpec;
import dev.hytalemodding.impulse.core.plugin.simulation.RigidBodySpawnSettings;
import dev.hytalemodding.impulse.examples.commands.ExamplePhysicsUtils.PendingBlockBody;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
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
        List<PendingBlockBody> pendingBodies = tryCreatePhysicsStoreDemo(world,
            spaceId,
            new Vector3d(origin));
        if (pendingBodies == null) {
            ctx.sender().sendMessage(Message.raw(
                "Cannot spawn joint demo because the target space is not bound in PhysicsStore."));
            return CompletableFuture.completedFuture(null);
        }
        for (PendingBlockBody pending : pendingBodies) {
            ExamplePhysicsUtils.attachPhysicsStoreBlockBody(store, time, pending);
        }

        ctx.sender().sendMessage(Message.raw(
            "Spawned joint demo: fixed, point, hinge, slider, and spring."));
        return CompletableFuture.completedFuture(null);
    }

    @Nullable
    private static List<PendingBlockBody> tryCreatePhysicsStoreDemo(@Nonnull World world,
        @Nonnull SpaceId spaceId,
        @Nonnull Vector3d origin) {
        UUID spaceUuid;
        try {
            spaceUuid = ExamplePhysicsUtils.resolvePhysicsStoreSpaceUuid(world, spaceId);
        } catch (IllegalStateException exception) {
            return null;
        }
        if (spaceUuid == null) {
            return null;
        }

        List<PendingBlockBody> pendingBodies = new ArrayList<>(10);
        try {
            createFixed(pendingBodies, world, spaceUuid, spaceId, new Vector3d(origin));
            createPoint(pendingBodies, world, spaceUuid, spaceId, new Vector3d(origin).add(2.5, 0.0, 0.0));
            createHinge(pendingBodies, world, spaceUuid, spaceId, new Vector3d(origin).add(5.0, 0.0, 0.0));
            createSlider(pendingBodies, world, spaceUuid, spaceId, new Vector3d(origin).add(7.5, 0.0, 0.0));
            createSpring(pendingBodies, world, spaceUuid, spaceId, new Vector3d(origin).add(10.0, 0.0, 0.0));
        } catch (IllegalStateException exception) {
            return null;
        }
        return pendingBodies;
    }

    private static void createFixed(@Nonnull List<PendingBlockBody> pendingBodies,
        @Nonnull World world,
        @Nonnull UUID spaceUuid,
        @Nonnull SpaceId spaceId,
        @Nonnull Vector3d origin) {
        RigidBodyKey anchorKey = spawnBox(pendingBodies, world, spaceUuid, spaceId, origin, 0.0f);
        RigidBodyKey childKey = spawnBox(pendingBodies, world, spaceUuid, spaceId,
            new Vector3d(origin).add(0.0, -TOUCHING_SPACING, 0.0), 1.0f);
        ExamplePhysicsUtils.addPhysicsStoreJoint(world,
            JointKey.random().value(),
            joint(spaceUuid,
                anchorKey,
                childKey,
                JointType.FIXED,
                new Vector3f(0.0f, -HALF_SIZE, 0.0f),
                new Vector3f(0.0f, HALF_SIZE, 0.0f),
                new Vector3f()));
    }

    private static void createPoint(@Nonnull List<PendingBlockBody> pendingBodies,
        @Nonnull World world,
        @Nonnull UUID spaceUuid,
        @Nonnull SpaceId spaceId,
        @Nonnull Vector3d origin) {
        RigidBodyKey anchorKey = spawnBox(pendingBodies, world, spaceUuid, spaceId, origin, 0.0f);
        RigidBodyKey bobKey = spawnBox(pendingBodies,
            world,
            spaceUuid,
            spaceId,
            new Vector3d(origin).add(0.0, -TOUCHING_SPACING, 0.0),
            1.0f,
            new Vector3f(1.5f, 0.0f, 0.0f));
        ExamplePhysicsUtils.addPhysicsStoreJoint(world,
            JointKey.random().value(),
            joint(spaceUuid,
                anchorKey,
                bobKey,
                JointType.POINT,
                new Vector3f(0.0f, -HALF_SIZE, 0.0f),
                new Vector3f(0.0f, HALF_SIZE, 0.0f),
                new Vector3f()));
    }

    private static void createHinge(@Nonnull List<PendingBlockBody> pendingBodies,
        @Nonnull World world,
        @Nonnull UUID spaceUuid,
        @Nonnull SpaceId spaceId,
        @Nonnull Vector3d origin) {
        RigidBodyKey anchorKey = spawnBox(pendingBodies, world, spaceUuid, spaceId, origin, 0.0f);
        RigidBodyKey armKey = spawnBox(pendingBodies, world, spaceUuid, spaceId,
            new Vector3d(origin).add(0.0, -TOUCHING_SPACING, 0.0), 1.0f);
        JointComponent joint = joint(spaceUuid,
            anchorKey,
            armKey,
            JointType.HINGE,
            new Vector3f(0.0f, -HALF_SIZE, 0.0f),
            new Vector3f(0.0f, HALF_SIZE, 0.0f),
            new Vector3f(0.0f, 0.0f, 1.0f));
        joint.setLowerLimit(-1.2f);
        joint.setUpperLimit(1.2f);
        joint.setMotorEnabled(true);
        joint.setMotorTargetVelocity(1.5f);
        joint.setMotorMaxForce(3.0f);
        ExamplePhysicsUtils.addPhysicsStoreJoint(world, JointKey.random().value(), joint);
    }

    private static void createSlider(@Nonnull List<PendingBlockBody> pendingBodies,
        @Nonnull World world,
        @Nonnull UUID spaceUuid,
        @Nonnull SpaceId spaceId,
        @Nonnull Vector3d origin) {
        RigidBodyKey anchorKey = spawnBox(pendingBodies, world, spaceUuid, spaceId, origin, 0.0f);
        RigidBodyKey blockKey = spawnBox(pendingBodies, world, spaceUuid, spaceId,
            new Vector3d(origin).add(TOUCHING_SPACING, 0.0, 0.0), 1.0f);
        JointComponent joint = joint(spaceUuid,
            anchorKey,
            blockKey,
            JointType.SLIDER,
            new Vector3f(HALF_SIZE, 0.0f, 0.0f),
            new Vector3f(-HALF_SIZE, 0.0f, 0.0f),
            new Vector3f(1.0f, 0.0f, 0.0f));
        joint.setLowerLimit(-1.0f);
        joint.setUpperLimit(1.0f);
        joint.setMotorEnabled(true);
        joint.setMotorTargetVelocity(1.0f);
        joint.setMotorMaxForce(4.0f);
        ExamplePhysicsUtils.addPhysicsStoreJoint(world, JointKey.random().value(), joint);
    }

    private static void createSpring(@Nonnull List<PendingBlockBody> pendingBodies,
        @Nonnull World world,
        @Nonnull UUID spaceUuid,
        @Nonnull SpaceId spaceId,
        @Nonnull Vector3d origin) {
        RigidBodyKey anchorKey = spawnBox(pendingBodies, world, spaceUuid, spaceId, origin, 0.0f);
        RigidBodyKey bobKey = spawnBox(pendingBodies,
            world,
            spaceUuid,
            spaceId,
            new Vector3d(origin).add(0.0, -(TOUCHING_SPACING + SPRING_REST_LENGTH), 0.0),
            1.0f,
            new Vector3f(1.0f, 0.0f, 0.0f));
        JointComponent joint = joint(spaceUuid,
            anchorKey,
            bobKey,
            JointType.SPRING,
            new Vector3f(0.0f, -HALF_SIZE, 0.0f),
            new Vector3f(0.0f, HALF_SIZE, 0.0f),
            new Vector3f());
        joint.setSpringRestLength(SPRING_REST_LENGTH);
        joint.setSpringStiffness(20.0f);
        joint.setSpringDamping(2.0f);
        ExamplePhysicsUtils.addPhysicsStoreJoint(world, JointKey.random().value(), joint);
    }

    private static RigidBodyKey spawnBox(@Nonnull List<PendingBlockBody> pendingBodies,
        @Nonnull World world,
        @Nonnull UUID spaceUuid,
        @Nonnull SpaceId spaceId,
        @Nonnull Vector3d position,
        float mass) {
        return spawnBox(pendingBodies, world, spaceUuid, spaceId, position, mass, null);
    }

    private static RigidBodyKey spawnBox(@Nonnull List<PendingBlockBody> pendingBodies,
        @Nonnull World world,
        @Nonnull UUID spaceUuid,
        @Nonnull SpaceId spaceId,
        @Nonnull Vector3d position,
        float mass,
        @Nullable Vector3f linearVelocity) {
        RigidBodyKey bodyKey = RigidBodyKey.random();
        ExamplePhysicsUtils.addPhysicsStoreBody(world,
            ExamplePhysicsUtils.bodyUpsertRequest(spaceUuid,
                bodyKey.value(),
                ExamplePhysicsUtils.toVector3f(position),
                PhysicsShapeSpec.box(HALF_SIZE, HALF_SIZE, HALF_SIZE),
                mass,
                RigidBodySpawnSettings.material(0.6f, 0.15f),
                linearVelocity));
        pendingBodies.add(new PendingBlockBody(bodyKey,
            spaceId,
            ExamplePhysicsUtils.DEFAULT_BLOCK_TYPE,
            (float) position.x,
            (float) position.y,
            (float) position.z,
            mass > 0.0f));
        return bodyKey;
    }

    @Nonnull
    private static JointComponent joint(@Nonnull UUID spaceUuid,
        @Nonnull RigidBodyKey bodyA,
        @Nonnull RigidBodyKey bodyB,
        @Nonnull JointType type,
        @Nonnull Vector3f anchorA,
        @Nonnull Vector3f anchorB,
        @Nonnull Vector3f axis) {
        JointComponent joint = new JointComponent();
        joint.setSpaceUuid(spaceUuid);
        joint.setBodyAUuid(bodyA.value());
        joint.setBodyBUuid(bodyB.value());
        joint.setType(type);
        joint.setAnchorA(anchorA);
        joint.setAnchorB(anchorB);
        joint.setAxis(axis);
        joint.setEnabled(true);
        return joint;
    }

}
