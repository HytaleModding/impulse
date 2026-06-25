package dev.hytalemodding.impulse.core.internal.commands;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import dev.hytalemodding.impulse.core.internal.modules.physicschunk.commands.PhysicsChunkCommandSet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class PhysicsChunkCommandSetTest {

    @AfterEach
    void resetRegistry() {
        ImpulseCommandTreeRegistry.resetForTests();
    }

    @Test
    void physicsChunkRegistersCommandSetUnderImpulseRoot() {
        PhysicsChunkCommandSet.register();

        ImpulseCommand root = ImpulseCommandTreeRegistry.createRootCommandForTests();

        AbstractCommand physicsChunk = root.getSubCommands().get("physicschunk");
        assertTrue(root.getSubCommands().containsKey("physicschunk"));
        assertTrue(physicsChunk.getSubCommands().containsKey("settings"));
        assertTrue(physicsChunk.getSubCommands().containsKey("perf"));
        assertTrue(debug(root).getSubCommands().containsKey("physicschunk"));
        assertTrue(settings(root).getSubCommands().containsKey("collision-lod"));
    }

    @Test
    void physicsChunkCommandSetIsIdempotentAndRemovable() {
        PhysicsChunkCommandSet.register();
        PhysicsChunkCommandSet.register();

        ImpulseCommand registered = ImpulseCommandTreeRegistry.createRootCommandForTests();
        assertTrue(registered.getSubCommands().containsKey("physicschunk"));
        assertTrue(debug(registered).getSubCommands().containsKey("physicschunk"));
        assertTrue(settings(registered).getSubCommands().containsKey("collision-lod"));

        PhysicsChunkCommandSet.unregister();

        ImpulseCommand removed = ImpulseCommandTreeRegistry.createRootCommandForTests();
        assertFalse(removed.getSubCommands().containsKey("physicschunk"));
        assertFalse(debug(removed).getSubCommands().containsKey("physicschunk"));
        assertFalse(settings(removed).getSubCommands().containsKey("collision-lod"));
    }

    private static AbstractCommand debug(ImpulseCommand root) {
        return root.getSubCommands().get("debug");
    }

    private static AbstractCommand settings(ImpulseCommand root) {
        return root.getSubCommands().get("settings");
    }
}
