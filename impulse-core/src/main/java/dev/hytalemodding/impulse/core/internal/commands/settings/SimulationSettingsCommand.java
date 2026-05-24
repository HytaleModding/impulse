package dev.hytalemodding.impulse.core.internal.commands.settings;

import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;

public class SimulationSettingsCommand extends AbstractCommandCollection {

    public SimulationSettingsCommand() {
        super("simulation", "World physics simulation settings");
        addSubCommand(new StepModeSettingCommand());
        addSubCommand(new SimulationStepsSettingCommand());
        addSubCommand(new MaxStepDtSettingCommand());
        addSubCommand(new StepSchedulingSettingCommand());
    }
}
