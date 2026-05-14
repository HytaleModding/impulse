package dev.hytalemodding.impulse.core.commands;

import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;

public class ImpulseCommand extends AbstractCommandCollection {

    public ImpulseCommand() {
        super("impulse", "Impulse runtime commands");
        addSubCommand(new BackendCommand());
        addSubCommand(new DebugCommand());
        addSubCommand(new SettingsCommand());
    }
}

