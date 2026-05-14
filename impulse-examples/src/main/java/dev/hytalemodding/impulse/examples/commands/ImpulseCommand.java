package dev.hytalemodding.impulse.examples.commands;

import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import dev.hytalemodding.impulse.core.resources.PhysicsWorldResource;

public class ImpulseCommand extends AbstractCommandCollection {

    public ImpulseCommand() {
        super("impulse", "Impulse physics commands");
        addSubCommand(new DropCommand());
        addSubCommand(new ShapesCommand());
        addSubCommand(new MaterialsCommand());
        addSubCommand(new ForcesCommand());
        addSubCommand(new JointsCommand());
        addSubCommand(new RaycastCommand());
        addSubCommand(new DebugCommand());
        addSubCommand(new DebugFlagCommand("debug-shapes", "shape",
            PhysicsWorldResource::isDebugShapesEnabled,
            PhysicsWorldResource::setDebugShapesEnabled));
        addSubCommand(new DebugFlagCommand("debug-motion", "motion",
            PhysicsWorldResource::isDebugMotionEnabled,
            PhysicsWorldResource::setDebugMotionEnabled));
        addSubCommand(new DebugFlagCommand("debug-contacts", "contact",
            PhysicsWorldResource::isDebugContactsEnabled,
            PhysicsWorldResource::setDebugContactsEnabled));
        addSubCommand(new DebugFlagCommand("debug-joints", "joint",
            PhysicsWorldResource::isDebugJointsEnabled,
            PhysicsWorldResource::setDebugJointsEnabled));
    }
}
