package dev.hytalemodding.impulse.examples.commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncPlayerCommand;
import com.hypixel.hytale.server.core.modules.debug.DebugUtils;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.api.PhysicsRayHit;
import dev.hytalemodding.impulse.api.PhysicsSpace;
import dev.hytalemodding.impulse.core.resources.PhysicsWorldResource;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nonnull;
import org.joml.Vector3d;

public class RaycastCommand extends AbstractAsyncPlayerCommand {

    private static final double RAY_LENGTH = 24.0;

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
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) {
            ctx.sender().sendMessage(Message.raw("Cannot determine player position."));
            return CompletableFuture.completedFuture(null);
        }

        PhysicsWorldResource resource = ExamplePhysicsUtils.resource(store);
        PhysicsSpace space = ExamplePhysicsUtils.defaultSpace(ctx, resource);
        if (space == null) {
            return CompletableFuture.completedFuture(null);
        }

        Vector3d start = ExamplePhysicsUtils.eyePosition(store, ref, transform);
        Vector3d direction = ExamplePhysicsUtils.lookDirection(store, ref, transform).mul(RAY_LENGTH);
        Vector3d end = new Vector3d(start).add(direction);

        DebugUtils.addArrow(world, start, direction, DebugUtils.COLOR_WHITE, 0.8f, 4.0f,
            DebugUtils.FLAG_FADE);
        Optional<PhysicsRayHit> hit = space.raycastClosest(ExamplePhysicsUtils.toVector3f(start),
            ExamplePhysicsUtils.toVector3f(end));
        if (hit.isEmpty()) {
            ctx.sender().sendMessage(Message.raw("Physics ray missed."));
            return CompletableFuture.completedFuture(null);
        }

        PhysicsRayHit rayHit = hit.get();
        Vector3d hitPoint = new Vector3d(rayHit.point().x, rayHit.point().y, rayHit.point().z);
        Vector3d normal = new Vector3d(rayHit.normal().x, rayHit.normal().y, rayHit.normal().z);
        DebugUtils.addSphere(world, hitPoint, DebugUtils.COLOR_RED, 0.18, 4.0f);
        if (normal.lengthSquared() > 0.0) {
            DebugUtils.addArrow(world, hitPoint, normal.normalize().mul(1.0), DebugUtils.COLOR_YELLOW,
                0.8f, 4.0f, DebugUtils.FLAG_FADE);
        }

        ctx.sender().sendMessage(Message.raw("Physics ray hit " + rayHit.body().getShapeType()
            + " at distance " + rayHit.distance()));
        return CompletableFuture.completedFuture(null);
    }
}
