package dev.hytalemodding.impulse.core.internal.commands;

import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import dev.hytalemodding.impulse.core.internal.commands.backend.BackendCommand;
import dev.hytalemodding.impulse.core.internal.commands.debug.DebugCommand;
import dev.hytalemodding.impulse.core.internal.commands.perf.PerfCommand;
import dev.hytalemodding.impulse.core.internal.commands.settings.SettingsCommand;

public class ImpulseCommand extends AbstractCommandCollection {

    public ImpulseCommand() {
        super("impulse", "Impulse runtime commands");
        addSubCommand(new BackendCommand());
        addSubCommand(new CleanCommand());
        addSubCommand(new DebugCommand());
        addSubCommand(new PerfCommand());
        addSubCommand(new SettingsCommand());
        addSubCommand(new SpaceCommand());
    }
}
