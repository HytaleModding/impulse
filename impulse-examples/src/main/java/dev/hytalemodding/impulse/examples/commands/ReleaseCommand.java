package dev.hytalemodding.impulse.examples.commands;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.api.PhysicsBody;
import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.PhysicsJoint;
import dev.hytalemodding.impulse.api.PhysicsSpace;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.plugin.components.PhysicsControlSessionComponent;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyId;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsWorldResource;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3f;

public class ReleaseCommand extends AbstractAsyncPlayerCommand {

    private static final ComponentType<EntityStore, PhysicsControlSessionComponent> CONTROL_SESSION_TYPE =
        PhysicsControlSessionComponent.getComponentType();

    public ReleaseCommand() {
        super("release", "Release the currently grabbed physics body");
    }

    @Nonnull
    @Override
    protected CompletableFuture<Void> executeAsync(@Nonnull CommandContext ctx,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull PlayerRef playerRef,
        @Nonnull World world) {
        PhysicsControlSessionComponent session = store.getComponent(ref, CONTROL_SESSION_TYPE);
        if (session == null) {
            ctx.sender().sendMessage(Message.raw("No grabbed physics body."));
            return CompletableFuture.completedFuture(null);
        }

        release(store, ref, session);
        ctx.sender().sendMessage(Message.raw("Released physics body."));
        return CompletableFuture.completedFuture(null);
    }

    static void release(@Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull PhysicsControlSessionComponent session) {
        PhysicsWorldResource resource = store.getResource(PhysicsWorldResource.getResourceType());
        PhysicsBodyId bodyId = session.getBodyId();
        PhysicsBodyId anchorBodyId = session.getAnchorBodyId();
        SpaceId spaceId = session.getSpaceId();
        if (bodyId != null && resource.getBodyRegistrationView(bodyId) != null) {
            resource.clearControlledBody(bodyId);
            PhysicsBodyType originalBodyType = session.getOriginalBodyType();
            ExamplePhysicsUtils.physicsOwnerRun(store, "release grabbed physics body",
                () -> {
                    PhysicsSpace space = spaceId != null ? resource.getSpace(spaceId) : null;
                    PhysicsBody body = resource.getBody(bodyId);
                    if (body == null) {
                        return;
                    }
                    if (space != null && anchorBodyId != null) {
                        removeControlJoint(resource, space, bodyId, anchorBodyId);
                    }
                    body.setBodyType(originalBodyType);
                    if (originalBodyType == PhysicsBodyType.DYNAMIC) {
                        Vector3f velocity = session.getReleaseVelocity();
                        body.setLinearVelocity(velocity);
                    } else {
                        body.setLinearVelocity(0.0f, 0.0f, 0.0f);
                    }
                    body.activate();
                });
        } else if (bodyId != null && anchorBodyId != null && spaceId != null) {
            ExamplePhysicsUtils.physicsOwnerRun(store, "release grabbed physics joint",
                () -> {
                    PhysicsSpace space = resource.getSpace(spaceId);
                    if (space != null) {
                        removeControlJoint(resource, space, bodyId, anchorBodyId);
                    }
                });
        }
        if (anchorBodyId != null) {
            resource.destroyBody(anchorBodyId);
        }

        session.deactivate();
        store.removeComponent(playerRef, CONTROL_SESSION_TYPE);
    }

    private static boolean removeControlJoint(@Nonnull PhysicsWorldResource resource,
        @Nonnull PhysicsSpace space,
        @Nonnull PhysicsBodyId bodyId,
        @Nonnull PhysicsBodyId anchorBodyId) {
        for (PhysicsJoint joint : new ArrayList<>(space.getJoints())) {
            PhysicsBodyId bodyAId = resource.getBodyId(joint.getBodyA());
            PhysicsBodyId bodyBId = resource.getBodyId(joint.getBodyB());
            if (isControlJoint(bodyAId, bodyBId, bodyId, anchorBodyId)) {
                space.removeJoint(joint);
                return true;
            }
        }
        return false;
    }

    private static boolean isControlJoint(@Nullable PhysicsBodyId bodyAId,
        @Nullable PhysicsBodyId bodyBId,
        @Nonnull PhysicsBodyId bodyId,
        @Nonnull PhysicsBodyId anchorBodyId) {
        return (anchorBodyId.equals(bodyAId) && bodyId.equals(bodyBId))
            || (anchorBodyId.equals(bodyBId) && bodyId.equals(bodyAId));
    }
}
