package dev.hytalemodding.impulse.examples;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.command.system.CommandRegistry;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import dev.hytalemodding.impulse.examples.commands.ImpulseCommand;
import javax.annotation.Nonnull;

public final class ImpulseExamplesPlugin extends JavaPlugin {

    private static ImpulseExamplesPlugin instance;

    private static final HytaleLogger LOGGER = HytaleLogger.get("Impulse");

    public ImpulseExamplesPlugin(@Nonnull JavaPluginInit init) {
        super(init);
        instance = this;
    }

    @Override
    protected void setup() {
        registerCommands();
    }

    private void registerCommands() {
        CommandRegistry commandRegistry = this.getCommandRegistry();
        commandRegistry.registerCommand(new ImpulseCommand());
    }

    public static ImpulseExamplesPlugin get() {
        return instance;
    }
}
