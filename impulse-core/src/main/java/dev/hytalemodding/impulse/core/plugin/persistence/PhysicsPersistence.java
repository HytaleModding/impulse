package dev.hytalemodding.impulse.core.plugin.persistence;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.core.internal.persistence.PhysicsStoreHolderStorage;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsRestoreStatusResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsSpaceCompatibilityIndexResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsSnapshotResource;
import dev.hytalemodding.impulse.core.plugin.physics.PhysicsDiagnostics;
import dev.hytalemodding.impulse.core.plugin.physics.PhysicsThreading;
import dev.hytalemodding.impulse.core.plugin.physics.SpaceSummary;
import java.util.List;
import java.util.concurrent.CompletionStage;
import javax.annotation.Nonnull;

/**
 * Public command/plugin facade for world-level Impulse persistence status and lifecycle actions.
 */
public final class PhysicsPersistence {

    public static final int CURRENT_SCHEMA_VERSION =
        PhysicsStoreHolderStorage.SCHEMA_VERSION;
    private static final String SAVE_SKIPPED_REASON =
        "authoritative-physics-store-holder-save-hook";
    private static final String RESTORE_SKIPPED_REASON =
        "authoritative-physics-store-auto-restore";

    private PhysicsPersistence() {
    }

    @Nonnull
    public static SaveResult saveRuntimeSnapshot(@Nonnull Store<EntityStore> store) {
        Status status = status(store);
        return new SaveResult(false,
            status.schemaVersion(),
            status.storedSpaces(),
            status.storedBodies(),
            status.storedJoints(),
            SAVE_SKIPPED_REASON);
    }

    @Nonnull
    public static CompletionStage<SaveResult> saveRuntimeSnapshotAsync(
        @Nonnull Store<EntityStore> store) {
        return statusAsync(store).thenApply(status -> new SaveResult(false,
            status.schemaVersion(),
            status.storedSpaces(),
            status.storedBodies(),
            status.storedJoints(),
            SAVE_SKIPPED_REASON));
    }

    @Nonnull
    public static RestoreRequestResult requestRuntimeRestore(@Nonnull Store<EntityStore> store) {
        Status status = status(store);
        return new RestoreRequestResult(false, RESTORE_SKIPPED_REASON, status);
    }

    @Nonnull
    public static CompletionStage<RestoreRequestResult> requestRuntimeRestoreAsync(
        @Nonnull Store<EntityStore> store) {
        return statusAsync(store).thenApply(status ->
            new RestoreRequestResult(false, RESTORE_SKIPPED_REASON, status));
    }

    @Nonnull
    public static Status status(@Nonnull Store<EntityStore> store) {
        Store<PhysicsStore> physicsStore = physicsStore(store);
        return copiedStatus(physicsStore);
    }

    @Nonnull
    public static CompletionStage<Status> statusAsync(@Nonnull Store<EntityStore> store) {
        return PhysicsThreading.enqueueReadOnWorldThread(store.getExternalData().getWorld(),
            "queue PhysicsStore persistence status read",
            PhysicsPersistence::liveStatus);
    }

    @Nonnull
    private static Store<PhysicsStore> physicsStore(@Nonnull Store<EntityStore> store) {
        return PhysicsThreading.store(store.getExternalData().getWorld());
    }

    @Nonnull
    private static Status liveStatus(@Nonnull Store<PhysicsStore> physicsStore) {
        SavedStateSummary saved = savedStateSummary(physicsStore);
        PhysicsRestoreStatusResource restore = physicsStore.getResource(
            PhysicsRestoreStatusResource.getResourceType());
        List<SpaceSummary> summaries = PhysicsDiagnostics.spaceSummaries(physicsStore);
        int runtimeBodies = summaries.stream().mapToInt(SpaceSummary::bodyCount).sum();
        int runtimeJoints = summaries.stream().mapToInt(SpaceSummary::jointCount).sum();
        int physicsStoreSpaces = physicsStore.getResource(
            PhysicsSpaceCompatibilityIndexResource.getResourceType()).size();
        return new Status(Math.max(physicsStoreSpaces,
                summaries.size()),
            runtimeBodies,
            0,
            runtimeJoints,
            saved.schemaVersion(),
            saved.spaces(),
            saved.bodies(),
            saved.joints(),
            restoreState(restore),
            restoreMessage(restore));
    }

    @Nonnull
    private static Status copiedStatus(@Nonnull Store<PhysicsStore> physicsStore) {
        PhysicsThreading.requireWorldThread(physicsStore,
            "read copied PhysicsStore persistence status");
        SavedStateSummary saved = savedStateSummary(physicsStore);
        PhysicsRestoreStatusResource restore = physicsStore.getResource(
            PhysicsRestoreStatusResource.getResourceType());
        int runtimeBodies = physicsStore.getResource(PhysicsSnapshotResource.getResourceType())
            .getLatestFrame()
            .bodies()
            .size();
        int physicsStoreSpaces = physicsStore.getResource(
            PhysicsSpaceCompatibilityIndexResource.getResourceType()).size();
        return new Status(physicsStoreSpaces,
            runtimeBodies,
            0,
            saved.joints(),
            saved.schemaVersion(),
            saved.spaces(),
            saved.bodies(),
            saved.joints(),
            restoreState(restore),
            restoreMessage(restore));
    }

    @Nonnull
    private static SavedStateSummary savedStateSummary(@Nonnull Store<PhysicsStore> physicsStore) {
        PhysicsStoreHolderStorage.Summary holderSummary = PhysicsStoreHolderStorage.summary(
            physicsStore);
        if (holderSummary.present()) {
            return new SavedStateSummary(CURRENT_SCHEMA_VERSION,
                holderSummary.spaces(),
                holderSummary.bodies(),
                holderSummary.joints());
        }
        return new SavedStateSummary(CURRENT_SCHEMA_VERSION, 0, 0, 0);
    }

    @Nonnull
    private static RestoreState restoreState(@Nonnull PhysicsRestoreStatusResource restore) {
        if (restore.isFailed()) {
            return RestoreState.FAILED;
        }
        if (restore.isPending()) {
            return RestoreState.PENDING_SPACES;
        }
        return RestoreState.IDLE;
    }

    @Nonnull
    private static String restoreMessage(@Nonnull PhysicsRestoreStatusResource restore) {
        if (restore.isFailed()) {
            return restore.getFailureMessage();
        }
        if (!restore.getSoftSkipsByReason().isEmpty()) {
            return "PhysicsStore restore soft skips: " + restore.getSoftSkipsByReason();
        }
        return "";
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
                         int schemaVersion,
                         int storedSpaces,
                         int storedBodies,
                         int storedJoints,
                         @Nonnull RestoreState restoreState,
                         @Nonnull String restoreMessage) {

        public boolean hasRestoreMessage() {
            return !restoreMessage.isEmpty();
        }
    }

    private record SavedStateSummary(int schemaVersion, int spaces, int bodies, int joints) {
    }

}
