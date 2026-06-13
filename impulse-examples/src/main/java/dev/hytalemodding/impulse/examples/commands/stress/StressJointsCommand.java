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
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import dev.hytalemodding.impulse.core.plugin.joint.JointKey;
import dev.hytalemodding.impulse.core.plugin.physicsstore.PhysicsStoreAccess;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.JointComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.requests.JointUpsertRequest;
import dev.hytalemodding.impulse.core.plugin.physicsstore.requests.PhysicsStoreRequest;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsWorldResource;
import dev.hytalemodding.impulse.core.plugin.simulation.JointType;
import dev.hytalemodding.impulse.core.plugin.simulation.PhysicsShapeSpec;
import dev.hytalemodding.impulse.core.plugin.simulation.RigidBodySpawnSettings;
import dev.hytalemodding.impulse.examples.commands.ExamplePhysicsUtils;
import dev.hytalemodding.impulse.examples.commands.ExamplePhysicsUtils.PendingBlockBody;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3f;

/**
 * Builds separate joint rows so backend differences are easier to isolate.
 * Every row starts with matching body spacing and local anchors, avoiding correction from an
 * already invalid initial pose.
 */
public class StressJointsCommand extends AbstractAsyncPlayerCommand {

    private static final int DEFAULT_JOINTS = 32;
    private static final int MAX_JOINTS = 1000;
    private static final int ROWS = 5;
    private static final float HALF_SIZE = 0.45f;
    private static final float TOUCHING_SPACING = HALF_SIZE * 2.0f;
    private static final float SPRING_REST_LENGTH = 0.7f;
    private static final double ROW_SPACING = 2.2;

    private final OptionalArg<Integer> countArg = this.withOptionalArg(
        "count",
        "Total number of joints to create across five rows",
        ArgTypes.INTEGER);
    private final OptionalArg<String> blockTypeArg = this.withOptionalArg(
        "blockType",
        "Hytale block type for stress joint body visuals",
        ArgTypes.STRING);
    private final OptionalArg<Integer> spaceArg = this.withOptionalArg(
        "space",
        "Physics space id to target",
        ArgTypes.INTEGER);

    public StressJointsCommand() {
        super("joints", "Spawn separate fixed, point, hinge, slider, and spring stress rows");
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

        int totalJoints = ExamplePhysicsUtils.optionalInt(ctx, countArg, DEFAULT_JOINTS, 1,
            MAX_JOINTS);
        String blockType = blockType(ctx);
        PhysicsWorldResource resource = ExamplePhysicsUtils.resource(store);
        SpaceId spaceId = ExamplePhysicsUtils.spaceId(ctx, resource, spaceArg);
        if (spaceId == null) {
            return CompletableFuture.completedFuture(null);
        }
        TimeResource time = store.getResource(TimeResource.getResourceType());
        UUID spaceUuid;
        try {
            spaceUuid = PhysicsStoreAccess.resolveSpaceUuid(world, spaceId);
        } catch (IllegalStateException exception) {
            spaceUuid = null;
        }
        if (spaceUuid == null) {
            ctx.sender().sendMessage(Message.raw(
                "Cannot queue stress joint demo because the target space is not bound in PhysicsStore."));
            return CompletableFuture.completedFuture(null);
        }

        Vector3d origin = new Vector3d(playerPos).add(-totalJoints * 0.1, 7.0, 5.0);
        int createdJoints = 0;
        int createdBodies = 0;
        List<PendingBlockBody> pendingBodies = new ArrayList<>(totalJoints + ROWS);
        List<PhysicsStoreRequest> requests = new ArrayList<>(totalJoints * 2 + ROWS);
        int baseJointsPerRow = totalJoints / ROWS;
        int remainder = totalJoints % ROWS;
        for (int row = 0; row < ROWS; row++) {
            int rowJoints = baseJointsPerRow + (row < remainder ? 1 : 0);
            if (rowJoints <= 0) {
                continue;
            }

            Vector3d rowOrigin = new Vector3d(origin).add(0.0, 0.0, row * ROW_SPACING);
            createdBodies += appendRow(pendingBodies,
                requests,
                spaceUuid,
                spaceId,
                rowOrigin,
                rowJoints,
                row,
                blockType);
            createdJoints += rowJoints;
        }
        try {
            PhysicsStoreAccess.enqueueAll(world, requests);
        } catch (IllegalStateException exception) {
            ctx.sender().sendMessage(Message.raw("Cannot queue stress joint demo: " + exception.getMessage()));
            return CompletableFuture.completedFuture(null);
        }
        for (PendingBlockBody pendingBody : pendingBodies) {
            ExamplePhysicsUtils.attachPhysicsStoreBlockBody(store, time, pendingBody);
        }

        ctx.sender().sendMessage(Message.raw("Queued " + createdJoints
            + " stress joints across fixed/point/hinge/slider/spring rows with "
            + createdBodies + " bodies and attached visuals. blockType=" + blockType + "."));
        return CompletableFuture.completedFuture(null);
    }

    private static int appendRow(@Nonnull List<PendingBlockBody> pendingBodies,
        @Nonnull List<PhysicsStoreRequest> requests,
        @Nonnull UUID spaceUuid,
        @Nonnull SpaceId spaceId,
        @Nonnull Vector3d origin,
        int jointCount,
        int jointType,
        @Nonnull String blockType) {
        double spacing = jointType == 4 ? TOUCHING_SPACING + SPRING_REST_LENGTH
            : TOUCHING_SPACING;
        int bodyCount = jointCount + 1;
        RigidBodyKey[] bodyKeys = new RigidBodyKey[bodyCount];
        float[] positions = new float[bodyCount * 3];
        long bodyKeyRunId = RigidBodyKey.random().mostSignificantBits();
        long jointKeyRunId = JointKey.random().mostSignificantBits();
        PhysicsShapeSpec box = PhysicsShapeSpec.box(HALF_SIZE, HALF_SIZE, HALF_SIZE);
        RigidBodySpawnSettings spawnSettings = RigidBodySpawnSettings.material(0.65f, 0.1f);

        for (int i = 0; i < bodyCount; i++) {
            RigidBodyKey bodyKey = RigidBodyKey.of(bodyKeyRunId, i + 1L);
            bodyKeys[i] = bodyKey;
            int positionOffset = i * 3;
            positions[positionOffset] = (float) (origin.x + i * spacing);
            positions[positionOffset + 1] = (float) origin.y;
            positions[positionOffset + 2] = (float) origin.z;
            float mass = i == 0 ? 0.0f : 1.0f;
            requests.add(ExamplePhysicsUtils.bodyUpsertRequest(spaceUuid,
                bodyKey.value(),
                new Vector3f(positions[positionOffset],
                    positions[positionOffset + 1],
                    positions[positionOffset + 2]),
                box,
                mass,
                spawnSettings,
                initialVelocity(jointType, i)));
            pendingBodies.add(new PendingBlockBody(
                bodyKeys[i],
                spaceId,
                blockType,
                positions[positionOffset],
                positions[positionOffset + 1],
                positions[positionOffset + 2],
                i > 0));
        }
        for (int i = 0; i < jointCount; i++) {
            requests.add(JointUpsertRequest.of(JointKey.of(jointKeyRunId, i + 1L).value(),
                joint(spaceUuid, bodyKeys[i], bodyKeys[i + 1], jointType)));
        }
        return bodyCount;
    }

    @Nonnull
    private String blockType(@Nonnull CommandContext ctx) {
        return blockTypeArg.provided(ctx)
            ? ExamplePhysicsUtils.resolveBlockType(blockTypeArg.get(ctx))
            : ExamplePhysicsUtils.DEFAULT_BLOCK_TYPE;
    }

    @Nonnull
    private static JointComponent joint(@Nonnull UUID spaceUuid,
        @Nonnull RigidBodyKey previousKey,
        @Nonnull RigidBodyKey currentKey,
        int jointType) {
        JointType type = switch (jointType) {
            case 0 -> JointType.FIXED;
            case 1 -> JointType.POINT;
            case 2 -> JointType.HINGE;
            case 3 -> JointType.SLIDER;
            default -> JointType.SPRING;
        };
        JointComponent joint = new JointComponent();
        joint.setSpaceUuid(spaceUuid);
        joint.setBodyAUuid(previousKey.value());
        joint.setBodyBUuid(currentKey.value());
        joint.setType(type);
        joint.setAnchorA(new Vector3f(HALF_SIZE, 0.0f, 0.0f));
        joint.setAnchorB(new Vector3f(-HALF_SIZE, 0.0f, 0.0f));
        joint.setEnabled(true);
        switch (type) {
            case FIXED, POINT -> {
            }
            case HINGE -> {
                joint.setAxis(new Vector3f(0.0f, 0.0f, 1.0f));
                joint.setLowerLimit(-0.8f);
                joint.setUpperLimit(0.8f);
                joint.setMotorEnabled(true);
                joint.setMotorTargetVelocity(0.6f);
                joint.setMotorMaxForce(2.0f);
            }
            case SLIDER -> {
                joint.setAxis(new Vector3f(1.0f, 0.0f, 0.0f));
                joint.setLowerLimit(-0.35f);
                joint.setUpperLimit(0.35f);
                joint.setMotorEnabled(true);
                joint.setMotorTargetVelocity(0.4f);
                joint.setMotorMaxForce(2.0f);
            }
            case SPRING -> {
                joint.setSpringRestLength(SPRING_REST_LENGTH);
                joint.setSpringStiffness(18.0f);
                joint.setSpringDamping(2.0f);
            }
        }
        return joint;
    }

    @Nullable
    private static Vector3f initialVelocity(int jointType, int bodyIndex) {
        if (bodyIndex <= 0) {
            return null;
        }
        int jointIndex = bodyIndex - 1;
        if (jointType == 1 && jointIndex % 5 == 0) {
            return new Vector3f(0.0f, 0.0f, 1.0f);
        }
        if (jointType == 4 && jointIndex % 3 == 0) {
            return new Vector3f(0.4f, 0.0f, 0.8f);
        }
        return null;
    }
}
