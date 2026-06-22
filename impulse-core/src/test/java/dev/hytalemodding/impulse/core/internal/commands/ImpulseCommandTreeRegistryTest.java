package dev.hytalemodding.impulse.core.internal.commands;

import static org.junit.jupiter.api.Assertions.assertFalse;

import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ImpulseCommandTreeRegistryTest {

    @AfterEach
    void resetRegistry() {
        ImpulseCommandTreeRegistry.resetForTests();
    }

    @Test
    void coreRootDoesNotOwnPhysicsChunkCommandsByDefault() {
        ImpulseCommand root = ImpulseCommandTreeRegistry.createRootCommandForTests();

        assertFalse(root.getSubCommands().containsKey("physicschunk"));
        assertFalse(debug(root).getSubCommands().containsKey("physicschunk"));
        assertFalse(settings(root).getSubCommands().containsKey("collision-lod"));
        assertFalse(settings(root).getSubCommands().containsKey("visual"));
    }

    private static AbstractCommand debug(ImpulseCommand root) {
        return root.getSubCommands().get("debug");
    }

    private static AbstractCommand settings(ImpulseCommand root) {
        return root.getSubCommands().get("settings");
    }
}
