package dev.hytalemodding.impulse.core.internal.commands.settings;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncWorldCommand;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.internal.commands.SpaceSelection;
import dev.hytalemodding.impulse.core.plugin.physicsstore.PhysicsDiagnostics;
import dev.hytalemodding.impulse.core.plugin.physicsstore.PhysicsAsync;
import dev.hytalemodding.impulse.core.plugin.physicsstore.PhysicsSpaces;
import dev.hytalemodding.impulse.core.plugin.physicsstore.PhysicsThreading;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsSpaceSettings;
import dev.hytalemodding.impulse.core.plugin.simulation.SolverCapabilitySummary;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nonnull;

public class SolverSettingsCommand extends AbstractAsyncWorldCommand {

    private final OptionalArg<Integer> solverIterationsArg = this.withOptionalArg(
        "solverIterations",
        "Constraint solver iterations",
        ArgTypes.INTEGER);
    private final OptionalArg<Integer> stabilizationIterationsArg = this.withOptionalArg(
        "stabilizationIterations",
        "Stabilization iterations per solver iteration",
        ArgTypes.INTEGER);
    private final OptionalArg<Float> sleepLinearThresholdArg = this.withOptionalArg(
        "sleepLinearThreshold",
        "Dynamic sleep linear velocity threshold",
        ArgTypes.FLOAT);
    private final OptionalArg<Float> sleepAngularThresholdArg = this.withOptionalArg(
        "sleepAngularThreshold",
        "Dynamic sleep angular velocity threshold",
        ArgTypes.FLOAT);
    private final OptionalArg<Float> sleepTimeArg = this.withOptionalArg(
        "sleepTime",
        "Seconds before eligible dynamic bodies sleep",
        ArgTypes.FLOAT);
    private final OptionalArg<Integer> spaceArg = this.withOptionalArg(
        "space",
        "Physics space id to target",
        ArgTypes.INTEGER);

    public SolverSettingsCommand() {
        super("solver", "Get or set solver tuning for a physics space", true);
    }

    @Nonnull
    @Override
    protected CompletableFuture<Void> executeAsync(@Nonnull CommandContext ctx,
        @Nonnull World world) {
        Store<PhysicsStore> physicsStore = PhysicsThreading.store(world);
        SpaceSelection.SelectedSpace selectedSpace = SpaceSelection.resolveStoreSpace(ctx,
            world,
            spaceArg);
        if (selectedSpace == null) {
            return CompletableFuture.completedFuture(null);
        }
        SpaceId spaceId = selectedSpace.spaceId();
        return PhysicsAsync.acceptOnWorldThread(world,
            PhysicsDiagnostics.solverCapabilityAsync(world, selectedSpace.spaceRef()),
            summary -> applySettings(ctx, physicsStore, selectedSpace.spaceRef(), spaceId, summary));
    }

    private void applySettings(@Nonnull CommandContext ctx,
        @Nonnull Store<PhysicsStore> physicsStore,
        @Nonnull Ref<PhysicsStore> spaceRef,
        @Nonnull SpaceId spaceId,
        @Nonnull SolverCapabilitySummary summary) {
        PhysicsSpaceSettings currentSettings = PhysicsSpaces.settings(physicsStore, spaceRef);
        if (currentSettings == null) {
            ctx.sender().sendMessage(Message.raw("Physics space id=" + spaceId.value()
                + " no longer exists."));
            return;
        }
        PhysicsSpaceSettings settings = new PhysicsSpaceSettings(currentSettings);
        if (!anyArgProvided(ctx)) {
            sendSummary(ctx, spaceId, summary, settings);
            return;
        }

        int solverIterations = solverIterationsArg.provided(ctx)
            ? solverIterationsArg.get(ctx)
            : settings.getSolverSettings().getSolverIterations();
        int stabilizationIterations = stabilizationIterationsArg.provided(ctx)
            ? stabilizationIterationsArg.get(ctx)
            : settings.getSolverSettings().getStabilizationIterations();
        float sleepLinearThreshold = sleepLinearThresholdArg.provided(ctx)
            ? sleepLinearThresholdArg.get(ctx)
            : settings.getSolverSettings().getDynamicSleepLinearThreshold();
        float sleepAngularThreshold = sleepAngularThresholdArg.provided(ctx)
            ? sleepAngularThresholdArg.get(ctx)
            : settings.getSolverSettings().getDynamicSleepAngularThreshold();
        float sleepTime = sleepTimeArg.provided(ctx)
            ? sleepTimeArg.get(ctx)
            : settings.getSolverSettings().getDynamicSleepTimeUntilSleep();

        if (solverIterations < 1
            || stabilizationIterations < 0
            || !Float.isFinite(sleepLinearThreshold)
            || !Float.isFinite(sleepAngularThreshold)
            || !Float.isFinite(sleepTime)
            || sleepLinearThreshold < 0.0f
            || sleepAngularThreshold < 0.0f
            || sleepTime < 0.0f) {
            ctx.sender().sendMessage(Message.raw(
                "solverIterations must be >= 1; stabilizationIterations and sleep tuning values must be >= 0."));
            return;
        }

        settings.getSolverSettings().setSolverIterations(solverIterations);
        settings.getSolverSettings().setStabilizationIterations(stabilizationIterations);
        settings.getSolverSettings().setDynamicSleepTuning(sleepLinearThreshold, sleepAngularThreshold, sleepTime);
        PhysicsSpaces.putSettings(physicsStore, spaceRef, settings);
        sendSummary(ctx, spaceId, summary, settings);
    }

    private boolean anyArgProvided(@Nonnull CommandContext ctx) {
        return solverIterationsArg.provided(ctx)
            || stabilizationIterationsArg.provided(ctx)
            || sleepLinearThresholdArg.provided(ctx)
            || sleepAngularThresholdArg.provided(ctx)
            || sleepTimeArg.provided(ctx);
    }

    private static void sendSummary(@Nonnull CommandContext ctx,
        @Nonnull SpaceId spaceId,
        @Nonnull SolverCapabilitySummary summary,
        @Nonnull PhysicsSpaceSettings settings) {
        ctx.sender().sendMessage(Message.raw("Impulse solver settings for space "
            + spaceId.value()
            + " backend=" + summary.backendId()
            + " solverApplied=" + summary.solverTuningSupported()
            + " sleepApplied=" + summary.activationTuningSupported()
            + ": solverIterations=" + settings.getSolverSettings().getSolverIterations()
            + " stabilizationIterations=" + settings.getSolverSettings().getStabilizationIterations()
            + " sleepLinearThreshold=" + settings.getSolverSettings().getDynamicSleepLinearThreshold()
            + " sleepAngularThreshold=" + settings.getSolverSettings().getDynamicSleepAngularThreshold()
            + " sleepTime=" + settings.getSolverSettings().getDynamicSleepTimeUntilSleep()));
    }

}
