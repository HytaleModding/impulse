package dev.hytalemodding.impulse.core.internal.modules.worldcollision.commands;

import dev.hytalemodding.impulse.core.internal.commands.ImpulseCommandContributionRegistry;

/**
 * Command contributions owned by the world-collision subplugin.
 */
public final class WorldCollisionCommandContributions {

    private static final String ROOT_COMMAND_ID = "worldcollision.root";
    private static final String COLLISION_LOD_SETTINGS_ID = "worldcollision.settings.collision-lod";

    private WorldCollisionCommandContributions() {
    }

    public static void register() {
        ImpulseCommandContributionRegistry.addRootAndSettingsSubCommands(
            ROOT_COMMAND_ID,
            WorldCollisionCommand::new,
            COLLISION_LOD_SETTINGS_ID,
            CollisionLodSettingsCommand::new);
    }

    public static void unregister() {
        ImpulseCommandContributionRegistry.removeRootAndSettingsSubCommands(ROOT_COMMAND_ID,
            COLLISION_LOD_SETTINGS_ID);
    }
}
