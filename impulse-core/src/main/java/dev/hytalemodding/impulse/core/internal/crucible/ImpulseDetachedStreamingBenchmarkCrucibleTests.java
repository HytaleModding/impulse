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
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.PhysicsCollisionFilters;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.internal.resources.profiling.PhysicsRuntimeProfilingResource;
import dev.hytalemodding.impulse.core.internal.resources.profiling.PhysicsRuntimeProfilingResource.StepSnapshot;
import dev.hytalemodding.impulse.core.internal.resources.profiling.PhysicsRuntimeProfilingResource.SyncSnapshot;
import dev.hytalemodding.impulse.core.internal.modules.physicschunk.PhysicsChunkCollisionStreamingResource;
import dev.hytalemodding.impulse.core.internal.modules.physicschunk.PhysicsChunkBuildOptions;
import dev.hytalemodding.impulse.core.internal.modules.physicschunk.profiling.PhysicsChunkProfilingResource;
import dev.hytalemodding.impulse.core.internal.modules.physicschunk.profiling.PhysicsChunkProfilingResource.Snapshot;
import dev.hytalemodding.impulse.core.internal.physics.PhysicsSpaceMutations;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsChunkCollisionMutationQueueResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsProfilingResource;
import dev.hytalemodding.impulse.core.plugin.modules.physicschunk.PhysicsChunkCollisionPrewarmStats;
import dev.hytalemodding.impulse.core.plugin.physics.PhysicsSpaces;
import dev.hytalemodding.impulse.core.plugin.physics.PhysicsWorlds;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsBackendExtensionId;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsExtensionSettings;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsSolverSettings;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsStepMode;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsStepSchedulingMode;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsWorldSettings;
import dev.hytalemodding.impulse.core.plugin.modules.physicschunk.PhysicsChunkCollisionMode;
import dev.hytalemodding.impulse.core.plugin.modules.physicschunk.settings.PhysicsChunkCollisionSettings;
import dev.hytalemodding.impulse.core.plugin.physics.PhysicsShapeSpec;
import dev.hytalemodding.impulse.core.plugin.physics.RigidBodySpawnSettings;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.logging.Level;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3f;

/**
 * Benchmark-oriented Crucible scenario for detached bodies using streamed PhysicsChunk collision.
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
        private final Store<PhysicsStore> physicsStore;
        private final PhysicsProfilingResource physicsStoreProfiling;
        private final PhysicsRuntimeProfilingResource runtimeProfiling;
        private final PhysicsChunkProfilingResource collisionProfiling;
        private final PhysicsChunkCollisionStreamingResource collisionStreaming;
        private final PhysicsWorldSettings previousWorldSettings;
        private final boolean previousPhysicsStoreProfilingEnabled;
        private final List<WorldChunk> retainedChunks = new ArrayList<>();

        private StageRunner(@Nonnull CrucibleContext context, @Nonnull StagePlan plan)
            throws ReflectiveOperationException {
            this.context = context;
            this.plan = plan;
            this.world = context.world();
            Store<EntityStore> store = world.getEntityStore().getStore();
            this.physicsStore = PhysicsStoreCrucibleSupport.physicsStore(world);
            this.physicsStoreProfiling = physicsStore.getResource(
                PhysicsProfilingResource.getResourceType());
            this.runtimeProfiling = store.getResource(PhysicsRuntimeProfilingResource.getResourceType());
            this.collisionProfiling = store.getResource(
                PhysicsChunkProfilingResource.getResourceType());
            this.collisionStreaming = store.getResource(
                PhysicsChunkCollisionStreamingResource.getResourceType());
            this.previousWorldSettings = PhysicsWorlds.settings(physicsStore);
            this.previousPhysicsStoreProfilingEnabled = physicsStoreProfiling.isEnabled();
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
                .thenCompose(started -> contextWait(plan.warmupTicks()).thenCompose(_ -> {
                    physicsStoreProfiling.reset();
                    runtimeProfiling.reset();
                    collisionProfiling.reset();
                    physicsStoreProfiling.setEnabled(true);
                    runtimeProfiling.setEnabled(true);
                    collisionProfiling.setEnabled(true);
                    long startedNanos = System.nanoTime();
                    return contextWait(plan.sampleTicks()).thenApply(
                        _ -> finishStage(count, started, startedNanos));
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
            if (!PhysicsChunkSubPluginCrucibleSupport.ensureLoaded()) {
                return CompletableFuture.completedFuture(StartedStage.failed(count,
                    "PhysicsChunk subplugin did not load"));
            }

            PhysicsWorldSettings worldSettings = PhysicsWorlds.settings(physicsStore);
            worldSettings.setStepMode(PhysicsStepMode.PROGRESSIVE_REFINEMENT);
            worldSettings.setStepSchedulingMode(PhysicsStepSchedulingMode.DROP_PENDING_DT);
            worldSettings.setSimulationSteps(1);
            worldSettings.setMaxStepDt(TARGET_MAX_STEP_DT);
            PhysicsWorlds.putSettings(physicsStore, worldSettings);

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
                    .thenCompose(_ -> startStageWhenReady(count, attempt + 1));
            }

            int retained = retainChunks(chunks);
            configureMissingSectionDiagnostics(chunks);
            SpaceId spaceId = PhysicsSpaces.create(physicsStore,
                CrucibleBackends.requireBackendId());
            PhysicsSpaceMutations.putChunkCollisionSettings(physicsStore,
                spaceId,
                benchmarkChunkCollisionSettings());
            PhysicsSpaceMutations.putSolverSettings(physicsStore,
                spaceId,
                benchmarkSolverSettings());
            PhysicsSpaceMutations.putExtensionSettings(physicsStore,
                spaceId,
                benchmarkExtensionSettings());
            PrewarmStats prewarm = prewarmPhysicsChunkCollision(spaceId, count);
            spawnDetachedBodies(spaceId, count);
            physicsStoreProfiling.reset();
            runtimeProfiling.reset();
            collisionProfiling.reset();
            physicsStoreProfiling.setEnabled(true);
            runtimeProfiling.setEnabled(true);
            collisionProfiling.setEnabled(true);
            return CompletableFuture.completedFuture(
                StartedStage.started(spaceId, chunks, retained, prewarm));
        }

        private StageReport finishStage(int count,
            StartedStage started,
            long startedNanos) {
            if (!started.started()) {
                return StageReport.failedPreflight(count, started.failureMessage());
            }
            SpaceId spaceId = started.spaceId();
            if (spaceId == null || !PhysicsSpaces.hasSpace(physicsStore, spaceId)) {
                return StageReport.failedPreflight(count, "space disappeared during benchmark");
            }

            StepSnapshot step = runtimeProfiling.getCumulativeStep();
            SyncSnapshot sync = runtimeProfiling.getCumulativeSync();
            Snapshot collisionProfilingSnapshot = collisionProfiling.getCumulativeSnapshot();
            double elapsedSeconds = Math.max(0.001,
                (System.nanoTime() - startedNanos) / 1_000_000_000.0);
            double observedTickRate = step.getTickSamples() / elapsedSeconds;
            SpaceStats stats = SpaceStats.collect(physicsStore, collisionStreaming, spaceId);
            double avgStepMs = averageMillis(step.getTickNanos(), step.getTickSamples());
            double avgSnapshotMs = averageMillis(step.getSnapshotNanos(), step.getTickSamples());
            double avgSyncMs = averageMillis(sync.getTickNanos(), sync.getTickSamples());
            double avgTerrainMs = averageMillis(collisionProfilingSnapshot.getTickNanos(),
                collisionProfilingSnapshot.getTickSamples());
            double totalMs = avgStepMs
                + avgSnapshotMs
                + avgSyncMs
                + avgTerrainMs;
            StageHealth health = assessHealth(count,
                observedTickRate,
                stats,
                collisionProfilingSnapshot.getMissingChunks());

            assert started.chunks() != null;
            assert started.prewarm() != null;
            return new StageReport(count,
                observedTickRate,
                avgStepMs,
                avgSnapshotMs,
                avgSyncMs,
                avgTerrainMs,
                totalMs,
                started.retainedColumns(),
                started.chunks().size(),
                started.prewarm().sectionTargets(),
                started.prewarm().sectionsBuilt(),
                stats.bodies,
                stats.dynamicBodies,
                stats.terrainBodies,
                stats.belowPlaneBodies,
                stats.belowTerrainBodies,
                stats.belowWorldMinBodies,
                stats.belowVoidBodies,
                stats.terrainBaselineBodies,
                stats.missingTerrainBaselineBodies,
                stats.minTerrainBottomClearance(),
                collisionProfilingSnapshot.getTickSamples(),
                collisionProfilingSnapshot.getEnsureCalls(),
                collisionProfilingSnapshot.getSectionRequests(),
                collisionProfilingSnapshot.getSectionCacheHits(),
                collisionProfilingSnapshot.getSectionsBuilt(),
                collisionProfilingSnapshot.getMissingChunks(),
                collisionProfilingSnapshot.getMissingBlockChunks(),
                collisionProfilingSnapshot.getMissingBlockSections(),
                collisionProfilingSnapshot.getUniqueMissingSections(),
                collisionProfilingSnapshot.getMissingOutsideRetainedEnvelope(),
                collisionProfilingSnapshot.getBodyStreamingTargets(),
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
            PhysicsStoreCrucibleSupport.clearAll(physicsStore);
            physicsStoreProfiling.reset();
            runtimeProfiling.reset();
            collisionProfiling.reset();
            collisionProfiling.clearDiagnosticRetainedSections();
        }

        private void restoreStepSettings() {
            PhysicsWorlds.putSettings(physicsStore, previousWorldSettings);
            physicsStoreProfiling.setEnabled(previousPhysicsStoreProfilingEnabled);
        }

        private PrewarmStats prewarmPhysicsChunkCollision(@Nonnull SpaceId spaceId, int count) {
            BenchmarkLayout layout = BenchmarkLayout.flatGrid(count);
            UUID spaceUuid = PhysicsSpaceMutations.requireSpaceUuid(physicsStore, spaceId);
            PhysicsChunkCollisionMutationQueueResource queue = physicsStore.getResource(
                PhysicsChunkCollisionMutationQueueResource.getResourceType());
            PhysicsChunkBuildOptions buildOptions = PhysicsChunkBuildOptions.fromSettings(
                benchmarkChunkCollisionSettings());
            PhysicsChunkCollisionPrewarmStats stats = collisionStreaming.ensureAround(world,
                spaceUuid,
                queue,
                prewarmCenters(layout, count),
                BODY_STREAMING_RADIUS,
                0L,
                null,
                buildOptions);
            return new PrewarmStats(stats.sectionTargets(),
                stats.buildStats().sectionsBuilt(),
                stats.buildStats().colliderBodies());
        }

        @Nonnull
        private static PhysicsChunkCollisionSettings benchmarkChunkCollisionSettings() {
            PhysicsChunkCollisionSettings settings = new PhysicsChunkCollisionSettings();
            settings.setMode(PhysicsChunkCollisionMode.STREAMING);
            settings.setBodyRadius(BODY_STREAMING_RADIUS);
            return settings;
        }

        @Nonnull
        private static PhysicsSolverSettings benchmarkSolverSettings() {
            PhysicsSolverSettings settings = new PhysicsSolverSettings();
            settings.setSolverIterations(4);
            settings.setStabilizationIterations(1);
            return settings;
        }

        @Nonnull
        private static PhysicsExtensionSettings benchmarkExtensionSettings() {
            PhysicsExtensionSettings settings = new PhysicsExtensionSettings();
            settings.setInt(RAPIER_SOLVER_EXTENSION_ID, RAPIER_INTERNAL_PGS_ITERATIONS, 1);
            settings.setInt(RAPIER_SOLVER_EXTENSION_ID, RAPIER_MIN_ISLAND_SIZE, 128);
            return settings;
        }

        @Nonnull
        private static List<Vector3d> prewarmCenters(@Nonnull BenchmarkLayout layout, int count) {
            List<Vector3d> centers = new ArrayList<>();
            for (int index = 0; index < Math.max(0, count); index++) {
                double positionX = layout.positionX(index);
                double positionY = layout.positionY();
                double positionZ = layout.positionZ(index);
                addPrewarmEnvelopeCenters(centers, positionX, positionY, positionZ);
            }
            return centers;
        }

        private static void addPrewarmEnvelopeCenters(@Nonnull List<Vector3d> centers,
            double positionX,
            double positionY,
            double positionZ) {
            double halo = STREAMING_HORIZONTAL_DRIFT_HALO_BLOCKS;
            for (int offsetX = -1; offsetX <= 1; offsetX++) {
                for (int offsetZ = -1; offsetZ <= 1; offsetZ++) {
                    addPrewarmEnvelopeCentersAt(centers,
                        positionX + offsetX * halo,
                        positionY,
                        positionZ + offsetZ * halo);
                }
            }
        }

        private static void addPrewarmEnvelopeCentersAt(@Nonnull List<Vector3d> centers,
            double positionX,
            double positionY,
            double positionZ) {
            double step = Math.max(1.0, BODY_STREAMING_RADIUS * 2.0);
            double minCenterY = Math.min(positionY,
                STREAMING_FALL_ENVELOPE_MIN_Y + BODY_STREAMING_RADIUS);
            double lastY = Double.NaN;
            for (double y = positionY; y >= minCenterY; y -= step) {
                centers.add(new Vector3d(positionX, y, positionZ));
                lastY = y;
            }
            if (Double.isNaN(lastY) || lastY > minCenterY) {
                centers.add(new Vector3d(positionX, minCenterY, positionZ));
            }
        }

        private void configureMissingSectionDiagnostics(@Nonnull BenchmarkChunks chunks) {
            LongSet sectionKeys = new LongOpenHashSet();
            for (ChunkSection section : chunks.sections()) {
                sectionKeys.add(PhysicsChunkProfilingResource.packDiagnosticSectionKey(
                    section.x(),
                    section.y(),
                    section.z()));
            }
            collisionProfiling.setDiagnosticRetainedSections(sectionKeys);
        }

        private void spawnDetachedBodies(@Nonnull SpaceId spaceId, int count) {
            BenchmarkLayout layout = BenchmarkLayout.flatGrid(count);
            PhysicsShapeSpec box = PhysicsShapeSpec.box(0.48f, 0.48f, 0.48f);
            RigidBodySpawnSettings settings = RigidBodySpawnSettings.of(0.45f,
                0.0f,
                0.02f,
                0.25f,
                PhysicsCollisionFilters.DYNAMIC_BODY,
                PhysicsCollisionFilters.TERRAIN | PhysicsCollisionFilters.DYNAMIC_BODY);
            for (int i = 0; i < count; i++) {
                PhysicsStoreCrucibleSupport.addBody(physicsStore,
                    spaceId,
                    UUID.randomUUID(),
                    new Vector3f((float) layout.positionX(i),
                        (float) layout.positionY(),
                        (float) layout.positionZ(i)),
                    box,
                    PhysicsBodyType.DYNAMIC,
                    1.0f,
                    settings,
                    null);
            }
        }

        private BenchmarkChunks benchmarkChunks(int count) {
            LongSet columns = new LongOpenHashSet();
            Set<ChunkSection> sections = new ObjectOpenHashSet<>();
            BenchmarkLayout layout = BenchmarkLayout.flatGrid(count);
            for (int i = 0; i < count; i++) {
                addStreamingCollisionChunks(columns,
                    sections,
                    layout.positionX(i),
                    layout.positionY(),
                    layout.positionZ(i));
            }
            return new BenchmarkChunks(columns, sections);
        }

        private void addStreamingCollisionChunks(@Nonnull LongSet columns,
            @Nonnull Set<ChunkSection> sections,
            double positionX,
            double positionY,
            double positionZ) {
            int centerBlockX = (int) Math.floor(positionX);
            int centerBlockZ = (int) Math.floor(positionZ);
            int horizontalRadius = BODY_STREAMING_RADIUS
                + (int) Math.ceil(STREAMING_HORIZONTAL_DRIFT_HALO_BLOCKS);
            int minChunkX = ChunkUtil.chunkCoordinate(centerBlockX - horizontalRadius);
            int maxChunkX = ChunkUtil.chunkCoordinate(centerBlockX + horizontalRadius);
            int minBlockY = Math.max(0, (int) Math.floor(Math.min(
                positionY - BODY_STREAMING_RADIUS,
                STREAMING_FALL_ENVELOPE_MIN_Y)));
            int maxBlockY = Math.min(ChunkUtil.HEIGHT_MINUS_1,
                (int) Math.floor(positionY + BODY_STREAMING_RADIUS));
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
        if (stats.terrainBodies == 0) {
            stops.add("terrainBodies=0");
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
                                @Nullable SpaceId spaceId,
                                @Nullable BenchmarkChunks chunks,
                                int retainedColumns,
                                @Nullable PrewarmStats prewarm,
                                @Nonnull String failureMessage) {

        private static StartedStage started(@Nonnull SpaceId spaceId,
            @Nonnull BenchmarkChunks chunks,
            int retainedColumns,
            @Nonnull PrewarmStats prewarm) {
            return new StartedStage(true, spaceId, chunks, retainedColumns, prewarm, "");
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
                               double avgTerrainMs,
                               double totalMs,
                               int retainedColumns,
                               int chunkRefs,
                               int prewarmTargets,
                               int prewarmSectionsBuilt,
                               int bodies,
                               int dynamicBodies,
                               int terrainBodies,
                               int belowPlaneBodies,
                               int belowTerrainBodies,
                               int belowWorldMinBodies,
                               int belowVoidBodies,
                               int terrainBaselineBodies,
                               int missingTerrainBaselineBodies,
                               double minTerrainBottomClearance,
                               int terrainSamples,
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
                + " step/snapshot/sync/terrainMs=" + format(avgStepMs)
                + "/" + format(avgSnapshotMs)
                + "/" + format(avgSyncMs)
                + "/" + format(avgTerrainMs)
                + " bodies dynamic/physicsChunk=" + dynamicBodies
                + "/" + terrainBodies
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
                + " terrain samples/ensure/req/hit/build/miss/bodyTargets="
                + terrainSamples
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

    private record BenchmarkLayout(@Nonnull Vector3d origin, int side, double spacing) {

        private static BenchmarkLayout flatGrid(int count) {
            int side = (int) Math.ceil(Math.sqrt(count));
            double half = (side - 1) * DETACHED_SPACING * 0.5;
            return new BenchmarkLayout(new Vector3d(
                ORIGIN.x - half,
                ORIGIN.y,
                ORIGIN.z - half), side, DETACHED_SPACING);
        }

        private double positionX(int index) {
            int x = index % side;
            return origin.x + x * spacing;
        }

        private double positionY() {
            return origin.y;
        }

        private double positionZ(int index) {
            int z = index / side;
            return origin.z + z * spacing;
        }
    }

    private static final class SpaceStats {

        private int bodies;
        private int dynamicBodies;
        private int terrainBodies;
        private int belowPlaneBodies;
        private int belowTerrainBodies;
        private int belowWorldMinBodies;
        private int belowVoidBodies;
        private int terrainBaselineBodies;
        private int missingTerrainBaselineBodies;
        private double minTerrainBottomClearance = Double.POSITIVE_INFINITY;

        private static SpaceStats collect(@Nonnull Store<PhysicsStore> physicsStore,
            @Nonnull PhysicsChunkCollisionStreamingResource collisionStreaming,
            @Nonnull SpaceId spaceId) {
            BenchmarkSpaceStatsView view = PhysicsStoreBenchmarkQueries.benchmarkSpaceStats(
                physicsStore,
                collisionStreaming,
                new PhysicsStoreBenchmarkQueries.BenchmarkSpaceStatsRequest(spaceId,
                    GROUND_Y,
                    BELOW_PLANE_TOLERANCE,
                    BODY_WORLD_MIN_Y,
                    BODY_VOID_Y,
                    true));
            SpaceStats stats = new SpaceStats();
            stats.bodies = view.bodies();
            stats.dynamicBodies = view.dynamicBodies();
            stats.terrainBodies = view.terrainBodies();
            stats.belowPlaneBodies = view.belowPlaneBodies();
            stats.belowTerrainBodies = view.belowTerrainBodies();
            stats.belowWorldMinBodies = view.belowWorldMinBodies();
            stats.belowVoidBodies = view.belowVoidBodies();
            stats.terrainBaselineBodies = view.terrainBaselineBodies();
            stats.missingTerrainBaselineBodies = view.missingTerrainBaselineBodies();
            stats.minTerrainBottomClearance = view.minTerrainBottomClearance();
            return stats;
        }

        private double minTerrainBottomClearance() {
            return terrainBaselineBodies > 0 ? minTerrainBottomClearance : Double.NaN;
        }
    }
}
