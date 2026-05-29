package dev.hytalemodding.impulse.core.internal.crucible;

import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.ChunkFlag;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.api.PhysicsBody;
import dev.hytalemodding.impulse.api.PhysicsCollisionFilters;
import dev.hytalemodding.impulse.api.PhysicsSpace;
import dev.hytalemodding.impulse.api.ShapeType;
import dev.hytalemodding.impulse.core.ImpulsePlugin;
import dev.hytalemodding.impulse.core.internal.resources.profiling.PhysicsRuntimeProfilingResource;
import dev.hytalemodding.impulse.core.internal.resources.profiling.PhysicsRuntimeProfilingResource.StepSnapshot;
import dev.hytalemodding.impulse.core.internal.resources.profiling.PhysicsRuntimeProfilingResource.SyncSnapshot;
import dev.hytalemodding.impulse.core.internal.resources.profiling.WorldCollisionProfilingResource;
import dev.hytalemodding.impulse.core.internal.resources.profiling.WorldCollisionProfilingResource.Snapshot;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsWorldRuntimeResource;
import dev.hytalemodding.impulse.core.internal.voxel.WorldVoxelCollisionCache;
import dev.hytalemodding.impulse.core.internal.voxel.WorldVoxelCollisionCache.BuildStats;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyId;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyKind;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyPersistenceMode;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsBackendExtensionId;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsSpaceSettings;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsStepMode;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsStepSchedulingMode;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsWorldSettings;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsWorldResource;
import dev.hytalemodding.impulse.core.plugin.collision.WorldCollisionMode;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.logging.Level;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3f;

/**
 * Benchmark-oriented Crucible scenario for detached bodies using streamed world collision.
 */
@SuppressWarnings("SameParameterValue")
final class ImpulseDetachedStreamingBenchmarkCrucibleTests {

    private static final HytaleLogger LOGGER = HytaleLogger.get("Impulse");
    private static final String COUNTS_PROPERTY = "impulse.crucible.detachedStreaming.counts";
    private static final String WARMUP_TICKS_PROPERTY =
        "impulse.crucible.detachedStreaming.warmupTicks";
    private static final String SAMPLE_TICKS_PROPERTY =
        "impulse.crucible.detachedStreaming.sampleTicks";
    private static final String MIN_TPS_PROPERTY =
        "impulse.crucible.detachedStreaming.minTps";
    private static final String WARN_TPS_PROPERTY =
        "impulse.crucible.detachedStreaming.warnTps";
    private static final String STRICT_PLANE_GATE_PROPERTY =
        "impulse.crucible.detachedStreaming.strictPlaneGate";
    private static final PhysicsBackendExtensionId RAPIER_SOLVER_EXTENSION_ID =
        new PhysicsBackendExtensionId("impulse:rapier_solver");
    private static final String RAPIER_INTERNAL_PGS_ITERATIONS = "internalPgsIterations";
    private static final String RAPIER_MIN_ISLAND_SIZE = "minIslandSize";

    private static final int DEFAULT_STAGE_COUNT = 500;
    private static final int DEFAULT_WARMUP_TICKS = 60;
    private static final int DEFAULT_SAMPLE_TICKS = 200;
    private static final int MIN_STAGE_COUNT = 1;
    private static final int MAX_STAGE_COUNT = 10_000;
    private static final int MIN_WARMUP_TICKS = 1;
    private static final int MAX_WARMUP_TICKS = 1_200;
    private static final int MIN_SAMPLE_TICKS = 20;
    private static final int MAX_SAMPLE_TICKS = 7_200;
    private static final int BODY_STREAMING_RADIUS = 8;
    private static final int TICKING_CHUNK_REQUEST_FLAGS = 4;
    private static final int MAX_CHUNK_PREFLIGHT_ATTEMPTS = 100;
    private static final int CHUNK_PREFLIGHT_WAIT_TICKS = 2;
    private static final float TARGET_MAX_STEP_DT = 1.0f / 30.0f;
    private static final float GROUND_Y = 122.0f;
    private static final float BELOW_PLANE_TOLERANCE = 1.0f;
    private static final float BODY_WORLD_MIN_Y = -32.0f;
    private static final float BODY_VOID_Y = -128.0f;
    private static final double STREAMING_FALL_ENVELOPE_MIN_Y = 0.0;
    private static final double STREAMING_HORIZONTAL_DRIFT_HALO_BLOCKS = 16.0;
    private static final double DETACHED_SPACING = 1.5;
    private static final Vector3d ORIGIN = new Vector3d(0.0, 128.0, 0.0);
    private static final ComponentType<ChunkStore, WorldChunk> WORLD_CHUNK_TYPE =
        WorldChunk.getComponentType();

    private ImpulseDetachedStreamingBenchmarkCrucibleTests() {
    }

    static void register(CrucibleBridge bridge, ClassLoader loader)
        throws ReflectiveOperationException {

        bridge.registerSuite(loader, benchmarkSuite());
    }

    private static CrucibleSuite benchmarkSuite() {
        return new CrucibleSuite(
            "impulse:detached_streaming_benchmark",
            "Impulse Detached Streaming Benchmark",
            "Runs detached full-collision streamed-world benchmark stages with health gates",
            Set.of("benchmark", "streaming"),
            List.of(CrucibleTestCase.asyncResult("detached full-collision streaming stages",
                ImpulseDetachedStreamingBenchmarkCrucibleTests::detachedStreamingStages,
                "Detached streaming benchmark breached a health gate")));
    }

    private static CompletionStage<CrucibleTestCase.TestOutcome> detachedStreamingStages(
        CrucibleContext context) {
        try {
            StagePlan plan = StagePlan.fromSystemProperties();
            StageRunner runner = new StageRunner(context, plan);
            return runner.run();
        } catch (ReflectiveOperationException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private static final class StageRunner {

        private final CrucibleContext context;
        private final StagePlan plan;
        private final World world;
        private final Store<EntityStore> store;
        private final PhysicsWorldRuntimeResource physics;
        private final PhysicsRuntimeProfilingResource runtimeProfiling;
        private final WorldCollisionProfilingResource worldCollisionProfiling;
        private final PhysicsWorldSettings previousWorldSettings;
        private final List<WorldChunk> retainedChunks = new ArrayList<>();

        private StageRunner(@Nonnull CrucibleContext context, @Nonnull StagePlan plan)
            throws ReflectiveOperationException {
            this.context = context;
            this.plan = plan;
            this.world = context.world();
            this.store = world.getEntityStore().getStore();
            this.physics = PhysicsWorldRuntimeResource.require(store);
            this.runtimeProfiling = store.getResource(PhysicsRuntimeProfilingResource.getResourceType());
            this.worldCollisionProfiling = store.getResource(
                WorldCollisionProfilingResource.getResourceType());
            this.previousWorldSettings = physics.getWorldSettings();
        }

        private CompletionStage<CrucibleTestCase.TestOutcome> run() {
            return runStage(0, new ArrayList<>()).handle((outcome, failure) -> {
                clearStageState();
                restoreStepSettings();
                if (failure != null) {
                    if (failure instanceof CompletionException completionException) {
                        throw completionException;
                    }
                    throw new CompletionException(failure);
                }
                return outcome;
            });
        }

        private CompletionStage<CrucibleTestCase.TestOutcome> runStage(int stageIndex,
            List<StageReport> reports) {
            if (stageIndex >= plan.counts().size()) {
                return CompletableFuture.completedFuture(outcome(reports));
            }

            int count = plan.counts().get(stageIndex);
            return startStageWhenReady(count, 1)
                .thenCompose(started -> contextWait(plan.warmupTicks()).thenCompose(ignored -> {
                    runtimeProfiling.reset();
                    worldCollisionProfiling.reset();
                    runtimeProfiling.setEnabled(true);
                    worldCollisionProfiling.setEnabled(true);
                    long startedNanos = System.nanoTime();
                    return contextWait(plan.sampleTicks()).thenApply(
                        unused -> finishStage(count, started, startedNanos));
                }))
                .thenCompose(report -> {
                    reports.add(report);
                    LOGGER.at(Level.INFO).log("Crucible detached streaming stage: %s",
                        report.summary());
                    clearStageState();
                    if (report.health().status() == StageStatus.STOP) {
                        return CompletableFuture.completedFuture(outcome(reports));
                    }
                    return runStage(stageIndex + 1, reports);
                });
        }

        private CompletionStage<StartedStage> startStageWhenReady(int count, int attempt) {
            clearStageState();
            PhysicsWorldSettings worldSettings = physics.getWorldSettings();
            worldSettings.setStepMode(PhysicsStepMode.PROGRESSIVE_REFINEMENT);
            worldSettings.setStepSchedulingMode(PhysicsStepSchedulingMode.DROP_PENDING_DT);
            worldSettings.setSimulationSteps(1);
            worldSettings.setMaxStepDt(TARGET_MAX_STEP_DT);
            physics.setWorldSettings(worldSettings);

            BenchmarkChunks chunks = benchmarkChunks(count);
            if (!areChunksReady(chunks)) {
                int requested = requestChunks(chunks);
                if (attempt >= MAX_CHUNK_PREFLIGHT_ATTEMPTS) {
                    String message = "count=" + count
                        + " chunk preflight failed after " + attempt
                        + " attempts; refs=" + chunks.size()
                        + " requested=" + requested;
                    return CompletableFuture.completedFuture(
                        StartedStage.failed(count, message));
                }
                return contextWait(CHUNK_PREFLIGHT_WAIT_TICKS)
                    .thenCompose(ignored -> startStageWhenReady(count, attempt + 1));
            }

            int retained = retainChunks(chunks);
            configureMissingSectionDiagnostics(chunks);
            PhysicsSpaceSettings settings = PhysicsSpaceSettings.defaults();
            settings.getWorldCollisionSettings().setWorldCollisionMode(WorldCollisionMode.STREAMING);
            settings.getWorldCollisionSettings().setWorldCollisionBodyRadius(BODY_STREAMING_RADIUS);
            settings.getSolverSettings().setSolverIterations(4);
            settings.getSolverSettings().setStabilizationIterations(1);
            settings.getExtensionSettings().setInt(RAPIER_SOLVER_EXTENSION_ID,
                RAPIER_INTERNAL_PGS_ITERATIONS,
                1);
            settings.getExtensionSettings().setInt(RAPIER_SOLVER_EXTENSION_ID,
                RAPIER_MIN_ISLAND_SIZE,
                128);

            PhysicsSpace space = physics.createLiveSpace(CrucibleBackends.requireBackendId(),
                world.getName(),
                settings);
            PrewarmStats prewarm = physics.callOnPhysicsOwner(
                "prewarm detached streaming benchmark collision",
                () -> prewarmWorldCollision(space, count));
            physics.runOnPhysicsOwner("spawn detached streaming benchmark bodies",
                () -> spawnDetachedBodies(space, count));
            runtimeProfiling.reset();
            worldCollisionProfiling.reset();
            runtimeProfiling.setEnabled(true);
            worldCollisionProfiling.setEnabled(true);
            return CompletableFuture.completedFuture(
                StartedStage.started(space, chunks, retained, prewarm));
        }

        private StageReport finishStage(int count,
            StartedStage started,
            long startedNanos) {
            if (!started.started()) {
                return StageReport.failedPreflight(count, started.failureMessage());
            }
            PhysicsSpace space = started.space();
            if (space == null || !physics.hasSpace(space.getId())) {
                return StageReport.failedPreflight(count, "space disappeared during benchmark");
            }

            StepSnapshot step = runtimeProfiling.getCumulativeStep();
            SyncSnapshot sync = runtimeProfiling.getCumulativeSync();
            Snapshot worldCollision = worldCollisionProfiling.getCumulativeSnapshot();
            SpaceStats stats = SpaceStats.collect(physics, space);
            double elapsedSeconds = Math.max(0.001,
                (System.nanoTime() - startedNanos) / 1_000_000_000.0);
            double observedTickRate = step.getTickSamples() / elapsedSeconds;
            double avgStepMs = averageMillis(step.getTickNanos(), step.getTickSamples());
            double avgSnapshotMs = averageMillis(step.getSnapshotNanos(), step.getTickSamples());
            double avgSyncMs = averageMillis(sync.getTickNanos(), sync.getTickSamples());
            double avgWorldMs = averageMillis(worldCollision.getTickNanos(),
                worldCollision.getTickSamples());
            double totalMs = avgStepMs + avgSnapshotMs + avgSyncMs + avgWorldMs;
            StageHealth health = assessHealth(count,
                observedTickRate,
                stats,
                worldCollision.getMissingChunks());

            assert started.chunks() != null;
            assert started.prewarm() != null;
            return new StageReport(count,
                observedTickRate,
                avgStepMs,
                avgSnapshotMs,
                avgSyncMs,
                avgWorldMs,
                totalMs,
                started.retainedColumns(),
                started.chunks().size(),
                started.prewarm().sectionTargets(),
                started.prewarm().sectionsBuilt(),
                stats.bodies,
                stats.dynamicBodies,
                stats.worldCollisionBodies,
                stats.belowPlaneBodies,
                stats.belowTerrainBodies,
                stats.belowWorldMinBodies,
                stats.belowVoidBodies,
                stats.terrainBaselineBodies,
                stats.missingTerrainBaselineBodies,
                stats.minTerrainBottomClearance(),
                worldCollision.getTickSamples(),
                worldCollision.getEnsureCalls(),
                worldCollision.getSectionRequests(),
                worldCollision.getSectionCacheHits(),
                worldCollision.getSectionsBuilt(),
                worldCollision.getMissingChunks(),
                worldCollision.getMissingBlockChunks(),
                worldCollision.getMissingBlockSections(),
                worldCollision.getUniqueMissingSections(),
                worldCollision.getMissingOutsideRetainedEnvelope(),
                worldCollision.getBodyStreamingTargets(),
                health);
        }

        private CompletionStage<Void> contextWait(int ticks) {
            try {
                return context.waitApproxTicksOnWorld(ticks);
            } catch (ReflectiveOperationException e) {
                return CompletableFuture.failedFuture(e);
            }
        }

        private void clearStageState() {
            releaseRetainedChunks();
            physics.clearBodies();
            physics.clearAllSpaces(world.getName());
            runtimeProfiling.reset();
            worldCollisionProfiling.reset();
            worldCollisionProfiling.clearDiagnosticRetainedSections();
        }

        private void restoreStepSettings() {
            physics.setWorldSettings(previousWorldSettings);
        }

        private PrewarmStats prewarmWorldCollision(@Nonnull PhysicsSpace space, int count) {
            WorldVoxelCollisionCache cache = PhysicsWorldRuntimeResource.require(physics).worldCollisionCache();
            BenchmarkLayout layout = BenchmarkLayout.flatGrid(count);
            LongSet visitedSections = new LongOpenHashSet();
            Set<StreamingPrewarmTarget> visitedTargets = new ObjectOpenHashSet<>();
            int sectionsBuilt = 0;
            int colliderBodies = 0;
            for (int i = 0; i < count; i++) {
                Vector3d position = layout.position(i);
                PrewarmStats stats = prewarmStreamingCollisionEnvelope(cache,
                    space,
                    position,
                    visitedSections,
                    visitedTargets);
                sectionsBuilt += stats.sectionsBuilt();
                colliderBodies += stats.colliderBodies();
            }
            return new PrewarmStats(visitedSections.size(), sectionsBuilt, colliderBodies);
        }

        private PrewarmStats prewarmStreamingCollisionEnvelope(
            @Nonnull WorldVoxelCollisionCache cache,
            @Nonnull PhysicsSpace space,
            @Nonnull Vector3d position,
            @Nonnull LongSet visitedSections,
            @Nonnull Set<StreamingPrewarmTarget> visitedTargets) {
            int sectionsBuilt = 0;
            int colliderBodies = 0;
            for (int offsetX = -1; offsetX <= 1; offsetX++) {
                for (int offsetZ = -1; offsetZ <= 1; offsetZ++) {
                    Vector3d shifted = new Vector3d(position).add(
                        offsetX * STREAMING_HORIZONTAL_DRIFT_HALO_BLOCKS,
                        0.0,
                        offsetZ * STREAMING_HORIZONTAL_DRIFT_HALO_BLOCKS);
                    PrewarmStats stats = prewarmStreamingCollisionEnvelopeAt(cache,
                        space,
                        shifted,
                        visitedSections,
                        visitedTargets);
                    sectionsBuilt += stats.sectionsBuilt();
                    colliderBodies += stats.colliderBodies();
                }
            }
            return new PrewarmStats(0, sectionsBuilt, colliderBodies);
        }

        private PrewarmStats prewarmStreamingCollisionEnvelopeAt(
            @Nonnull WorldVoxelCollisionCache cache,
            @Nonnull PhysicsSpace space,
            @Nonnull Vector3d position,
            @Nonnull LongSet visitedSections,
            @Nonnull Set<StreamingPrewarmTarget> visitedTargets) {
            double step = Math.max(1.0, BODY_STREAMING_RADIUS * 2.0);
            double minCenterY = Math.min(position.y,
                STREAMING_FALL_ENVELOPE_MIN_Y + BODY_STREAMING_RADIUS);
            Vector3d center = new Vector3d(position);
            int sectionsBuilt = 0;
            int colliderBodies = 0;
            double lastY = Double.NaN;
            for (double y = position.y; y >= minCenterY; y -= step) {
                center.y = y;
                BuildStats stats = prewarmStreamingCollisionTarget(cache,
                    space,
                    center,
                    visitedSections,
                    visitedTargets);
                sectionsBuilt += stats.sectionsBuilt();
                colliderBodies += stats.colliderBodies();
                lastY = y;
            }
            if (Double.isNaN(lastY) || lastY > minCenterY) {
                center.y = minCenterY;
                BuildStats stats = prewarmStreamingCollisionTarget(cache,
                    space,
                    center,
                    visitedSections,
                    visitedTargets);
                sectionsBuilt += stats.sectionsBuilt();
                colliderBodies += stats.colliderBodies();
            }
            return new PrewarmStats(0, sectionsBuilt, colliderBodies);
        }

        @Nonnull
        private BuildStats prewarmStreamingCollisionTarget(
            @Nonnull WorldVoxelCollisionCache cache,
            @Nonnull PhysicsSpace space,
            @Nonnull Vector3d center,
            @Nonnull LongSet visitedSections,
            @Nonnull Set<StreamingPrewarmTarget> visitedTargets) {
            StreamingPrewarmTarget target = streamingPrewarmTarget(center, BODY_STREAMING_RADIUS);
            if (!visitedTargets.add(target)) {
                return emptyBuildStats();
            }
            return cache.ensureAround(world,
                space,
                center,
                BODY_STREAMING_RADIUS,
                0L,
                null,
                visitedSections);
        }

        @Nonnull
        private StreamingPrewarmTarget streamingPrewarmTarget(@Nonnull Vector3d center, int radius) {
            int minX = (int) Math.floor(center.x) - radius;
            int maxX = (int) Math.floor(center.x) + radius;
            int minY = Math.max(0, (int) Math.floor(center.y) - radius);
            int maxY = Math.min(ChunkUtil.HEIGHT_MINUS_1, (int) Math.floor(center.y) + radius);
            int minZ = (int) Math.floor(center.z) - radius;
            int maxZ = (int) Math.floor(center.z) + radius;
            return new StreamingPrewarmTarget(
                ChunkUtil.chunkCoordinate(minX),
                ChunkUtil.chunkCoordinate(maxX),
                ChunkUtil.indexSection(minY),
                ChunkUtil.indexSection(maxY),
                ChunkUtil.chunkCoordinate(minZ),
                ChunkUtil.chunkCoordinate(maxZ));
        }

        @Nonnull
        private BuildStats emptyBuildStats() {
            return new BuildStats(0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        }

        private void configureMissingSectionDiagnostics(@Nonnull BenchmarkChunks chunks) {
            LongSet sectionKeys = new LongOpenHashSet();
            for (ChunkSection section : chunks.sections()) {
                sectionKeys.add(WorldCollisionProfilingResource.packDiagnosticSectionKey(
                    section.x(),
                    section.y(),
                    section.z()));
            }
            worldCollisionProfiling.setDiagnosticRetainedSections(sectionKeys);
        }

        private void spawnDetachedBodies(@Nonnull PhysicsSpace space, int count) {
            BenchmarkLayout layout = BenchmarkLayout.flatGrid(count);
            for (int i = 0; i < count; i++) {
                PhysicsBody body = space.createBox(0.48f, 0.48f, 0.48f, 1.0f);
                body.setFriction(0.45f);
                body.setRestitution(0.0f);
                body.setDamping(0.02f, 0.25f);
                body.setCollisionFilter(PhysicsCollisionFilters.DYNAMIC_BODY,
                    PhysicsCollisionFilters.TERRAIN | PhysicsCollisionFilters.DYNAMIC_BODY);
                Vector3d position = layout.position(i);
                body.setPosition((float) position.x, (float) position.y, (float) position.z);
                PhysicsBodyId ignored = physics.addBody(space.getId(),
                    body,
                    PhysicsBodyKind.BODY,
                    PhysicsBodyPersistenceMode.RUNTIME_ONLY);
            }
        }

        private BenchmarkChunks benchmarkChunks(int count) {
            LongSet columns = new LongOpenHashSet();
            Set<ChunkSection> sections = new ObjectOpenHashSet<>();
            BenchmarkLayout layout = BenchmarkLayout.flatGrid(count);
            for (int i = 0; i < count; i++) {
                addStreamingCollisionChunks(columns, sections, layout.position(i));
            }
            return new BenchmarkChunks(columns, sections);
        }

        private void addStreamingCollisionChunks(@Nonnull LongSet columns,
            @Nonnull Set<ChunkSection> sections,
            @Nonnull Vector3d position) {
            int centerBlockX = (int) Math.floor(position.x);
            int centerBlockZ = (int) Math.floor(position.z);
            int horizontalRadius = BODY_STREAMING_RADIUS
                + (int) Math.ceil(STREAMING_HORIZONTAL_DRIFT_HALO_BLOCKS);
            int minChunkX = ChunkUtil.chunkCoordinate(centerBlockX - horizontalRadius);
            int maxChunkX = ChunkUtil.chunkCoordinate(centerBlockX + horizontalRadius);
            int minBlockY = Math.max(0, (int) Math.floor(Math.min(
                position.y - BODY_STREAMING_RADIUS,
                STREAMING_FALL_ENVELOPE_MIN_Y)));
            int maxBlockY = Math.min(ChunkUtil.HEIGHT_MINUS_1,
                (int) Math.floor(position.y + BODY_STREAMING_RADIUS));
            int minChunkY = ChunkUtil.indexSection(minBlockY);
            int maxChunkY = ChunkUtil.indexSection(maxBlockY);
            int minChunkZ = ChunkUtil.chunkCoordinate(centerBlockZ - horizontalRadius);
            int maxChunkZ = ChunkUtil.chunkCoordinate(centerBlockZ + horizontalRadius);
            for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
                for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                    columns.add(ChunkUtil.indexChunk(chunkX, chunkZ));
                    for (int chunkY = minChunkY; chunkY <= maxChunkY; chunkY++) {
                        sections.add(new ChunkSection(chunkX, chunkY, chunkZ));
                    }
                }
            }
        }

        private boolean areChunksReady(@Nonnull BenchmarkChunks chunks) {
            ChunkStore chunkStore = world.getChunkStore();
            Store<ChunkStore> chunkComponentStore = chunkStore.getStore();
            for (long chunkIndex : chunks.columns()) {
                if (!isChunkTicking(chunkStore, chunkComponentStore, chunkIndex)) {
                    return false;
                }
            }
            for (ChunkSection section : chunks.sections()) {
                if (!isChunkSectionReady(chunkStore, chunkComponentStore, section)) {
                    return false;
                }
            }
            return true;
        }

        private int requestChunks(@Nonnull BenchmarkChunks chunks) {
            int requested = 0;
            ChunkStore chunkStore = world.getChunkStore();
            Store<ChunkStore> chunkComponentStore = chunkStore.getStore();
            for (long chunkIndex : chunks.columns()) {
                if (!isChunkTicking(chunkStore, chunkComponentStore, chunkIndex)) {
                    chunkStore.getChunkReferenceAsync(chunkIndex, TICKING_CHUNK_REQUEST_FLAGS);
                    requested++;
                }
            }
            for (ChunkSection section : chunks.sections()) {
                if (!isChunkSectionReady(chunkStore, chunkComponentStore, section)) {
                    chunkStore.getChunkSectionReferenceAsync(section.x(),
                        section.y(),
                        section.z(),
                        TICKING_CHUNK_REQUEST_FLAGS);
                    requested++;
                }
            }
            return requested;
        }

        private int retainChunks(@Nonnull BenchmarkChunks chunks) {
            int retained = 0;
            ChunkStore chunkStore = world.getChunkStore();
            Store<ChunkStore> chunkComponentStore = chunkStore.getStore();
            for (long chunkIndex : chunks.columns()) {
                Ref<ChunkStore> chunkRef = chunkStore.getChunkReference(chunkIndex);
                retained += retainChunkRef(chunkComponentStore, chunkRef);
            }
            return retained;
        }

        private int retainChunkRef(@Nonnull Store<ChunkStore> chunkComponentStore,
            @Nullable Ref<ChunkStore> chunkRef) {
            if (chunkRef == null || !chunkRef.isValid()) {
                return 0;
            }
            WorldChunk worldChunk = chunkComponentStore.getComponent(chunkRef, WORLD_CHUNK_TYPE);
            if (worldChunk == null || !worldChunk.is(ChunkFlag.TICKING)) {
                return 0;
            }
            worldChunk.addKeepLoaded();
            worldChunk.resetKeepAlive();
            worldChunk.resetActiveTimer();
            retainedChunks.add(worldChunk);
            return 1;
        }

        private void releaseRetainedChunks() {
            for (WorldChunk worldChunk : retainedChunks) {
                worldChunk.removeKeepLoaded();
            }
            retainedChunks.clear();
        }

        private boolean isChunkTicking(@Nonnull ChunkStore chunkStore,
            @Nonnull Store<ChunkStore> chunkComponentStore,
            long chunkIndex) {
            Ref<ChunkStore> chunkRef = chunkStore.getChunkReference(chunkIndex);
            if (chunkRef == null || !chunkRef.isValid()) {
                return false;
            }
            WorldChunk worldChunk = chunkComponentStore.getComponent(chunkRef, WORLD_CHUNK_TYPE);
            return worldChunk != null && worldChunk.is(ChunkFlag.TICKING);
        }

        private boolean isChunkSectionReady(@Nonnull ChunkStore chunkStore,
            @Nonnull Store<ChunkStore> chunkComponentStore,
            @Nonnull ChunkSection section) {
            Ref<ChunkStore> chunkRef = chunkStore.getChunkSectionReference(section.x(),
                section.y(),
                section.z());
            if (chunkRef == null || !chunkRef.isValid()) {
                return false;
            }
            Archetype<ChunkStore> archetype = chunkComponentStore.getArchetype(chunkRef);
            return !archetype.contains(ChunkStore.REGISTRY.getNonTickingComponentType());
        }
    }

    private static CrucibleTestCase.TestOutcome outcome(@Nonnull List<StageReport> reports) {
        List<String> failed = reports.stream()
            .filter(report -> report.health().status() == StageStatus.STOP)
            .map(StageReport::summary)
            .toList();
        if (!failed.isEmpty()) {
            return CrucibleTestCase.TestOutcome.fail(String.join(" | ", failed));
        }
        return CrucibleTestCase.TestOutcome.pass();
    }

    private static StageHealth assessHealth(int count,
        double observedTickRate,
        @Nonnull SpaceStats stats,
        int missingChunks) {
        List<String> stops = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        if (observedTickRate < configuredDouble(MIN_TPS_PROPERTY, 8.0)) {
            stops.add("observedTPS=" + format(observedTickRate) + "<8");
        }
        if (stats.worldCollisionBodies == 0) {
            stops.add("worldCollisionBodies=0");
        }
        if (stats.belowWorldMinBodies > 0) {
            stops.add("belowWorldMinBodies=" + stats.belowWorldMinBodies);
        }
        if (stats.belowVoidBodies > 0) {
            stops.add("belowVoidBodies=" + stats.belowVoidBodies);
        }
        int maxBelowGround = Math.max(5, (int) Math.ceil(count * 0.01));
        if (stats.belowTerrainBodies > maxBelowGround) {
            stops.add("belowTerrainBodies=" + stats.belowTerrainBodies
                + ">" + maxBelowGround);
        }
        if (stats.belowPlaneBodies > maxBelowGround) {
            String planeReason = "belowPlaneBodies=" + stats.belowPlaneBodies
                + ">" + maxBelowGround;
            if (configuredBoolean(STRICT_PLANE_GATE_PROPERTY, false)) {
                stops.add(planeReason);
            } else {
                warnings.add(planeReason + " (strictPlaneGate=false)");
            }
        }
        if (missingChunks > 0) {
            stops.add("missingChunks=" + missingChunks);
        }
        if (!stops.isEmpty()) {
            return new StageHealth(StageStatus.STOP, String.join("; ", stops));
        }
        if (observedTickRate < configuredDouble(WARN_TPS_PROPERTY, 15.0)) {
            warnings.add("observedTPS=" + format(observedTickRate) + "<15");
        }
        if (!warnings.isEmpty()) {
            return new StageHealth(StageStatus.WARN, String.join("; ", warnings));
        }
        return new StageHealth(StageStatus.PASS, "within gates");
    }

    private static double averageMillis(long nanos, int samples) {
        if (samples <= 0) {
            return 0.0;
        }
        return nanos / 1_000_000.0 / samples;
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }

    private static String formatOptional(double value) {
        return Double.isFinite(value) ? format(value) : "n/a";
    }

    private static int configuredInt(String property, int fallback, int min, int max) {
        String raw = System.getProperty(property);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Math.clamp(Integer.parseInt(raw.trim()), min, max);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static double configuredDouble(String property, double fallback) {
        String raw = System.getProperty(property);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Double.parseDouble(raw.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static boolean configuredBoolean(String property, boolean fallback) {
        String raw = System.getProperty(property);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        return Boolean.parseBoolean(raw.trim());
    }

    private record StagePlan(@Nonnull List<Integer> counts,
                             int warmupTicks,
                             int sampleTicks) {

        private static StagePlan fromSystemProperties() {
            return new StagePlan(configuredCounts(),
                configuredInt(WARMUP_TICKS_PROPERTY,
                    DEFAULT_WARMUP_TICKS,
                    MIN_WARMUP_TICKS,
                    MAX_WARMUP_TICKS),
                configuredInt(SAMPLE_TICKS_PROPERTY,
                    DEFAULT_SAMPLE_TICKS,
                    MIN_SAMPLE_TICKS,
                    MAX_SAMPLE_TICKS));
        }

        private static List<Integer> configuredCounts() {
            String raw = System.getProperty(COUNTS_PROPERTY);
            if (raw == null || raw.isBlank()) {
                return List.of(DEFAULT_STAGE_COUNT);
            }
            List<Integer> counts = new ArrayList<>();
            for (String token : raw.split(",")) {
                try {
                    int count = Math.clamp(Integer.parseInt(token.trim()),
                        MIN_STAGE_COUNT,
                        MAX_STAGE_COUNT);
                    counts.add(count);
                } catch (NumberFormatException ignored) {
                }
            }
            return counts.isEmpty() ? List.of(DEFAULT_STAGE_COUNT) : List.copyOf(counts);
        }
    }

    private record StartedStage(boolean started,
                                @Nullable PhysicsSpace space,
                                @Nullable BenchmarkChunks chunks,
                                int retainedColumns,
                                @Nullable PrewarmStats prewarm,
                                @Nonnull String failureMessage) {

        private static StartedStage started(@Nonnull PhysicsSpace space,
            @Nonnull BenchmarkChunks chunks,
            int retainedColumns,
            @Nonnull PrewarmStats prewarm) {
            return new StartedStage(true, space, chunks, retainedColumns, prewarm, "");
        }

        private static StartedStage failed(int count, @Nonnull String failureMessage) {
            return new StartedStage(false,
                null,
                new BenchmarkChunks(new LongOpenHashSet(), Set.of()),
                0,
                new PrewarmStats(0, 0, 0),
                "count=" + count + " " + failureMessage);
        }
    }

    private record StageReport(int count,
                               double observedTickRate,
                               double avgStepMs,
                               double avgSnapshotMs,
                               double avgSyncMs,
                               double avgWorldMs,
                               double totalMs,
                               int retainedColumns,
                               int chunkRefs,
                               int prewarmTargets,
                               int prewarmSectionsBuilt,
                               int bodies,
                               int dynamicBodies,
                               int worldCollisionBodies,
                               int belowPlaneBodies,
                               int belowTerrainBodies,
                               int belowWorldMinBodies,
                               int belowVoidBodies,
                               int terrainBaselineBodies,
                               int missingTerrainBaselineBodies,
                               double minTerrainBottomClearance,
                               int worldCollisionSamples,
                               int ensureCalls,
                               int sectionRequests,
                               int sectionCacheHits,
                               int sectionsBuilt,
                               int missingChunks,
                               int missingBlockChunks,
                               int missingBlockSections,
                               int uniqueMissingSections,
                               int missingOutsideRetainedEnvelope,
                               int bodyTargets,
                               @Nonnull StageHealth health) {

        private static StageReport failedPreflight(int count, @Nonnull String reason) {
            StageHealth health = new StageHealth(StageStatus.STOP, reason);
            return new StageReport(count,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                Double.NaN,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                health);
        }

        private String summary() {
            return "count=" + count
                + " health=" + health.status()
                + " reason=" + health.reason()
                + " tps=" + format(observedTickRate)
                + " totalMs=" + format(totalMs)
                + " step/snapshot/sync/worldMs=" + format(avgStepMs)
                + "/" + format(avgSnapshotMs)
                + "/" + format(avgSyncMs)
                + "/" + format(avgWorldMs)
                + " bodies dynamic/worldCollision=" + dynamicBodies
                + "/" + worldCollisionBodies
                + " belowPlane/terrain/worldMin/void=" + belowPlaneBodies
                + "/" + belowTerrainBodies
                + "/" + belowWorldMinBodies
                + "/" + belowVoidBodies
                + " terrainBaseline samples/missing/minClearance=" + terrainBaselineBodies
                + "/" + missingTerrainBaselineBodies
                + "/" + formatOptional(minTerrainBottomClearance)
                + " chunks retained/refs=" + retainedColumns
                + "/" + chunkRefs
                + " prewarm targets/built=" + prewarmTargets
                + "/" + prewarmSectionsBuilt
                + " streamingFallMinY=" + format(STREAMING_FALL_ENVELOPE_MIN_Y)
                + " streamingHorizontalHalo=" + format(STREAMING_HORIZONTAL_DRIFT_HALO_BLOCKS)
                + " world samples/ensure/req/hit/build/miss/bodyTargets="
                + worldCollisionSamples
                + "/" + ensureCalls
                + "/" + sectionRequests
                + "/" + sectionCacheHits
                + "/" + sectionsBuilt
                + "/" + missingChunks
                + "/" + bodyTargets
                + " missing blockChunk/blockSection/unique/outsideRetained="
                + missingBlockChunks
                + "/" + missingBlockSections
                + "/" + uniqueMissingSections
                + "/" + missingOutsideRetainedEnvelope
                + " totalBodies=" + bodies;
        }
    }

    private record StageHealth(@Nonnull StageStatus status, @Nonnull String reason) {
    }

    private enum StageStatus {
        PASS,
        WARN,
        STOP
    }

    private record PrewarmStats(int sectionTargets, int sectionsBuilt, int colliderBodies) {
    }

    private record BenchmarkChunks(@Nonnull LongSet columns, @Nonnull Set<ChunkSection> sections) {

        private int size() {
            return columns.size() + sections.size();
        }
    }

        private record ChunkSection(int x, int y, int z) {
        }

        private record StreamingPrewarmTarget(int minChunkX,
            int maxChunkX,
            int minSectionY,
            int maxSectionY,
            int minChunkZ,
            int maxChunkZ) {
        }

        private record BenchmarkLayout(@Nonnull Vector3d origin, int side, double spacing) {

        private static BenchmarkLayout flatGrid(int count) {
            int side = (int) Math.ceil(Math.sqrt(count));
            double half = (side - 1) * DETACHED_SPACING * 0.5;
            return new BenchmarkLayout(new Vector3d(
                ORIGIN.x - half,
                ORIGIN.y,
                ORIGIN.z - half), side, DETACHED_SPACING);
        }

        private Vector3d position(int index) {
            int x = index % side;
            int z = index / side;
            return new Vector3d(origin).add(x * spacing, 0.0, z * spacing);
        }
    }

    private static final class SpaceStats {

        private int bodies;
        private int dynamicBodies;
        private int worldCollisionBodies;
        private int belowPlaneBodies;
        private int belowTerrainBodies;
        private int belowWorldMinBodies;
        private int belowVoidBodies;
        private int terrainBaselineBodies;
        private int missingTerrainBaselineBodies;
        private double minTerrainBottomClearance = Double.POSITIVE_INFINITY;

        private static SpaceStats collect(@Nonnull PhysicsWorldResource physics,
            @Nonnull PhysicsSpace space) {
            return physics.callOnPhysicsOwner("collect detached streaming benchmark space stats",
                () -> collectOnOwner(physics, space));
        }

        private static SpaceStats collectOnOwner(@Nonnull PhysicsWorldResource physics,
            @Nonnull PhysicsSpace space) {
            SpaceStats stats = new SpaceStats();
            WorldVoxelCollisionCache cache = PhysicsWorldRuntimeResource.require(physics).worldCollisionCache();
            for (PhysicsBody body : space.getBodies()) {
                stats.classify(cache, space, body);
            }
            return stats;
        }

        private void classify(@Nonnull WorldVoxelCollisionCache cache,
            @Nonnull PhysicsSpace space,
            @Nonnull PhysicsBody body) {
            bodies++;
            if (body.isDynamic()) {
                dynamicBodies++;
                Vector3f position = body.getPosition();
                if (position.y < GROUND_Y - BELOW_PLANE_TOLERANCE) {
                    belowPlaneBodies++;
                }
                WorldVoxelCollisionCache.GroundProbe ground = cache.probeGround(space.getId(),
                    position.x,
                    position.z,
                    horizontalHalfExtent(body));
                if (ground.found()) {
                    terrainBaselineBodies++;
                    double bottomClearance = position.y - verticalHalfExtent(body) - ground.topY();
                    minTerrainBottomClearance = Math.min(minTerrainBottomClearance, bottomClearance);
                    if (bottomClearance < -BELOW_PLANE_TOLERANCE) {
                        belowTerrainBodies++;
                    }
                } else {
                    missingTerrainBaselineBodies++;
                }
                if (position.y < BODY_WORLD_MIN_Y) {
                    belowWorldMinBodies++;
                }
                if (position.y < BODY_VOID_Y) {
                    belowVoidBodies++;
                }
            }
            if (body.getShapeType() == ShapeType.PLANE) {
                return;
            }
            if (cache.containsBody(space.getId(), body)) {
                worldCollisionBodies++;
            }
        }

        private static double horizontalHalfExtent(@Nonnull PhysicsBody body) {
            if (body.getShapeType() == ShapeType.BOX) {
                Vector3f halfExtents = body.getBoxHalfExtents();
                if (halfExtents != null) {
                    return Math.max(finitePositive(halfExtents.x), finitePositive(halfExtents.z));
                }
            }
            return Math.max(finitePositive(body.getSphereRadius()), finitePositive(body.getHalfHeight()));
        }

        private static double verticalHalfExtent(@Nonnull PhysicsBody body) {
            if (body.getShapeType() == ShapeType.BOX) {
                Vector3f halfExtents = body.getBoxHalfExtents();
                if (halfExtents != null) {
                    return finitePositive(halfExtents.y);
                }
            }
            if (body.getShapeType() == ShapeType.SPHERE) {
                return finitePositive(body.getSphereRadius());
            }
            return finitePositive(body.getHalfHeight()) + finitePositive(body.getSphereRadius());
        }

        private static double finitePositive(float value) {
            return Float.isFinite(value) ? Math.max(0.0f, value) : 0.0;
        }

        private double minTerrainBottomClearance() {
            return terrainBaselineBodies > 0 ? minTerrainBottomClearance : Double.NaN;
        }
    }
}
