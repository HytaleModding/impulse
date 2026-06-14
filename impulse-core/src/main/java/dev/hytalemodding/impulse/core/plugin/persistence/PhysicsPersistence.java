package dev.hytalemodding.impulse.core.plugin.persistence;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.early.PhysicsStoreWorld;
import dev.hytalemodding.impulse.core.internal.persistence.PersistentPhysicsWorldResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.persistence.PersistentPhysicsStoreResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsRestoreStatusResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsSpaceCompatibilityIndexResource;
import dev.hytalemodding.impulse.core.plugin.physicsstore.PhysicsStoreDiagnostics;
import dev.hytalemodding.impulse.core.plugin.simulation.SpaceSummary;
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
        Store<PhysicsStore> physicsStore = ((PhysicsStoreWorld) store.getExternalData().getWorld())
            .getPhysicsStore()
            .getStore();
        PersistentPhysicsStoreResource persistent = physicsStore.getResource(
            PersistentPhysicsStoreResource.getResourceType());
        PersistentPhysicsWorldResource legacy = store.getResource(
            PersistentPhysicsWorldResource.getResourceType());
        PhysicsRestoreStatusResource restore = physicsStore.getResource(
            PhysicsRestoreStatusResource.getResourceType());
        List<SpaceSummary> summaries = PhysicsStoreDiagnostics.spaceSummaries(physicsStore);
        int runtimeBodies = summaries.stream().mapToInt(SpaceSummary::bodyCount).sum();
        int runtimeJoints = summaries.stream().mapToInt(SpaceSummary::jointCount).sum();
        int physicsStoreSpaces = physicsStore.getResource(
            PhysicsSpaceCompatibilityIndexResource.getResourceType()).size();
        return new Status(Math.max(physicsStoreSpaces,
                summaries.size()),
            runtimeBodies,
            0,
            runtimeJoints,
            persistent.getSchemaVersion(),
            persistent.getSpaces().length,
            persistent.getBodies().length,
            persistent.getJoints().length,
            restoreState(restore),
            restoreMessage(restore, persistent, legacy));
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
    private static String restoreMessage(@Nonnull PhysicsRestoreStatusResource restore,
        @Nonnull PersistentPhysicsStoreResource persistent,
        @Nonnull PersistentPhysicsWorldResource legacy) {
        if (restore.isFailed()) {
            return restore.getFailureMessage();
        }
        if (!restore.getSoftSkipsByReason().isEmpty()) {
            return "PhysicsStore restore soft skips: " + restore.getSoftSkipsByReason();
        }
        if (hasLegacyData(legacy)) {
            String legacyCounts = "legacy PersistentPhysicsWorld spaces=" + legacy.getSpaceCount()
                + ", bodies=" + legacy.getBodyCount()
                + ", joints=" + legacy.getJointCount();
            if (hasAuthoritativeData(persistent)) {
                return legacyCounts
                    + " ignored because PersistentPhysicsStore contains authoritative state.";
            }
            return legacyCounts
                + " present, but legacy import into PersistentPhysicsStore is deferred.";
        }
        return "";
    }

    private static boolean hasAuthoritativeData(@Nonnull PersistentPhysicsStoreResource persistent) {
        return persistent.getSpaces().length > 0
            || persistent.getBodies().length > 0
            || persistent.getJoints().length > 0;
    }

    private static boolean hasLegacyData(@Nonnull PersistentPhysicsWorldResource legacy) {
        return legacy.getSpaceCount() > 0 || legacy.getBodyCount() > 0 || legacy.getJointCount() > 0;
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
