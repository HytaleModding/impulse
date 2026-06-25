package dev.hytalemodding.impulse.core.internal.modules.physicsentity.commands;

import dev.hytalemodding.impulse.core.internal.commands.ImpulseCommandTreeRegistry;

/**
 * Command set owned by the PhysicsEntity subplugin.
 */
public final class PhysicsEntityCommandSet {

    private static final String VISUAL_SETTINGS_COMMAND_ID = "physicsentity.settings.visual";

    private PhysicsEntityCommandSet() {
    }

    public static void register() {
        ImpulseCommandTreeRegistry.registerSettingsSubCommand(
            VISUAL_SETTINGS_COMMAND_ID,
            VisualSettingsCommand::new);
    }

    public static void unregister() {
        ImpulseCommandTreeRegistry.unregisterSettingsSubCommand(VISUAL_SETTINGS_COMMAND_ID);
    }
}
