package dev.hytalemodding.impulse.core.internal.commands.settings;

import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;

public class SettingsCommand extends AbstractCommandCollection {

    public SettingsCommand() {
        super("settings", "Impulse runtime settings commands");
        addSubCommand(new StepModeSettingCommand());
        addSubCommand(new SimulationStepsSettingCommand());
        addSubCommand(new MaxStepDtSettingCommand());
        addSubCommand(new SolverSettingsCommand());
        addSubCommand(new ExecutionSettingCommand());
        addSubCommand(new VisualSyncSettingsCommand());
        addSubCommand(new CollisionLodSettingsCommand());
        addSubCommand(new WorldCollisionSettingsCommand());
    }
}
