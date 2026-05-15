package dev.hytalemodding.impulse.core.commands;

import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import dev.hytalemodding.impulse.core.resources.PhysicsWorldResource;

public class DebugCommand extends AbstractCommandCollection {

    public DebugCommand() {
        super("debug", "Impulse debug rendering commands");
        addSubCommand(new DebugToggleCommand());
        addSubCommand(new DebugFlagCommand("shapes", "shape",
            PhysicsWorldResource::isDebugShapesEnabled,
            PhysicsWorldResource::setDebugShapesEnabled));
        addSubCommand(new DebugFlagCommand("motion", "motion",
            PhysicsWorldResource::isDebugMotionEnabled,
            PhysicsWorldResource::setDebugMotionEnabled));
        addSubCommand(new DebugFlagCommand("contacts", "contact",
            PhysicsWorldResource::isDebugContactsEnabled,
            PhysicsWorldResource::setDebugContactsEnabled));
        addSubCommand(new DebugFlagCommand("joints", "joint",
            PhysicsWorldResource::isDebugJointsEnabled,
            PhysicsWorldResource::setDebugJointsEnabled));
        addSubCommand(new DebugFlagCommand("world-collision", "world collision",
            PhysicsWorldResource::isDebugWorldCollisionEnabled,
            PhysicsWorldResource::setDebugWorldCollisionEnabled));
    }
}
