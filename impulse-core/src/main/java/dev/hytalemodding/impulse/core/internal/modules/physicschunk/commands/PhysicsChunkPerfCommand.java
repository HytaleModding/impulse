package dev.hytalemodding.impulse.core.internal.modules.physicschunk.commands;

import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;

public final class PhysicsChunkPerfCommand extends AbstractCommandCollection {

    public PhysicsChunkPerfCommand() {
        super("perf", "Impulse runtime and PhysicsChunk profiling commands");
        addSubCommand(new PhysicsChunkPerfToggleCommand());
        addSubCommand(new PhysicsChunkPerfReportCommand());
        addSubCommand(new PhysicsChunkPerfResetCommand());
    }
}
