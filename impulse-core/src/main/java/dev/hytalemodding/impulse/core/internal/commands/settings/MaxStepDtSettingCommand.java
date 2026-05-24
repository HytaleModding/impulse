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
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsWorldSettings;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nonnull;

public class MaxStepDtSettingCommand extends AbstractAsyncPlayerCommand {

    private final OptionalArg<Float> dtArg = this.withOptionalArg(
        "dt",
        "Maximum substep dt used by adaptive step modes",
        ArgTypes.FLOAT);

    public MaxStepDtSettingCommand() {
        super("max-step-dt", "Get or set the adaptive substep dt limit");
    }

    @Nonnull
    @Override
    protected CompletableFuture<Void> executeAsync(@Nonnull CommandContext ctx,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull PlayerRef playerRef,
        @Nonnull World world) {
        PhysicsWorldResource resource = store.getResource(PhysicsWorldResource.getResourceType());
        if (!dtArg.provided(ctx)) {
            ctx.sender().sendMessage(Message.raw("Impulse max step dt: "
                + resource.getWorldSettings().getMaxStepDt() + " (used by adaptive step modes)"));
            return CompletableFuture.completedFuture(null);
        }

        float maxStepDt = dtArg.get(ctx);
        if (!Float.isFinite(maxStepDt) || maxStepDt <= 0f) {
            ctx.sender().sendMessage(Message.raw("Max step dt must be finite and greater than 0."));
            return CompletableFuture.completedFuture(null);
        }

        PhysicsWorldSettings settings = resource.getWorldSettings();
        settings.setMaxStepDt(maxStepDt);
        resource.setWorldSettings(settings);
        ctx.sender().sendMessage(Message.raw("Impulse max step dt set to " + maxStepDt));
        return CompletableFuture.completedFuture(null);
    }
}
