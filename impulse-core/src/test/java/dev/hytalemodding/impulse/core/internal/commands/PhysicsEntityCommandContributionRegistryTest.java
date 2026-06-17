package dev.hytalemodding.impulse.core.internal.commands;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import dev.hytalemodding.impulse.core.internal.modules.physicsentity.commands.PhysicsEntityCommandContributions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class PhysicsEntityCommandContributionRegistryTest {

    @AfterEach
    void resetRegistry() {
        ImpulseCommandContributionRegistry.resetForTests();
    }

    @Test
    void physicsEntityContributesVisualSettingsUnderImpulseSettings() {
        PhysicsEntityCommandContributions.register();

        ImpulseCommand root = ImpulseCommandContributionRegistry.createRootCommandForTests();

        AbstractCommand visual = settings(root).getSubCommands().get("visual");
        assertTrue(settings(root).getSubCommands().containsKey("visual"));
        assertTrue(visual.getSubCommands().containsKey("sync"));
        assertTrue(visual.getSubCommands().containsKey("materialization"));
    }

    @Test
    void physicsEntityContributionsAreIdempotentAndRemovable() {
        PhysicsEntityCommandContributions.register();
        PhysicsEntityCommandContributions.register();

        ImpulseCommand contributed = ImpulseCommandContributionRegistry.createRootCommandForTests();
        assertTrue(settings(contributed).getSubCommands().containsKey("visual"));

        PhysicsEntityCommandContributions.unregister();

        ImpulseCommand removed = ImpulseCommandContributionRegistry.createRootCommandForTests();
        assertFalse(settings(removed).getSubCommands().containsKey("visual"));
    }

    private static AbstractCommand settings(ImpulseCommand root) {
        return root.getSubCommands().get("settings");
    }
}
