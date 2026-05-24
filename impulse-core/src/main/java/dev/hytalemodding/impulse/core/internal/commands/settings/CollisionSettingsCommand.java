package dev.hytalemodding.impulse.core.internal.commands.settings;

import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;

public class CollisionSettingsCommand extends AbstractCommandCollection {

    public CollisionSettingsCommand() {
        super("collision", "World collision and collision LOD settings");
        addSubCommand(new WorldCollisionSettingsCommand());
        addSubCommand(new CollisionLodSettingsCommand());
    }
}
