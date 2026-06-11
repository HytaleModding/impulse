package dev.hytalemodding.impulse.core.internal.modules.worldcollision.commands;

import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;

public final class WorldCollisionCommand extends AbstractCommandCollection {

    public WorldCollisionCommand() {
        super("worldcollision", "Impulse world-collision module commands");
        addSubCommand(new WorldCollisionSettingsCommand());
        addSubCommand(new WorldCollisionPerfCommand());
    }
}
