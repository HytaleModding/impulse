package dev.hytalemodding.impulse.core.commands;

import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;

public class SettingsCommand extends AbstractCommandCollection {

    public SettingsCommand() {
        super("settings", "Impulse runtime settings commands");
        addSubCommand(new SimulationStepsCommand());
    }
}

