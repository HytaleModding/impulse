package dev.hytalemodding.impulse.core.internal.commands.backend;

import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;

public class BackendCommand extends AbstractCommandCollection {

    public BackendCommand() {
        super("backend", "Impulse backend commands");
        addSubCommand(new BackendListCommand());
    }
}
