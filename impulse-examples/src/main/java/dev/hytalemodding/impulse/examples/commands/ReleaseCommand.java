package dev.hytalemodding.impulse.examples.commands;

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
import dev.hytalemodding.impulse.core.components.PhysicsControlSessionComponent;
import dev.hytalemodding.impulse.core.resources.PhysicsWorldResource;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nonnull;
import org.joml.Vector3f;

public class ReleaseCommand extends AbstractAsyncPlayerCommand {

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
        PhysicsControlSessionComponent session = store.getComponent(ref,
            PhysicsControlSessionComponent.getComponentType());
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
        if (space != null) {
            PhysicsJoint joint = session.getJoint();
            if (joint != null) {
                space.removeJoint(joint);
            }

            PhysicsBody anchorBody = session.getAnchorBody();
            if (anchorBody != null) {
                space.removeBody(anchorBody);
            }
        }

        PhysicsBody body = session.getBody();
        if (body != null) {
            PhysicsBodyType originalBodyType = session.getOriginalBodyType();
            body.setBodyType(originalBodyType);
            if (originalBodyType == PhysicsBodyType.DYNAMIC) {
                Vector3f velocity = session.getReleaseVelocity();
                body.setLinearVelocity(velocity);
            } else {
                body.setLinearVelocity(0.0f, 0.0f, 0.0f);
            }
            body.activate();
        }

        session.deactivate();
        store.removeComponent(playerRef, PhysicsControlSessionComponent.getComponentType());
    }
}
