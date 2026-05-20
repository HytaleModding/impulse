package dev.hytalemodding.impulse.core.commands.perf;

import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;

public class PerfCommand extends AbstractCommandCollection {

    public PerfCommand() {
        super("perf", "Impulse runtime profiling commands");
        addSubCommand(new PerfToggleCommand());
        addSubCommand(new PerfReportCommand());
        addSubCommand(new PerfStatsCommand());
        addSubCommand(new PerfResetCommand());
    }
}
