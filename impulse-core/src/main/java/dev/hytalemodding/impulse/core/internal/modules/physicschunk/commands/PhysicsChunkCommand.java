package dev.hytalemodding.impulse.core.internal.modules.physicschunk.commands;

import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;

public final class PhysicsChunkCommand extends AbstractCommandCollection {

    public PhysicsChunkCommand() {
        super("physicschunk", "Impulse PhysicsChunk terrain commands");
        addSubCommand(new PhysicsChunkSettingsCommand());
        addSubCommand(new PhysicsChunkPerfCommand());
    }
}
