package dev.hytalemodding.impulse.core.commands.settings;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractWorldCommand;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.api.PhysicsSpace;
import dev.hytalemodding.impulse.api.PhysicsSolverTuning;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.resources.PhysicsSpaceSettings;
import dev.hytalemodding.impulse.core.resources.PhysicsWorldResource;
import javax.annotation.Nonnull;

public class SolverSettingsCommand extends AbstractWorldCommand {

    private final OptionalArg<Integer> solverIterationsArg = this.withOptionalArg(
        "solverIterations",
        "Constraint solver iterations",
        ArgTypes.INTEGER);
    private final OptionalArg<Integer> pgsIterationsArg = this.withOptionalArg(
        "pgsIterations",
        "Internal PGS iterations per solver iteration",
        ArgTypes.INTEGER);
    private final OptionalArg<Integer> stabilizationIterationsArg = this.withOptionalArg(
        "stabilizationIterations",
        "Stabilization iterations per solver iteration",
        ArgTypes.INTEGER);
    private final OptionalArg<Integer> minIslandSizeArg = this.withOptionalArg(
        "minIslandSize",
        "Minimum island size used by compatible parallel solvers",
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

    public SolverSettingsCommand() {
        super("solver", "Get or set solver tuning for the default physics space", true);
    }

    @Override
    protected void execute(@Nonnull CommandContext ctx,
        @Nonnull World world,
        @Nonnull Store<EntityStore> store) {
        PhysicsWorldResource resource = store.getResource(PhysicsWorldResource.getResourceType());
        SpaceId defaultSpaceId = resource.getDefaultSpaceId();
        PhysicsSpace space = defaultSpaceId != null ? resource.getSpace(defaultSpaceId) : null;
        if (defaultSpaceId == null || space == null) {
            ctx.sender().sendMessage(Message.raw("No default physics space exists yet."));
            return;
        }

        PhysicsSpaceSettings settings = new PhysicsSpaceSettings(resource.getSpaceSettings(defaultSpaceId));
        if (!anyArgProvided(ctx)) {
            sendSummary(ctx, defaultSpaceId, space, settings);
            return;
        }

        int solverIterations = solverIterationsArg.provided(ctx)
            ? solverIterationsArg.get(ctx)
            : settings.getSolverIterations();
        int pgsIterations = pgsIterationsArg.provided(ctx)
            ? pgsIterationsArg.get(ctx)
            : settings.getInternalPgsIterations();
        int stabilizationIterations = stabilizationIterationsArg.provided(ctx)
            ? stabilizationIterationsArg.get(ctx)
            : settings.getStabilizationIterations();
        int minIslandSize = minIslandSizeArg.provided(ctx)
            ? minIslandSizeArg.get(ctx)
            : settings.getMinIslandSize();
        float sleepLinearThreshold = sleepLinearThresholdArg.provided(ctx)
            ? sleepLinearThresholdArg.get(ctx)
            : settings.getDynamicSleepLinearThreshold();
        float sleepAngularThreshold = sleepAngularThresholdArg.provided(ctx)
            ? sleepAngularThresholdArg.get(ctx)
            : settings.getDynamicSleepAngularThreshold();
        float sleepTime = sleepTimeArg.provided(ctx)
            ? sleepTimeArg.get(ctx)
            : settings.getDynamicSleepTimeUntilSleep();

        if (solverIterations < 1
            || pgsIterations < 1
            || stabilizationIterations < 0
            || minIslandSize < 1
            || !Float.isFinite(sleepLinearThreshold)
            || !Float.isFinite(sleepAngularThreshold)
            || !Float.isFinite(sleepTime)
            || sleepLinearThreshold < 0.0f
            || sleepAngularThreshold < 0.0f
            || sleepTime < 0.0f) {
            ctx.sender().sendMessage(Message.raw(
                "solverIterations, pgsIterations, and minIslandSize must be >= 1; "
                    + "stabilizationIterations and sleep tuning values must be >= 0."));
            return;
        }

        settings.setSolverIterations(solverIterations);
        settings.setInternalPgsIterations(pgsIterations);
        settings.setStabilizationIterations(stabilizationIterations);
        settings.setMinIslandSize(minIslandSize);
        settings.setDynamicSleepTuning(sleepLinearThreshold, sleepAngularThreshold, sleepTime);
        resource.setSpaceSettings(defaultSpaceId, settings);
        sendSummary(ctx, defaultSpaceId, space, settings);
    }

    private boolean anyArgProvided(@Nonnull CommandContext ctx) {
        return solverIterationsArg.provided(ctx)
            || pgsIterationsArg.provided(ctx)
            || stabilizationIterationsArg.provided(ctx)
            || minIslandSizeArg.provided(ctx)
            || sleepLinearThresholdArg.provided(ctx)
            || sleepAngularThresholdArg.provided(ctx)
            || sleepTimeArg.provided(ctx);
    }

    private static void sendSummary(@Nonnull CommandContext ctx,
        @Nonnull SpaceId spaceId,
        @Nonnull PhysicsSpace space,
        @Nonnull PhysicsSpaceSettings settings) {
        ctx.sender().sendMessage(Message.raw("Impulse solver settings for space "
            + spaceId.value()
            + " backend=" + space.getBackendId().value()
            + " applied=" + (space instanceof PhysicsSolverTuning)
            + ": solverIterations=" + settings.getSolverIterations()
            + " pgsIterations=" + settings.getInternalPgsIterations()
            + " stabilizationIterations=" + settings.getStabilizationIterations()
            + " minIslandSize=" + settings.getMinIslandSize()
            + " sleepLinearThreshold=" + settings.getDynamicSleepLinearThreshold()
            + " sleepAngularThreshold=" + settings.getDynamicSleepAngularThreshold()
            + " sleepTime=" + settings.getDynamicSleepTimeUntilSleep()));
    }
}
