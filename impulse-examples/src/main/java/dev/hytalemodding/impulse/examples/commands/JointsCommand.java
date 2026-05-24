package dev.hytalemodding.impulse.examples.commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncPlayerCommand;
import com.hypixel.hytale.server.core.modules.time.TimeResource;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.api.PhysicsBody;
import dev.hytalemodding.impulse.api.PhysicsJoint;
import dev.hytalemodding.impulse.api.PhysicsSpace;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyId;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsWorldResource;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nonnull;
import org.joml.Vector3d;
import org.joml.Vector3f;

public class JointsCommand extends AbstractAsyncPlayerCommand {

    private static final float HALF_SIZE = 0.45f;
    private static final float TOUCHING_SPACING = HALF_SIZE * 2.0f;
    private static final float SPRING_REST_LENGTH = 1.2f;

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
        PhysicsSpace space = ExamplePhysicsUtils.defaultSpace(ctx, resource);
        if (space == null) {
            return CompletableFuture.completedFuture(null);
        }
        TimeResource time = store.getResource(TimeResource.getResourceType());

        Vector3d origin = new Vector3d(playerPos).add(-5.0, 5.0, 5.0);
        createFixed(store, time, resource, space, new Vector3d(origin));
        createPoint(store, time, resource, space, new Vector3d(origin).add(2.5, 0.0, 0.0));
        createHinge(store, time, resource, space, new Vector3d(origin).add(5.0, 0.0, 0.0));
        createSlider(store, time, resource, space, new Vector3d(origin).add(7.5, 0.0, 0.0));
        createSpring(store, time, resource, space, new Vector3d(origin).add(10.0, 0.0, 0.0));

        ctx.sender().sendMessage(Message.raw(
            "Spawned joint demo: fixed, point, hinge, slider, and spring."));
        return CompletableFuture.completedFuture(null);
    }

    private static void createFixed(@Nonnull Store<EntityStore> store,
        @Nonnull TimeResource time,
        @Nonnull PhysicsWorldResource resource,
        @Nonnull PhysicsSpace space,
        @Nonnull Vector3d origin) {
        PhysicsBodyId anchorId = spawnBox(store, time, resource, space, origin, 0.0f);
        PhysicsBodyId childId = spawnBox(store, time, resource, space,
            new Vector3d(origin).add(0.0, -TOUCHING_SPACING, 0.0), 1.0f);
        ExamplePhysicsUtils.physicsOwnerRun(store, "create fixed joint demo",
            () -> space.createFixedJoint(ExamplePhysicsUtils.requireLiveBody(resource, anchorId),
                ExamplePhysicsUtils.requireLiveBody(resource, childId),
                new Vector3f(0.0f, -HALF_SIZE, 0.0f),
                new Vector3f(0.0f, HALF_SIZE, 0.0f)));
    }

    private static void createPoint(@Nonnull Store<EntityStore> store,
        @Nonnull TimeResource time,
        @Nonnull PhysicsWorldResource resource,
        @Nonnull PhysicsSpace space,
        @Nonnull Vector3d origin) {
        PhysicsBodyId anchorId = spawnBox(store, time, resource, space, origin, 0.0f);
        PhysicsBodyId bobId = spawnBox(store, time, resource, space,
            new Vector3d(origin).add(0.0, -TOUCHING_SPACING, 0.0), 1.0f);
        ExamplePhysicsUtils.physicsOwnerRun(store, "create point joint demo",
            () -> {
                PhysicsBody anchor = ExamplePhysicsUtils.requireLiveBody(resource, anchorId);
                PhysicsBody bob = ExamplePhysicsUtils.requireLiveBody(resource, bobId);
                bob.setLinearVelocity(1.5f, 0.0f, 0.0f);
                space.createPointJoint(anchor, bob, new Vector3f(0.0f, -HALF_SIZE, 0.0f),
                    new Vector3f(0.0f, HALF_SIZE, 0.0f));
            });
    }

    private static void createHinge(@Nonnull Store<EntityStore> store,
        @Nonnull TimeResource time,
        @Nonnull PhysicsWorldResource resource,
        @Nonnull PhysicsSpace space,
        @Nonnull Vector3d origin) {
        PhysicsBodyId anchorId = spawnBox(store, time, resource, space, origin, 0.0f);
        PhysicsBodyId armId = spawnBox(store, time, resource, space,
            new Vector3d(origin).add(0.0, -TOUCHING_SPACING, 0.0), 1.0f);
        ExamplePhysicsUtils.physicsOwnerRun(store, "create hinge joint demo",
            () -> {
                PhysicsBody anchor = ExamplePhysicsUtils.requireLiveBody(resource, anchorId);
                PhysicsBody arm = ExamplePhysicsUtils.requireLiveBody(resource, armId);
                PhysicsJoint hinge = space.createHingeJoint(anchor, arm,
                    new Vector3f(0.0f, -HALF_SIZE, 0.0f),
                    new Vector3f(0.0f, HALF_SIZE, 0.0f),
                    new Vector3f(0.0f, 0.0f, 1.0f));
                hinge.setLimits(-1.2f, 1.2f);
                hinge.setMotor(1.5f, 3.0f);
                hinge.setMotorEnabled(true);
            });
    }

    private static void createSlider(@Nonnull Store<EntityStore> store,
        @Nonnull TimeResource time,
        @Nonnull PhysicsWorldResource resource,
        @Nonnull PhysicsSpace space,
        @Nonnull Vector3d origin) {
        PhysicsBodyId anchorId = spawnBox(store, time, resource, space, origin, 0.0f);
        PhysicsBodyId blockId = spawnBox(store, time, resource, space,
            new Vector3d(origin).add(TOUCHING_SPACING, 0.0, 0.0), 1.0f);
        ExamplePhysicsUtils.physicsOwnerRun(store, "create slider joint demo",
            () -> {
                PhysicsBody anchor = ExamplePhysicsUtils.requireLiveBody(resource, anchorId);
                PhysicsBody block = ExamplePhysicsUtils.requireLiveBody(resource, blockId);
                PhysicsJoint slider = space.createSliderJoint(anchor, block,
                    new Vector3f(HALF_SIZE, 0.0f, 0.0f),
                    new Vector3f(-HALF_SIZE, 0.0f, 0.0f),
                    new Vector3f(1.0f, 0.0f, 0.0f));
                slider.setLimits(-1.0f, 1.0f);
                slider.setMotor(1.0f, 4.0f);
                slider.setMotorEnabled(true);
            });
    }

    private static void createSpring(@Nonnull Store<EntityStore> store,
        @Nonnull TimeResource time,
        @Nonnull PhysicsWorldResource resource,
        @Nonnull PhysicsSpace space,
        @Nonnull Vector3d origin) {
        PhysicsBodyId anchorId = spawnBox(store, time, resource, space, origin, 0.0f);
        PhysicsBodyId bobId = spawnBox(store, time, resource, space,
            new Vector3d(origin).add(0.0, -(TOUCHING_SPACING + SPRING_REST_LENGTH), 0.0),
            1.0f);
        ExamplePhysicsUtils.physicsOwnerRun(store, "create spring joint demo",
            () -> {
                PhysicsBody anchor = ExamplePhysicsUtils.requireLiveBody(resource, anchorId);
                PhysicsBody bob = ExamplePhysicsUtils.requireLiveBody(resource, bobId);
                bob.setLinearVelocity(1.0f, 0.0f, 0.0f);
                space.createSpringJoint(anchor, bob, new Vector3f(0.0f, -HALF_SIZE, 0.0f),
                    new Vector3f(0.0f, HALF_SIZE, 0.0f), SPRING_REST_LENGTH, 20.0f, 2.0f);
            });
    }

    private static PhysicsBodyId spawnBox(@Nonnull Store<EntityStore> store,
        @Nonnull TimeResource time,
        @Nonnull PhysicsWorldResource resource,
        @Nonnull PhysicsSpace space,
        @Nonnull Vector3d position,
        float mass) {
        return ExamplePhysicsUtils.spawnBlockBody(store,
            time,
            resource,
            space.getId(),
            position,
            bodySpace -> {
                PhysicsBody created = bodySpace.createBox(HALF_SIZE, HALF_SIZE, HALF_SIZE, mass);
                created.setFriction(0.6f);
                created.setRestitution(0.15f);
                return created;
            }).bodyId();
    }
}
