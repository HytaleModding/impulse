package dev.hytalemodding.impulse.physicschunk.commands;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractWorldCommand;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.core.plugin.modules.physicschunk.PhysicsWorldCollisionProfiling;
import javax.annotation.Nonnull;

public class WorldCollisionPerfToggleCommand extends AbstractWorldCommand {

    public WorldCollisionPerfToggleCommand() {
        super("toggle", "Toggle Impulse runtime profiling");
    }

    @Override
    protected void execute(@Nonnull CommandContext ctx,
        @Nonnull World world,
        @Nonnull Store<EntityStore> store) {
        boolean enabled = !PhysicsWorldCollisionProfiling.isRuntimeProfilingEnabled(store);
        PhysicsWorldCollisionProfiling.setRuntimeProfilingEnabled(world, store, enabled);
        ctx.sender().sendMessage(Message.raw("Impulse runtime profiling "
            + (enabled ? "enabled" : "disabled")));
    }
}
