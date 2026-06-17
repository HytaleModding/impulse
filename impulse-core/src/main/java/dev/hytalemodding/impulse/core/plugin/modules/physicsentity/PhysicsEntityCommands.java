package dev.hytalemodding.impulse.core.plugin.modules.physicsentity;

import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import dev.hytalemodding.impulse.core.internal.commands.ImpulseCommandContributionRegistry;
import java.util.Objects;
import java.util.function.Supplier;
import javax.annotation.Nonnull;

/**
 * Public command contribution endpoint for the PhysicsEntity module.
 */
public final class PhysicsEntityCommands {

    private static final String VISUAL_SETTINGS_COMMAND_ID = "physicsentity.settings.visual";

    private PhysicsEntityCommands() {
    }

    public static void registerPhysicsEntityCommands(
        @Nonnull Supplier<? extends AbstractCommand> visualSettingsCommand) {
        ImpulseCommandContributionRegistry.addSettingsSubCommand(
            VISUAL_SETTINGS_COMMAND_ID,
            Objects.requireNonNull(visualSettingsCommand, "visualSettingsCommand"));
    }

    public static void unregisterPhysicsEntityCommands() {
        ImpulseCommandContributionRegistry.removeSettingsSubCommand(VISUAL_SETTINGS_COMMAND_ID);
    }
}
