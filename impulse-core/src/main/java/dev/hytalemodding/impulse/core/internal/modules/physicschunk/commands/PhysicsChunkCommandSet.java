package dev.hytalemodding.impulse.core.internal.modules.physicschunk.commands;

import dev.hytalemodding.impulse.core.internal.commands.ImpulseCommandTreeRegistry;
import dev.hytalemodding.impulse.core.internal.commands.debug.DebugFlagCommand;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsDebugResource;

/**
 * Command set owned by the PhysicsChunk subplugin.
 */
public final class PhysicsChunkCommandSet {

    private static final String PHYSICS_CHUNK_ROOT_COMMAND_ID = "physicschunk.root";
    private static final String PHYSICS_CHUNK_DEBUG_COMMAND_ID = "physicschunk.debug";
    private static final String COLLISION_LOD_SETTINGS_COMMAND_ID =
        "physicschunk.settings.collision-lod";

    private PhysicsChunkCommandSet() {
    }

    public static void register() {
        ImpulseCommandTreeRegistry.registerRootAndSettingsSubCommands(
            PHYSICS_CHUNK_ROOT_COMMAND_ID,
            PhysicsChunkCommand::new,
            COLLISION_LOD_SETTINGS_COMMAND_ID,
            CollisionLodSettingsCommand::new);
        ImpulseCommandTreeRegistry.registerDebugSubCommand(
            PHYSICS_CHUNK_DEBUG_COMMAND_ID,
            PhysicsChunkCommandSet::debugCommand);
    }

    public static void unregister() {
        ImpulseCommandTreeRegistry.unregisterRootAndSettingsSubCommands(
            PHYSICS_CHUNK_ROOT_COMMAND_ID,
            COLLISION_LOD_SETTINGS_COMMAND_ID);
        ImpulseCommandTreeRegistry.unregisterDebugSubCommand(PHYSICS_CHUNK_DEBUG_COMMAND_ID);
    }

    private static DebugFlagCommand debugCommand() {
        return new DebugFlagCommand("physicschunk", "PhysicsChunk collision",
            PhysicsDebugResource::isDebugPhysicsChunkCollisionEnabled,
            PhysicsDebugResource::setDebugPhysicsChunkCollisionEnabled);
    }
}
