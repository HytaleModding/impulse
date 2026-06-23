package dev.hytalemodding.impulse.core.internal.commands.space;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import dev.hytalemodding.impulse.api.BackendId;
import dev.hytalemodding.impulse.api.ImpulseBackendRegistry;
import dev.hytalemodding.impulse.api.runtime.PhysicsBackendRuntimeProvider;
import dev.hytalemodding.impulse.core.ImpulsePlugin;
import dev.hytalemodding.impulse.core.plugin.modules.physicschunk.PhysicsChunkCollisionMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletionException;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class SpaceCommand extends AbstractCommandCollection {

    public SpaceCommand() {
        super("space", "Explicit physics space lifecycle commands");
        addSubCommand(new SpaceCreateCommand());
        addSubCommand(new SpaceListCommand());
        addSubCommand(new SpaceDeleteCommand());
    }

    @Nullable
    static BackendId parseBackendId(@Nonnull CommandContext context,
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
    static PhysicsChunkCollisionMode parsePhysicsChunkMode(@Nonnull String value) {
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
        for (PhysicsBackendRuntimeProvider provider : ImpulseBackendRegistry.getRuntimeProviders()) {
            backendIds.add(provider.getId().value());
        }
        backendIds.sort(String::compareTo);
        return backendIds.isEmpty() ? "<none>" : String.join(", ", backendIds);
    }


    @Nonnull
    static Throwable unwrap(@Nonnull Throwable failure) {
        if (failure instanceof CompletionException completionException
            && completionException.getCause() != null) {
            return completionException.getCause();
        }
        return failure;
    }
}
