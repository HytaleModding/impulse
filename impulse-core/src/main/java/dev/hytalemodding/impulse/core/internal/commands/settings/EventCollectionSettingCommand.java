package dev.hytalemodding.impulse.core.internal.commands.settings;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsWorldResource;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsEventCollectionMode;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsWorldSettings;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nonnull;

public class EventCollectionSettingCommand extends AbstractAsyncPlayerCommand {

    private final OptionalArg<String> modeArg = this.withOptionalArg(
        "mode",
        "Physics event collection mode: disabled or contacts",
        ArgTypes.STRING);

    public EventCollectionSettingCommand() {
        super("events", "Get or set backend event collection");
    }

    @Nonnull
    @Override
    protected CompletableFuture<Void> executeAsync(@Nonnull CommandContext ctx,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull PlayerRef playerRef,
        @Nonnull World world) {
        PhysicsWorldResource resource = store.getResource(PhysicsWorldResource.getResourceType());
        if (!modeArg.provided(ctx)) {
            PhysicsEventCollectionMode mode = resource.getWorldSettings().getEventCollectionMode();
            ctx.sender().sendMessage(Message.raw("Impulse event collection: "
                + mode.getSerializedName()));
            return CompletableFuture.completedFuture(null);
        }

        PhysicsEventCollectionMode mode;
        try {
            mode = PhysicsEventCollectionMode.parse(modeArg.get(ctx));
        } catch (IllegalArgumentException exception) {
            ctx.sender().sendMessage(Message.raw("Unknown event collection mode. Use one of: "
                + "disabled, contacts."));
            return CompletableFuture.completedFuture(null);
        }

        PhysicsWorldSettings settings = resource.getWorldSettings();
        settings.setEventCollectionMode(mode);
        resource.setWorldSettings(settings);
        ctx.sender().sendMessage(Message.raw("Impulse event collection set to "
            + mode.getSerializedName()));
        return CompletableFuture.completedFuture(null);
    }
}
