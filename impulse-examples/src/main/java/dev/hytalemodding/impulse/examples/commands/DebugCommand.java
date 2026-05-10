package dev.hytalemodding.impulse.examples.commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.core.resources.PhysicsWorldResource;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nonnull;

/**
 * /impulse debug — toggle collision shape rendering.
 */
public class DebugCommand extends AbstractAsyncPlayerCommand {

    public DebugCommand() {
        super("debug", "Toggle collision shape rendering");
    }

    @Nonnull
    @Override
    protected CompletableFuture<Void> executeAsync(@Nonnull CommandContext ctx,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull PlayerRef playerRef,
        @Nonnull World world) {

        PhysicsWorldResource resource = store.getResource(PhysicsWorldResource.getResourceType());
        boolean enabled = !resource.isDebugEnabled();
        resource.setDebugEnabled(enabled);

        ctx.sender()
            .sendMessage(Message.raw("Debug rendering " + (enabled ? "enabled" : "disabled")));
        return CompletableFuture.completedFuture(null);
    }
}
