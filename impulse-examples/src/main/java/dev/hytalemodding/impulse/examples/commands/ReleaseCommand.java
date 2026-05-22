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
import dev.hytalemodding.impulse.core.plugin.components.PhysicsControlSessionComponent;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyId;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsWorldResource;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nonnull;
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
        PhysicsSpace space = session.getSpaceId() != null ? resource.getSpace(session.getSpaceId()) : null;

        PhysicsBodyId bodyId = session.getBodyId();
        PhysicsBody body = bodyId != null ? resource.getBody(bodyId) : null;
        if (body != null) {
            resource.clearControlledBody(bodyId);
            PhysicsBodyType originalBodyType = session.getOriginalBodyType();
            ExamplePhysicsUtils.physicsOwnerRun(store, "release grabbed physics body",
                () -> {
                    PhysicsJoint joint = session.getJoint();
                    if (space != null && joint != null) {
                        space.removeJoint(joint);
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
        } else if (space != null && session.getJoint() != null) {
            PhysicsJoint joint = session.getJoint();
            ExamplePhysicsUtils.physicsOwnerRun(store, "release grabbed physics joint",
                () -> space.removeJoint(joint));
        }
        if (session.getAnchorBodyId() != null) {
            resource.destroyBody(session.getAnchorBodyId());
        }

        session.deactivate();
        store.removeComponent(playerRef, CONTROL_SESSION_TYPE);
    }
}
