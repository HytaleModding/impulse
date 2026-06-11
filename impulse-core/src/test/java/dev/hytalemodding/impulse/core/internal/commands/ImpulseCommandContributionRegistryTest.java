package dev.hytalemodding.impulse.core.internal.commands;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import dev.hytalemodding.impulse.core.internal.modules.worldcollision.commands.WorldCollisionCommandContributions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ImpulseCommandContributionRegistryTest {

    @AfterEach
    void resetRegistry() {
        ImpulseCommandContributionRegistry.resetForTests();
    }

    @Test
    void coreRootDoesNotOwnWorldCollisionCommandsByDefault() {
        ImpulseCommand root = ImpulseCommandContributionRegistry.createRootCommandForTests();

        assertFalse(root.getSubCommands().containsKey("worldcollision"));
        assertFalse(settings(root).getSubCommands().containsKey("collision-lod"));
    }

    @Test
    void worldCollisionContributesCommandsUnderImpulseRoot() {
        WorldCollisionCommandContributions.register();

        ImpulseCommand root = ImpulseCommandContributionRegistry.createRootCommandForTests();

        AbstractCommand worldCollision = root.getSubCommands().get("worldcollision");
        assertTrue(root.getSubCommands().containsKey("worldcollision"));
        assertTrue(worldCollision.getSubCommands().containsKey("settings"));
        assertTrue(worldCollision.getSubCommands().containsKey("perf"));
        assertTrue(settings(root).getSubCommands().containsKey("collision-lod"));
    }

    @Test
    void worldCollisionContributionsAreIdempotentAndRemovable() {
        WorldCollisionCommandContributions.register();
        WorldCollisionCommandContributions.register();

        ImpulseCommand contributed = ImpulseCommandContributionRegistry.createRootCommandForTests();
        assertTrue(contributed.getSubCommands().containsKey("worldcollision"));
        assertTrue(settings(contributed).getSubCommands().containsKey("collision-lod"));

        WorldCollisionCommandContributions.unregister();

        ImpulseCommand removed = ImpulseCommandContributionRegistry.createRootCommandForTests();
        assertFalse(removed.getSubCommands().containsKey("worldcollision"));
        assertFalse(settings(removed).getSubCommands().containsKey("collision-lod"));
    }

    private static AbstractCommand settings(ImpulseCommand root) {
        return root.getSubCommands().get("settings");
    }
}
