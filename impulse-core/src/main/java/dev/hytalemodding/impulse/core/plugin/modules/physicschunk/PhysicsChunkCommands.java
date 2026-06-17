package dev.hytalemodding.impulse.core.plugin.modules.physicschunk;

import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import dev.hytalemodding.impulse.core.internal.commands.ImpulseCommandContributionRegistry;
import java.util.Objects;
import java.util.function.Supplier;
import javax.annotation.Nonnull;

/**
 * Public command contribution endpoint for the physics chunk module.
 */
public final class PhysicsChunkCommands {

    private static final String WORLD_COLLISION_ROOT_COMMAND_ID = "worldcollision.root";
    private static final String COLLISION_LOD_SETTINGS_COMMAND_ID =
        "worldcollision.settings.collision-lod";

    private PhysicsChunkCommands() {
    }

    public static void registerWorldCollisionCommands(
        @Nonnull Supplier<? extends AbstractCommand> worldCollisionCommand,
        @Nonnull Supplier<? extends AbstractCommand> collisionLodSettingsCommand) {
        ImpulseCommandContributionRegistry.addRootAndSettingsSubCommands(
            WORLD_COLLISION_ROOT_COMMAND_ID,
            Objects.requireNonNull(worldCollisionCommand, "worldCollisionCommand"),
            COLLISION_LOD_SETTINGS_COMMAND_ID,
            Objects.requireNonNull(collisionLodSettingsCommand, "collisionLodSettingsCommand"));
    }

    public static void unregisterWorldCollisionCommands() {
        ImpulseCommandContributionRegistry.removeRootAndSettingsSubCommands(
            WORLD_COLLISION_ROOT_COMMAND_ID,
            COLLISION_LOD_SETTINGS_COMMAND_ID);
    }
}
