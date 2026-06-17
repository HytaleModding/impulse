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

    private static final String PHYSICS_CHUNK_ROOT_COMMAND_ID = "physicschunk.root";
    private static final String COLLISION_LOD_SETTINGS_COMMAND_ID =
        "physicschunk.settings.collision-lod";

    private PhysicsChunkCommands() {
    }

    public static void registerPhysicsChunkCommands(
        @Nonnull Supplier<? extends AbstractCommand> physicsChunkCommand,
        @Nonnull Supplier<? extends AbstractCommand> collisionLodSettingsCommand) {
        ImpulseCommandContributionRegistry.addRootAndSettingsSubCommands(
            PHYSICS_CHUNK_ROOT_COMMAND_ID,
            Objects.requireNonNull(physicsChunkCommand, "physicsChunkCommand"),
            COLLISION_LOD_SETTINGS_COMMAND_ID,
            Objects.requireNonNull(collisionLodSettingsCommand, "collisionLodSettingsCommand"));
    }

    public static void unregisterPhysicsChunkCommands() {
        ImpulseCommandContributionRegistry.removeRootAndSettingsSubCommands(
            PHYSICS_CHUNK_ROOT_COMMAND_ID,
            COLLISION_LOD_SETTINGS_COMMAND_ID);
    }
}
