package dev.hytalemodding.impulse.examples.commands;

import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;

public class StressCommand extends AbstractCommandCollection {

    public StressCommand() {
        super("stress", "Impulse stress test commands");
        addSubCommand(new StressAutoBenchmarkCommand());
        addSubCommand(new StressBenchmarkCommand());
        addSubCommand(new StressBodiesCommand());
        addSubCommand(new StressRawBodiesCommand());
        addSubCommand(new StressShapesCommand());
        addSubCommand(new StressJointsCommand());
        addSubCommand(new StressRaycastCommand());
        addSubCommand(new StressSwapCommand());
    }
}
