package dev.hytalemodding.impulse.physicschunk.commands;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractWorldCommand;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.core.plugin.modules.physicschunk.PhysicsWorldCollisionProfiling;
import javax.annotation.Nonnull;

public class WorldCollisionPerfResetCommand extends AbstractWorldCommand {

    public WorldCollisionPerfResetCommand() {
        super("reset", "Reset Impulse runtime profiling counters");
    }

    @Override
    protected void execute(@Nonnull CommandContext ctx,
        @Nonnull World world,
        @Nonnull Store<EntityStore> store) {
        PhysicsWorldCollisionProfiling.resetRuntimeProfiling(world, store);
        ctx.sender().sendMessage(Message.raw("Impulse runtime profiling counters reset"));
    }
}
