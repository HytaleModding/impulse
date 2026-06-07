package dev.hytalemodding.impulse.core.internal.modules.worldcollision.commands;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractWorldCommand;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.core.internal.resources.profiling.PhysicsRuntimeProfilingResource;
import dev.hytalemodding.impulse.core.internal.modules.worldcollision.profiling.WorldCollisionProfilingResource;
import javax.annotation.Nonnull;

public class WorldCollisionPerfToggleCommand extends AbstractWorldCommand {

    public WorldCollisionPerfToggleCommand() {
        super("toggle", "Toggle Impulse runtime profiling");
    }

    @Override
    protected void execute(@Nonnull CommandContext ctx,
        @Nonnull World world,
        @Nonnull Store<EntityStore> store) {
        PhysicsRuntimeProfilingResource runtimeProfiling = store.getResource(
            PhysicsRuntimeProfilingResource.getResourceType());
        WorldCollisionProfilingResource profiling = store.getResource(
            WorldCollisionProfilingResource.getResourceType());
        boolean enabled = !runtimeProfiling.isEnabled() || !profiling.isEnabled();
        runtimeProfiling.setEnabled(enabled);
        profiling.setEnabled(enabled);
        ctx.sender().sendMessage(Message.raw("Impulse runtime profiling "
            + (enabled ? "enabled" : "disabled")));
    }
}
