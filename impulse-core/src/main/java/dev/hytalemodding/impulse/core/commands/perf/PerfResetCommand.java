package dev.hytalemodding.impulse.core.commands.perf;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractWorldCommand;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.core.resources.PhysicsRuntimeProfilingResource;
import dev.hytalemodding.impulse.core.resources.WorldCollisionProfilingResource;
import javax.annotation.Nonnull;

public class PerfResetCommand extends AbstractWorldCommand {

    public PerfResetCommand() {
        super("reset", "Reset Impulse runtime profiling counters");
    }

    @Override
    protected void execute(@Nonnull CommandContext ctx,
        @Nonnull World world,
        @Nonnull Store<EntityStore> store) {
        PhysicsRuntimeProfilingResource runtimeProfiling = store.getResource(
            PhysicsRuntimeProfilingResource.getResourceType());
        WorldCollisionProfilingResource profiling = store.getResource(
            WorldCollisionProfilingResource.getResourceType());
        runtimeProfiling.reset();
        profiling.reset();
        ctx.sender().sendMessage(Message.raw("Impulse runtime profiling counters reset"));
    }
}
