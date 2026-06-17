package dev.hytalemodding.impulse.core.internal.modules.physicschunk.commands;

import dev.hytalemodding.impulse.core.plugin.modules.physicschunk.PhysicsChunkCommands;

/**
 * Command contributions owned by the PhysicsChunk subplugin.
 */
public final class PhysicsChunkCommandContributions {

    private PhysicsChunkCommandContributions() {
    }

    public static void register() {
        PhysicsChunkCommands.registerPhysicsChunkCommands(
            PhysicsChunkCommand::new,
            CollisionLodSettingsCommand::new);
    }

    public static void unregister() {
        PhysicsChunkCommands.unregisterPhysicsChunkCommands();
    }
}
