package dev.hytalemodding.impulse.examples.commands;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncPlayerCommand;
import com.hypixel.hytale.server.core.modules.debug.DebugUtils;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.plugin.physicsstore.PhysicsStoreAsync;
import dev.hytalemodding.impulse.core.plugin.physicsstore.PhysicsStoreRaycasts;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsWorldResource;
import dev.hytalemodding.impulse.core.plugin.simulation.view.RaycastHitView;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nonnull;
import org.joml.Vector3d;

public class RaycastCommand extends AbstractAsyncPlayerCommand {

    private static final double RAY_LENGTH = 24.0;
    private static final ComponentType<EntityStore, TransformComponent> TRANSFORM_TYPE =
        TransformComponent.getComponentType();
    private final OptionalArg<Integer> spaceArg = this.withOptionalArg(
        "space",
        "Physics space id to target",
        ArgTypes.INTEGER);

    public RaycastCommand() {
        super("raycast", "Cast a physics ray from the player view");
    }

    @Nonnull
    @Override
    protected CompletableFuture<Void> executeAsync(@Nonnull CommandContext ctx,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull PlayerRef playerRef,
        @Nonnull World world) {
        TransformComponent transform = store.getComponent(ref, TRANSFORM_TYPE);
        if (transform == null) {
            ctx.sender().sendMessage(Message.raw("Cannot determine player position."));
            return CompletableFuture.completedFuture(null);
        }

        PhysicsWorldResource resource = ExamplePhysicsUtils.resource(store);
        SpaceId spaceId = ExamplePhysicsUtils.spaceId(ctx, resource, spaceArg);
        if (spaceId == null) {
            return CompletableFuture.completedFuture(null);
        }

        Transform look = ExamplePhysicsUtils.lookTransform(store, ref);
        Vector3d start = new Vector3d(look.getPosition());
        Vector3d direction = new Vector3d(look.getDirection()).mul(RAY_LENGTH);
        Vector3d end = new Vector3d(start).add(direction);

        DebugUtils.addArrow(world, start, direction, DebugUtils.COLOR_WHITE, 0.8f, 4.0f,
            DebugUtils.FLAG_FADE);
        return PhysicsStoreAsync.acceptOnWorldThread(world,
            PhysicsStoreRaycasts.closestAsync(world,
                spaceId,
                ExamplePhysicsUtils.toVector3f(start),
                ExamplePhysicsUtils.toVector3f(end)),
            hit -> handleHit(ctx, world, hit.map(RaycastCommand::toResult).orElse(null)));
    }

    private static void handleHit(@Nonnull CommandContext ctx,
        @Nonnull World world,
        RaycastResult hit) {
        if (hit == null) {
            ctx.sender().sendMessage(Message.raw("Physics ray missed."));
            return;
        }

        Vector3d hitPoint = hit.point();
        Vector3d normal = hit.normal();
        DebugUtils.addSphere(world, hitPoint, DebugUtils.COLOR_RED, 0.18, 4.0f);
        if (normal.lengthSquared() > 0.0) {
            DebugUtils.addArrow(world, hitPoint, normal.normalize().mul(1.0), DebugUtils.COLOR_YELLOW,
                0.8f, 4.0f, DebugUtils.FLAG_FADE);
        }

        ctx.sender().sendMessage(Message.raw("Physics ray hit " + hit.shapeType()
            + " at distance " + hit.distance()));
    }

    private record RaycastResult(@Nonnull Vector3d point,
                                 @Nonnull Vector3d normal,
                                 @Nonnull String shapeType,
                                 float distance) {

        private RaycastResult {
            point = new Vector3d(point);
            normal = new Vector3d(normal);
        }
    }

    @Nonnull
    private static RaycastResult toResult(@Nonnull RaycastHitView hit) {
        return new RaycastResult(
            new Vector3d(hit.point().x, hit.point().y, hit.point().z),
            new Vector3d(hit.normal().x, hit.normal().y, hit.normal().z),
            hit.shapeType().name(),
            hit.distance());
    }
}
