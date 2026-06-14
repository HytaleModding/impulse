package dev.hytalemodding.impulse.core.internal.modules.worldcollision.commands;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractWorldCommand;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.early.PhysicsStoreWorld;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsProfilingResource;
import dev.hytalemodding.impulse.core.internal.resources.profiling.PhysicsRuntimeProfilingResource;
import dev.hytalemodding.impulse.core.internal.modules.worldcollision.profiling.WorldCollisionProfilingResource;
import javax.annotation.Nonnull;

public class WorldCollisionPerfResetCommand extends AbstractWorldCommand {

    public WorldCollisionPerfResetCommand() {
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
        Store<PhysicsStore> physicsStore = physicsStoreOrNull(world);
        if (physicsStore != null) {
            physicsStore.getResource(PhysicsProfilingResource.getResourceType()).reset();
        }
        ctx.sender().sendMessage(Message.raw("Impulse runtime profiling counters reset"));
    }

    private static Store<PhysicsStore> physicsStoreOrNull(@Nonnull World world) {
        if (!(world instanceof PhysicsStoreWorld physicsStoreWorld)) {
            return null;
        }
        Store<PhysicsStore> store = physicsStoreWorld.getPhysicsStore().getStore();
        return store.isShutdown() ? null : store;
    }
}
