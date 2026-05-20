package dev.hytalemodding.impulse.core.commands.settings;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractWorldCommand;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.resources.PhysicsSpaceSettings;
import dev.hytalemodding.impulse.core.resources.PhysicsSpaceSettings.ExecutionMode;
import dev.hytalemodding.impulse.core.resources.PhysicsWorldResource;
import java.util.Locale;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class ExecutionSettingCommand extends AbstractWorldCommand {

    private final OptionalArg<String> modeArg = this.withOptionalArg(
        "mode",
        "Physics execution mode: inline or worker",
        ArgTypes.STRING);

    public ExecutionSettingCommand() {
        super("execution", "Get or set physics execution mode for the default physics space", true);
    }

    @Override
    protected void execute(@Nonnull CommandContext ctx,
        @Nonnull World world,
        @Nonnull Store<EntityStore> store) {
        PhysicsWorldResource resource = store.getResource(PhysicsWorldResource.getResourceType());
        SpaceId defaultSpaceId = resource.getDefaultSpaceId();
        if (defaultSpaceId == null || resource.getSpace(defaultSpaceId) == null) {
            ctx.sender().sendMessage(Message.raw("No default physics space exists yet."));
            return;
        }

        PhysicsSpaceSettings settings = new PhysicsSpaceSettings(resource.getSpaceSettings(defaultSpaceId));
        if (!modeArg.provided(ctx)) {
            sendSummary(ctx, defaultSpaceId, settings);
            return;
        }

        ExecutionMode mode = parseMode(modeArg.get(ctx));
        if (mode == null) {
            ctx.sender().sendMessage(Message.raw("mode must be inline or worker."));
            return;
        }
        if (mode == ExecutionMode.WORKER) {
            ctx.sender().sendMessage(Message.raw(
                "Worker physics execution is not available yet; keeping executionMode=inline."));
            return;
        }

        settings.setExecutionMode(mode);
        resource.setSpaceSettings(defaultSpaceId, settings);
        sendSummary(ctx, defaultSpaceId, settings);
    }

    private static void sendSummary(@Nonnull CommandContext ctx,
        @Nonnull SpaceId spaceId,
        @Nonnull PhysicsSpaceSettings settings) {
        ctx.sender().sendMessage(Message.raw("Impulse execution settings for space "
            + spaceId.value()
            + ": mode=" + settings.getExecutionMode().name().toLowerCase(Locale.ROOT)
            + " workerAvailable=false"));
    }

    @Nullable
    private static ExecutionMode parseMode(@Nonnull String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "inline", "main", "main-thread", "main_thread" -> ExecutionMode.INLINE;
            case "worker", "thread", "threaded", "async" -> ExecutionMode.WORKER;
            default -> null;
        };
    }
}
