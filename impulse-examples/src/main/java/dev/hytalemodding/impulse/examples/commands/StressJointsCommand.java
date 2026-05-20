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
import dev.hytalemodding.impulse.api.PhysicsBody;
import dev.hytalemodding.impulse.api.PhysicsJoint;
import dev.hytalemodding.impulse.api.PhysicsSpace;
import dev.hytalemodding.impulse.core.resources.PhysicsWorldResource;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nonnull;
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
        PhysicsWorldResource resource = ExamplePhysicsUtils.resource(store);
        PhysicsSpace space = ExamplePhysicsUtils.defaultSpace(ctx, resource);
        if (space == null) {
            return CompletableFuture.completedFuture(null);
        }
        TimeResource time = store.getResource(TimeResource.getResourceType());

        Vector3d origin = new Vector3d(playerPos).add(-totalJoints * 0.1, 7.0, 5.0);
        int createdJoints = 0;
        int createdBodies = 0;
        int baseJointsPerRow = totalJoints / ROWS;
        int remainder = totalJoints % ROWS;
        for (int row = 0; row < ROWS; row++) {
            int rowJoints = baseJointsPerRow + (row < remainder ? 1 : 0);
            if (rowJoints <= 0) {
                continue;
            }

            Vector3d rowOrigin = new Vector3d(origin).add(0.0, 0.0, row * ROW_SPACING);
            createdBodies += createRow(store, time, resource, space, rowOrigin, rowJoints, row);
            createdJoints += rowJoints;
        }

        ctx.sender().sendMessage(Message.raw("Spawned " + createdJoints
            + " stress joints as separate fixed/point/hinge/slider/spring rows with "
            + createdBodies + " bodies."));
        return CompletableFuture.completedFuture(null);
    }

    private static int createRow(@Nonnull Store<EntityStore> store,
        @Nonnull TimeResource time,
        @Nonnull PhysicsWorldResource resource,
        @Nonnull PhysicsSpace space,
        @Nonnull Vector3d origin,
        int jointCount,
        int jointType) {
        double spacing = jointType == 4 ? TOUCHING_SPACING + SPRING_REST_LENGTH
            : TOUCHING_SPACING;
        PhysicsBody previous = spawnBox(store, time, resource, space, origin, 0.0f);
        int bodies = 1;

        for (int i = 0; i < jointCount; i++) {
            PhysicsBody current = spawnBox(store, time, resource, space,
                new Vector3d(origin).add((i + 1) * spacing, 0.0, 0.0), 1.0f);
            createJoint(space, previous, current, jointType);
            if (jointType == 1 && i % 5 == 0) {
                current.setLinearVelocity(0.0f, 0.0f, 1.0f);
            } else if (jointType == 4 && i % 3 == 0) {
                current.setLinearVelocity(0.4f, 0.0f, 0.8f);
            }
            previous = current;
            bodies++;
        }
        return bodies;
    }

    private static PhysicsBody spawnBox(@Nonnull Store<EntityStore> store,
        @Nonnull TimeResource time,
        @Nonnull PhysicsWorldResource resource,
        @Nonnull PhysicsSpace space,
        @Nonnull Vector3d position,
        float mass) {
        PhysicsBody body = space.createBox(HALF_SIZE, HALF_SIZE, HALF_SIZE, mass);
        body.setFriction(0.65f);
        body.setRestitution(0.1f);
        ExamplePhysicsUtils.spawnBlockBody(store, time, resource, space.getId(), space, body, position);
        return body;
    }

    private static void createJoint(@Nonnull PhysicsSpace space,
        @Nonnull PhysicsBody previous,
        @Nonnull PhysicsBody current,
        int jointType) {
        Vector3f previousAnchor = new Vector3f(HALF_SIZE, 0.0f, 0.0f);
        Vector3f currentAnchor = new Vector3f(-HALF_SIZE, 0.0f, 0.0f);
        PhysicsJoint joint = switch (jointType) {
            case 0 -> space.createFixedJoint(previous, current, previousAnchor, currentAnchor);
            case 1 -> space.createPointJoint(previous, current, previousAnchor, currentAnchor);
            case 2 -> space.createHingeJoint(previous, current, previousAnchor, currentAnchor,
                new Vector3f(0.0f, 0.0f, 1.0f));
            case 3 -> space.createSliderJoint(previous, current, previousAnchor, currentAnchor,
                new Vector3f(1.0f, 0.0f, 0.0f));
            default -> space.createSpringJoint(previous, current, previousAnchor, currentAnchor,
                SPRING_REST_LENGTH, 18.0f, 2.0f);
        };

        if (jointType == 2) {
            joint.setLimits(-0.8f, 0.8f);
            joint.setMotor(0.6f, 2.0f);
            joint.setMotorEnabled(true);
        } else if (jointType == 3) {
            joint.setLimits(-0.35f, 0.35f);
            joint.setMotor(0.4f, 2.0f);
            joint.setMotorEnabled(true);
        }
    }
}
