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
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.core.plugin.physics.PhysicsWorlds;
import dev.hytalemodding.impulse.core.plugin.physics.PhysicsThreading;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsStepSchedulingMode;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsWorldSettings;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nonnull;

public class StepSchedulingSettingCommand extends AbstractAsyncPlayerCommand {

    private final OptionalArg<String> modeArg = this.withOptionalArg(
        "mode",
        "Step scheduling mode: drop_pending_dt keeps cadence steady; accumulate_pending_dt catches up",
        ArgTypes.STRING);

    public StepSchedulingSettingCommand() {
        super("scheduling", "Get or set how pending store tick dt is handled");
    }

    @Nonnull
    @Override
    protected CompletableFuture<Void> executeAsync(@Nonnull CommandContext ctx,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull PlayerRef playerRef,
        @Nonnull World world) {
        Store<PhysicsStore> physicsStore = PhysicsThreading.store(world);
        if (!modeArg.provided(ctx)) {
            PhysicsStepSchedulingMode mode = PhysicsWorlds.settings(physicsStore)
                .getStepSchedulingMode();
            ctx.sender().sendMessage(Message.raw("Impulse step scheduling: "
                + mode.getSerializedName() + " (" + mode.describePendingStepBehavior() + ")"));
            return CompletableFuture.completedFuture(null);
        }

        PhysicsStepSchedulingMode mode;
        try {
            mode = PhysicsStepSchedulingMode.parse(modeArg.get(ctx));
        } catch (IllegalArgumentException exception) {
            ctx.sender().sendMessage(Message.raw("Unknown step scheduling mode. Use one of: "
                + "drop_pending_dt (steady/no catch-up), accumulate_pending_dt (catch-up)."));
            return CompletableFuture.completedFuture(null);
        }

        PhysicsWorldSettings settings = PhysicsWorlds.settings(physicsStore);
        settings.setStepSchedulingMode(mode);
        PhysicsWorlds.putSettings(physicsStore, settings);
        ctx.sender().sendMessage(Message.raw("Impulse step scheduling set to "
            + mode.getSerializedName() + " (" + mode.describePendingStepBehavior() + ")"));
        return CompletableFuture.completedFuture(null);
    }
}
