package dev.hytalemodding.impulse.core.internal.modules.physicsentity.commands;

import dev.hytalemodding.impulse.core.plugin.modules.physicsentity.PhysicsEntityCommands;

/**
 * Command contributions owned by the PhysicsEntity subplugin.
 */
public final class PhysicsEntityCommandContributions {

    private PhysicsEntityCommandContributions() {
    }

    public static void register() {
        PhysicsEntityCommands.registerPhysicsEntityCommands(VisualSettingsCommand::new);
    }

    public static void unregister() {
        PhysicsEntityCommands.unregisterPhysicsEntityCommands();
    }
}
