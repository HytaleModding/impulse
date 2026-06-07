package dev.hytalemodding.impulse.core.internal.modules.worldcollision.commands;

import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;

public final class WorldCollisionPerfCommand extends AbstractCommandCollection {

    public WorldCollisionPerfCommand() {
        super("perf", "Impulse world-collision profiling commands");
        addSubCommand(new WorldCollisionPerfToggleCommand());
        addSubCommand(new WorldCollisionPerfReportCommand());
        addSubCommand(new WorldCollisionPerfResetCommand());
    }
}
