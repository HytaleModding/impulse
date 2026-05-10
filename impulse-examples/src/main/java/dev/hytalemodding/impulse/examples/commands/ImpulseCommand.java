package dev.hytalemodding.impulse.examples.commands;

import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;

public class ImpulseCommand extends AbstractCommandCollection {

    public ImpulseCommand() {
        super("impulse", "Impulse physics commands");
        addSubCommand(new DropCommand());
        addSubCommand(new DebugCommand());
    }
}
