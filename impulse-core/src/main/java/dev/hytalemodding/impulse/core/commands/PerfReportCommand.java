package dev.hytalemodding.impulse.core.commands;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractWorldCommand;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.api.PhysicsBody;
import dev.hytalemodding.impulse.api.PhysicsSpace;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.diagnostics.PhysicsEntityDiagnostics;
import dev.hytalemodding.impulse.core.resources.PhysicsRuntimeProfilingResource;
import dev.hytalemodding.impulse.core.resources.PhysicsRuntimeProfilingResource.StepSnapshot;
import dev.hytalemodding.impulse.core.resources.PhysicsRuntimeProfilingResource.SyncSnapshot;
import dev.hytalemodding.impulse.core.resources.PhysicsWorldResource;
import dev.hytalemodding.impulse.core.resources.WorldCollisionProfilingResource;
import dev.hytalemodding.impulse.core.resources.WorldCollisionProfilingResource.Snapshot;
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
        WorldCollisionProfilingResource profiling = store.getResource(
            WorldCollisionProfilingResource.getResourceType());
        Snapshot cumulative = profiling.getCumulativeSnapshot();
        Snapshot latest = profiling.getLatestTickSnapshot();
        Snapshot worst = profiling.getWorstTickSnapshot();
        PhysicsEntityDiagnostics.Snapshot entityDiagnostics = PhysicsEntityDiagnostics.collect(store);
        PhysicsWorldResource physicsWorld = store.getResource(PhysicsWorldResource.getResourceType());
        RuntimeFootprint runtimeFootprint = RuntimeFootprint.collect(physicsWorld);

        ctx.sender().sendMessage(Message.raw("Impulse runtime profiling: "
            + ((runtimeProfiling.isEnabled() || profiling.isEnabled()) ? "enabled" : "disabled")));
        ctx.sender().sendMessage(Message.raw("Impulse runtime physics: "
            + runtimeFootprint.summary()));
        ctx.sender().sendMessage(Message.raw("Hytale entity diagnostics: "
            + entityDiagnostics.hytaleSummary()));
        ctx.sender().sendMessage(Message.raw("Impulse entity diagnostics: "
            + entityDiagnostics.impulseSummary()));

        if (cumulativeStep.getTickSamples() > 0 || cumulativeSync.getTickSamples() > 0) {
            ctx.sender().sendMessage(Message.raw("Physics step avg ms/tick="
                + formatAverageMillis(cumulativeStep.getTickNanos(), cumulativeStep.getTickSamples())
                + " spaces=" + formatAverage(cumulativeStep.getSpaces(), cumulativeStep.getTickSamples())
                + " substeps=" + formatAverage(cumulativeStep.getSubsteps(), cumulativeStep.getTickSamples())));
            ctx.sender().sendMessage(Message.raw("Physics step latest/worst ms="
                + formatMillis(latestStep.getTickNanos())
                + "/" + formatMillis(worstStep.getTickNanos())
                + " latest spaces/substeps=" + latestStep.getSpaces()
                + "/" + latestStep.getSubsteps()));
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
        } else {
            ctx.sender().sendMessage(Message.raw("No profiled physics step/sync ticks recorded yet."
                + (runtimeProfiling.isEnabled()
                ? ""
                : " Run /impulse perf toggle, wait a few seconds, then run /impulse perf report.")));
        }

        if (cumulative.getTickSamples() <= 0) {
            ctx.sender().sendMessage(Message.raw("No profiled world collision ticks recorded yet."
                + (profiling.isEnabled()
                ? ""
                : " Run /impulse perf toggle, wait a few seconds, then run /impulse perf report.")));
            return;
        }

        ctx.sender().sendMessage(Message.raw("World collision profiling: "
            + (profiling.isEnabled() ? "enabled" : "disabled")));

        ctx.sender().sendMessage(Message.raw("Since reset: ticks=" + cumulative.getTickSamples()
            + " playerTargets=" + cumulative.getPlayerStreamingTargets()
            + " bodyCandidates/targets=" + cumulative.getBodyStreamingCandidates()
            + "/" + cumulative.getBodyStreamingTargets()
            + " spaces=" + cumulative.getStreamingSpaces()
            + " ensureCalls=" + cumulative.getEnsureCalls()
            + " sections req/hit/build/rebuild/miss=" + cumulative.getSectionRequests()
            + "/" + cumulative.getSectionCacheHits()
            + "/" + cumulative.getSectionsBuilt()
            + "/" + cumulative.getSectionsRebuilt()
            + "/" + cumulative.getMissingChunks()
            + " targetDedupes=" + cumulative.getBodyTargetDedupeSkips()
            + " sectionDupSkips=" + cumulative.getDuplicateSkips()));
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
            + " bodyCandidates/targets=" + latest.getBodyStreamingCandidates()
            + "/" + latest.getBodyStreamingTargets()
            + " spaces=" + latest.getStreamingSpaces()
            + " ensure=" + latest.getEnsureCalls()
            + " req/hit/build/rebuild/miss=" + latest.getSectionRequests()
            + "/" + latest.getSectionCacheHits()
            + "/" + latest.getSectionsBuilt()
            + "/" + latest.getSectionsRebuilt()
            + "/" + latest.getMissingChunks()
            + " targetDedupes=" + latest.getBodyTargetDedupeSkips()
            + " sectionDupSkips=" + latest.getDuplicateSkips()));
        ctx.sender().sendMessage(Message.raw("Worst tick: ms=" + formatMillis(worst.getTickNanos())
            + " playerTargets=" + worst.getPlayerStreamingTargets()
            + " bodyCandidates/targets=" + worst.getBodyStreamingCandidates()
            + "/" + worst.getBodyStreamingTargets()
            + " spaces=" + worst.getStreamingSpaces()
            + " ensure=" + worst.getEnsureCalls()
            + " req/hit/build/rebuild/miss=" + worst.getSectionRequests()
            + "/" + worst.getSectionCacheHits()
            + "/" + worst.getSectionsBuilt()
            + "/" + worst.getSectionsRebuilt()
            + "/" + worst.getMissingChunks()
            + " targetDedupes=" + worst.getBodyTargetDedupeSkips()
            + " sectionDupSkips=" + worst.getDuplicateSkips()));
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
    private static String formatAverage(int total, int samples) {
        if (samples <= 0) {
            return "0.0";
        }
        return String.format(Locale.ROOT, "%.1f", (double) total / samples);
    }

    private record RuntimeFootprint(int spaces,
        int backendBodies,
        int backendJoints,
        int detachedBodies,
        int detachedVisualProxies,
        String defaultSpace) {

        @Nonnull
        private static RuntimeFootprint collect(@Nonnull PhysicsWorldResource resource) {
            int spaces = 0;
            int backendBodies = 0;
            int backendJoints = 0;
            for (PhysicsSpace space : resource.getSpaces()) {
                spaces++;
                backendBodies += space.bodyCount();
                backendJoints += space.getJoints().size();
            }

            int detachedBodies = 0;
            int detachedVisualProxies = 0;
            for (PhysicsBody body : resource.getDetachedBodies()) {
                detachedBodies++;
                if (resource.getDetachedVisualProxy(body) != null) {
                    detachedVisualProxies++;
                }
            }

            SpaceId defaultSpaceId = resource.getDefaultSpaceId();
            String defaultSpace = defaultSpaceId == null ? "none" : String.valueOf(defaultSpaceId.value());
            return new RuntimeFootprint(spaces,
                backendBodies,
                backendJoints,
                detachedBodies,
                detachedVisualProxies,
                defaultSpace);
        }

        @Nonnull
        private String summary() {
            return "spaces=" + spaces
                + " defaultSpace=" + defaultSpace
                + " backendBodies=" + backendBodies
                + " backendJoints=" + backendJoints
                + " detachedBodies=" + detachedBodies
                + " detachedVisualProxies=" + detachedVisualProxies;
        }
    }
}
