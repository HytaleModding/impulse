package dev.hytalemodding.impulse.core.internal.commands.space;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncWorldCommand;
import com.hypixel.hytale.server.core.universe.world.World;
import dev.hytalemodding.impulse.core.internal.commands.space.SpaceDeleteSupport.DeleteResult;
import dev.hytalemodding.impulse.core.plugin.physics.PhysicsThreading;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.concurrent.CompletableFuture;

public class SpaceDeleteCommand extends AbstractAsyncWorldCommand {

    private final OptionalArg<Integer> spaceArg = withOptionalArg(
        "space",
        "Space id to delete",
        ArgTypes.INTEGER);

    SpaceDeleteCommand() {
        super("delete", "Delete a physics space and its runtime backend state", true);
    }

    @Nonnull
    @Override
    protected CompletableFuture<Void> executeAsync(@Nonnull CommandContext context,
        @Nonnull World world) {
        if (!spaceArg.provided(context)) {
            context.sendMessage(Message.raw("Missing space id. Example:"
                + " /impulse space delete --space=1 --confirm"));
            return CompletableFuture.completedFuture(null);
        }

        int rawSpaceId = spaceArg.get(context);
        return PhysicsThreading.callWhenBackendIdleOnWorldThread(world,
                "delete PhysicsStore space",
                physicsStore -> SpaceDeleteSupport.deleteOnWorldThread(world,
                    physicsStore,
                    rawSpaceId))
            .handle((result, failure) -> {
                sendDeleteResult(context, world, result, failure);
                return (Void) null;
            })
            .toCompletableFuture();
    }

    private static void sendDeleteResult(@Nonnull CommandContext context,
        @Nonnull World world,
        @Nullable DeleteResult result,
        @Nullable Throwable failure) {
        Runnable sender = () -> {
            if (failure != null) {
                Throwable cause = SpaceCommand.unwrap(failure);
                String message = cause.getMessage() != null
                    ? cause.getMessage()
                    : cause.toString();
                context.sendMessage(Message.raw("Failed to delete physics space: "
                    + message));
                return;
            }
            if (result == null) {
                context.sendMessage(Message.raw("Failed to delete physics space."));
                return;
            }
            switch (result.outcome()) {
                case INVALID -> context.sendMessage(Message.raw(
                    "Space id must be a positive integer."));
                case MISSING -> context.sendMessage(Message.raw("No physics space id="
                    + result.rawSpaceId()
                    + " exists in world " + world.getName() + "."));
                case UNBOUND -> context.sendMessage(Message.raw("PhysicsStore space id="
                    + result.rawSpaceId()
                    + " is not bound in world " + world.getName() + "."));
                case NOT_EMPTY -> context.sendMessage(Message.raw("Physics space id="
                    + result.rawSpaceId()
                    + " is not empty (" + result.registeredBodies()
                    + " registered bodies, " + result.backendBodies()
                    + " backend bodies, " + result.joints() + " joints)."
                    + " Use /impulse clean for populated worlds, then delete empty spaces."));
                case DELETED -> context.sendMessage(Message.raw("Deleted physics space id="
                    + result.rawSpaceId()
                    + " with " + result.backendBodies()
                    + " backend bodies and " + result.joints() + " joints."));
            }
        };
        if (world.isInThread()) {
            sender.run();
            return;
        }
        world.execute(sender);
    }
}
