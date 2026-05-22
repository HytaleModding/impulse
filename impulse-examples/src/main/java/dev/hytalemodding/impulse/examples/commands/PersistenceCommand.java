package dev.hytalemodding.impulse.examples.commands;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractWorldCommand;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.plugin.persistence.PhysicsPersistence;
import dev.hytalemodding.impulse.core.plugin.persistence.PhysicsPersistence.RestoreRequestResult;
import dev.hytalemodding.impulse.core.plugin.persistence.PhysicsPersistence.SaveResult;
import dev.hytalemodding.impulse.core.plugin.persistence.PhysicsPersistence.Status;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Example controls for schema-v3 world-level Impulse persistence.
 */
public class PersistenceCommand extends AbstractCommandCollection {

    public PersistenceCommand() {
        super("persistence", "Inspect and exercise Hytale world physics persistence");
        addSubCommand(new SaveCommand());
        addSubCommand(new LoadCommand());
        addSubCommand(new StatusCommand());
    }

    private abstract static class LegacyNamedWorldCommand extends AbstractWorldCommand {

        protected final OptionalArg<String> nameArg = withOptionalArg(
            "name",
            "Ignored legacy snapshot name",
            ArgTypes.STRING);

        private LegacyNamedWorldCommand(@Nonnull String name, @Nonnull String description) {
            super(name, description, false);
        }

        @Nonnull
        protected String ignoredNameSuffix(@Nonnull CommandContext ctx) {
            if (!nameArg.provided(ctx)) {
                return "";
            }

            String name = nameArg.get(ctx).trim();
            if (name.isEmpty()) {
                return " Legacy name argument was ignored because schema-v3 persistence is stored with the world.";
            }
            return " Legacy --name '" + name
                + "' was ignored because schema-v3 persistence is stored with the world.";
        }
    }

    private static final class SaveCommand extends LegacyNamedWorldCommand {

        private SaveCommand() {
            super("save", "Synchronize current runtime physics into Hytale world persistence");
        }

        @Override
        protected void execute(@Nonnull CommandContext ctx,
            @Nonnull World world,
            @Nonnull Store<EntityStore> store) {
            SaveResult result = PhysicsPersistence.saveRuntimeSnapshot(store);
            if (!result.synced()) {
                ctx.sendMessage(Message.raw("Cannot save Impulse world persistence while "
                    + result.skippedReason() + "."));
                return;
            }

            ctx.sendMessage(Message.raw("Saved Impulse world persistence for " + world.getName()
                + ": schema=" + result.schemaVersion()
                + ", spaces=" + result.spaces()
                + ", persistentBodies=" + result.bodies()
                + ", joints=" + result.joints()
                + "." + ignoredNameSuffix(ctx)));
        }
    }

    private static final class LoadCommand extends LegacyNamedWorldCommand {

        private final OptionalArg<String> confirmArg = withOptionalArg(
            "confirm",
            "Required: true, because restore resets runtime physics before hydration rebuilds it",
            ArgTypes.STRING);

        private LoadCommand() {
            super("load", "Restore runtime physics from Hytale world persistence");
        }

        @Override
        protected void execute(@Nonnull CommandContext ctx,
            @Nonnull World world,
            @Nonnull Store<EntityStore> store) {
            Status status = PhysicsPersistence.status(store);
            if (status.restoreState() == PhysicsPersistence.RestoreState.PENDING_SPACES
                || status.restoreState() == PhysicsPersistence.RestoreState.PENDING_BODIES_AND_JOINTS) {
                ctx.sendMessage(Message.raw("Impulse persistence restore is already pending."));
                return;
            }

            if (!confirmRestore(ctx, world, status)) {
                return;
            }

            RestoreRequestResult result = PhysicsPersistence.requestRuntimeRestore(store);
            if (!result.queued()) {
                if ("schema-mismatch".equals(result.skippedReason())) {
                    ctx.sendMessage(Message.raw("Cannot load Impulse world persistence schema "
                        + status.schemaVersion() + "; expected schema "
                        + PhysicsPersistence.CURRENT_SCHEMA_VERSION + "."));
                } else {
                    ctx.sendMessage(Message.raw("Cannot queue Impulse persistence restore: "
                        + result.skippedReason() + "."));
                }
                return;
            }

            ctx.sendMessage(Message.raw("Queued Impulse world persistence restore for "
                + world.getName()
                + ": schema=" + status.schemaVersion()
                + ", spaces=" + status.storedSpaces()
                + ", persistentBodies=" + status.storedBodies()
                + ", joints=" + status.storedJoints()
                + ". Runtime physics will reset and hydrate on the next tick."
                + ignoredNameSuffix(ctx)));
        }

        private boolean confirmRestore(@Nonnull CommandContext ctx,
            @Nonnull World world,
            @Nonnull Status status) {
            if (confirmArg.provided(ctx) && "true".equalsIgnoreCase(confirmArg.get(ctx).trim())) {
                return true;
            }

            ctx.sendMessage(Message.raw("Loading Impulse world persistence for " + world.getName()
                + " resets runtime spaces, bodies, joints, generated visual proxies, and control sessions, "
                + "then rebuilds from the stored world resource (schema=" + status.schemaVersion()
                + ", spaces=" + status.storedSpaces()
                + ", persistentBodies=" + status.storedBodies()
                + ", joints=" + status.storedJoints()
                + "). Re-run with --confirm=true to continue."));
            return false;
        }
    }

    private static final class StatusCommand extends AbstractWorldCommand {

        private StatusCommand() {
            super("status", "Show runtime and stored Impulse persistence counts", false);
        }

        @Override
        protected void execute(@Nonnull CommandContext ctx,
            @Nonnull World world,
            @Nonnull Store<EntityStore> store) {
            Status status = PhysicsPersistence.status(store);

            ctx.sendMessage(Message.raw("Impulse persistence status for " + world.getName()
                + ": runtime spaces=" + status.runtimeSpaces()
                + ", persistentBodies="
                + status.runtimePersistentBodies()
                + ", runtimeOnlyBodies="
                + status.runtimeOnlyBodies()
                + ", joints=" + status.runtimeJoints()
                + ", defaultSpace=" + formatSpaceId(status.runtimeDefaultSpaceId())
                + "; stored schema=" + status.schemaVersion()
                + ", spaces=" + status.storedSpaces()
                + ", bodies=" + status.storedBodies()
                + ", joints=" + status.storedJoints()
                + ", defaultSpace=" + formatSpaceId(status.storedDefaultSpaceId())
                + ", restore=" + status.restoreState().serialized()
                + "."));
            if (status.hasRestoreMessage()) {
                ctx.sendMessage(Message.raw(status.restoreMessage()));
            }
        }
    }

    @Nonnull
    private static String formatSpaceId(@Nullable SpaceId spaceId) {
        return spaceId != null ? Integer.toString(spaceId.value()) : "<none>";
    }
}
