package dev.hytalemodding.impulse.core.internal.commands.settings;

import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import javax.annotation.Nonnull;

public class SettingsCommand extends AbstractCommandCollection {

    public SettingsCommand() {
        super("settings", "Impulse runtime settings commands");
        addSubCommand(new SimulationSettingsCommand());
        addSubCommand(new SolverSettingsCommand());
        addSubCommand(new VisualSettingsCommand());
    }

    public void addContribution(@Nonnull AbstractCommand command) {
        addSubCommand(command);
    }
}
