package dev.hytalemodding.impulse.core.commands;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractWorldCommand;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.core.resources.WorldCollisionProfilingResource;
import javax.annotation.Nonnull;

public class PerfToggleCommand extends AbstractWorldCommand {

    public PerfToggleCommand() {
        super("toggle", "Toggle Impulse world collision profiling");
    }

    @Override
    protected void execute(@Nonnull CommandContext ctx,
        @Nonnull World world,
        @Nonnull Store<EntityStore> store) {
        WorldCollisionProfilingResource profiling = store.getResource(
            WorldCollisionProfilingResource.getResourceType());
        boolean enabled = !profiling.isEnabled();
        profiling.setEnabled(enabled);
        ctx.sender().sendMessage(Message.raw("Impulse world collision profiling "
            + (enabled ? "enabled" : "disabled")));
    }
}
