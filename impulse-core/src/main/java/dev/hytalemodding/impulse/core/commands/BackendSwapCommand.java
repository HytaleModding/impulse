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
import dev.hytalemodding.impulse.api.BackendId;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.resources.PhysicsWorldResource;
import dev.hytalemodding.impulse.core.services.PhysicsSpaceMigrationService;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nonnull;

public class BackendSwapCommand extends AbstractAsyncPlayerCommand {

    private final OptionalArg<String> backendArg = this.withOptionalArg(
        "backend",
        "Target backend id, for example impulse:rapier",
        ArgTypes.STRING);

    private final OptionalArg<Integer> spaceArg = this.withOptionalArg(
        "space",
        "Space id to migrate, defaults to the main space",
        ArgTypes.INTEGER);

    public BackendSwapCommand() {
        super("swap", "Swap a physics space to another backend");
    }

    @Nonnull
    @Override
    protected CompletableFuture<Void> executeAsync(@Nonnull CommandContext ctx,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull PlayerRef playerRef,
        @Nonnull World world) {
        if (!backendArg.provided(ctx)) {
            ctx.sender().sendMessage(Message.raw("Missing backend id. Example:"
                + " /impulse backend swap --backend impulse:rapier"));
            return CompletableFuture.completedFuture(null);
        }

        String rawBackendId = backendArg.get(ctx).trim();
        BackendId targetBackendId;
        try {
            targetBackendId = new BackendId(rawBackendId);
        } catch (Exception exception) {
            ctx.sender().sendMessage(Message.raw("Invalid backend id: " + rawBackendId));
            return CompletableFuture.completedFuture(null);
        }

        PhysicsWorldResource resource = store.getResource(PhysicsWorldResource.getResourceType());
        SpaceId sourceSpaceId;
        if (spaceArg.provided(ctx)) {
            int rawSpaceId = spaceArg.get(ctx);
            if (rawSpaceId <= 0) {
                ctx.sender().sendMessage(Message.raw("Space id must be a positive integer."));
                return CompletableFuture.completedFuture(null);
            }
            sourceSpaceId = new SpaceId(rawSpaceId);
        } else {
            sourceSpaceId = resource.getDefaultSpaceId();
            if (sourceSpaceId == null) {
                ctx.sender().sendMessage(Message.raw("No default physics space is configured."
                    + " Provide --space explicitly."));
                return CompletableFuture.completedFuture(null);
            }
        }

        try {
            PhysicsSpaceMigrationService.MigrationResult result =
                PhysicsSpaceMigrationService.migrateSpace(
                    store,
                    resource,
                    sourceSpaceId,
                    targetBackendId,
                    world.getName());

            if (!result.changed()) {
                ctx.sender().sendMessage(Message.raw("Space id=" + result.spaceId().value()
                    + " already uses backend " + result.targetBackendId().value()));
                return CompletableFuture.completedFuture(null);
            }

            ctx.sender().sendMessage(Message.raw(
                "Migrated space id=" + result.spaceId().value()
                    + " backend " + result.sourceBackendId().value()
                    + " -> " + result.targetBackendId().value()
                    + " (" + result.migratedBodies() + " bodies, "
                    + result.migratedJoints() + " joints)"));
        } catch (Exception exception) {
            ctx.sender().sendMessage(Message.raw("Backend swap failed: " + exception.getMessage()));
        }

        return CompletableFuture.completedFuture(null);
    }
}
