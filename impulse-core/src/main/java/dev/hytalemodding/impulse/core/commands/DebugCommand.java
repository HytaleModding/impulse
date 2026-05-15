package dev.hytalemodding.impulse.core.commands;

import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import dev.hytalemodding.impulse.core.resources.PhysicsDebugResource;

public class DebugCommand extends AbstractCommandCollection {

    public DebugCommand() {
        super("debug", "Impulse debug rendering commands");
        addSubCommand(new DebugToggleCommand());
        addSubCommand(new DebugFlagCommand("shapes", "shape",
            PhysicsDebugResource::isDebugShapesEnabled,
            PhysicsDebugResource::setDebugShapesEnabled));
        addSubCommand(new DebugFlagCommand("motion", "motion",
            PhysicsDebugResource::isDebugMotionEnabled,
            PhysicsDebugResource::setDebugMotionEnabled));
        addSubCommand(new DebugFlagCommand("contacts", "contact",
            PhysicsDebugResource::isDebugContactsEnabled,
            PhysicsDebugResource::setDebugContactsEnabled));
        addSubCommand(new DebugFlagCommand("joints", "joint",
            PhysicsDebugResource::isDebugJointsEnabled,
            PhysicsDebugResource::setDebugJointsEnabled));
        addSubCommand(new DebugFlagCommand("world-collision", "world collision",
            PhysicsDebugResource::isDebugWorldCollisionEnabled,
            PhysicsDebugResource::setDebugWorldCollisionEnabled));
    }
}
