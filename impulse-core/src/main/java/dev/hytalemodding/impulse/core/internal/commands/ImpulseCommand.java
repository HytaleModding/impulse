package dev.hytalemodding.impulse.core.internal.commands;

import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import dev.hytalemodding.impulse.core.internal.commands.backend.BackendCommand;
import dev.hytalemodding.impulse.core.internal.commands.debug.DebugCommand;
import dev.hytalemodding.impulse.core.internal.commands.perf.PerfCommand;
import dev.hytalemodding.impulse.core.internal.commands.settings.SettingsCommand;
import java.util.Collection;
import java.util.List;
import javax.annotation.Nonnull;

public class ImpulseCommand extends AbstractCommandCollection {

    public ImpulseCommand() {
        this(List.of());
    }

    ImpulseCommand(@Nonnull Collection<? extends AbstractCommand> settingsContributions) {
        super("impulse", "Impulse runtime commands");
        addSubCommand(new BackendCommand());
        addSubCommand(new CleanCommand());
        addSubCommand(new DebugCommand());
        addSubCommand(new PerfCommand());
        SettingsCommand settingsCommand = new SettingsCommand();
        for (AbstractCommand command : settingsContributions) {
            settingsCommand.addContribution(command);
        }
        addSubCommand(settingsCommand);
        addSubCommand(new SpaceCommand());
    }

    void addRootContribution(@Nonnull AbstractCommand command) {
        addSubCommand(command);
    }

}
