package dev.hytalemodding.impulse.examples.commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncPlayerCommand;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.api.PhysicsBody;
import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.PhysicsJoint;
import dev.hytalemodding.impulse.api.PhysicsRayHit;
import dev.hytalemodding.impulse.api.PhysicsSpace;
import dev.hytalemodding.impulse.core.components.ImpulseControllableComponent;
import dev.hytalemodding.impulse.core.components.PhysicsBodyComponent;
import dev.hytalemodding.impulse.core.components.PhysicsControlSessionComponent;
import dev.hytalemodding.impulse.core.resources.PhysicsWorldResource;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;

public class GrabCommand extends AbstractAsyncPlayerCommand {

    private static final double RAY_LENGTH = 24.0;
    private static final float MIN_HOLD_DISTANCE = 4.0f;
    private static final Vector3f VIEW_OFFSET = new Vector3f(0.85f, -0.35f, 0.0f);

    public GrabCommand() {
        super("grab", "Grab a controllable physics body from your view");
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

        HitSelection selection = findControllableHit(resource, store, space, start, end);
        if (selection == null) {
            ctx.sender().sendMessage(Message.raw("No controllable physics body in sight."));
            return CompletableFuture.completedFuture(null);
        }

        releaseExisting(store, ref);

        PhysicsBody body = selection.hit.body();
        PhysicsBodyType originalBodyType = body.getBodyType();
        Vector3f bodyPosition = body.getPosition();
        Vector3f hitPoint = selection.hit.point();
        Vector3f bodyLocalHit = new Vector3f(hitPoint).sub(bodyPosition);
        new Quaternionf(body.getRotation()).invert().transform(bodyLocalHit);
        PhysicsBodyComponent bodyComponent = store.getComponent(selection.owner,
            PhysicsBodyComponent.getComponentType());

        PhysicsBody anchorBody = space.createSphere(0.08f, 1.0f);
        anchorBody.setBodyType(PhysicsBodyType.KINEMATIC);
        anchorBody.setSensor(true);
        anchorBody.setCollisionFilter(1, 0);
        anchorBody.setPosition(hitPoint);
        space.addBody(anchorBody);

        PhysicsJoint joint = space.createPointJoint(anchorBody, body,
            new Vector3f(), bodyLocalHit);
        body.activate();

        store.putComponent(ref,
            PhysicsControlSessionComponent.getComponentType(),
            new PhysicsControlSessionComponent(
                body,
                anchorBody,
                joint,
                selection.owner,
                bodyComponent != null ? bodyComponent.getSpaceId() : null,
                originalBodyType,
                Math.max((float) selection.hit.distance(), MIN_HOLD_DISTANCE),
                VIEW_OFFSET,
                hitPoint
            ));
        resource.markBodyControlled(body);

        ctx.sender().sendMessage(Message.raw("Grabbed physics body at distance "
            + selection.hit.distance()));
        return CompletableFuture.completedFuture(null);
    }

    @Nullable
    private static HitSelection findControllableHit(@Nonnull PhysicsWorldResource resource,
        @Nonnull Store<EntityStore> store,
        @Nonnull PhysicsSpace space,
        @Nonnull Vector3d start,
        @Nonnull Vector3d end) {
        List<PhysicsRayHit> hits = space.raycastAll(ExamplePhysicsUtils.toVector3f(start),
            ExamplePhysicsUtils.toVector3f(end));
        HitSelection best = null;
        for (PhysicsRayHit hit : hits) {
            Ref<EntityStore> owner = resource.getBodyOwner(hit.body());
            if (owner == null || !owner.isValid()) {
                continue;
            }
            if (hit.body().getBodyType() != PhysicsBodyType.DYNAMIC) {
                continue;
            }
            ImpulseControllableComponent controllable = store.getComponent(owner,
                ImpulseControllableComponent.getComponentType());
            if (controllable == null) {
                continue;
            }
            if (best == null || hit.fraction() < best.hit.fraction()) {
                best = new HitSelection(hit, owner);
            }
        }
        return best;
    }

    private static void releaseExisting(@Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> playerRef) {
        PhysicsControlSessionComponent existing = store.getComponent(playerRef,
            PhysicsControlSessionComponent.getComponentType());
        if (existing == null) {
            return;
        }
        ReleaseCommand.release(store, playerRef, existing);
    }

    private record HitSelection(@Nonnull PhysicsRayHit hit, @Nonnull Ref<EntityStore> owner) {
    }
}
