package dev.hytalemodding.impulse.examples.commands;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncWorldCommand;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.core.plugin.persistence.PhysicsPersistence;
import dev.hytalemodding.impulse.core.plugin.physicsstore.PhysicsStoreAsync;
import dev.hytalemodding.impulse.core.plugin.persistence.PhysicsPersistence.RestoreRequestResult;
import dev.hytalemodding.impulse.core.plugin.persistence.PhysicsPersistence.SaveResult;
import dev.hytalemodding.impulse.core.plugin.persistence.PhysicsPersistence.Status;
import java.util.concurrent.CompletableFuture;
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

    private static final class SaveCommand extends AbstractAsyncWorldCommand {

        private SaveCommand() {
            super("save", "Report authoritative PhysicsStore persistence capture status", false);
        }

        @Nonnull
        @Override
        protected CompletableFuture<Void> executeAsync(@Nonnull CommandContext ctx,
            @Nonnull World world) {
            Store<EntityStore> store = world.getEntityStore().getStore();
            return PhysicsStoreAsync.acceptOnWorldThread(world,
                PhysicsPersistence.saveRuntimeSnapshotAsync(store),
                result -> sendSaveResult(ctx, world, result));
        }

        private static void sendSaveResult(@Nonnull CommandContext ctx,
            @Nonnull World world,
            @Nonnull SaveResult result) {
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

    private static final class LoadCommand extends AbstractAsyncWorldCommand {

        private LoadCommand() {
            super("load", "Report authoritative PhysicsStore persistence restore status", false);
        }

        @Nonnull
        @Override
        protected CompletableFuture<Void> executeAsync(@Nonnull CommandContext ctx,
            @Nonnull World world) {
            Store<EntityStore> store = world.getEntityStore().getStore();
            return PhysicsStoreAsync.acceptOnWorldThread(world,
                PhysicsPersistence.requestRuntimeRestoreAsync(store),
                result -> sendLoadResult(ctx, world, result));
        }

        private static void sendLoadResult(@Nonnull CommandContext ctx,
            @Nonnull World world,
            @Nonnull RestoreRequestResult result) {
            Status status = result.status();
            if (status.restoreState() == PhysicsPersistence.RestoreState.PENDING_SPACES
                || status.restoreState() == PhysicsPersistence.RestoreState.PENDING_BODIES_AND_JOINTS) {
                ctx.sendMessage(Message.raw("Impulse persistence restore is already pending."));
                return;
            }

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

    private static final class StatusCommand extends AbstractAsyncWorldCommand {

        private StatusCommand() {
            super("status", "Show runtime and stored Impulse persistence counts", false);
        }

        @Nonnull
        @Override
        protected CompletableFuture<Void> executeAsync(@Nonnull CommandContext ctx,
            @Nonnull World world) {
            Store<EntityStore> store = world.getEntityStore().getStore();
            return PhysicsStoreAsync.acceptOnWorldThread(world,
                PhysicsPersistence.statusAsync(store),
                status -> sendStatus(ctx, world, status));
        }

        private static void sendStatus(@Nonnull CommandContext ctx,
            @Nonnull World world,
            @Nonnull Status status) {
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
