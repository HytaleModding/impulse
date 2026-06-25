package dev.hytalemodding.impulse.core.internal.commands.debug;

import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsDebugResource;
import java.util.Collection;
import javax.annotation.Nonnull;

public class DebugCommand extends AbstractCommandCollection {

    public DebugCommand(@Nonnull Collection<? extends AbstractCommand> moduleCommands) {
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
        for (AbstractCommand command : moduleCommands) {
            addSubCommand(command);
        }
    }
}
