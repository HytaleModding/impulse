package dev.hytalemodding.impulse.core.internal.commands.perf;

import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;

public class PerfCommand extends AbstractCommandCollection {

    public PerfCommand() {
        super("perf", "Impulse runtime profiling commands");
        addSubCommand(new PerfStatsCommand());
    }
}
