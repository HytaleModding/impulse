package dev.hytalemodding.impulse.core.internal.commands.debug;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.player.ClearDebugShapes;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsDebugResource;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nonnull;

public class DebugToggleCommand extends AbstractAsyncPlayerCommand {

    public DebugToggleCommand() {
        super("toggle", "Toggle Impulse debug rendering for yourself");
    }

    @Nonnull
    @Override
    protected CompletableFuture<Void> executeAsync(@Nonnull CommandContext ctx,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull PlayerRef playerRef,
        @Nonnull World world) {
        PhysicsDebugResource debug = store.getResource(PhysicsDebugResource.getResourceType());
        boolean enabled;
        if (debug.removeSubscriber(playerRef.getUuid())) {
            enabled = false;
            playerRef.getPacketHandler().write(new ClearDebugShapes());
        } else {
            enabled = true;
            debug.addSubscriber(playerRef.getUuid());
        }

        ctx.sender()
            .sendMessage(Message.raw("Impulse debug rendering "
                + (enabled ? "enabled" : "disabled")));
        return CompletableFuture.completedFuture(null);
    }
}
