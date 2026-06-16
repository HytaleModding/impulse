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
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.JointComponent;
import dev.hytalemodding.impulse.core.plugin.simulation.JointType;
import dev.hytalemodding.impulse.core.plugin.simulation.PhysicsShapeSpec;
import dev.hytalemodding.impulse.core.plugin.simulation.RigidBodySpawnSettings;
import dev.hytalemodding.impulse.examples.commands.ExamplePhysicsUtils.CreatedBlockBody;
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
        Vector3d playerPos = new Vector3d(playerRef.getTransform().getPosition());

        SpaceId spaceId = ExamplePhysicsUtils.spaceId(ctx, world, spaceArg);
        if (spaceId == null) {
            return CompletableFuture.completedFuture(null);
        }
        TimeResource time = store.getResource(TimeResource.getResourceType());

        Vector3d origin = new Vector3d(playerPos).add(-5.0, 5.0, 5.0);
        List<CreatedBlockBody> createdBodies = tryCreatePhysicsStoreDemo(world,
            spaceId,
            new Vector3d(origin));
        if (createdBodies == null) {
            ctx.sender().sendMessage(Message.raw(
                "Cannot spawn joint demo because the target space is not bound in PhysicsStore."));
            return CompletableFuture.completedFuture(null);
        }
        for (CreatedBlockBody created : createdBodies) {
            ExamplePhysicsUtils.attachPhysicsStoreBlockBody(store, time, created);
        }

        ctx.sender().sendMessage(Message.raw(
            "Spawned joint demo: fixed, point, hinge, slider, and spring."));
        return CompletableFuture.completedFuture(null);
    }

    @Nullable
    private static List<CreatedBlockBody> tryCreatePhysicsStoreDemo(@Nonnull World world,
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

        List<CreatedBlockBody> createdBodies = new ArrayList<>(10);
        try {
            createFixed(createdBodies, world, spaceUuid, spaceId, new Vector3d(origin));
            createPoint(createdBodies, world, spaceUuid, spaceId, new Vector3d(origin).add(2.5, 0.0, 0.0));
            createHinge(createdBodies, world, spaceUuid, spaceId, new Vector3d(origin).add(5.0, 0.0, 0.0));
            createSlider(createdBodies, world, spaceUuid, spaceId, new Vector3d(origin).add(7.5, 0.0, 0.0));
            createSpring(createdBodies, world, spaceUuid, spaceId, new Vector3d(origin).add(10.0, 0.0, 0.0));
        } catch (IllegalStateException exception) {
            return null;
        }
        return createdBodies;
    }

    private static void createFixed(@Nonnull List<CreatedBlockBody> createdBodies,
        @Nonnull World world,
        @Nonnull UUID spaceUuid,
        @Nonnull SpaceId spaceId,
        @Nonnull Vector3d origin) {
        UUID anchorUuid = spawnBox(createdBodies, world, spaceUuid, spaceId, origin, 0.0f);
        UUID childUuid = spawnBox(createdBodies, world, spaceUuid, spaceId,
            new Vector3d(origin).add(0.0, -TOUCHING_SPACING, 0.0), 1.0f);
        ExamplePhysicsUtils.addPhysicsStoreJoint(world,
            UUID.randomUUID(),
            joint(spaceUuid,
                anchorUuid,
                childUuid,
                JointType.FIXED,
                new Vector3f(0.0f, -HALF_SIZE, 0.0f),
                new Vector3f(0.0f, HALF_SIZE, 0.0f),
                new Vector3f()));
    }

    private static void createPoint(@Nonnull List<CreatedBlockBody> createdBodies,
        @Nonnull World world,
        @Nonnull UUID spaceUuid,
        @Nonnull SpaceId spaceId,
        @Nonnull Vector3d origin) {
        UUID anchorUuid = spawnBox(createdBodies, world, spaceUuid, spaceId, origin, 0.0f);
        UUID bobUuid = spawnBox(createdBodies,
            world,
            spaceUuid,
            spaceId,
            new Vector3d(origin).add(0.0, -TOUCHING_SPACING, 0.0),
            1.0f,
            new Vector3f(1.5f, 0.0f, 0.0f));
        ExamplePhysicsUtils.addPhysicsStoreJoint(world,
            UUID.randomUUID(),
            joint(spaceUuid,
                anchorUuid,
                bobUuid,
                JointType.POINT,
                new Vector3f(0.0f, -HALF_SIZE, 0.0f),
                new Vector3f(0.0f, HALF_SIZE, 0.0f),
                new Vector3f()));
    }

    private static void createHinge(@Nonnull List<CreatedBlockBody> createdBodies,
        @Nonnull World world,
        @Nonnull UUID spaceUuid,
        @Nonnull SpaceId spaceId,
        @Nonnull Vector3d origin) {
        UUID anchorUuid = spawnBox(createdBodies, world, spaceUuid, spaceId, origin, 0.0f);
        UUID armUuid = spawnBox(createdBodies, world, spaceUuid, spaceId,
            new Vector3d(origin).add(0.0, -TOUCHING_SPACING, 0.0), 1.0f);
        JointComponent joint = joint(spaceUuid,
            anchorUuid,
            armUuid,
            JointType.HINGE,
            new Vector3f(0.0f, -HALF_SIZE, 0.0f),
            new Vector3f(0.0f, HALF_SIZE, 0.0f),
            new Vector3f(0.0f, 0.0f, 1.0f));
        joint.setLowerLimit(-1.2f);
        joint.setUpperLimit(1.2f);
        joint.setMotorEnabled(true);
        joint.setMotorTargetVelocity(1.5f);
        joint.setMotorMaxForce(3.0f);
        ExamplePhysicsUtils.addPhysicsStoreJoint(world, UUID.randomUUID(), joint);
    }

    private static void createSlider(@Nonnull List<CreatedBlockBody> createdBodies,
        @Nonnull World world,
        @Nonnull UUID spaceUuid,
        @Nonnull SpaceId spaceId,
        @Nonnull Vector3d origin) {
        UUID anchorUuid = spawnBox(createdBodies, world, spaceUuid, spaceId, origin, 0.0f);
        UUID blockUuid = spawnBox(createdBodies, world, spaceUuid, spaceId,
            new Vector3d(origin).add(TOUCHING_SPACING, 0.0, 0.0), 1.0f);
        JointComponent joint = joint(spaceUuid,
            anchorUuid,
            blockUuid,
            JointType.SLIDER,
            new Vector3f(HALF_SIZE, 0.0f, 0.0f),
            new Vector3f(-HALF_SIZE, 0.0f, 0.0f),
            new Vector3f(1.0f, 0.0f, 0.0f));
        joint.setLowerLimit(-1.0f);
        joint.setUpperLimit(1.0f);
        joint.setMotorEnabled(true);
        joint.setMotorTargetVelocity(1.0f);
        joint.setMotorMaxForce(4.0f);
        ExamplePhysicsUtils.addPhysicsStoreJoint(world, UUID.randomUUID(), joint);
    }

    private static void createSpring(@Nonnull List<CreatedBlockBody> createdBodies,
        @Nonnull World world,
        @Nonnull UUID spaceUuid,
        @Nonnull SpaceId spaceId,
        @Nonnull Vector3d origin) {
        UUID anchorUuid = spawnBox(createdBodies, world, spaceUuid, spaceId, origin, 0.0f);
        UUID bobUuid = spawnBox(createdBodies,
            world,
            spaceUuid,
            spaceId,
            new Vector3d(origin).add(0.0, -(TOUCHING_SPACING + SPRING_REST_LENGTH), 0.0),
            1.0f,
            new Vector3f(1.0f, 0.0f, 0.0f));
        JointComponent joint = joint(spaceUuid,
            anchorUuid,
            bobUuid,
            JointType.SPRING,
            new Vector3f(0.0f, -HALF_SIZE, 0.0f),
            new Vector3f(0.0f, HALF_SIZE, 0.0f),
            new Vector3f());
        joint.setSpringRestLength(SPRING_REST_LENGTH);
        joint.setSpringStiffness(20.0f);
        joint.setSpringDamping(2.0f);
        ExamplePhysicsUtils.addPhysicsStoreJoint(world, UUID.randomUUID(), joint);
    }

    private static UUID spawnBox(@Nonnull List<CreatedBlockBody> createdBodies,
        @Nonnull World world,
        @Nonnull UUID spaceUuid,
        @Nonnull SpaceId spaceId,
        @Nonnull Vector3d position,
        float mass) {
        return spawnBox(createdBodies, world, spaceUuid, spaceId, position, mass, null);
    }

    private static UUID spawnBox(@Nonnull List<CreatedBlockBody> createdBodies,
        @Nonnull World world,
        @Nonnull UUID spaceUuid,
        @Nonnull SpaceId spaceId,
        @Nonnull Vector3d position,
        float mass,
        @Nullable Vector3f linearVelocity) {
        UUID bodyUuid = UUID.randomUUID();
        var bodyRef = ExamplePhysicsUtils.addPhysicsStoreBody(world,
            ExamplePhysicsUtils.bodyRow(spaceUuid,
                bodyUuid,
                ExamplePhysicsUtils.toVector3f(position),
                PhysicsShapeSpec.box(HALF_SIZE, HALF_SIZE, HALF_SIZE),
                mass,
                RigidBodySpawnSettings.material(0.6f, 0.15f),
                linearVelocity));
        createdBodies.add(new CreatedBlockBody(bodyUuid,
            bodyRef,
            spaceId,
            ExamplePhysicsUtils.DEFAULT_BLOCK_TYPE,
            (float) position.x,
            (float) position.y,
            (float) position.z,
            mass > 0.0f));
        return bodyUuid;
    }

    @Nonnull
    private static JointComponent joint(@Nonnull UUID spaceUuid,
        @Nonnull UUID bodyAUuid,
        @Nonnull UUID bodyBUuid,
        @Nonnull JointType type,
        @Nonnull Vector3f anchorA,
        @Nonnull Vector3f anchorB,
        @Nonnull Vector3f axis) {
        JointComponent joint = new JointComponent();
        joint.setSpaceUuid(spaceUuid);
        joint.setBodyAUuid(bodyAUuid);
        joint.setBodyBUuid(bodyBUuid);
        joint.setType(type);
        joint.setAnchorA(anchorA);
        joint.setAnchorB(anchorB);
        joint.setAxis(axis);
        joint.setEnabled(true);
        return joint;
    }

}
