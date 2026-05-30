package dev.hytalemodding.impulse.core.internal.resources.profiling;

import com.hypixel.hytale.component.Resource;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.api.PhysicsStepPhaseStats;
import dev.hytalemodding.impulse.core.ImpulsePlugin;
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

    public void recordStepScheduling(float inputDtSeconds,
        float submittedDtSeconds,
        float backlogDtSeconds,
        float droppedBacklogDtSeconds,
        boolean dtCapHit) {
        StepSnapshot snapshot = new StepSnapshot();
        snapshot.recordSchedulerSample(secondsToNanos(inputDtSeconds),
            secondsToNanos(submittedDtSeconds),
            secondsToNanos(backlogDtSeconds),
            secondsToNanos(droppedBacklogDtSeconds),
            dtCapHit);
        latestStep.recordSchedulerSample(secondsToNanos(inputDtSeconds),
            secondsToNanos(submittedDtSeconds),
            secondsToNanos(backlogDtSeconds),
            secondsToNanos(droppedBacklogDtSeconds),
            dtCapHit);
        cumulativeStep.add(snapshot);
        if (snapshot.getMaxSchedulerBacklogDtNanos()
            >= worstStep.getMaxSchedulerBacklogDtNanos()) {
            worstStep.setMaxSchedulerBacklogDtNanos(snapshot.getMaxSchedulerBacklogDtNanos());
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

    private static long secondsToNanos(float seconds) {
        if (!Float.isFinite(seconds) || seconds <= 0.0f) {
            return 0L;
        }
        double nanos = seconds * 1_000_000_000.0;
        if (nanos >= Long.MAX_VALUE) {
            return Long.MAX_VALUE;
        }
        return Math.max(0L, Math.round(nanos));
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
        private int schedulerSamples;
        private long schedulerInputDtNanos;
        private long schedulerSubmittedDtNanos;
        private long schedulerBacklogDtNanos;
        @Setter
        private long maxSchedulerBacklogDtNanos;
        private long droppedBacklogDtNanos;
        private int droppedBacklogTicks;
        private int dtCapHits;
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
            schedulerSamples = other.schedulerSamples;
            schedulerInputDtNanos = other.schedulerInputDtNanos;
            schedulerSubmittedDtNanos = other.schedulerSubmittedDtNanos;
            schedulerBacklogDtNanos = other.schedulerBacklogDtNanos;
            maxSchedulerBacklogDtNanos = other.maxSchedulerBacklogDtNanos;
            droppedBacklogDtNanos = other.droppedBacklogDtNanos;
            droppedBacklogTicks = other.droppedBacklogTicks;
            dtCapHits = other.dtCapHits;
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
            schedulerSamples += other.schedulerSamples;
            schedulerInputDtNanos += other.schedulerInputDtNanos;
            schedulerSubmittedDtNanos += other.schedulerSubmittedDtNanos;
            schedulerBacklogDtNanos += other.schedulerBacklogDtNanos;
            maxSchedulerBacklogDtNanos = Math.max(maxSchedulerBacklogDtNanos,
                other.maxSchedulerBacklogDtNanos);
            droppedBacklogDtNanos += other.droppedBacklogDtNanos;
            droppedBacklogTicks += other.droppedBacklogTicks;
            dtCapHits += other.dtCapHits;
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
            schedulerSamples = 0;
            schedulerInputDtNanos = 0L;
            schedulerSubmittedDtNanos = 0L;
            schedulerBacklogDtNanos = 0L;
            maxSchedulerBacklogDtNanos = 0L;
            droppedBacklogDtNanos = 0L;
            droppedBacklogTicks = 0;
            dtCapHits = 0;
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

        public void recordSchedulerSample(long inputDtNanos,
            long submittedDtNanos,
            long backlogDtNanos,
            long droppedBacklogDtNanos,
            boolean dtCapHit) {
            schedulerSamples = 1;
            schedulerInputDtNanos = inputDtNanos;
            schedulerSubmittedDtNanos = submittedDtNanos;
            schedulerBacklogDtNanos = backlogDtNanos;
            maxSchedulerBacklogDtNanos = backlogDtNanos;
            this.droppedBacklogDtNanos = droppedBacklogDtNanos;
            droppedBacklogTicks = droppedBacklogDtNanos > 0L ? 1 : 0;
            dtCapHits = dtCapHit ? 1 : 0;
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

        private int bodiesInspected;
        private int bodiesSynced;
        private int transitionSyncs;
        private int keepaliveSyncs;
        private int skippedSleeping;
        private int skippedThreshold;
        private int skippedVisualDeadzone;
        private int skippedVisualRange;
        private int skippedStatic;
        private int skippedMissingSpace;

        public void incrementBodiesInspected() {
            bodiesInspected++;
        }

        public void incrementBodiesSynced() {
            bodiesSynced++;
        }

        public void incrementTransitionSyncs() {
            transitionSyncs++;
        }

        public void incrementKeepaliveSyncs() {
            keepaliveSyncs++;
        }

        public void incrementSkippedSleeping() {
            skippedSleeping++;
        }

        public void incrementSkippedThreshold() {
            skippedThreshold++;
        }

        public void incrementSkippedVisualDeadzone() {
            skippedVisualDeadzone++;
        }

        public void incrementSkippedVisualRange() {
            skippedVisualRange++;
        }

        public void incrementSkippedStatic() {
            skippedStatic++;
        }

        public void incrementSkippedMissingSpace() {
            skippedMissingSpace++;
        }

        @Nonnull
        private SyncSnapshot snapshot(long nanos) {
            SyncSnapshot snapshot = new SyncSnapshot();
            snapshot.setBodiesInspected(bodiesInspected);
            snapshot.setBodiesSynced(bodiesSynced);
            snapshot.setTransitionSyncs(transitionSyncs);
            snapshot.setKeepaliveSyncs(keepaliveSyncs);
            snapshot.setSkippedSleeping(skippedSleeping);
            snapshot.setSkippedThreshold(skippedThreshold);
            snapshot.setSkippedVisualDeadzone(skippedVisualDeadzone);
            snapshot.setSkippedVisualRange(skippedVisualRange);
            snapshot.setSkippedStatic(skippedStatic);
            snapshot.setSkippedMissingSpace(skippedMissingSpace);
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

        private int interests;
        private int materialized;
        private int candidates;
        private int spawned;
        private int dematerialized;
        private int nearQueries;
        private int nearQueryCandidates;
        private int raycasts;
        private int raycastCacheHits;
        private int candidateRefreshes;
        private int candidateCacheUses;
        private int visibilityChecks;
        private int visibilityCheckSkips;

        public void setInterests(int count) {
            interests = count;
        }

        public void setMaterialized(int count) {
            materialized = count;
        }

        public void setCandidates(int count) {
            candidates = count;
        }

        public void incrementSpawned() {
            spawned++;
        }

        public void incrementDematerialized() {
            dematerialized++;
        }

        public void incrementNearQueries() {
            nearQueries++;
        }

        public void addNearQueryCandidates(int count) {
            nearQueryCandidates += count;
        }

        public void incrementRaycasts() {
            raycasts++;
        }

        public void incrementRaycastCacheHits() {
            raycastCacheHits++;
        }

        public void incrementCandidateRefreshes() {
            candidateRefreshes++;
        }

        public void incrementCandidateCacheUses() {
            candidateCacheUses++;
        }

        public void incrementVisibilityChecks() {
            visibilityChecks++;
        }

        public void incrementVisibilityCheckSkips() {
            visibilityCheckSkips++;
        }

        public void addVisibilityCheckSkips(int count) {
            visibilityCheckSkips += count;
        }

        @Nonnull
        private VisualSnapshot snapshot(long nanos) {
            VisualSnapshot snapshot = new VisualSnapshot();
            snapshot.setInterests(interests);
            snapshot.setMaterialized(materialized);
            snapshot.setCandidates(candidates);
            snapshot.setSpawned(spawned);
            snapshot.setDematerialized(dematerialized);
            snapshot.setNearQueries(nearQueries);
            snapshot.setNearQueryCandidates(nearQueryCandidates);
            snapshot.setRaycasts(raycasts);
            snapshot.setRaycastCacheHits(raycastCacheHits);
            snapshot.setCandidateRefreshes(candidateRefreshes);
            snapshot.setCandidateCacheUses(candidateCacheUses);
            snapshot.setVisibilityChecks(visibilityChecks);
            snapshot.setVisibilityCheckSkips(visibilityCheckSkips);
            snapshot.setTickNanos(nanos);
            return snapshot;
        }
    }
}
