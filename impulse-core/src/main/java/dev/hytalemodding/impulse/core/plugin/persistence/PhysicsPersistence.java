package dev.hytalemodding.impulse.core.plugin.persistence;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.api.PhysicsSpace;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.internal.persistence.PersistentPhysicsWorldResource;
import dev.hytalemodding.impulse.core.internal.systems.persistence.PersistentPhysicsWorldSyncSystem;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyPersistenceMode;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsWorldResource;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Public command/plugin facade for world-level Impulse persistence status and lifecycle actions.
 */
public final class PhysicsPersistence {

    public static final int CURRENT_SCHEMA_VERSION =
        PersistentPhysicsWorldResource.CURRENT_SCHEMA_VERSION;

    private PhysicsPersistence() {
    }

    @Nonnull
    public static SaveResult saveRuntimeSnapshot(@Nonnull Store<EntityStore> store) {
        PersistentPhysicsWorldResource persistent = persistent(store);
        PhysicsWorldResource runtime = runtime(store);
        PersistentPhysicsWorldSyncSystem.SyncResult result =
            PersistentPhysicsWorldSyncSystem.syncRuntimeSnapshot(store, persistent, runtime);
        return new SaveResult(result.synced(),
            persistent.getSchemaVersion(),
            result.spaces(),
            result.bodies(),
            result.joints(),
            result.skippedReason());
    }

    @Nonnull
    public static RestoreRequestResult requestRuntimeRestore(@Nonnull Store<EntityStore> store) {
        PersistentPhysicsWorldResource persistent = persistent(store);
        Status status = status(store);
        if (persistent.isRuntimeRestorePending()) {
            return new RestoreRequestResult(false, "already-pending", status);
        }
        if (persistent.getSchemaVersion() != CURRENT_SCHEMA_VERSION) {
            return new RestoreRequestResult(false, "schema-mismatch", status);
        }

        persistent.markRuntimeRestorePending();
        return new RestoreRequestResult(true, "", status(store));
    }

    @Nonnull
    public static Status status(@Nonnull Store<EntityStore> store) {
        PersistentPhysicsWorldResource persistent = persistent(store);
        PhysicsWorldResource runtime = runtime(store);
        return new Status(runtime.getSpaceCount(),
            runtime.getBodyRegistrationCount(PhysicsBodyPersistenceMode.PERSISTENT),
            runtime.getBodyRegistrationCount(PhysicsBodyPersistenceMode.RUNTIME_ONLY),
            countRuntimeJoints(runtime),
            runtime.getDefaultSpaceId(),
            persistent.getSchemaVersion(),
            persistent.getSpaceCount(),
            persistent.getBodyCount(),
            persistent.getJointCount(),
            persistent.getDefaultSpaceIdValue(),
            restoreState(persistent),
            restoreMessage(persistent));
    }

    private static int countRuntimeJoints(@Nonnull PhysicsWorldResource runtime) {
        return runtime.callOnPhysicsOwner("count runtime persistence joints", access -> {
            int count = 0;
            for (PhysicsSpace space : access.getSpaces()) {
                count += space.getJoints().size();
            }
            return count;
        });
    }

    @Nonnull
    private static RestoreState restoreState(@Nonnull PersistentPhysicsWorldResource persistent) {
        if (persistent.hasRuntimeRestoreFailed()) {
            return RestoreState.FAILED;
        }
        if (!persistent.isRuntimeRestorePending()) {
            return RestoreState.IDLE;
        }
        return persistent.isRuntimeSpaceBootstrapComplete()
            ? RestoreState.PENDING_BODIES_AND_JOINTS
            : RestoreState.PENDING_SPACES;
    }

    @Nonnull
    private static String restoreMessage(@Nonnull PersistentPhysicsWorldResource persistent) {
        if (persistent.hasRuntimeRestoreFailed()) {
            return persistent.runtimeRestoreFailureSummary();
        }
        if (persistent.hasRuntimeRestoreSkips()) {
            return persistent.runtimeRestoreSummary();
        }
        return "";
    }

    @Nonnull
    private static PersistentPhysicsWorldResource persistent(@Nonnull Store<EntityStore> store) {
        return store.getResource(PersistentPhysicsWorldResource.getResourceType());
    }

    @Nonnull
    private static PhysicsWorldResource runtime(@Nonnull Store<EntityStore> store) {
        return store.getResource(PhysicsWorldResource.getResourceType());
    }

    public enum RestoreState {
        IDLE("idle"),
        PENDING_SPACES("pending-spaces"),
        PENDING_BODIES_AND_JOINTS("pending-bodies-and-joints"),
        FAILED("failed");

        @Nonnull
        private final String serialized;

        RestoreState(@Nonnull String serialized) {
            this.serialized = serialized;
        }

        @Nonnull
        public String serialized() {
            return serialized;
        }
    }

    public record SaveResult(boolean synced,
                             int schemaVersion,
                             int spaces,
                             int bodies,
                             int joints,
                             @Nonnull String skippedReason) {
    }

    public record RestoreRequestResult(boolean queued,
                                       @Nonnull String skippedReason,
                                       @Nonnull Status status) {
    }

    public record Status(int runtimeSpaces,
                         int runtimePersistentBodies,
                         int runtimeOnlyBodies,
                         int runtimeJoints,
                         @Nullable SpaceId runtimeDefaultSpaceId,
                         int schemaVersion,
                         int storedSpaces,
                         int storedBodies,
                         int storedJoints,
                         @Nullable SpaceId storedDefaultSpaceId,
                         @Nonnull RestoreState restoreState,
                         @Nonnull String restoreMessage) {

        public boolean hasRestoreMessage() {
            return !restoreMessage.isEmpty();
        }
    }
}
