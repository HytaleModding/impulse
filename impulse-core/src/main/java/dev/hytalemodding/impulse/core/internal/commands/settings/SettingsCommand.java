package dev.hytalemodding.impulse.core.internal.commands.settings;

import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;

public class SettingsCommand extends AbstractCommandCollection {

    public SettingsCommand() {
        super("settings", "Impulse runtime settings commands");
        // TODO: Migrate module-owned settings into a core /impulse command contribution hub.
        addSubCommand(new CollisionLodSettingsCommand());
        addSubCommand(new SimulationSettingsCommand());
        addSubCommand(new SolverSettingsCommand());
        addSubCommand(new VisualSettingsCommand());
    }
}
