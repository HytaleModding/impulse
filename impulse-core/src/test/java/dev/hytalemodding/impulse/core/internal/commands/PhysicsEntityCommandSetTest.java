package dev.hytalemodding.impulse.core.internal.commands;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import dev.hytalemodding.impulse.core.internal.modules.physicsentity.commands.PhysicsEntityCommandSet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class PhysicsEntityCommandSetTest {

    @AfterEach
    void resetRegistry() {
        ImpulseCommandTreeRegistry.resetForTests();
    }

    @Test
    void physicsEntityRegistersVisualSettingsUnderImpulseSettings() {
        PhysicsEntityCommandSet.register();

        ImpulseCommand root = ImpulseCommandTreeRegistry.createRootCommandForTests();

        AbstractCommand visual = settings(root).getSubCommands().get("visual");
        assertTrue(settings(root).getSubCommands().containsKey("visual"));
        assertTrue(visual.getSubCommands().containsKey("sync"));
        assertTrue(visual.getSubCommands().containsKey("materialization"));
    }

    @Test
    void physicsEntityCommandSetIsIdempotentAndRemovable() {
        PhysicsEntityCommandSet.register();
        PhysicsEntityCommandSet.register();

        ImpulseCommand registered = ImpulseCommandTreeRegistry.createRootCommandForTests();
        assertTrue(settings(registered).getSubCommands().containsKey("visual"));

        PhysicsEntityCommandSet.unregister();

        ImpulseCommand removed = ImpulseCommandTreeRegistry.createRootCommandForTests();
        assertFalse(settings(removed).getSubCommands().containsKey("visual"));
    }

    private static AbstractCommand settings(ImpulseCommand root) {
        return root.getSubCommands().get("settings");
    }
}
