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
import dev.hytalemodding.impulse.api.PhysicsSpace;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.internal.persistence.PersistentPhysicsWorldResource;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsBodyPersistenceMode;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsWorldResource;
import dev.hytalemodding.impulse.core.internal.systems.persistence.PersistentPhysicsWorldSyncSystem;
import javax.annotation.Nonnull;

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
            PersistentPhysicsWorldResource persistent = store.getResource(
                PersistentPhysicsWorldResource.getResourceType());
            PhysicsWorldResource runtime = store.getResource(PhysicsWorldResource.getResourceType());

            PersistentPhysicsWorldSyncSystem.SyncResult result =
                PersistentPhysicsWorldSyncSystem.syncRuntimeSnapshot(store, persistent, runtime);
            if (!result.synced()) {
                ctx.sendMessage(Message.raw("Cannot save Impulse world persistence while "
                    + result.skippedReason() + "."));
                return;
            }

            ctx.sendMessage(Message.raw("Saved Impulse world persistence for " + world.getName()
                + ": schema=" + persistent.getSchemaVersion()
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
            PersistentPhysicsWorldResource persistent = store.getResource(
                PersistentPhysicsWorldResource.getResourceType());
            if (persistent.isRuntimeRestorePending()) {
                ctx.sendMessage(Message.raw("Impulse persistence restore is already pending."));
                return;
            }

            if (!confirmRestore(ctx, world, persistent)) {
                return;
            }
            if (persistent.getSchemaVersion() != PersistentPhysicsWorldResource.CURRENT_SCHEMA_VERSION) {
                ctx.sendMessage(Message.raw("Cannot load Impulse world persistence schema "
                    + persistent.getSchemaVersion() + "; expected schema "
                    + PersistentPhysicsWorldResource.CURRENT_SCHEMA_VERSION + "."));
                return;
            }

            persistent.markRuntimeRestorePending();
            ctx.sendMessage(Message.raw("Queued Impulse world persistence restore for "
                + world.getName()
                + ": schema=" + persistent.getSchemaVersion()
                + ", spaces=" + persistent.getSpaceCount()
                + ", persistentBodies=" + persistent.getBodyCount()
                + ", joints=" + persistent.getJointCount()
                + ". Runtime physics will reset and hydrate on the next tick."
                + ignoredNameSuffix(ctx)));
        }

        private boolean confirmRestore(@Nonnull CommandContext ctx,
            @Nonnull World world,
            @Nonnull PersistentPhysicsWorldResource persistent) {
            if (confirmArg.provided(ctx) && "true".equalsIgnoreCase(confirmArg.get(ctx).trim())) {
                return true;
            }

            ctx.sendMessage(Message.raw("Loading Impulse world persistence for " + world.getName()
                + " resets runtime spaces, bodies, joints, generated visual proxies, and control sessions, "
                + "then rebuilds from the stored world resource (schema=" + persistent.getSchemaVersion()
                + ", spaces=" + persistent.getSpaceCount()
                + ", persistentBodies=" + persistent.getBodyCount()
                + ", joints=" + persistent.getJointCount()
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
            PersistentPhysicsWorldResource persistent = store.getResource(
                PersistentPhysicsWorldResource.getResourceType());
            PhysicsWorldResource runtime = store.getResource(PhysicsWorldResource.getResourceType());

            ctx.sendMessage(Message.raw("Impulse persistence status for " + world.getName()
                + ": runtime spaces=" + runtime.getSpaceCount()
                + ", persistentBodies="
                + runtime.getBodyRegistrationCount(PhysicsBodyPersistenceMode.PERSISTENT)
                + ", runtimeOnlyBodies="
                + runtime.getBodyRegistrationCount(PhysicsBodyPersistenceMode.RUNTIME_ONLY)
                + ", joints=" + countRuntimeJoints(store, runtime)
                + ", defaultSpace=" + formatSpaceId(runtime.getDefaultSpaceId())
                + "; stored schema=" + persistent.getSchemaVersion()
                + ", spaces=" + persistent.getSpaceCount()
                + ", bodies=" + persistent.getBodyCount()
                + ", joints=" + persistent.getJointCount()
                + ", defaultSpace=" + formatSpaceId(persistent.getDefaultSpaceIdValue())
                + ", restore=" + restoreState(persistent)
                + "."));
            if (persistent.hasRuntimeRestoreFailed()) {
                ctx.sendMessage(Message.raw(persistent.runtimeRestoreFailureSummary()));
            } else if (persistent.hasRuntimeRestoreSkips()) {
                ctx.sendMessage(Message.raw(persistent.runtimeRestoreSummary()));
            }
        }
    }

    private static int countRuntimeJoints(@Nonnull Store<EntityStore> store,
        @Nonnull PhysicsWorldResource runtime) {
        return ExamplePhysicsUtils.physicsOwnerCall(store, "count runtime persistence joints", () -> {
            int count = 0;
            for (PhysicsSpace space : runtime.getSpaces()) {
                count += space.getJoints().size();
            }
            return count;
        });
    }

    @Nonnull
    private static String formatSpaceId(SpaceId spaceId) {
        return spaceId != null ? Integer.toString(spaceId.value()) : "<none>";
    }

    @Nonnull
    private static String restoreState(@Nonnull PersistentPhysicsWorldResource persistent) {
        if (persistent.hasRuntimeRestoreFailed()) {
            return "failed";
        }
        if (!persistent.isRuntimeRestorePending()) {
            return "idle";
        }
        return persistent.isRuntimeSpaceBootstrapComplete()
            ? "pending-bodies-and-joints"
            : "pending-spaces";
    }
}
