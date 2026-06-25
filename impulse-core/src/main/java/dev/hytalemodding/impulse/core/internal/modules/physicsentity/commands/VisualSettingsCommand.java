package dev.hytalemodding.impulse.core.internal.modules.physicsentity.commands;

import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;

public class VisualSettingsCommand extends AbstractCommandCollection {

    public VisualSettingsCommand() {
        super("visual", "Entity visual sync and detached materialization settings");
        addSubCommand(new VisualSyncSettingsCommand());
        addSubCommand(new VisualMaterializationSettingsCommand());
    }
}
