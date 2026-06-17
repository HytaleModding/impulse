package dev.hytalemodding.impulse.physicschunk.commands;

import dev.hytalemodding.impulse.core.plugin.modules.physicschunk.PhysicsChunkCommands;

/**
 * Command contributions owned by the world-collision subplugin.
 */
public final class WorldCollisionCommandContributions {

    private WorldCollisionCommandContributions() {
    }

    public static void register() {
        PhysicsChunkCommands.registerWorldCollisionCommands(
            WorldCollisionCommand::new,
            CollisionLodSettingsCommand::new);
    }

    public static void unregister() {
        PhysicsChunkCommands.unregisterWorldCollisionCommands();
    }
}
