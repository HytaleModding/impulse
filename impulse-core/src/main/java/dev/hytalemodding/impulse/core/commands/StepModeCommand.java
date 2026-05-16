package dev.hytalemodding.impulse.core.commands;

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
import dev.hytalemodding.impulse.api.PhysicsSpace;
import dev.hytalemodding.impulse.core.resources.PhysicsStepMode;
import dev.hytalemodding.impulse.core.resources.PhysicsWorldResource;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nonnull;

public class StepModeCommand extends AbstractAsyncPlayerCommand {

    private final OptionalArg<String> modeArg = this.withOptionalArg(
        "mode",
        "Physics step mode: progressive_refinement, fixed, or ccd",
        ArgTypes.STRING);

    public StepModeCommand() {
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
                + resource.getStepMode().getSerializedName()));
            return CompletableFuture.completedFuture(null);
        }

        PhysicsStepMode stepMode;
        try {
            stepMode = PhysicsStepMode.parse(modeArg.get(ctx));
        } catch (IllegalArgumentException exception) {
            ctx.sender().sendMessage(Message.raw("Unknown step mode. Use one of: "
                + "progressive_refinement, fixed, ccd."));
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

        resource.setStepMode(stepMode);
        ctx.sender().sendMessage(Message.raw("Impulse step mode set to "
            + stepMode.getSerializedName()));
        return CompletableFuture.completedFuture(null);
    }

    @Nonnull
    private static List<String> unsupportedCcdSpaces(@Nonnull PhysicsWorldResource resource) {
        List<String> unsupportedSpaces = new ArrayList<>();
        for (PhysicsSpace space : resource.iterateSpaces()) {
            if (space.supportsContinuousCollision()) {
                continue;
            }

            unsupportedSpaces.add("space " + space.getId().value() + " ("
                + space.getBackendId().value() + ")");
        }
        return unsupportedSpaces;
    }
}
