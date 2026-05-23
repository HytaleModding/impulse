package dev.hytalemodding.impulse.core.internal.resources.profiling;

import com.hypixel.hytale.component.Resource;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.api.PhysicsStepPhaseStats;
import dev.hytalemodding.impulse.core.ImpulsePlugin;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import lombok.Getter;
import lombok.Setter;

/**
 * Runtime-only profiling state for hot Impulse simulation paths.
 *
 * <p>This resource tracks coarse performance and selectivity metrics for the
 * world step and entity sync phases. It is intentionally operational only and
 * is not part of persisted world physics state.</p>
 */
@Getter
public class PhysicsRuntimeProfilingResource implements Resource<EntityStore> {

    @Setter
    private boolean enabled;

    private final StepSnapshot cumulativeStep = new StepSnapshot();
    private final StepSnapshot latestStep = new StepSnapshot();
    private final StepSnapshot worstStep = new StepSnapshot();
    private final SyncSnapshot cumulativeSync = new SyncSnapshot();
    private final SyncSnapshot latestSync = new SyncSnapshot();
    private final SyncSnapshot worstSync = new SyncSnapshot();
    private final VisualSnapshot cumulativeVisual = new VisualSnapshot();
    private final VisualSnapshot latestVisual = new VisualSnapshot();
    private final VisualSnapshot worstVisual = new VisualSnapshot();

    @Nullable
    private transient SyncCollector activeSyncCollector;
    private transient long previousWorkerStepCompletedNanos;

    public PhysicsRuntimeProfilingResource() {
    }

    public void recordStep(int spaces, int substeps, long nanos) {
        recordStep(spaces, substeps, nanos, 0, 0, 0, 0, 0);
    }

    public void recordStep(int spaces,
        int substeps,
        long nanos,
        int bodySnapshots,
        int spatialIndexCells,
        long snapshotNanos) {
        recordStep(spaces,
            substeps,
            nanos,
            bodySnapshots,
            spatialIndexCells,
            snapshotNanos,
            0L,
            0L);
    }

    public void recordStep(int spaces,
        int substeps,
        long nanos,
        int bodySnapshots,
        int spatialIndexCells,
        long snapshotNanos,
        long workerQueuedNanos,
        long workerRunNanos) {
        recordStep(spaces,
            substeps,
            nanos,
            bodySnapshots,
            spatialIndexCells,
            snapshotNanos,
            workerQueuedNanos,
            workerRunNanos,
            0L,
            PhysicsStepPhaseStats.unavailable());
    }

    public void recordStep(int spaces,
        int substeps,
        long nanos,
        int bodySnapshots,
        int spatialIndexCells,
        long snapshotNanos,
        long workerQueuedNanos,
        long workerRunNanos,
        @Nonnull PhysicsStepPhaseStats nativePhaseStats) {
        recordStep(spaces,
            substeps,
            nanos,
            bodySnapshots,
            spatialIndexCells,
            snapshotNanos,
            workerQueuedNanos,
            workerRunNanos,
            0L,
            nativePhaseStats);
    }

    public void recordStep(int spaces,
        int substeps,
        long nanos,
        int bodySnapshots,
        int spatialIndexCells,
        long snapshotNanos,
        long workerQueuedNanos,
        long workerRunNanos,
        long workerCompletedNanos,
        @Nonnull PhysicsStepPhaseStats nativePhaseStats) {
        StepSnapshot snapshot = new StepSnapshot();
        snapshot.recordTickSample();
        snapshot.setSpaces(spaces);
        snapshot.setSubsteps(substeps);
        snapshot.setTickNanos(nanos);
        snapshot.setBodySnapshots(bodySnapshots);
        snapshot.setSpatialIndexCells(spatialIndexCells);
        snapshot.setSnapshotNanos(snapshotNanos);
        snapshot.setWorkerQueuedNanos(workerQueuedNanos);
        snapshot.setWorkerRunNanos(workerRunNanos);
        snapshot.recordWorkerStepInterval(recordWorkerStepInterval(workerCompletedNanos));
        snapshot.setNativePhaseStats(nativePhaseStats);

        latestStep.copyFrom(snapshot);
        cumulativeStep.add(snapshot);
        if (snapshot.getTickNanos() >= worstStep.getTickNanos()) {
            worstStep.copyFrom(snapshot);
        }
    }

    public void recordStepSkippedPending(long pendingStepAgeNanos) {
        long safePendingStepAgeNanos = Math.max(0L, pendingStepAgeNanos);
        StepSnapshot snapshot = new StepSnapshot();
        snapshot.setSkippedPendingSteps(1);
        snapshot.setPendingStepAgeNanos(safePendingStepAgeNanos);
        snapshot.setMaxPendingStepAgeNanos(safePendingStepAgeNanos);
        latestStep.setSkippedPendingSteps(1);
        latestStep.setPendingStepAgeNanos(safePendingStepAgeNanos);
        latestStep.setMaxPendingStepAgeNanos(safePendingStepAgeNanos);
        cumulativeStep.add(snapshot);
        if (safePendingStepAgeNanos >= worstStep.getMaxPendingStepAgeNanos()) {
            worstStep.setMaxPendingStepAgeNanos(safePendingStepAgeNanos);
        }
    }

    @Nonnull
    public SyncCollector beginSyncSample() {
        SyncCollector collector = new SyncCollector();
        activeSyncCollector = collector;
        return collector;
    }

    public void finishSyncSample(@Nonnull SyncCollector collector, long nanos) {
        SyncSnapshot snapshot = new SyncSnapshot();
        snapshot.copyFrom(collector.snapshot(nanos));
        snapshot.recordTickSample();

        latestSync.copyFrom(snapshot);
        cumulativeSync.add(snapshot);
        if (snapshot.getTickNanos() >= worstSync.getTickNanos()) {
            worstSync.copyFrom(snapshot);
        }
        if (activeSyncCollector == collector) {
            activeSyncCollector = null;
        }
    }

    @Nonnull
    public VisualCollector beginVisualSample() {
        return new VisualCollector();
    }

    public void finishVisualSample(@Nonnull VisualCollector collector, long nanos) {
        VisualSnapshot snapshot = new VisualSnapshot();
        snapshot.copyFrom(collector.snapshot(nanos));
        snapshot.recordTickSample();

        latestVisual.copyFrom(snapshot);
        cumulativeVisual.add(snapshot);
        if (snapshot.getTickNanos() >= worstVisual.getTickNanos()) {
            worstVisual.copyFrom(snapshot);
        }
    }

    public void reset() {
        cumulativeStep.reset();
        latestStep.reset();
        worstStep.reset();
        cumulativeSync.reset();
        latestSync.reset();
        worstSync.reset();
        cumulativeVisual.reset();
        latestVisual.reset();
        worstVisual.reset();
        activeSyncCollector = null;
        previousWorkerStepCompletedNanos = 0L;
    }

    @Nonnull
    @Override
    public PhysicsRuntimeProfilingResource clone() {
        PhysicsRuntimeProfilingResource copy = new PhysicsRuntimeProfilingResource();
        copy.enabled = enabled;
        copy.cumulativeStep.copyFrom(cumulativeStep);
        copy.latestStep.copyFrom(latestStep);
        copy.worstStep.copyFrom(worstStep);
        copy.cumulativeSync.copyFrom(cumulativeSync);
        copy.latestSync.copyFrom(latestSync);
        copy.worstSync.copyFrom(worstSync);
        copy.cumulativeVisual.copyFrom(cumulativeVisual);
        copy.latestVisual.copyFrom(latestVisual);
        copy.worstVisual.copyFrom(worstVisual);
        copy.previousWorkerStepCompletedNanos = previousWorkerStepCompletedNanos;
        return copy;
    }

    private long recordWorkerStepInterval(long workerCompletedNanos) {
        if (workerCompletedNanos <= 0L) {
            return 0L;
        }
        if (previousWorkerStepCompletedNanos <= 0L) {
            previousWorkerStepCompletedNanos = workerCompletedNanos;
            return 0L;
        }
        if (workerCompletedNanos <= previousWorkerStepCompletedNanos) {
            return 0L;
        }
        long intervalNanos = workerCompletedNanos - previousWorkerStepCompletedNanos;
        previousWorkerStepCompletedNanos = workerCompletedNanos;
        return intervalNanos;
    }

    public static ResourceType<EntityStore, PhysicsRuntimeProfilingResource> getResourceType() {
        return ImpulsePlugin.get().getPhysicsRuntimeProfilingResourceType();
    }

    @Getter
    public static final class StepSnapshot {

        private int tickSamples;
        @Setter
        private int spaces;
        @Setter
        private int substeps;
        @Setter
        private int bodySnapshots;
        @Setter
        private int spatialIndexCells;
        @Setter
        private long tickNanos;
        @Setter
        private long snapshotNanos;
        @Setter
        private long workerQueuedNanos;
        @Setter
        private long workerRunNanos;
        private int workerStepRateSamples;
        private long workerStepIntervalNanos;
        private long maxWorkerStepIntervalNanos;
        @Setter
        private int skippedPendingSteps;
        @Setter
        private long pendingStepAgeNanos;
        @Setter
        private long maxPendingStepAgeNanos;
        private int nativePhaseSamples;
        private long nativeStepNanos;
        private long nativeBroadPhaseNanos;
        private long nativeNarrowPhaseNanos;
        private long nativeSolverNanos;
        private long nativeCcdNanos;
        private long nativeSnapshotNanos;

        public void recordTickSample() {
            tickSamples++;
        }

        public void copyFrom(@Nonnull StepSnapshot other) {
            tickSamples = other.tickSamples;
            spaces = other.spaces;
            substeps = other.substeps;
            bodySnapshots = other.bodySnapshots;
            spatialIndexCells = other.spatialIndexCells;
            tickNanos = other.tickNanos;
            snapshotNanos = other.snapshotNanos;
            workerQueuedNanos = other.workerQueuedNanos;
            workerRunNanos = other.workerRunNanos;
            workerStepRateSamples = other.workerStepRateSamples;
            workerStepIntervalNanos = other.workerStepIntervalNanos;
            maxWorkerStepIntervalNanos = other.maxWorkerStepIntervalNanos;
            skippedPendingSteps = other.skippedPendingSteps;
            pendingStepAgeNanos = other.pendingStepAgeNanos;
            maxPendingStepAgeNanos = other.maxPendingStepAgeNanos;
            nativePhaseSamples = other.nativePhaseSamples;
            nativeStepNanos = other.nativeStepNanos;
            nativeBroadPhaseNanos = other.nativeBroadPhaseNanos;
            nativeNarrowPhaseNanos = other.nativeNarrowPhaseNanos;
            nativeSolverNanos = other.nativeSolverNanos;
            nativeCcdNanos = other.nativeCcdNanos;
            nativeSnapshotNanos = other.nativeSnapshotNanos;
        }

        public void add(@Nonnull StepSnapshot other) {
            tickSamples += other.tickSamples;
            spaces += other.spaces;
            substeps += other.substeps;
            bodySnapshots += other.bodySnapshots;
            spatialIndexCells += other.spatialIndexCells;
            tickNanos += other.tickNanos;
            snapshotNanos += other.snapshotNanos;
            workerQueuedNanos += other.workerQueuedNanos;
            workerRunNanos += other.workerRunNanos;
            workerStepRateSamples += other.workerStepRateSamples;
            workerStepIntervalNanos += other.workerStepIntervalNanos;
            maxWorkerStepIntervalNanos = Math.max(maxWorkerStepIntervalNanos,
                other.maxWorkerStepIntervalNanos);
            skippedPendingSteps += other.skippedPendingSteps;
            pendingStepAgeNanos += other.pendingStepAgeNanos;
            maxPendingStepAgeNanos = Math.max(maxPendingStepAgeNanos,
                other.maxPendingStepAgeNanos);
            nativePhaseSamples += other.nativePhaseSamples;
            nativeStepNanos += other.nativeStepNanos;
            nativeBroadPhaseNanos += other.nativeBroadPhaseNanos;
            nativeNarrowPhaseNanos += other.nativeNarrowPhaseNanos;
            nativeSolverNanos += other.nativeSolverNanos;
            nativeCcdNanos += other.nativeCcdNanos;
            nativeSnapshotNanos += other.nativeSnapshotNanos;
        }

        public void reset() {
            tickSamples = 0;
            spaces = 0;
            substeps = 0;
            bodySnapshots = 0;
            spatialIndexCells = 0;
            tickNanos = 0L;
            snapshotNanos = 0L;
            workerQueuedNanos = 0L;
            workerRunNanos = 0L;
            workerStepRateSamples = 0;
            workerStepIntervalNanos = 0L;
            maxWorkerStepIntervalNanos = 0L;
            skippedPendingSteps = 0;
            pendingStepAgeNanos = 0L;
            maxPendingStepAgeNanos = 0L;
            nativePhaseSamples = 0;
            nativeStepNanos = 0L;
            nativeBroadPhaseNanos = 0L;
            nativeNarrowPhaseNanos = 0L;
            nativeSolverNanos = 0L;
            nativeCcdNanos = 0L;
            nativeSnapshotNanos = 0L;
        }

        public void recordWorkerStepInterval(long intervalNanos) {
            if (intervalNanos <= 0L) {
                workerStepRateSamples = 0;
                workerStepIntervalNanos = 0L;
                maxWorkerStepIntervalNanos = 0L;
                return;
            }
            workerStepRateSamples = 1;
            workerStepIntervalNanos = intervalNanos;
            maxWorkerStepIntervalNanos = intervalNanos;
        }

        public void setNativePhaseStats(@Nonnull PhysicsStepPhaseStats stats) {
            if (!stats.available()) {
                nativePhaseSamples = 0;
                nativeStepNanos = 0L;
                nativeBroadPhaseNanos = 0L;
                nativeNarrowPhaseNanos = 0L;
                nativeSolverNanos = 0L;
                nativeCcdNanos = 0L;
                nativeSnapshotNanos = 0L;
                return;
            }
            nativePhaseSamples = 1;
            nativeStepNanos = stats.stepNanos();
            nativeBroadPhaseNanos = stats.broadPhaseNanos();
            nativeNarrowPhaseNanos = stats.narrowPhaseNanos();
            nativeSolverNanos = stats.solverNanos();
            nativeCcdNanos = stats.ccdNanos();
            nativeSnapshotNanos = stats.snapshotNanos();
        }
    }

    @Getter
    public static final class SyncSnapshot {

        private int tickSamples;
        @Setter
        private int bodiesInspected;
        @Setter
        private int bodiesSynced;
        @Setter
        private int transitionSyncs;
        @Setter
        private int keepaliveSyncs;
        @Setter
        private int skippedSleeping;
        @Setter
        private int skippedThreshold;
        @Setter
        private int skippedVisualDeadzone;
        @Setter
        private int skippedVisualRange;
        @Setter
        private int skippedStatic;
        @Setter
        private int skippedMissingSpace;
        @Setter
        private long tickNanos;

        public void recordTickSample() {
            tickSamples++;
        }

        public void copyFrom(@Nonnull SyncSnapshot other) {
            tickSamples = other.tickSamples;
            bodiesInspected = other.bodiesInspected;
            bodiesSynced = other.bodiesSynced;
            transitionSyncs = other.transitionSyncs;
            keepaliveSyncs = other.keepaliveSyncs;
            skippedSleeping = other.skippedSleeping;
            skippedThreshold = other.skippedThreshold;
            skippedVisualDeadzone = other.skippedVisualDeadzone;
            skippedVisualRange = other.skippedVisualRange;
            skippedStatic = other.skippedStatic;
            skippedMissingSpace = other.skippedMissingSpace;
            tickNanos = other.tickNanos;
        }

        public void add(@Nonnull SyncSnapshot other) {
            tickSamples += other.tickSamples;
            bodiesInspected += other.bodiesInspected;
            bodiesSynced += other.bodiesSynced;
            transitionSyncs += other.transitionSyncs;
            keepaliveSyncs += other.keepaliveSyncs;
            skippedSleeping += other.skippedSleeping;
            skippedThreshold += other.skippedThreshold;
            skippedVisualDeadzone += other.skippedVisualDeadzone;
            skippedVisualRange += other.skippedVisualRange;
            skippedStatic += other.skippedStatic;
            skippedMissingSpace += other.skippedMissingSpace;
            tickNanos += other.tickNanos;
        }

        public void reset() {
            tickSamples = 0;
            bodiesInspected = 0;
            bodiesSynced = 0;
            transitionSyncs = 0;
            keepaliveSyncs = 0;
            skippedSleeping = 0;
            skippedThreshold = 0;
            skippedVisualDeadzone = 0;
            skippedVisualRange = 0;
            skippedStatic = 0;
            skippedMissingSpace = 0;
            tickNanos = 0L;
        }
    }

    public static final class SyncCollector {

        private final AtomicInteger bodiesInspected = new AtomicInteger();
        private final AtomicInteger bodiesSynced = new AtomicInteger();
        private final AtomicInteger transitionSyncs = new AtomicInteger();
        private final AtomicInteger keepaliveSyncs = new AtomicInteger();
        private final AtomicInteger skippedSleeping = new AtomicInteger();
        private final AtomicInteger skippedThreshold = new AtomicInteger();
        private final AtomicInteger skippedVisualDeadzone = new AtomicInteger();
        private final AtomicInteger skippedVisualRange = new AtomicInteger();
        private final AtomicInteger skippedStatic = new AtomicInteger();
        private final AtomicInteger skippedMissingSpace = new AtomicInteger();

        public void incrementBodiesInspected() {
            bodiesInspected.incrementAndGet();
        }

        public void incrementBodiesSynced() {
            bodiesSynced.incrementAndGet();
        }

        public void incrementTransitionSyncs() {
            transitionSyncs.incrementAndGet();
        }

        public void incrementKeepaliveSyncs() {
            keepaliveSyncs.incrementAndGet();
        }

        public void incrementSkippedSleeping() {
            skippedSleeping.incrementAndGet();
        }

        public void incrementSkippedThreshold() {
            skippedThreshold.incrementAndGet();
        }

        public void incrementSkippedVisualDeadzone() {
            skippedVisualDeadzone.incrementAndGet();
        }

        public void incrementSkippedVisualRange() {
            skippedVisualRange.incrementAndGet();
        }

        public void incrementSkippedStatic() {
            skippedStatic.incrementAndGet();
        }

        public void incrementSkippedMissingSpace() {
            skippedMissingSpace.incrementAndGet();
        }

        @Nonnull
        private SyncSnapshot snapshot(long nanos) {
            SyncSnapshot snapshot = new SyncSnapshot();
            snapshot.setBodiesInspected(bodiesInspected.get());
            snapshot.setBodiesSynced(bodiesSynced.get());
            snapshot.setTransitionSyncs(transitionSyncs.get());
            snapshot.setKeepaliveSyncs(keepaliveSyncs.get());
            snapshot.setSkippedSleeping(skippedSleeping.get());
            snapshot.setSkippedThreshold(skippedThreshold.get());
            snapshot.setSkippedVisualDeadzone(skippedVisualDeadzone.get());
            snapshot.setSkippedVisualRange(skippedVisualRange.get());
            snapshot.setSkippedStatic(skippedStatic.get());
            snapshot.setSkippedMissingSpace(skippedMissingSpace.get());
            snapshot.setTickNanos(nanos);
            return snapshot;
        }
    }

    @Getter
    public static final class VisualSnapshot {

        private int tickSamples;
        @Setter
        private int interests;
        @Setter
        private int materialized;
        @Setter
        private int candidates;
        @Setter
        private int spawned;
        @Setter
        private int dematerialized;
        @Setter
        private int nearQueries;
        @Setter
        private int nearQueryCandidates;
        @Setter
        private int raycasts;
        @Setter
        private int raycastCacheHits;
        @Setter
        private int candidateRefreshes;
        @Setter
        private int candidateCacheUses;
        @Setter
        private int visibilityChecks;
        @Setter
        private int visibilityCheckSkips;
        @Setter
        private long tickNanos;

        public void recordTickSample() {
            tickSamples++;
        }

        public void copyFrom(@Nonnull VisualSnapshot other) {
            tickSamples = other.tickSamples;
            interests = other.interests;
            materialized = other.materialized;
            candidates = other.candidates;
            spawned = other.spawned;
            dematerialized = other.dematerialized;
            nearQueries = other.nearQueries;
            nearQueryCandidates = other.nearQueryCandidates;
            raycasts = other.raycasts;
            raycastCacheHits = other.raycastCacheHits;
            candidateRefreshes = other.candidateRefreshes;
            candidateCacheUses = other.candidateCacheUses;
            visibilityChecks = other.visibilityChecks;
            visibilityCheckSkips = other.visibilityCheckSkips;
            tickNanos = other.tickNanos;
        }

        public void add(@Nonnull VisualSnapshot other) {
            tickSamples += other.tickSamples;
            interests += other.interests;
            materialized += other.materialized;
            candidates += other.candidates;
            spawned += other.spawned;
            dematerialized += other.dematerialized;
            nearQueries += other.nearQueries;
            nearQueryCandidates += other.nearQueryCandidates;
            raycasts += other.raycasts;
            raycastCacheHits += other.raycastCacheHits;
            candidateRefreshes += other.candidateRefreshes;
            candidateCacheUses += other.candidateCacheUses;
            visibilityChecks += other.visibilityChecks;
            visibilityCheckSkips += other.visibilityCheckSkips;
            tickNanos += other.tickNanos;
        }

        public void reset() {
            tickSamples = 0;
            interests = 0;
            materialized = 0;
            candidates = 0;
            spawned = 0;
            dematerialized = 0;
            nearQueries = 0;
            nearQueryCandidates = 0;
            raycasts = 0;
            raycastCacheHits = 0;
            candidateRefreshes = 0;
            candidateCacheUses = 0;
            visibilityChecks = 0;
            visibilityCheckSkips = 0;
            tickNanos = 0L;
        }
    }

    public static final class VisualCollector {

        private final AtomicInteger interests = new AtomicInteger();
        private final AtomicInteger materialized = new AtomicInteger();
        private final AtomicInteger candidates = new AtomicInteger();
        private final AtomicInteger spawned = new AtomicInteger();
        private final AtomicInteger dematerialized = new AtomicInteger();
        private final AtomicInteger nearQueries = new AtomicInteger();
        private final AtomicInteger nearQueryCandidates = new AtomicInteger();
        private final AtomicInteger raycasts = new AtomicInteger();
        private final AtomicInteger raycastCacheHits = new AtomicInteger();
        private final AtomicInteger candidateRefreshes = new AtomicInteger();
        private final AtomicInteger candidateCacheUses = new AtomicInteger();
        private final AtomicInteger visibilityChecks = new AtomicInteger();
        private final AtomicInteger visibilityCheckSkips = new AtomicInteger();

        public void setInterests(int count) {
            interests.set(count);
        }

        public void setMaterialized(int count) {
            materialized.set(count);
        }

        public void setCandidates(int count) {
            candidates.set(count);
        }

        public void incrementSpawned() {
            spawned.incrementAndGet();
        }

        public void incrementDematerialized() {
            dematerialized.incrementAndGet();
        }

        public void incrementNearQueries() {
            nearQueries.incrementAndGet();
        }

        public void addNearQueryCandidates(int count) {
            nearQueryCandidates.addAndGet(count);
        }

        public void incrementRaycasts() {
            raycasts.incrementAndGet();
        }

        public void incrementRaycastCacheHits() {
            raycastCacheHits.incrementAndGet();
        }

        public void incrementCandidateRefreshes() {
            candidateRefreshes.incrementAndGet();
        }

        public void incrementCandidateCacheUses() {
            candidateCacheUses.incrementAndGet();
        }

        public void incrementVisibilityChecks() {
            visibilityChecks.incrementAndGet();
        }

        public void incrementVisibilityCheckSkips() {
            visibilityCheckSkips.incrementAndGet();
        }

        public void addVisibilityCheckSkips(int count) {
            visibilityCheckSkips.addAndGet(count);
        }

        @Nonnull
        private VisualSnapshot snapshot(long nanos) {
            VisualSnapshot snapshot = new VisualSnapshot();
            snapshot.setInterests(interests.get());
            snapshot.setMaterialized(materialized.get());
            snapshot.setCandidates(candidates.get());
            snapshot.setSpawned(spawned.get());
            snapshot.setDematerialized(dematerialized.get());
            snapshot.setNearQueries(nearQueries.get());
            snapshot.setNearQueryCandidates(nearQueryCandidates.get());
            snapshot.setRaycasts(raycasts.get());
            snapshot.setRaycastCacheHits(raycastCacheHits.get());
            snapshot.setCandidateRefreshes(candidateRefreshes.get());
            snapshot.setCandidateCacheUses(candidateCacheUses.get());
            snapshot.setVisibilityChecks(visibilityChecks.get());
            snapshot.setVisibilityCheckSkips(visibilityCheckSkips.get());
            snapshot.setTickNanos(nanos);
            return snapshot;
        }
    }
}
