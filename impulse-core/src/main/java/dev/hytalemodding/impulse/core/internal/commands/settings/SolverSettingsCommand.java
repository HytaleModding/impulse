package dev.hytalemodding.impulse.core.internal.commands.settings;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractWorldCommand;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.internal.commands.SpaceSelection;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsSpaceSettings;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsWorldResource;
import dev.hytalemodding.impulse.core.plugin.simulation.query.SolverCapabilityQuery;
import dev.hytalemodding.impulse.core.plugin.simulation.SolverCapabilitySummary;
import javax.annotation.Nonnull;

public class SolverSettingsCommand extends AbstractWorldCommand {

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

    @Override
    protected void execute(@Nonnull CommandContext ctx,
        @Nonnull World world,
        @Nonnull Store<EntityStore> store) {
        PhysicsWorldResource resource = store.getResource(PhysicsWorldResource.getResourceType());
        SpaceId spaceId = SpaceSelection.resolve(ctx, world, resource, spaceArg);
        if (spaceId == null) {
            return;
        }
        SolverCapabilitySummary summary = resource.query(new SolverCapabilityQuery(spaceId))
            .completion()
            .toCompletableFuture()
            .join();

        PhysicsSpaceSettings settings = new PhysicsSpaceSettings(resource.getSpaceSettings(spaceId));
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
        resource.setSpaceSettings(spaceId, settings);
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
