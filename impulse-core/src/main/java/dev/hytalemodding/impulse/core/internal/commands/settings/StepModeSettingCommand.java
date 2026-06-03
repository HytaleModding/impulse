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
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsStepMode;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsWorldResource;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsWorldSettings;
import dev.hytalemodding.impulse.core.plugin.simulation.SpaceSummary;
import dev.hytalemodding.impulse.core.plugin.simulation.query.UnsupportedCcdSpacesQuery;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nonnull;

public class StepModeSettingCommand extends AbstractAsyncPlayerCommand {

    private final OptionalArg<String> modeArg = this.withOptionalArg(
        "mode",
        "Physics step mode: progressive_refinement, adaptive, fixed, or ccd",
        ArgTypes.STRING);

    public StepModeSettingCommand() {
        super("step-mode", "Get or set the world physics step mode");
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
            ctx.sender().sendMessage(Message.raw("Impulse step mode: "
                + resource.getWorldSettings().getStepMode().getSerializedName()));
            return CompletableFuture.completedFuture(null);
        }

        PhysicsStepMode stepMode;
        try {
            stepMode = PhysicsStepMode.parse(modeArg.get(ctx));
        } catch (IllegalArgumentException exception) {
            ctx.sender().sendMessage(Message.raw("Unknown step mode. Use one of: "
                + "progressive_refinement, adaptive, fixed, ccd."));
            return CompletableFuture.completedFuture(null);
        }

        if (stepMode == PhysicsStepMode.CCD) {
            List<String> unsupportedSpaces = unsupportedCcdSpaces(resource);
            if (!unsupportedSpaces.isEmpty()) {
                ctx.sender().sendMessage(Message.raw("CCD mode is not available for: "
                    + String.join(", ", unsupportedSpaces)));
                return CompletableFuture.completedFuture(null);
            }
        }

        PhysicsWorldSettings settings = resource.getWorldSettings();
        settings.setStepMode(stepMode);
        resource.setWorldSettings(settings);
        ctx.sender().sendMessage(Message.raw("Impulse step mode set to "
            + stepMode.getSerializedName()));
        return CompletableFuture.completedFuture(null);
    }

    @Nonnull
    private static List<String> unsupportedCcdSpaces(@Nonnull PhysicsWorldResource resource) {
        return resource.query(new UnsupportedCcdSpacesQuery())
            .completion()
            .toCompletableFuture()
            .join()
            .stream()
            .map(StepModeSettingCommand::formatSpace)
            .toList();
    }

    @Nonnull
    private static String formatSpace(@Nonnull SpaceSummary summary) {
        return "space " + summary.spaceId().value() + " (" + summary.backendId().value() + ")";
    }
}
