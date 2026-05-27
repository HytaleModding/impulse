package dev.hytalemodding.impulse.core.internal.commands.perf;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractWorldCommand;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.api.PhysicsSpace;
import dev.hytalemodding.impulse.api.PhysicsRuntimeStats;
import dev.hytalemodding.impulse.core.internal.diagnostics.PhysicsEntityDiagnostics;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsWorldRuntimeResource;
import dev.hytalemodding.impulse.core.internal.resources.profiling.PhysicsRuntimeProfilingResource;
import dev.hytalemodding.impulse.core.internal.resources.profiling.PhysicsRuntimeProfilingResource.StepSnapshot;
import dev.hytalemodding.impulse.core.internal.resources.profiling.PhysicsRuntimeProfilingResource.SyncSnapshot;
import dev.hytalemodding.impulse.core.internal.resources.profiling.PhysicsRuntimeProfilingResource.VisualSnapshot;
import dev.hytalemodding.impulse.core.internal.worker.PhysicsWorkerAccess;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyKind;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyRegistrationView;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsWorldResource;
import dev.hytalemodding.impulse.core.internal.resources.profiling.WorldCollisionProfilingResource;
import dev.hytalemodding.impulse.core.internal.resources.profiling.WorldCollisionProfilingResource.Snapshot;
import java.util.Locale;
import javax.annotation.Nonnull;

public class PerfReportCommand extends AbstractWorldCommand {

    public PerfReportCommand() {
        super("report", "Report Impulse world collision profiling metrics");
    }

    @Override
    protected void execute(@Nonnull CommandContext ctx,
        @Nonnull World world,
        @Nonnull Store<EntityStore> store) {
        PhysicsRuntimeProfilingResource runtimeProfiling = store.getResource(
            PhysicsRuntimeProfilingResource.getResourceType());
        StepSnapshot cumulativeStep = runtimeProfiling.getCumulativeStep();
        StepSnapshot latestStep = runtimeProfiling.getLatestStep();
        StepSnapshot worstStep = runtimeProfiling.getWorstStep();
        SyncSnapshot cumulativeSync = runtimeProfiling.getCumulativeSync();
        SyncSnapshot latestSync = runtimeProfiling.getLatestSync();
        SyncSnapshot worstSync = runtimeProfiling.getWorstSync();
        VisualSnapshot cumulativeVisual = runtimeProfiling.getCumulativeVisual();
        VisualSnapshot latestVisual = runtimeProfiling.getLatestVisual();
        VisualSnapshot worstVisual = runtimeProfiling.getWorstVisual();
        WorldCollisionProfilingResource profiling = store.getResource(
            WorldCollisionProfilingResource.getResourceType());
        Snapshot cumulative = profiling.getCumulativeSnapshot();
        Snapshot latest = profiling.getLatestTickSnapshot();
        Snapshot worst = profiling.getWorstTickSnapshot();
        PhysicsEntityDiagnostics.Snapshot entityDiagnostics = PhysicsEntityDiagnostics.collect(store);
        PhysicsWorldResource physicsWorld = store.getResource(PhysicsWorldResource.getResourceType());
        RuntimeFootprint runtimeFootprint = RuntimeFootprint.collect(store, physicsWorld);

        ctx.sender().sendMessage(Message.raw("Impulse runtime profiling: "
            + ((runtimeProfiling.isEnabled() || profiling.isEnabled()) ? "enabled" : "disabled")));
        ctx.sender().sendMessage(Message.raw("Impulse runtime physics: "
            + runtimeFootprint.summary()));
        if (runtimeFootprint.hasRuntimeStats()) {
            ctx.sender().sendMessage(Message.raw("Physics backend runtime stats: "
                + runtimeFootprint.runtimeStatsSummary()));
        }
        ctx.sender().sendMessage(Message.raw("Hytale entity diagnostics: "
            + entityDiagnostics.hytaleSummary()));
        ctx.sender().sendMessage(Message.raw("Impulse entity diagnostics: "
            + entityDiagnostics.impulseSummary()));

        if (cumulativeStep.getTickSamples() > 0
            || cumulativeStep.getSchedulerSamples() > 0
            || cumulativeSync.getTickSamples() > 0
            || cumulativeVisual.getTickSamples() > 0) {
            ctx.sender().sendMessage(Message.raw("Physics step avg ms/tick="
                + formatAverageMillis(cumulativeStep.getTickNanos(), cumulativeStep.getTickSamples())
                + " spaces=" + formatAverage(cumulativeStep.getSpaces(), cumulativeStep.getTickSamples())
                + " substeps=" + formatAverage(cumulativeStep.getSubsteps(), cumulativeStep.getTickSamples())
                + " snapshots=" + formatAverage(cumulativeStep.getBodySnapshots(), cumulativeStep.getTickSamples())
                + " indexCells=" + formatAverage(cumulativeStep.getSpatialIndexCells(), cumulativeStep.getTickSamples())));
            ctx.sender().sendMessage(Message.raw("Physics snapshot avg ms/tick="
                + formatAverageMillis(cumulativeStep.getSnapshotNanos(), cumulativeStep.getTickSamples())));
            ctx.sender().sendMessage(Message.raw("Physics worker avg queued/run/latency ms="
                + formatAverageMillis(cumulativeStep.getWorkerQueuedNanos(), cumulativeStep.getTickSamples())
                + "/" + formatAverageMillis(cumulativeStep.getWorkerRunNanos(), cumulativeStep.getTickSamples())
                + "/" + formatAverageMillis(
                cumulativeStep.getWorkerQueuedNanos() + cumulativeStep.getWorkerRunNanos(),
                cumulativeStep.getTickSamples())
                + " workerTPS latest/avg=" + formatHertz(latestStep.getWorkerStepIntervalNanos())
                + "/" + formatAverageHertz(cumulativeStep.getWorkerStepIntervalNanos(),
                cumulativeStep.getWorkerStepRateSamples())
                + " maxGapMs=" + formatMillis(cumulativeStep.getMaxWorkerStepIntervalNanos())
                + " pendingSkips=" + cumulativeStep.getSkippedPendingSteps()
                + " pendingAge avg/max ms="
                + formatAverageMillis(cumulativeStep.getPendingStepAgeNanos(),
                cumulativeStep.getSkippedPendingSteps())
                + "/" + formatMillis(cumulativeStep.getMaxPendingStepAgeNanos())));
            if (cumulativeStep.getSchedulerSamples() > 0) {
                ctx.sender().sendMessage(Message.raw("Physics scheduler dt avg input/submitted/backlog/dropped s="
                    + formatAverageSeconds(cumulativeStep.getSchedulerInputDtNanos(),
                    cumulativeStep.getSchedulerSamples())
                    + "/" + formatAverageSeconds(cumulativeStep.getSchedulerSubmittedDtNanos(),
                    cumulativeStep.getSchedulerSamples())
                    + "/" + formatAverageSeconds(cumulativeStep.getSchedulerBacklogDtNanos(),
                    cumulativeStep.getSchedulerSamples())
                    + "/" + formatAverageSeconds(cumulativeStep.getDroppedBacklogDtNanos(),
                    cumulativeStep.getSchedulerSamples())
                    + " backlog latest/max s="
                    + formatSeconds(latestStep.getSchedulerBacklogDtNanos())
                    + "/" + formatSeconds(cumulativeStep.getMaxSchedulerBacklogDtNanos())
                    + " droppedTotal s=" + formatSeconds(cumulativeStep.getDroppedBacklogDtNanos())
                    + " capHits/droppedTicks=" + cumulativeStep.getDtCapHits()
                    + "/" + cumulativeStep.getDroppedBacklogTicks()));
            }
            ctx.sender().sendMessage(Message.raw("Physics step latest/worst ms="
                + formatMillis(latestStep.getTickNanos())
                + "/" + formatMillis(worstStep.getTickNanos())
                + " latest spaces/substeps=" + latestStep.getSpaces()
                + "/" + latestStep.getSubsteps()
                + " snapshots/cells=" + latestStep.getBodySnapshots()
                + "/" + latestStep.getSpatialIndexCells()
                + " snapshot latest/worst ms=" + formatMillis(latestStep.getSnapshotNanos())
                + "/" + formatMillis(worstStep.getSnapshotNanos())
                + " pendingAge latest/max ms=" + formatMillis(latestStep.getPendingStepAgeNanos())
                + "/" + formatMillis(worstStep.getMaxPendingStepAgeNanos())));
            if (cumulativeStep.getNativePhaseSamples() > 0) {
                ctx.sender().sendMessage(Message.raw("Physics native phases avg ms/tick "
                    + "step/broad/narrow/solver/ccd/snapshot="
                    + formatAverageMillis(cumulativeStep.getNativeStepNanos(),
                    cumulativeStep.getNativePhaseSamples())
                    + "/" + formatAverageMillis(cumulativeStep.getNativeBroadPhaseNanos(),
                    cumulativeStep.getNativePhaseSamples())
                    + "/" + formatAverageMillis(cumulativeStep.getNativeNarrowPhaseNanos(),
                    cumulativeStep.getNativePhaseSamples())
                    + "/" + formatAverageMillis(cumulativeStep.getNativeSolverNanos(),
                    cumulativeStep.getNativePhaseSamples())
                    + "/" + formatAverageMillis(cumulativeStep.getNativeCcdNanos(),
                    cumulativeStep.getNativePhaseSamples())
                    + "/" + formatAverageMillis(cumulativeStep.getNativeSnapshotNanos(),
                    cumulativeStep.getNativePhaseSamples())
                    + " samples=" + cumulativeStep.getNativePhaseSamples()));
                ctx.sender().sendMessage(Message.raw("Physics native phases latest ms "
                    + "step/broad/narrow/solver/ccd/snapshot="
                    + formatMillis(latestStep.getNativeStepNanos())
                    + "/" + formatMillis(latestStep.getNativeBroadPhaseNanos())
                    + "/" + formatMillis(latestStep.getNativeNarrowPhaseNanos())
                    + "/" + formatMillis(latestStep.getNativeSolverNanos())
                    + "/" + formatMillis(latestStep.getNativeCcdNanos())
                    + "/" + formatMillis(latestStep.getNativeSnapshotNanos())));
            }
            ctx.sender().sendMessage(Message.raw("Physics sync avg ms/tick="
                + formatAverageMillis(cumulativeSync.getTickNanos(), cumulativeSync.getTickSamples())
                + " inspected=" + formatAverage(cumulativeSync.getBodiesInspected(), cumulativeSync.getTickSamples())
                + " synced=" + formatAverage(cumulativeSync.getBodiesSynced(), cumulativeSync.getTickSamples())
                + " skipSleeping=" + formatAverage(cumulativeSync.getSkippedSleeping(), cumulativeSync.getTickSamples())
                + " skipThreshold=" + formatAverage(cumulativeSync.getSkippedThreshold(), cumulativeSync.getTickSamples())
                + " skipVisualDeadzone=" + formatAverage(cumulativeSync.getSkippedVisualDeadzone(), cumulativeSync.getTickSamples())
                + " skipVisualRange=" + formatAverage(cumulativeSync.getSkippedVisualRange(), cumulativeSync.getTickSamples())));
            ctx.sender().sendMessage(Message.raw("Physics sync latest ms=" + formatMillis(latestSync.getTickNanos())
                + " inspected/synced=" + latestSync.getBodiesInspected()
                + "/" + latestSync.getBodiesSynced()
                + " transition/keepalive=" + latestSync.getTransitionSyncs()
                + "/" + latestSync.getKeepaliveSyncs()
                + " skipSleeping/threshold/visualDeadzone/visualRange/static/missing=" + latestSync.getSkippedSleeping()
                + "/" + latestSync.getSkippedThreshold()
                + "/" + latestSync.getSkippedVisualDeadzone()
                + "/" + latestSync.getSkippedVisualRange()
                + "/" + latestSync.getSkippedStatic()
                + "/" + latestSync.getSkippedMissingSpace()));
            ctx.sender().sendMessage(Message.raw("Physics sync worst ms=" + formatMillis(worstSync.getTickNanos())
                + " inspected/synced=" + worstSync.getBodiesInspected()
                + "/" + worstSync.getBodiesSynced()));
            if (cumulativeVisual.getTickSamples() > 0) {
                ctx.sender().sendMessage(Message.raw("Detached visual materialization avg ms/tick="
                    + formatAverageMillis(cumulativeVisual.getTickNanos(),
                    cumulativeVisual.getTickSamples())
                    + " interests=" + formatAverage(cumulativeVisual.getInterests(),
                    cumulativeVisual.getTickSamples())
                    + " materialized=" + formatAverage(cumulativeVisual.getMaterialized(),
                    cumulativeVisual.getTickSamples())
                    + " candidates=" + formatAverage(cumulativeVisual.getCandidates(),
                    cumulativeVisual.getTickSamples())
                    + " spawned/dematerialized=" + cumulativeVisual.getSpawned()
                    + "/" + cumulativeVisual.getDematerialized()));
                ctx.sender().sendMessage(Message.raw("Detached visual queries since reset: nearQueries="
                    + cumulativeVisual.getNearQueries()
                    + " nearCandidates=" + cumulativeVisual.getNearQueryCandidates()
                    + " raycasts/cacheHits=" + cumulativeVisual.getRaycasts()
                    + "/" + cumulativeVisual.getRaycastCacheHits()
                    + " candidateRefresh/cacheUse=" + cumulativeVisual.getCandidateRefreshes()
                    + "/" + cumulativeVisual.getCandidateCacheUses()
                    + " visibilityChecks/skips=" + cumulativeVisual.getVisibilityChecks()
                    + "/" + cumulativeVisual.getVisibilityCheckSkips()));
                ctx.sender().sendMessage(Message.raw("Detached visual latest/worst ms="
                    + formatMillis(latestVisual.getTickNanos())
                    + "/" + formatMillis(worstVisual.getTickNanos())
                    + " latest materialized/candidates/spawned/dematerialized="
                    + latestVisual.getMaterialized()
                    + "/" + latestVisual.getCandidates()
                    + "/" + latestVisual.getSpawned()
                    + "/" + latestVisual.getDematerialized()));
            }
        } else {
            ctx.sender().sendMessage(Message.raw("No profiled physics step/sync/visual ticks recorded yet."
                + (runtimeProfiling.isEnabled()
                ? ""
                : " Run /impulse perf enable, wait a few seconds, then run /impulse perf report.")));
        }

        if (cumulative.getTickSamples() <= 0) {
            ctx.sender().sendMessage(Message.raw("No profiled world collision ticks recorded yet."
                + (profiling.isEnabled()
                ? ""
                : " Run /impulse perf enable, wait a few seconds, then run /impulse perf report.")));
            return;
        }

        ctx.sender().sendMessage(Message.raw("World collision profiling: "
            + (profiling.isEnabled() ? "enabled" : "disabled")));

        ctx.sender().sendMessage(Message.raw("Since reset: ticks=" + cumulative.getTickSamples()
            + " playerTargets=" + cumulative.getPlayerStreamingTargets()
            + " bodyCandidates/index/targets=" + cumulative.getBodyStreamingCandidates()
            + "/" + cumulative.getBodySpatialIndexCandidates()
            + "/" + cumulative.getBodyStreamingTargets()
            + " spaces=" + cumulative.getStreamingSpaces()
            + " terrainApply queued/skipped=" + cumulative.getTerrainApplyQueued()
            + "/" + cumulative.getTerrainApplySkippedPending()
            + " sectionTargets player/body=" + cumulative.getPlayerSectionTargets()
            + "/" + cumulative.getBodySectionTargets()
            + " ensureCalls=" + cumulative.getEnsureCalls()
            + " sections req/hit/build/rebuild/miss=" + cumulative.getSectionRequests()
            + "/" + cumulative.getSectionCacheHits()
            + "/" + cumulative.getSectionsBuilt()
            + "/" + cumulative.getSectionsRebuilt()
            + "/" + cumulative.getMissingChunks()
            + " targetDedupes=" + cumulative.getBodyTargetDedupeSkips()
            + " sectionDupSkips=" + cumulative.getDuplicateSkips()));
        ctx.sender().sendMessage(Message.raw("Target cache since reset: hit/first/bounds/activeRefresh/sleepRefresh="
            + cumulative.getBodyTargetCacheHits()
            + "/" + cumulative.getBodyTargetFirstSeen()
            + "/" + cumulative.getBodyTargetBoundsChanged()
            + "/" + cumulative.getBodyTargetActiveRefreshes()
            + "/" + cumulative.getBodyTargetSleepingRefreshes()
            + " stableSkips active/sleeping="
            + cumulative.getBodyTargetActiveStableSkips()
            + "/" + cumulative.getBodyTargetSleepingStableSkips()
            + " pruned=" + cumulative.getBodyTargetsPruned()));
        ctx.sender().sendMessage(Message.raw("Missing sections: blockChunk/blockSection/unknown/unique="
            + cumulative.getMissingBlockChunks()
            + "/" + cumulative.getMissingBlockSections()
            + "/" + cumulative.getMissingReasonUnknown()
            + "/" + cumulative.getUniqueMissingSections()
            + " backoff total/blockChunk/blockSection="
            + cumulative.getMissingBackoffSkips()
            + "/" + cumulative.getMissingBlockChunkBackoffSkips()
            + "/" + cumulative.getMissingBlockSectionBackoffSkips()
            + " retained inside/outside/unconfigured="
            + cumulative.getMissingInsideRetainedEnvelope()
            + "/" + cumulative.getMissingOutsideRetainedEnvelope()
            + "/" + cumulative.getMissingUnconfiguredRetainedEnvelope()));
        if (!cumulative.getMissingSectionSamples().isEmpty()) {
            ctx.sender().sendMessage(Message.raw("Missing section samples: "
                + formatMissingSectionSamples(cumulative)));
        }
        ctx.sender().sendMessage(Message.raw("Since reset bodies+blocks: added="
            + cumulative.getColliderBodiesAdded()
            + " voxel=" + cumulative.getVoxelBodies()
            + " sections removed unloaded/ttl=" + cumulative.getSectionsRemovedFromUnloadedPrune()
            + "/" + cumulative.getSectionsRemovedFromTtlPrune()
            + " removed rebuild/unloaded/ttl=" + cumulative.getBodiesRemovedFromRebuild()
            + "/" + cumulative.getBodiesRemovedFromUnloadedPrune()
            + "/" + cumulative.getBodiesRemovedFromTtlPrune()
            + " scanned/solid/culled/detail/full=" + cumulative.getScannedBlocks()
            + "/" + cumulative.getSolidBlocks()
            + "/" + cumulative.getCulledInteriorBlocks()
            + "/" + cumulative.getDetailBoxes()
            + "/" + cumulative.getFullCubeRuns()));
        ctx.sender().sendMessage(Message.raw("Avg timings ms/tick: tick="
            + formatAverageMillis(cumulative.getTickNanos(), cumulative.getTickSamples())
            + " ensureAround=" + formatAverageMillis(cumulative.getEnsureAroundNanos(), cumulative.getTickSamples())
            + " ensureSection=" + formatAverageMillis(cumulative.getEnsureSectionNanos(), cumulative.getTickSamples())
            + " pruneUnloaded=" + formatAverageMillis(cumulative.getPruneUnloadedNanos(), cumulative.getTickSamples())
            + " pruneUnused=" + formatAverageMillis(cumulative.getPruneUnusedNanos(), cumulative.getTickSamples())));
        ctx.sender().sendMessage(Message.raw("Latest tick: ms=" + formatMillis(latest.getTickNanos())
            + " playerTargets=" + latest.getPlayerStreamingTargets()
            + " bodyCandidates/index/targets=" + latest.getBodyStreamingCandidates()
            + "/" + latest.getBodySpatialIndexCandidates()
            + "/" + latest.getBodyStreamingTargets()
            + " spaces=" + latest.getStreamingSpaces()
            + " terrainApply queued/skipped=" + latest.getTerrainApplyQueued()
            + "/" + latest.getTerrainApplySkippedPending()
            + " sectionTargets player/body=" + latest.getPlayerSectionTargets()
            + "/" + latest.getBodySectionTargets()
            + " ensure=" + latest.getEnsureCalls()
            + " req/hit/build/rebuild/miss=" + latest.getSectionRequests()
            + "/" + latest.getSectionCacheHits()
            + "/" + latest.getSectionsBuilt()
            + "/" + latest.getSectionsRebuilt()
            + "/" + latest.getMissingChunks()
            + " targetDedupes=" + latest.getBodyTargetDedupeSkips()
            + " targetCache hit/activeSkip/sleepSkip="
            + latest.getBodyTargetCacheHits()
            + "/" + latest.getBodyTargetActiveStableSkips()
            + "/" + latest.getBodyTargetSleepingStableSkips()
            + " missingBackoff=" + latest.getMissingBackoffSkips()
            + " sectionDupSkips=" + latest.getDuplicateSkips()));
        ctx.sender().sendMessage(Message.raw("Worst tick: ms=" + formatMillis(worst.getTickNanos())
            + " playerTargets=" + worst.getPlayerStreamingTargets()
            + " bodyCandidates/index/targets=" + worst.getBodyStreamingCandidates()
            + "/" + worst.getBodySpatialIndexCandidates()
            + "/" + worst.getBodyStreamingTargets()
            + " spaces=" + worst.getStreamingSpaces()
            + " terrainApply queued/skipped=" + worst.getTerrainApplyQueued()
            + "/" + worst.getTerrainApplySkippedPending()
            + " sectionTargets player/body=" + worst.getPlayerSectionTargets()
            + "/" + worst.getBodySectionTargets()
            + " ensure=" + worst.getEnsureCalls()
            + " req/hit/build/rebuild/miss=" + worst.getSectionRequests()
            + "/" + worst.getSectionCacheHits()
            + "/" + worst.getSectionsBuilt()
            + "/" + worst.getSectionsRebuilt()
            + "/" + worst.getMissingChunks()
            + " targetDedupes=" + worst.getBodyTargetDedupeSkips()
            + " targetCache hit/activeSkip/sleepSkip="
            + worst.getBodyTargetCacheHits()
            + "/" + worst.getBodyTargetActiveStableSkips()
            + "/" + worst.getBodyTargetSleepingStableSkips()
            + " missingBackoff=" + worst.getMissingBackoffSkips()
            + " sectionDupSkips=" + worst.getDuplicateSkips()));
    }

    @Nonnull
    private static String formatMissingSectionSamples(@Nonnull Snapshot snapshot) {
        StringBuilder builder = new StringBuilder();
        int emitted = 0;
        for (WorldCollisionProfilingResource.MissingSectionSample sample
            : snapshot.getMissingSectionSamples()) {
            if (emitted > 0) {
                builder.append(" | ");
            }
            builder.append("section=")
                .append(sample.chunkX())
                .append("/")
                .append(sample.sectionY())
                .append("/")
                .append(sample.chunkZ())
                .append(" reason=")
                .append(sample.reason().name().toLowerCase(Locale.ROOT))
                .append(" retained=")
                .append(sample.retainedEnvelopeStatus().name().toLowerCase(Locale.ROOT))
                .append(" target=")
                .append(sample.target().targetType().name().toLowerCase(Locale.ROOT));
            if (sample.target().bodyId() != null) {
                builder.append(" body=").append(sample.target().bodyId());
            }
            if (sample.target().snapshotPosition() != null) {
                builder.append(" snapshot=(")
                    .append(sample.target().snapshotPosition().compact())
                    .append(")");
            }
            if (sample.target().livePosition() != null) {
                builder.append(" live=(")
                    .append(sample.target().livePosition().compact())
                    .append(")");
            }
            emitted++;
            if (emitted >= 3) {
                break;
            }
        }
        if (snapshot.getMissingSectionSamples().size() > emitted) {
            builder.append(" | +")
                .append(snapshot.getMissingSectionSamples().size() - emitted)
                .append(" more");
        }
        return builder.toString();
    }

    @Nonnull
    private static String formatMillis(long nanos) {
        return String.format(Locale.ROOT, "%.3f", nanos / 1_000_000.0);
    }

    @Nonnull
    private static String formatAverageMillis(long totalNanos, int samples) {
        if (samples <= 0) {
            return "0.000";
        }
        return formatMillis(totalNanos / samples);
    }

    @Nonnull
    private static String formatSeconds(long nanos) {
        return String.format(Locale.ROOT, "%.3f", nanos / 1_000_000_000.0);
    }

    @Nonnull
    private static String formatAverageSeconds(long totalNanos, int samples) {
        if (samples <= 0) {
            return "0.000";
        }
        return formatSeconds(totalNanos / samples);
    }

    @Nonnull
    private static String formatAverage(int total, int samples) {
        if (samples <= 0) {
            return "0.0";
        }
        return String.format(Locale.ROOT, "%.1f", (double) total / samples);
    }

    @Nonnull
    private static String formatHertz(long intervalNanos) {
        if (intervalNanos <= 0L) {
            return "0.0";
        }
        return String.format(Locale.ROOT, "%.1f", 1_000_000_000.0 / intervalNanos);
    }

    @Nonnull
    private static String formatAverageHertz(long totalIntervalNanos, int samples) {
        if (samples <= 0 || totalIntervalNanos <= 0L) {
            return "0.0";
        }
        return String.format(Locale.ROOT,
            "%.1f",
            (samples * 1_000_000_000.0) / totalIntervalNanos);
    }

    private record RuntimeFootprint(int spaces,
        int backendBodies,
        int backendJoints,
        int detachedBodies,
        int detachedVisualProxies,
        int runtimeStatsSpaces,
        int runtimeBodies,
        int runtimeColliders,
        int runtimeActiveBodies,
        int runtimeContactPairs,
        int runtimeContactManifolds,
        int runtimeContactPoints,
        int runtimeDynamicDynamicContactPairs,
        int runtimeTerrainContactPairs,
        int runtimeActiveIslands,
        int runtimeJoints) {

        @Nonnull
        private static RuntimeFootprint collect(@Nonnull Store<EntityStore> store,
            @Nonnull PhysicsWorldResource resource) {
            return PhysicsWorkerAccess.call(store, "collect perf runtime footprint",
                () -> collectOnWorker(resource));
        }

        @Nonnull
        private static RuntimeFootprint collectOnWorker(@Nonnull PhysicsWorldResource resource) {
            PhysicsWorldRuntimeResource runtime = PhysicsWorldRuntimeResource.require(resource);
            int spaces = 0;
            int backendBodies = 0;
            int backendJoints = 0;
            int runtimeStatsSpaces = 0;
            int runtimeBodies = 0;
            int runtimeColliders = 0;
            int runtimeActiveBodies = 0;
            int runtimeContactPairs = 0;
            int runtimeContactManifolds = 0;
            int runtimeContactPoints = 0;
            int runtimeDynamicDynamicContactPairs = 0;
            int runtimeTerrainContactPairs = 0;
            int runtimeActiveIslands = 0;
            int runtimeJoints = 0;
            for (PhysicsSpace space : runtime.getSpaces()) {
                spaces++;
                backendBodies += space.bodyCount();
                backendJoints += space.jointCount();

                PhysicsRuntimeStats runtimeStats = space.getRuntimeStats();
                if (runtimeStats.available()) {
                    runtimeStatsSpaces++;
                    runtimeBodies += runtimeStats.bodyCount();
                    runtimeColliders += runtimeStats.colliderCount();
                    runtimeActiveBodies += runtimeStats.activeBodyCount();
                    runtimeContactPairs += runtimeStats.contactPairCount();
                    runtimeContactManifolds += runtimeStats.contactManifoldCount();
                    runtimeContactPoints += runtimeStats.contactPointCount();
                    runtimeDynamicDynamicContactPairs += runtimeStats.dynamicDynamicContactPairCount();
                    runtimeTerrainContactPairs += runtimeStats.terrainContactPairCount();
                    runtimeActiveIslands += runtimeStats.activeIslandCount();
                    runtimeJoints += runtimeStats.jointCount();
                }
            }

            int detachedBodies = 0;
            int detachedVisualProxies = 0;
            for (PhysicsBodyRegistrationView registration : resource.getBodyRegistrationViews()) {
                if (registration.kind() != PhysicsBodyKind.BODY
                    || !resource.getBodyAttachments(registration.id()).isEmpty()) {
                    continue;
                }
                detachedBodies++;
                if (runtime.getGeneratedVisualProxy(registration.id()) != null) {
                    detachedVisualProxies++;
                }
            }

            return new RuntimeFootprint(spaces,
                backendBodies,
                backendJoints,
                detachedBodies,
                detachedVisualProxies,
                runtimeStatsSpaces,
                runtimeBodies,
                runtimeColliders,
                runtimeActiveBodies,
                runtimeContactPairs,
                runtimeContactManifolds,
                runtimeContactPoints,
                runtimeDynamicDynamicContactPairs,
                runtimeTerrainContactPairs,
                runtimeActiveIslands,
                runtimeJoints);
        }

        @Nonnull
        private String summary() {
            return "spaces=" + spaces
                + " backendBodies=" + backendBodies
                + " backendJoints=" + backendJoints
                + " detachedBodies=" + detachedBodies
                + " detachedVisualProxies=" + detachedVisualProxies;
        }

        private boolean hasRuntimeStats() {
            return runtimeStatsSpaces > 0;
        }

        @Nonnull
        private String runtimeStatsSummary() {
            return "spaces=" + runtimeStatsSpaces
                + " bodies=" + runtimeBodies
                + " colliders=" + runtimeColliders
                + " activeBodies=" + runtimeActiveBodies
                + " activeIslands=" + runtimeActiveIslands
                + " contactPairs=" + runtimeContactPairs
                + " contactManifolds=" + runtimeContactManifolds
                + " contactPoints=" + runtimeContactPoints
                + " dynamicDynamicPairs=" + runtimeDynamicDynamicContactPairs
                + " terrainPairs=" + runtimeTerrainContactPairs
                + " joints=" + runtimeJoints;
        }
    }
}
