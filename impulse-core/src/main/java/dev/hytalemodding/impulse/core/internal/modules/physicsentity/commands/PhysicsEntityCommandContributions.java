package dev.hytalemodding.impulse.core.internal.modules.physicsentity.commands;

import dev.hytalemodding.impulse.core.internal.commands.ImpulseCommandContributionRegistry;

/**
 * Command contributions owned by the PhysicsEntity subplugin.
 */
public final class PhysicsEntityCommandContributions {

    private static final String VISUAL_SETTINGS_COMMAND_ID = "physicsentity.settings.visual";

    private PhysicsEntityCommandContributions() {
    }

    public static void register() {
        ImpulseCommandContributionRegistry.addSettingsSubCommand(
            VISUAL_SETTINGS_COMMAND_ID,
            VisualSettingsCommand::new);
    }

    public static void unregister() {
        ImpulseCommandContributionRegistry.removeSettingsSubCommand(VISUAL_SETTINGS_COMMAND_ID);
    }
}
