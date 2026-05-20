package dev.hytalemodding.impulse.core.commands.settings;

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
import dev.hytalemodding.impulse.core.resources.PhysicsStepMode;
import dev.hytalemodding.impulse.core.resources.PhysicsWorldResource;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nonnull;

public class SimulationStepsSettingCommand extends AbstractAsyncPlayerCommand {

    private final OptionalArg<Integer> stepsArg = this.withOptionalArg(
        "steps",
        "Number of physics substeps per server tick",
        ArgTypes.INTEGER);

    public SimulationStepsSettingCommand() {
        super("simulation-steps", "Get or set physics substeps per server tick");
    }

    @Nonnull
    @Override
    protected CompletableFuture<Void> executeAsync(@Nonnull CommandContext ctx,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull PlayerRef playerRef,
        @Nonnull World world) {
        PhysicsWorldResource resource = store.getResource(PhysicsWorldResource.getResourceType());
        PhysicsStepMode stepMode = resource.getStepMode();
        if (!stepsArg.provided(ctx)) {
            ctx.sender().sendMessage(Message.raw("Impulse simulation steps: "
                + resource.getSimulationSteps() + " (" + stepMode.getSerializedName()
                + ", " + stepMode.describeSimulationSteps() + ")"));
            return CompletableFuture.completedFuture(null);
        }

        int steps = stepsArg.get(ctx);
        if (steps < PhysicsWorldResource.MIN_SIMULATION_STEPS
            || steps > PhysicsWorldResource.MAX_SIMULATION_STEPS) {
            ctx.sender().sendMessage(Message.raw("Simulation steps must be between "
                + PhysicsWorldResource.MIN_SIMULATION_STEPS + " and "
                + PhysicsWorldResource.MAX_SIMULATION_STEPS + "."));
            return CompletableFuture.completedFuture(null);
        }

        resource.setSimulationSteps(steps);
        ctx.sender().sendMessage(Message.raw("Impulse simulation steps set to " + steps
            + " (" + stepMode.describeSimulationSteps() + " in "
            + stepMode.getSerializedName() + " mode)"));
        return CompletableFuture.completedFuture(null);
    }
}
