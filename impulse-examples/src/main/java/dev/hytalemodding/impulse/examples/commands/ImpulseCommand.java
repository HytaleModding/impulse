package dev.hytalemodding.impulse.examples.commands;

import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;

public class ImpulseCommand extends AbstractCommandCollection {

    public ImpulseCommand() {
        super("impulse-examples", "Impulse example and stress test commands");
        addSubCommand(new DropCommand());
        addSubCommand(new ShapesCommand());
        addSubCommand(new MaterialsCommand());
        addSubCommand(new ForcesCommand());
        addSubCommand(new JointsCommand());
        addSubCommand(new RaycastCommand());
        addSubCommand(new StressCommand());
    }
}
