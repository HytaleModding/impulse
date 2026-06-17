package dev.hytalemodding.impulse.core.internal.modules.physicschunk.commands;

import dev.hytalemodding.impulse.core.internal.commands.ImpulseCommandContributionRegistry;

/**
 * Command contributions owned by the PhysicsChunk subplugin.
 */
public final class PhysicsChunkCommandContributions {

    private static final String PHYSICS_CHUNK_ROOT_COMMAND_ID = "physicschunk.root";
    private static final String COLLISION_LOD_SETTINGS_COMMAND_ID =
        "physicschunk.settings.collision-lod";

    private PhysicsChunkCommandContributions() {
    }

    public static void register() {
        ImpulseCommandContributionRegistry.addRootAndSettingsSubCommands(
            PHYSICS_CHUNK_ROOT_COMMAND_ID,
            PhysicsChunkCommand::new,
            COLLISION_LOD_SETTINGS_COMMAND_ID,
            CollisionLodSettingsCommand::new);
    }

    public static void unregister() {
        ImpulseCommandContributionRegistry.removeRootAndSettingsSubCommands(
            PHYSICS_CHUNK_ROOT_COMMAND_ID,
            COLLISION_LOD_SETTINGS_COMMAND_ID);
    }
}
