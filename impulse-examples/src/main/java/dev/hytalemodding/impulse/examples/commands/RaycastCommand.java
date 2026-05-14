package dev.hytalemodding.impulse.examples.commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.math.vector.Vector3dUtil;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncPlayerCommand;
import com.hypixel.hytale.server.core.modules.debug.DebugUtils;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
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
import org.joml.Quaterniond;
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
        PhysicsSpace space = ExamplePhysicsUtils.mainSpace(resource, world);
        ExamplePhysicsUtils.enableDebug(resource);

        Vector3d start = new Vector3d(transform.getPosition()).add(0.0, eyeHeight(store, ref), 0.0);
        Vector3d direction = lookDirection(store, ref, transform).normalize().mul(RAY_LENGTH);
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

    private static double eyeHeight(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref) {
        ModelComponent modelComponent = store.getComponent(ref, ModelComponent.getComponentType());
        if (modelComponent == null) {
            return 1.6;
        }

        Model model = modelComponent.getModel();
        if (model == null) {
            return 1.6;
        }
        return model.getEyeHeight(ref, store);
    }

    @Nonnull
    private static Vector3d lookDirection(@Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull TransformComponent transform) {
        HeadRotation headRotation = store.getComponent(ref, HeadRotation.getComponentType());
        Rotation3f rotation = headRotation != null ? headRotation.getRotation() : transform.getRotation();
        Quaterniond quaternion = rotation.getQuaternion(new Quaterniond());
        Vector3d direction = new Vector3d(Vector3dUtil.FORWARD);
        quaternion.transform(direction);
        if (direction.lengthSquared() == 0.0) {
            return new Vector3d(Vector3dUtil.FORWARD);
        }
        return direction;
    }
}
