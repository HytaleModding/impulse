package dev.hytalemodding.impulse.core.internal.commands.space;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractWorldCommand;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.api.BackendId;
import dev.hytalemodding.impulse.api.ImpulseBackendRegistry;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.plugin.modules.physicschunk.PhysicsChunkCollisionMode;
import dev.hytalemodding.impulse.core.plugin.modules.physicschunk.settings.PhysicsChunkCollisionSettings;
import dev.hytalemodding.impulse.core.plugin.physics.PhysicsSpaces;
import dev.hytalemodding.impulse.core.plugin.physics.PhysicsThreading;
import javax.annotation.Nonnull;
import java.util.Locale;

public class SpaceCreateCommand extends AbstractWorldCommand {

    private final OptionalArg<String> backendArg = withOptionalArg(
        "backend",
        "Backend id, for example impulse:rapier",
        ArgTypes.STRING);
    private final OptionalArg<String> physicsChunkArg = withOptionalArg(
        "physicsChunk",
        "PhysicsChunk collision mode: none, manual, or streaming",
        ArgTypes.STRING);

    SpaceCreateCommand() {
        super("create", "Create an explicit physics space", false);
    }

    @Override
    protected void execute(@Nonnull CommandContext context,
        @Nonnull World world,
        @Nonnull Store<EntityStore> store) {
        BackendId backendId = SpaceCommand.parseBackendId(context, backendArg, true);
        if (backendId == null) {
            return;
        }

        PhysicsChunkCollisionMode physicsChunkMode = physicsChunkArg.provided(context)
            ? SpaceCommand.parsePhysicsChunkMode(physicsChunkArg.get(context))
            : PhysicsChunkCollisionMode.STREAMING;
        if (physicsChunkMode == null) {
            context.sendMessage(
                Message.raw("physicsChunk must be none, manual, or streaming."));
            return;
        }

        Store<PhysicsStore> physicsStore = PhysicsThreading.store(world);
        try {
            ImpulseBackendRegistry.getRuntimeProvider(backendId);
            SpaceId spaceId = PhysicsSpaces.create(physicsStore, backendId);
            PhysicsChunkCollisionSettings chunkCollisionSettings =
                new PhysicsChunkCollisionSettings();
            chunkCollisionSettings.setMode(physicsChunkMode);
            PhysicsSpaces.putChunkCollisionSettings(physicsStore,
                spaceId,
                chunkCollisionSettings);
            context.sendMessage(Message.raw("Created physics space id="
                + spaceId.value()
                + " backend=" + backendId.value()
                + " physicsChunk=" + physicsChunkMode.name().toLowerCase(Locale.ROOT)
                + "."));
        } catch (RuntimeException exception) {
            context.sendMessage(Message.raw("Failed to create physics space: "
                + exception.getMessage()));
        }
    }
}