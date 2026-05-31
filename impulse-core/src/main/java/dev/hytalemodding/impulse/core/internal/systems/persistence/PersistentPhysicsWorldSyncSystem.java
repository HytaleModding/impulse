package dev.hytalemodding.impulse.core.internal.systems.persistence;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.core.internal.persistence.PersistentPhysicsRuntimeSnapshot;
import dev.hytalemodding.impulse.core.internal.persistence.PersistentPhysicsWorldResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsWorldRuntimeResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsWorldWorkerResource;
import dev.hytalemodding.impulse.core.internal.systems.publication.PhysicsSnapshotPublicationSystem;
import dev.hytalemodding.impulse.core.internal.worker.PhysicsWorkerAccess;
import dev.hytalemodding.impulse.core.internal.worker.PhysicsWorkerSnapshot;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyPersistenceMode;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsWorldSettings;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsWorldResource;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.RejectedExecutionException;
import javax.annotation.Nonnull;

/**
 * Syncs the persisted world resource from worker-owned runtime snapshots.
 *
 * <p>Mirrors the live spaces, their settings, persistent bodies, and persistent
 * endpoint joints back into {@link PersistentPhysicsWorldResource} on a bounded
 * cadence. Scalar world settings and cheap topology count changes request a full
 * worker snapshot immediately; joint-only footprint checks are queued as light
 * worker reads so the main tick does not inspect live backend joints.</p>
 *
 * <p>Runs after restore hydration to ensure both sides are settled before copying.
 * Skipped while a restore is in progress, or after a hard restore failure, to avoid
 * overwriting deserialized data before hydration finishes or before the failure is
 * resolved.</p>
 */
public class PersistentPhysicsWorldSyncSystem extends TickingSystem<EntityStore> {

    private static final int WORLD_SYNC_INTERVAL_TICKS = 20;
    private static final Set<Dependency<EntityStore>> DEPENDENCIES = Set.of(
        new SystemDependency<>(Order.AFTER, PersistentPhysicsJointHydrationSystem.class),
        new SystemDependency<>(Order.AFTER, PhysicsSnapshotPublicationSystem.class)
    );

    private final Map<PersistentPhysicsWorldResource, PendingRuntimeRead> pendingRuntimeReads =
        new WeakHashMap<>();

    @Override
    public void tick(float dt, int systemIndex, @Nonnull Store<EntityStore> store) {
        PersistentPhysicsWorldResource persistent = store.getResource(
            PersistentPhysicsWorldResource.getResourceType());
        if (persistent.isRuntimeRestorePending() || persistent.hasRuntimeRestoreFailed()) {
            pendingRuntimeReads.remove(persistent);
            return;
        }

        PhysicsWorldRuntimeResource runtime = PhysicsWorldRuntimeResource.require(store);
        PendingRuntimeRead pending = pendingRuntimeReads.get(persistent);
        if (pending != null) {
            if (!pending.isDone()) {
                return;
            }
            pendingRuntimeReads.remove(persistent);
            if (pending.kind() == RuntimeReadKind.SNAPSHOT) {
                syncRuntimeSnapshot(persistent, pending.joinSnapshot());
                return;
            }
            if (hasRuntimePersistenceFootprintChanged(persistent, pending.joinFootprint())) {
                requestSnapshotRead(store, persistent, runtime);
            }
            return;
        }

        if (!hasScalarWorldStateChanged(persistent, runtime)
            && !hasCheapRuntimePersistenceFootprintChanged(persistent, runtime)
            && !persistent.shouldSyncRuntimeSnapshot(WORLD_SYNC_INTERVAL_TICKS)) {
            requestFootprintRead(store, persistent, runtime);
            return;
        }

        requestSnapshotRead(store, persistent, runtime);
    }

    @Nonnull
    public static SyncResult syncRuntimeSnapshot(@Nonnull Store<EntityStore> store,
        @Nonnull PersistentPhysicsWorldResource persistent,
        @Nonnull PhysicsWorldResource runtime) {
        if (persistent.isRuntimeRestorePending()) {
            return SyncResult.skipped("restore pending");
        }
        if (persistent.hasRuntimeRestoreFailed()) {
            return SyncResult.skipped("restore failed");
        }

        PhysicsWorldRuntimeResource runtimeResource = PhysicsWorldRuntimeResource.require(runtime);
        PersistentPhysicsRuntimeSnapshot snapshot = PhysicsWorkerAccess.call(store,
            "capture persisted physics runtime snapshot",
            () -> PersistentPhysicsRuntimeSnapshot.capture(runtimeResource));
        return syncRuntimeSnapshot(persistent, snapshot);
    }

    @Nonnull
    public static SyncResult syncRuntimeSnapshot(@Nonnull PersistentPhysicsWorldResource persistent,
        @Nonnull PersistentPhysicsRuntimeSnapshot snapshot) {
        if (persistent.isRuntimeRestorePending()) {
            return SyncResult.skipped("restore pending");
        }
        if (persistent.hasRuntimeRestoreFailed()) {
            return SyncResult.skipped("restore failed");
        }

        persistent.setSchemaVersion(PersistentPhysicsWorldResource.CURRENT_SCHEMA_VERSION);
        persistent.setWorldSettings(snapshot.getWorldSettings());
        persistent.setSpaces(snapshot.getSpaces());
        persistent.setBodies(snapshot.getBodies());
        persistent.setJoints(snapshot.getJoints());
        persistent.markRuntimeSnapshotSynced();

        PersistentPhysicsRuntimeSnapshot.Footprint footprint = snapshot.getFootprint();
        return SyncResult.synced(footprint.spaces(), footprint.bodies(), footprint.joints());
    }

    private static boolean hasScalarWorldStateChanged(@Nonnull PersistentPhysicsWorldResource persistent,
        @Nonnull PhysicsWorldRuntimeResource runtime) {
        PhysicsWorldSettings runtimeSettings = runtime.getWorldSettings();
        PhysicsWorldSettings persistedSettings = persistent.getWorldSettings();
        return persistedSettings.getSimulationSteps() != runtimeSettings.getSimulationSteps()
            || persistedSettings.getStepMode() != runtimeSettings.getStepMode()
            || persistedSettings.getStepSchedulingMode() != runtimeSettings.getStepSchedulingMode()
            || Float.compare(persistedSettings.getMaxStepDt(), runtimeSettings.getMaxStepDt()) != 0;
    }

    private static boolean hasCheapRuntimePersistenceFootprintChanged(
        @Nonnull PersistentPhysicsWorldResource persistent,
        @Nonnull PhysicsWorldRuntimeResource runtime) {
        return persistent.getSpaceCount() != runtime.getSpaceCount()
            || persistent.getBodyCount() != runtime.getBodyRegistrationCount(
                PhysicsBodyPersistenceMode.PERSISTENT);
    }

    static boolean hasRuntimePersistenceFootprintChanged(
        @Nonnull PersistentPhysicsWorldResource persistent,
        @Nonnull PersistentPhysicsRuntimeSnapshot.Footprint footprint) {
        return persistent.getSpaceCount() != footprint.spaces()
            || persistent.getBodyCount() != footprint.bodies()
            || persistent.getJointCount() != footprint.joints();
    }

    private void requestSnapshotRead(@Nonnull Store<EntityStore> store,
        @Nonnull PersistentPhysicsWorldResource persistent,
        @Nonnull PhysicsWorldRuntimeResource runtime) {
        if (runtime.canAccessLiveBackendDirectly()) {
            syncRuntimeSnapshot(persistent, PersistentPhysicsRuntimeSnapshot.capture(runtime));
            return;
        }
        submitWorkerRead(store,
            persistent,
            PendingRuntimeRead.snapshot(),
            future -> future.complete(PersistentPhysicsRuntimeSnapshot.capture(runtime)));
    }

    private void requestFootprintRead(@Nonnull Store<EntityStore> store,
        @Nonnull PersistentPhysicsWorldResource persistent,
        @Nonnull PhysicsWorldRuntimeResource runtime) {
        if (runtime.canAccessLiveBackendDirectly()) {
            PersistentPhysicsRuntimeSnapshot.Footprint footprint =
                PersistentPhysicsRuntimeSnapshot.captureFootprint(runtime);
            if (hasRuntimePersistenceFootprintChanged(persistent, footprint)) {
                requestSnapshotRead(store, persistent, runtime);
            }
            return;
        }
        submitWorkerRead(store,
            persistent,
            PendingRuntimeRead.footprint(),
            future -> future.complete(PersistentPhysicsRuntimeSnapshot.captureFootprint(runtime)));
    }

    private void submitWorkerRead(@Nonnull Store<EntityStore> store,
        @Nonnull PersistentPhysicsWorldResource persistent,
        @Nonnull PendingRuntimeRead pending,
        @Nonnull WorkerReadCapture capture) {
        PhysicsWorldWorkerResource worker = store.getResource(PhysicsWorldWorkerResource.getResourceType());
        if (worker.isClosed()) {
            return;
        }

        try {
            worker.submitMutation(pending.operation(), () -> {
                try {
                    capture.capture(pending.future());
                    return PhysicsWorkerSnapshot.empty();
                } catch (RuntimeException | Error exception) {
                    pending.future().completeExceptionally(exception);
                    throw exception;
                }
            });
            pendingRuntimeReads.put(persistent, pending);
        } catch (RejectedExecutionException exception) {
            pending.future().completeExceptionally(exception);
        }
    }

    public record SyncResult(boolean synced,
        int spaces,
        int bodies,
        int joints,
        @Nonnull String skippedReason) {

        @Nonnull
        private static SyncResult synced(int spaces, int bodies, int joints) {
            return new SyncResult(true, spaces, bodies, joints, "");
        }

        @Nonnull
        private static SyncResult skipped(@Nonnull String reason) {
            return new SyncResult(false, 0, 0, 0, reason);
        }
    }

    private enum RuntimeReadKind {
        SNAPSHOT,
        FOOTPRINT
    }

    private record PendingRuntimeRead(@Nonnull RuntimeReadKind kind,
                                      @Nonnull CompletableFuture<Object> future) {

        @Nonnull
        private static PendingRuntimeRead snapshot() {
            return new PendingRuntimeRead(RuntimeReadKind.SNAPSHOT, new CompletableFuture<>());
        }

        @Nonnull
        private static PendingRuntimeRead footprint() {
            return new PendingRuntimeRead(RuntimeReadKind.FOOTPRINT, new CompletableFuture<>());
        }

        private boolean isDone() {
            return future.isDone();
        }

        @Nonnull
        private String operation() {
            return kind == RuntimeReadKind.SNAPSHOT
                ? "capture persisted physics runtime snapshot"
                : "capture persisted physics runtime footprint";
        }

        @Nonnull
        private PersistentPhysicsRuntimeSnapshot joinSnapshot() {
            return (PersistentPhysicsRuntimeSnapshot) joinFuture();
        }

        @Nonnull
        private PersistentPhysicsRuntimeSnapshot.Footprint joinFootprint() {
            return (PersistentPhysicsRuntimeSnapshot.Footprint) joinFuture();
        }

        @Nonnull
        private Object joinFuture() {
            try {
                return future.join();
            } catch (CompletionException exception) {
                Throwable cause = exception.getCause();
                if (cause instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                }
                if (cause instanceof Error error) {
                    throw error;
                }
                throw exception;
            }
        }
    }

    @FunctionalInterface
    private interface WorkerReadCapture {

        void capture(@Nonnull CompletableFuture<Object> future);
    }

    @Nonnull
    @Override
    public Set<Dependency<EntityStore>> getDependencies() {
        return DEPENDENCIES;
    }

}
