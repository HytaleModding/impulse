package dev.hytalemodding.impulse.examples.commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncPlayerCommand;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nonnull;

/**
 * Legacy manual entity snapshot commands are intentionally disabled by the
 * detached body-id lifecycle migration.
 */
public class PersistenceCommand extends AbstractCommandCollection {

    public PersistenceCommand() {
        super("persistence", "Legacy entity-backed physics snapshots are no longer supported");
        addSubCommand(new UnsupportedCommand("save"));
        addSubCommand(new UnsupportedCommand("load"));
    }

    private static final class UnsupportedCommand extends AbstractAsyncPlayerCommand {

        private UnsupportedCommand(@Nonnull String name) {
            super(name, "Unsupported after the detached body-id migration");
        }

        @Nonnull
        @Override
        protected CompletableFuture<Void> executeAsync(@Nonnull CommandContext ctx,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world) {
            ctx.sender().sendMessage(Message.raw(
                "Legacy entity-backed Impulse snapshot files are not supported after schema v3. "
                    + "Use Hytale world persistence, which now stores persistent bodies by PhysicsBodyId."));
            return CompletableFuture.completedFuture(null);
        }
    }
}
