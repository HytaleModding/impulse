package dev.hytalemodding.impulse.core.internal.commands;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import dev.hytalemodding.impulse.core.internal.modules.physicschunk.commands.PhysicsChunkCommandContributions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class PhysicsChunkCommandContributionRegistryTest {

    @AfterEach
    void resetRegistry() {
        ImpulseCommandContributionRegistry.resetForTests();
    }

    @Test
    void physicsChunkContributesCommandsUnderImpulseRoot() {
        PhysicsChunkCommandContributions.register();

        ImpulseCommand root = ImpulseCommandContributionRegistry.createRootCommandForTests();

        AbstractCommand physicsChunk = root.getSubCommands().get("physicschunk");
        assertTrue(root.getSubCommands().containsKey("physicschunk"));
        assertTrue(physicsChunk.getSubCommands().containsKey("settings"));
        assertTrue(physicsChunk.getSubCommands().containsKey("perf"));
        assertTrue(settings(root).getSubCommands().containsKey("collision-lod"));
    }

    @Test
    void physicsChunkContributionsAreIdempotentAndRemovable() {
        PhysicsChunkCommandContributions.register();
        PhysicsChunkCommandContributions.register();

        ImpulseCommand contributed = ImpulseCommandContributionRegistry.createRootCommandForTests();
        assertTrue(contributed.getSubCommands().containsKey("physicschunk"));
        assertTrue(settings(contributed).getSubCommands().containsKey("collision-lod"));

        PhysicsChunkCommandContributions.unregister();

        ImpulseCommand removed = ImpulseCommandContributionRegistry.createRootCommandForTests();
        assertFalse(removed.getSubCommands().containsKey("physicschunk"));
        assertFalse(settings(removed).getSubCommands().containsKey("collision-lod"));
    }

    private static AbstractCommand settings(ImpulseCommand root) {
        return root.getSubCommands().get("settings");
    }
}
