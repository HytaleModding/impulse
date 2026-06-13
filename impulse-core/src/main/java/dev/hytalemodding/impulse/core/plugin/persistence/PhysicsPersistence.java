package dev.hytalemodding.impulse.core.plugin.persistence;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.core.internal.physicsstore.persistence.PersistentPhysicsStoreResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsRestoreStatusResource;
import dev.hytalemodding.impulse.core.plugin.physicsstore.PhysicsStoreAccess;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsWorldResource;
import dev.hytalemodding.impulse.core.plugin.simulation.SpaceSummary;
import dev.hytalemodding.impulse.core.plugin.simulation.query.SpaceSummaryQuery;
import java.util.List;
import javax.annotation.Nonnull;

/**
 * Public command/plugin facade for world-level Impulse persistence status and lifecycle actions.
 */
public final class PhysicsPersistence {

    public static final int CURRENT_SCHEMA_VERSION =
        PersistentPhysicsStoreResource.CURRENT_SCHEMA_VERSION;

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
            "authoritative-physics-store-auto-capture");
    }

    @Nonnull
    public static RestoreRequestResult requestRuntimeRestore(@Nonnull Store<EntityStore> store) {
        Status status = status(store);
        return new RestoreRequestResult(false, "authoritative-physics-store-auto-restore", status);
    }

    @Nonnull
    public static Status status(@Nonnull Store<EntityStore> store) {
        PhysicsWorldResource runtime = runtime(store);
        Store<PhysicsStore> physicsStore = PhysicsStoreAccess.require(store.getExternalData().getWorld())
            .getStore();
        PersistentPhysicsStoreResource persistent = physicsStore.getResource(
            PersistentPhysicsStoreResource.getResourceType());
        PhysicsRestoreStatusResource restore = physicsStore.getResource(
            PhysicsRestoreStatusResource.getResourceType());
        List<SpaceSummary> summaries = spaceSummaries(runtime);
        int runtimeBodies = summaries.stream().mapToInt(SpaceSummary::bodyCount).sum();
        int runtimeJoints = summaries.stream().mapToInt(SpaceSummary::jointCount).sum();
        return new Status(Math.max(PhysicsStoreAccess.spaceCount(store.getExternalData().getWorld()),
                summaries.size()),
            runtimeBodies,
            0,
            runtimeJoints,
            persistent.getSchemaVersion(),
            persistent.getSpaces().length,
            persistent.getBodies().length,
            persistent.getJoints().length,
            restoreState(restore),
            restoreMessage(restore));
    }

    @Nonnull
    private static List<SpaceSummary> spaceSummaries(@Nonnull PhysicsWorldResource runtime) {
        return runtime.query(new SpaceSummaryQuery(null))
            .completion()
            .toCompletableFuture()
            .join();
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
}
