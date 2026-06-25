package dev.hytalemodding.impulse.core.internal.commands.space;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncWorldCommand;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.plugin.modules.physicschunk.PhysicsChunkCollisionMode;
import dev.hytalemodding.impulse.core.plugin.modules.physicschunk.settings.PhysicsChunkCollisionSettings;
import dev.hytalemodding.impulse.core.plugin.physics.PhysicsAsync;
import dev.hytalemodding.impulse.core.plugin.physics.PhysicsDiagnostics;
import dev.hytalemodding.impulse.core.plugin.physics.PhysicsSpaces;
import dev.hytalemodding.impulse.core.plugin.physics.PhysicsThreading;
import dev.hytalemodding.impulse.core.plugin.physics.SpaceSummary;
import javax.annotation.Nonnull;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

public class SpaceListCommand extends AbstractAsyncWorldCommand {

    SpaceListCommand() {
        super("list", "List physics spaces in the target world", false);
    }

    @Nonnull
    @Override
    protected CompletableFuture<Void> executeAsync(@Nonnull CommandContext context,
        @Nonnull World world) {
        Store<PhysicsStore> physicsStore = PhysicsThreading.store(world);
        return PhysicsAsync.acceptOnWorldThread(world,
            PhysicsDiagnostics.spaceSummariesAsync(world),
            summaries -> sendSpaces(context, world, physicsStore, summaries));
    }

    private static void sendSpaces(@Nonnull CommandContext context,
        @Nonnull World world,
        @Nonnull Store<PhysicsStore> physicsStore,
        @Nonnull List<SpaceSummary> summaries) {
        List<SpaceListEntry> spaces = summaries.stream()
            .map(summary -> {
                PhysicsChunkCollisionSettings settings = PhysicsSpaces.chunkCollisionSettings(
                    physicsStore,
                    summary.spaceId());
                PhysicsChunkCollisionMode physicsChunkMode = settings != null
                    ? settings.getMode()
                    : PhysicsChunkCollisionMode.NONE;
                return new SpaceListEntry(summary.spaceId(),
                    summary.backendId().value(),
                    summary.bodyCount(),
                    summary.jointCount(),
                    physicsChunkMode);
            })
            .sorted(Comparator.comparingInt(entry -> entry.spaceId().value()))
            .toList();

        context.sendMessage(Message.raw("Physics spaces in world " + world.getName() + ":"));
        if (spaces.isEmpty()) {
            context.sendMessage(Message.raw("- <none>"));
            return;
        }

        for (SpaceListEntry space : spaces) {
            context.sendMessage(Message.raw("- id=" + space.spaceId().value()
                + " backend=" + space.backendId()
                + " bodies=" + space.bodies()
                + " joints=" + space.joints()
                + " physicsChunk="
                + space.physicsChunkMode().name().toLowerCase(Locale.ROOT)));
        }
    }

    private record SpaceListEntry(@Nonnull SpaceId spaceId,
                                  @Nonnull String backendId,
                                  int bodies,
                                  int joints,
                                  @Nonnull PhysicsChunkCollisionMode physicsChunkMode) {
    }
}