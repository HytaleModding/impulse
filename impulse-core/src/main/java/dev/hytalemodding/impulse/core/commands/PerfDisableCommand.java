package dev.hytalemodding.impulse.core.commands;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractWorldCommand;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.core.resources.PhysicsRuntimeProfilingResource;
import dev.hytalemodding.impulse.core.resources.WorldCollisionProfilingResource;
import javax.annotation.Nonnull;

public class PerfDisableCommand extends AbstractWorldCommand {

    public PerfDisableCommand() {
        super("disable", "Disable Impulse runtime profiling");
    }

    @Override
    protected void execute(@Nonnull CommandContext ctx,
        @Nonnull World world,
        @Nonnull Store<EntityStore> store) {
        store.getResource(PhysicsRuntimeProfilingResource.getResourceType()).setEnabled(false);
        store.getResource(WorldCollisionProfilingResource.getResourceType()).setEnabled(false);
        ctx.sender().sendMessage(Message.raw("Impulse runtime profiling disabled"));
    }
}
