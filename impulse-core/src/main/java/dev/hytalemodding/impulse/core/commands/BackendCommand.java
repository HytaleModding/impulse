package dev.hytalemodding.impulse.core.commands;

import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;

public class BackendCommand extends AbstractCommandCollection {

    public BackendCommand() {
        super("backend", "Impulse backend commands");
        addSubCommand(new BackendListCommand());
        addSubCommand(new BackendSwapCommand());
    }
}

