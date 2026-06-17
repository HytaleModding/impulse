package dev.hytalemodding.impulse.core.internal.commands;

import static org.junit.jupiter.api.Assertions.assertFalse;

import com.hypixel.hytale.server.core.command.system.AbstractCommand;
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

    private static AbstractCommand settings(ImpulseCommand root) {
        return root.getSubCommands().get("settings");
    }
}
