package dev.hytalemodding.impulse.core.internal.commands;

import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import dev.hytalemodding.impulse.core.internal.commands.backend.BackendCommand;
import dev.hytalemodding.impulse.core.internal.commands.debug.DebugCommand;
import dev.hytalemodding.impulse.core.internal.commands.perf.PerfCommand;
import dev.hytalemodding.impulse.core.internal.commands.settings.SettingsCommand;
import dev.hytalemodding.impulse.core.internal.commands.space.SpaceCommand;
import java.util.Collection;
import javax.annotation.Nonnull;

public class ImpulseCommand extends AbstractCommandCollection {

    ImpulseCommand(@Nonnull Collection<? extends AbstractCommand> debugCommands,
        @Nonnull Collection<? extends AbstractCommand> settingsCommands) {
        super("impulse", "Impulse runtime commands");
        addSubCommand(new BackendCommand());
        addSubCommand(new CleanCommand());
        addSubCommand(new DebugCommand(debugCommands));
        addSubCommand(new PerfCommand());
        SettingsCommand settingsCommand = new SettingsCommand();
        for (AbstractCommand command : settingsCommands) {
            settingsCommand.registerSettingsCommand(command);
        }
        addSubCommand(settingsCommand);
        addSubCommand(new SpaceCommand());
    }

    void registerRootCommand(@Nonnull AbstractCommand command) {
        addSubCommand(command);
    }

}
