package dev.hytalemodding.impulse.core.internal.commands;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncWorldCommand;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractWorldCommand;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.api.BackendId;
import dev.hytalemodding.impulse.api.Impulse;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.api.runtime.PhysicsBackendRuntimeProvider;
import dev.hytalemodding.impulse.core.ImpulsePlugin;
import dev.hytalemodding.impulse.core.internal.commands.SpaceDeleteSupport.DeleteResult;
import dev.hytalemodding.impulse.core.plugin.physics.PhysicsDiagnostics;
import dev.hytalemodding.impulse.core.plugin.physics.PhysicsAsync;
import dev.hytalemodding.impulse.core.plugin.physics.PhysicsSpaces;
import dev.hytalemodding.impulse.core.plugin.physics.PhysicsThreading;
import dev.hytalemodding.impulse.core.plugin.modules.physicschunk.PhysicsChunkCollisionMode;
import dev.hytalemodding.impulse.core.plugin.modules.physicschunk.settings.PhysicsChunkCollisionSettings;
import dev.hytalemodding.impulse.core.plugin.physics.SpaceSummary;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class SpaceCommand extends AbstractCommandCollection {

    public SpaceCommand() {
        super("space", "Explicit physics space lifecycle commands");
        addSubCommand(new CreateCommand());
        addSubCommand(new ListCommand());
        addSubCommand(new DeleteCommand());
    }

    private static final class CreateCommand extends AbstractWorldCommand {

        private final OptionalArg<String> backendArg = withOptionalArg(
            "backend",
            "Backend id, for example impulse:rapier",
            ArgTypes.STRING);
        private final OptionalArg<String> physicsChunkArg = withOptionalArg(
            "physicsChunk",
            "PhysicsChunk collision mode: none, manual, or streaming",
            ArgTypes.STRING);
        private CreateCommand() {
            super("create", "Create an explicit physics space", false);
        }

        @Override
        protected void execute(@Nonnull CommandContext context,
            @Nonnull World world,
            @Nonnull Store<EntityStore> store) {
            BackendId backendId = parseBackendId(context, backendArg, true);
            if (backendId == null) {
                return;
            }

            PhysicsChunkCollisionMode physicsChunkMode = physicsChunkArg.provided(context)
                ? parsePhysicsChunkMode(physicsChunkArg.get(context))
                : PhysicsChunkCollisionMode.STREAMING;
            if (physicsChunkMode == null) {
                context.sendMessage(Message.raw("physicsChunk must be none, manual, or streaming."));
                return;
            }

            Store<PhysicsStore> physicsStore = PhysicsThreading.store(world);
            try {
                Impulse.getRuntimeProvider(backendId);
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

    private static final class ListCommand extends AbstractAsyncWorldCommand {

        private ListCommand() {
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
    }

    private static final class DeleteCommand extends AbstractAsyncWorldCommand {

        private final OptionalArg<Integer> spaceArg = withOptionalArg(
            "space",
            "Space id to delete",
            ArgTypes.INTEGER);

        private DeleteCommand() {
            super("delete", "Delete a physics space and its runtime backend state", true);
        }

        @Nonnull
        @Override
        protected CompletableFuture<Void> executeAsync(@Nonnull CommandContext context,
            @Nonnull World world) {
            if (!spaceArg.provided(context)) {
                context.sendMessage(Message.raw("Missing space id. Example:"
                    + " /impulse space delete --space=1 --confirm"));
                return CompletableFuture.completedFuture(null);
            }

            int rawSpaceId = spaceArg.get(context);
            return PhysicsThreading.callWhenBackendIdleOnWorldThread(world,
                    "delete PhysicsStore space",
                    physicsStore -> SpaceDeleteSupport.deleteOnWorldThread(world,
                        physicsStore,
                        rawSpaceId))
                .handle((result, failure) -> {
                    sendDeleteResult(context, world, result, failure);
                    return (Void) null;
                })
                .toCompletableFuture();
        }

        private static void sendDeleteResult(@Nonnull CommandContext context,
            @Nonnull World world,
            @Nullable DeleteResult result,
            @Nullable Throwable failure) {
            Runnable sender = () -> {
                if (failure != null) {
                    Throwable cause = unwrap(failure);
                    String message = cause.getMessage() != null
                        ? cause.getMessage()
                        : cause.toString();
                    context.sendMessage(Message.raw("Failed to delete physics space: "
                        + message));
                    return;
                }
                if (result == null) {
                    context.sendMessage(Message.raw("Failed to delete physics space."));
                    return;
                }
                switch (result.outcome()) {
                    case INVALID -> context.sendMessage(Message.raw(
                        "Space id must be a positive integer."));
                    case MISSING -> context.sendMessage(Message.raw("No physics space id="
                        + result.rawSpaceId()
                        + " exists in world " + world.getName() + "."));
                    case UNBOUND -> context.sendMessage(Message.raw("PhysicsStore space id="
                        + result.rawSpaceId()
                        + " is not bound in world " + world.getName() + "."));
                    case NOT_EMPTY -> context.sendMessage(Message.raw("Physics space id="
                        + result.rawSpaceId()
                        + " is not empty (" + result.registeredBodies()
                        + " registered bodies, " + result.backendBodies()
                        + " backend bodies, " + result.joints() + " joints)."
                        + " Use /impulse clean for populated worlds, then delete empty spaces."));
                    case DELETED -> context.sendMessage(Message.raw("Deleted physics space id="
                        + result.rawSpaceId()
                        + " with " + result.backendBodies()
                        + " backend bodies and " + result.joints() + " joints."));
                }
            };
            if (world.isInThread()) {
                sender.run();
                return;
            }
            world.execute(sender);
        }
    }

    @Nullable
    private static BackendId parseBackendId(@Nonnull CommandContext context,
        @Nonnull OptionalArg<String> backendArg,
        boolean useDefaultWhenMissing) {
        if (!backendArg.provided(context)) {
            if (!useDefaultWhenMissing) {
                return null;
            }

            BackendId defaultBackendId = ImpulsePlugin.get().getDefaultBackendId();
            if (defaultBackendId != null) {
                return defaultBackendId;
            }

            context.sendMessage(Message.raw("Missing backend id. Multiple backends are installed; "
                + "use --backend=<id>. Available backends: " + availableBackendIds()));
            return null;
        }

        String rawBackendId = backendArg.get(context).trim();
        try {
            return new BackendId(rawBackendId);
        } catch (RuntimeException exception) {
            context.sendMessage(Message.raw("Invalid backend id: " + rawBackendId));
            return null;
        }
    }

    @Nullable
    private static PhysicsChunkCollisionMode parsePhysicsChunkMode(@Nonnull String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "none", "off", "disabled" -> PhysicsChunkCollisionMode.NONE;
            case "manual" -> PhysicsChunkCollisionMode.MANUAL;
            case "streaming", "stream", "on", "enabled" -> PhysicsChunkCollisionMode.STREAMING;
            default -> null;
        };
    }

    @Nonnull
    private static String availableBackendIds() {
        List<String> backendIds = new ArrayList<>();
        for (PhysicsBackendRuntimeProvider provider : Impulse.getRuntimeProviders()) {
            backendIds.add(provider.getId().value());
        }
        backendIds.sort(String::compareTo);
        return backendIds.isEmpty() ? "<none>" : String.join(", ", backendIds);
    }

    private record SpaceListEntry(@Nonnull SpaceId spaceId,
                                  @Nonnull String backendId,
                                  int bodies,
                                  int joints,
                                  @Nonnull PhysicsChunkCollisionMode physicsChunkMode) {
    }

    @Nonnull
    private static Throwable unwrap(@Nonnull Throwable failure) {
        if (failure instanceof CompletionException completionException
            && completionException.getCause() != null) {
            return completionException.getCause();
        }
        return failure;
    }
}
