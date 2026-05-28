package dev.hytalemodding.impulse.examples.commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.core.plugin.control.PhysicsControlSessions;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nonnull;

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
        if (!PhysicsControlSessions.releaseSession(store, ref)) {
            ctx.sender().sendMessage(Message.raw("No grabbed physics body."));
            return CompletableFuture.completedFuture(null);
        }

        ctx.sender().sendMessage(Message.raw("Released physics body."));
        return CompletableFuture.completedFuture(null);
    }
}
