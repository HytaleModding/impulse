package dev.hytalemodding.impulse.examples.commands;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractWorldCommand;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.core.plugin.persistence.PhysicsPersistence;
import dev.hytalemodding.impulse.core.plugin.persistence.PhysicsPersistence.RestoreRequestResult;
import dev.hytalemodding.impulse.core.plugin.persistence.PhysicsPersistence.SaveResult;
import dev.hytalemodding.impulse.core.plugin.persistence.PhysicsPersistence.Status;
import javax.annotation.Nonnull;

/**
 * Example controls for schema-v6 world-level Impulse persistence.
 */
public class PersistenceCommand extends AbstractCommandCollection {

    public PersistenceCommand() {
        super("persistence", "Inspect and exercise Hytale world physics persistence");
        addSubCommand(new SaveCommand());
        addSubCommand(new LoadCommand());
        addSubCommand(new StatusCommand());
    }

    private static final class SaveCommand extends AbstractWorldCommand {

        private SaveCommand() {
            super("save", "Report authoritative PhysicsStore persistence capture status", false);
        }

        @Override
        protected void execute(@Nonnull CommandContext ctx,
            @Nonnull World world,
            @Nonnull Store<EntityStore> store) {
            SaveResult result = PhysicsPersistence.saveRuntimeSnapshot(store);
            if (!result.synced()) {
                ctx.sendMessage(Message.raw("Manual Impulse persistence save is disabled in "
                    + "authoritative PhysicsStore mode; PhysicsStore captures canonical state "
                    + "automatically. reason=" + result.skippedReason()
                    + ", stored schema=" + result.schemaVersion()
                    + ", spaces=" + result.spaces()
                    + ", persistentBodies=" + result.bodies()
                    + ", joints=" + result.joints()
                    + "."));
                return;
            }

            ctx.sendMessage(Message.raw("Saved Impulse world persistence for " + world.getName()
                + ": schema=" + result.schemaVersion()
                + ", spaces=" + result.spaces()
                + ", persistentBodies=" + result.bodies()
                + ", joints=" + result.joints()
                + "."));
        }
    }

    private static final class LoadCommand extends AbstractWorldCommand {

        private LoadCommand() {
            super("load", "Report authoritative PhysicsStore persistence restore status", false);
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

            RestoreRequestResult result = PhysicsPersistence.requestRuntimeRestore(store);
            if (!result.queued()) {
                ctx.sendMessage(Message.raw("Manual Impulse persistence restore is disabled in "
                    + "authoritative PhysicsStore mode; PhysicsStore restores automatically from "
                    + "PersistentPhysicsStore during world startup. reason="
                    + result.skippedReason()
                    + ", stored schema=" + status.schemaVersion()
                    + ", spaces=" + status.storedSpaces()
                    + ", persistentBodies=" + status.storedBodies()
                    + ", joints=" + status.storedJoints()
                    + "."));
                return;
            }

            ctx.sendMessage(Message.raw("Queued Impulse world persistence restore for "
                + world.getName()
                + ": schema=" + status.schemaVersion()
                + ", spaces=" + status.storedSpaces()
                + ", persistentBodies=" + status.storedBodies()
                + ", joints=" + status.storedJoints()
                + ". Runtime physics will reset and hydrate on the next tick."));
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
                + ", runtimeBodies="
                + status.runtimePersistentBodies()
                + ", joints=" + status.runtimeJoints()
                + "; PhysicsStore schema=" + status.schemaVersion()
                + ", spaces=" + status.storedSpaces()
                + ", bodies=" + status.storedBodies()
                + ", joints=" + status.storedJoints()
                + ", restore=" + status.restoreState().serialized()
                + "."));
            if (status.hasRestoreMessage()) {
                ctx.sendMessage(Message.raw(status.restoreMessage()));
            }
        }
    }

}
