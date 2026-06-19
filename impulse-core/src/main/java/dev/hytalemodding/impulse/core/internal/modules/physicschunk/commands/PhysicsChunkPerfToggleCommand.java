package dev.hytalemodding.impulse.core.internal.modules.physicschunk.commands;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractWorldCommand;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.core.plugin.modules.physicschunk.PhysicsChunkCollisionProfiling;
import javax.annotation.Nonnull;

public class PhysicsChunkPerfToggleCommand extends AbstractWorldCommand {

    public PhysicsChunkPerfToggleCommand() {
        super("toggle", "Toggle Impulse runtime profiling");
    }

    @Override
    protected void execute(@Nonnull CommandContext ctx,
        @Nonnull World world,
        @Nonnull Store<EntityStore> store) {
        boolean enabled = !PhysicsChunkCollisionProfiling.isRuntimeProfilingEnabled(store);
        PhysicsChunkCollisionProfiling.setRuntimeProfilingEnabled(world, store, enabled);
        ctx.sender().sendMessage(Message.raw("Impulse runtime profiling "
            + (enabled ? "enabled" : "disabled")));
    }
}
