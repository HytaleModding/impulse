package dev.hytalemodding.impulse.examples.commands.stress;

import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractWorldCommand;
import com.hypixel.hytale.server.core.modules.time.TimeResource;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.ChunkFlag;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.api.PhysicsBody;
import dev.hytalemodding.impulse.api.PhysicsRuntimeStats;
import dev.hytalemodding.impulse.api.PhysicsSpace;
import dev.hytalemodding.impulse.api.ShapeType;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.ImpulsePlugin;
import dev.hytalemodding.impulse.core.plugin.components.PhysicsBodyAttachmentComponent;
import dev.hytalemodding.impulse.core.internal.components.PhysicsControlSessionComponent;
import dev.hytalemodding.impulse.core.internal.diagnostics.PhysicsEntityDiagnostics;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsBodyKind;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsBodyPersistenceMode;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsRuntimeProfilingResource;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsRuntimeProfilingResource.StepSnapshot;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsRuntimeProfilingResource.SyncSnapshot;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsSpaceSettings;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsStepMode;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsWorldResource;
import dev.hytalemodding.impulse.core.internal.resources.WorldCollisionProfilingResource;
import dev.hytalemodding.impulse.core.internal.resources.WorldCollisionProfilingResource.Snapshot;
import dev.hytalemodding.impulse.core.internal.worker.PhysicsWorkerAccess;
import dev.hytalemodding.impulse.core.internal.voxel.WorldVoxelCollisionCache;
import dev.hytalemodding.impulse.core.plugin.voxel.WorldCollisionMode;
import dev.hytalemodding.impulse.examples.commands.ExamplePhysicsUtils;
import dev.hytalemodding.impulse.examples.commands.stress.StressAutoBenchmarkSupport.BenchmarkCollisionPolicy;
import dev.hytalemodding.impulse.examples.commands.stress.StressAutoBenchmarkSupport.BenchmarkWorldCollision;
import dev.hytalemodding.impulse.examples.commands.stress.StressAutoBenchmarkSupport.StageHealth;
import dev.hytalemodding.impulse.examples.commands.stress.StressAutoBenchmarkSupport.StageMetrics;
import dev.hytalemodding.impulse.examples.commands.stress.StressAutoBenchmarkSupport.StageStatus;
import dev.hytalemodding.impulse.examples.systems.BenchmarkEntityRemovalDiagnosticsSystem;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3f;

/**
 * Runs a fixed-origin benchmark from the server console without requiring a player.
 */
public class StressAutoBenchmarkCommand extends AbstractWorldCommand {

    private static final int DEFAULT_COUNT = 1000;
    private static final int MAX_COUNT = 10000;
    private static final int DEFAULT_SAMPLE_TICKS = 1200;
    private static final int MIN_SAMPLE_TICKS = 20;
    private static final int MAX_SAMPLE_TICKS = 7200;
    private static final int STRESS_BODY_WORLD_COLLISION_RADIUS = 8;
    private static final int TICKING_CHUNK_REQUEST_FLAGS = 4;
    private static final int MAX_CHUNK_PREFLIGHT_ATTEMPTS = 100;
    private static final long MILLIS_PER_TICK = 50L;
    private static final long CHUNK_PREFLIGHT_DELAY_MILLIS = 100L;
    private static final long CHUNK_KEEP_ALIVE_MILLIS = 250L;
    private static final float TARGET_MAX_STEP_DT = 1.0f / 30.0f;
    private static final double SPACING = 1.05;
    private static final double ENTITY_SPACING = 1.5;
    private static final double DETACHED_SPACING = 1.5;
    private static final float GROUND_Y = 122.0f;
    private static final float BELOW_PLANE_TOLERANCE = 1.0f;
    private static final float BODY_WORLD_MIN_Y = -32.0f;
    private static final float BODY_VOID_Y = -128.0f;
    private static final double STREAMING_FALL_ENVELOPE_MIN_Y = 0.0;
    private static final double STREAMING_HORIZONTAL_DRIFT_HALO_BLOCKS = 16.0;
    private static final Vector3d ORIGIN = new Vector3d(0.0, 128.0, 0.0);
    private static final float DETACHED_VIEW_X = 0.0f;
    private static final float DETACHED_VIEW_Y = 132.0f;
    private static final float DETACHED_VIEW_Z = -48.0f;
    private static final int DETACHED_VIEW_MATERIALIZATION_RADIUS = 48;
    private static final int DETACHED_VIEW_DEMATERIALIZATION_RADIUS = 64;
    private static final int DETACHED_VIEW_CHUNK_HALO_BLOCKS = 16;
    private static final double DETACHED_VIEW_VERTICAL_HALO_BLOCKS = 16.0;
    private static final List<WorldChunk> RETAINED_ENTITY_CHUNKS = new ArrayList<>();

    private static final ScheduledExecutorService REPORT_EXECUTOR =
        Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "Impulse auto benchmark report");
            thread.setDaemon(true);
            return thread;
        });

    private final OptionalArg<String> modeArg = this.withOptionalArg(
        "mode",
        "Benchmark mode, default detached-view: raw, detached, detached-view, "
            + "detached-view-chunks, or entity",
        ArgTypes.STRING);
    private final OptionalArg<String> confirmArg = this.withOptionalArg(
        "confirm",
        "Required: true, because this clears existing Impulse example physics state",
        ArgTypes.STRING);
    private final OptionalArg<Integer> countArg = this.withOptionalArg(
        "count",
        "Number of benchmark bodies to spawn",
        ArgTypes.INTEGER);
    private final OptionalArg<Integer> sampleTicksArg = this.withOptionalArg(
        "sampleTicks",
        "Approximate ticks to collect before reporting",
        ArgTypes.INTEGER);
    private final OptionalArg<String> stepModeArg = this.withOptionalArg(
        "stepMode",
        "Physics step mode: progressive_refinement, adaptive, fixed, or ccd",
        ArgTypes.STRING);
    private final OptionalArg<Integer> simulationStepsArg = this.withOptionalArg(
        "simulationSteps",
        "Configured physics substeps or minimum substeps",
        ArgTypes.INTEGER);
    private final OptionalArg<Integer> solverIterationsArg = this.withOptionalArg(
        "solverIterations",
        "Constraint solver iterations",
        ArgTypes.INTEGER);
    private final OptionalArg<Integer> pgsIterationsArg = this.withOptionalArg(
        "pgsIterations",
        "Internal PGS iterations per solver iteration",
        ArgTypes.INTEGER);
    private final OptionalArg<Integer> stabilizationIterationsArg = this.withOptionalArg(
        "stabilizationIterations",
        "Stabilization iterations per solver iteration",
        ArgTypes.INTEGER);
    private final OptionalArg<Integer> minIslandSizeArg = this.withOptionalArg(
        "minIslandSize",
        "Minimum island size for compatible parallel solvers",
        ArgTypes.INTEGER);
    private final OptionalArg<Float> sleepLinearThresholdArg = this.withOptionalArg(
        "sleepLinearThreshold",
        "Dynamic sleep linear velocity threshold",
        ArgTypes.FLOAT);
    private final OptionalArg<Float> sleepAngularThresholdArg = this.withOptionalArg(
        "sleepAngularThreshold",
        "Dynamic sleep angular velocity threshold",
        ArgTypes.FLOAT);
    private final OptionalArg<Float> sleepTimeArg = this.withOptionalArg(
        "sleepTime",
        "Seconds before eligible dynamic bodies sleep",
        ArgTypes.FLOAT);
    private final OptionalArg<String> worldCollisionArg = this.withOptionalArg(
        "worldCollision",
        "World collision mode: none or streaming",
        ArgTypes.STRING);
    private final OptionalArg<String> collisionsArg = this.withOptionalArg(
        "collisions",
        "Detached collision policy: world or full",
        ArgTypes.STRING);
    private final OptionalArg<String> rampArg = this.withOptionalArg(
        "ramp",
        "Whether to stage counts upward before the requested count",
        ArgTypes.STRING);
    private final OptionalArg<Integer> startCountArg = this.withOptionalArg(
        "startCount",
        "First body count for ramp mode",
        ArgTypes.INTEGER);
    private final OptionalArg<Integer> stageTicksArg = this.withOptionalArg(
        "stageTicks",
        "Sample ticks for each ramp stage",
        ArgTypes.INTEGER);
    private final OptionalArg<Integer> warmupTicksArg = this.withOptionalArg(
        "warmupTicks",
        "Warmup ticks before profiling starts",
        ArgTypes.INTEGER);

    public StressAutoBenchmarkCommand() {
        super("auto-benchmark", "Run a console-safe benchmark and report after a sample window", false);
    }

    @Override
    protected void execute(@Nonnull CommandContext ctx,
        @Nonnull World world,
        @Nonnull Store<EntityStore> store) {
        BenchmarkRequest request = parseRequest(ctx);
        if (request == null) {
            return;
        }
        if (!confirmDestructiveRun(ctx)) {
            return;
        }

        if (request.ramp()) {
            startRampStage(ctx, world, request, request.rampCounts(), 0);
            return;
        }
        startBenchmarkWhenReady(ctx, world, request);
    }

    private void startBenchmarkWhenReady(@Nonnull CommandContext ctx,
        @Nonnull World world,
        @Nonnull BenchmarkRequest request) {
        if (request.requiresBenchmarkChunks()) {
            BenchmarkChunks requiredChunks = benchmarkChunks(request);
            if (!areEntityBenchmarkChunksTicking(world, requiredChunks)) {
                int requested = requestEntityBenchmarkChunks(world, requiredChunks, false);
                ctx.sender().sendMessage(Message.raw("Impulse auto benchmark waiting for "
                    + requiredChunks.size()
                    + " entity chunk ref(s) to tick before spawning; requested="
                    + requested
                    + "."));
                scheduleEntityBenchmarkStart(ctx, world, request, requiredChunks, 1);
                return;
            }
        }

        startBenchmark(ctx, world, world.getEntityStore().getStore(), request, null);
    }

    private void startRampStage(@Nonnull CommandContext ctx,
        @Nonnull World world,
        @Nonnull BenchmarkRequest request,
        @Nonnull List<Integer> stageCounts,
        int stageIndex) {
        if (stageIndex >= stageCounts.size()) {
            ctx.sender().sendMessage(Message.raw("Impulse auto benchmark ramp complete."));
            return;
        }

        BenchmarkRequest stageRequest = request.withStageCount(stageCounts.get(stageIndex));
        RampState rampState = new RampState(request, stageCounts, stageIndex);
        if (stageRequest.requiresBenchmarkChunks()) {
            BenchmarkChunks requiredChunks = benchmarkChunks(stageRequest);
            if (!areEntityBenchmarkChunksTicking(world, requiredChunks)) {
                int requested = requestEntityBenchmarkChunks(world, requiredChunks, false);
                ctx.sender().sendMessage(Message.raw("Impulse auto benchmark ramp waiting for "
                    + requiredChunks.size()
                    + " chunk ref(s) before stage "
                    + rampState.stageNumber()
                    + "/"
                    + rampState.stageTotal()
                    + "; requested="
                    + requested
                    + "."));
                scheduleRampStageStart(ctx, world, request, stageCounts, stageIndex, 1);
                return;
            }
        }

        startBenchmark(ctx, world, world.getEntityStore().getStore(), stageRequest, rampState);
    }

    private void startBenchmark(@Nonnull CommandContext ctx,
        @Nonnull World world,
        @Nonnull Store<EntityStore> store,
        @Nonnull BenchmarkRequest request,
        @Nullable RampState rampState) {
        PhysicsWorldResource physics = store.getResource(PhysicsWorldResource.getResourceType());
        PhysicsRuntimeProfilingResource runtimeProfiling = store.getResource(
            PhysicsRuntimeProfilingResource.getResourceType());
        WorldCollisionProfilingResource worldCollisionProfiling = store.getResource(
            WorldCollisionProfilingResource.getResourceType());

        releaseRetainedEntityBenchmarkChunks();
        int removedEntities = clearBenchmarkState(store, physics, world);
        BenchmarkEntityRemovalDiagnosticsSystem.reset();
        physics.setStepMode(request.stepMode());
        physics.setSimulationSteps(request.simulationSteps());
        physics.setMaxStepDt(TARGET_MAX_STEP_DT);
        PhysicsSpaceSettings settings = PhysicsSpaceSettings.defaults();
        if (request.worldCollision() == BenchmarkWorldCollision.STREAMING) {
            settings.setWorldCollisionMode(WorldCollisionMode.STREAMING);
            settings.setWorldCollisionBodyRadius(STRESS_BODY_WORLD_COLLISION_RADIUS);
        }
        settings.setSolverIterations(request.solverIterations());
        settings.setInternalPgsIterations(request.pgsIterations());
        settings.setStabilizationIterations(request.stabilizationIterations());
        settings.setMinIslandSize(request.minIslandSize());
        settings.setDynamicSleepTuning(request.sleepLinearThreshold(),
            request.sleepAngularThreshold(),
            request.sleepTime());
        if (request.mode().usesDetachedBodies()) {
            settings.setDetachedVisualMaterializationEnabled(true);
            settings.setDetachedVisualMaxMaterialized(1024);
        }
        if (request.mode().simulatesViewer()) {
            settings.setVisualVisibilityCullingEnabled(true);
            settings.setDetachedVisualDematerializationRadius(DETACHED_VIEW_DEMATERIALIZATION_RADIUS);
            settings.setDetachedVisualMaterializationRadius(DETACHED_VIEW_MATERIALIZATION_RADIUS);
            settings.setDetachedVisualMaxMaterialized(512);
            settings.setDetachedVisualMaxSpawnsPerTick(128);
        }
        PhysicsSpace space = physics.createSpace(ImpulsePlugin.get().getDefaultBackendId(),
            world.getName(),
            settings,
            true);
        if (request.mode().simulatesViewer()) {
            physics.setSyntheticVisualInterests(List.of(new PhysicsWorldResource.VisualInterest(
                new Vector3f(DETACHED_VIEW_X, DETACHED_VIEW_Y, DETACHED_VIEW_Z),
                new Vector3f(0.0f, 0.0f, 1.0f))));
        } else {
            physics.clearSyntheticVisualInterests();
        }

        int retainedChunks = 0;
        BenchmarkChunks requiredChunks = null;
        if (request.requiresBenchmarkChunks()) {
            requiredChunks = benchmarkChunks(request);
            retainedChunks = retainEntityBenchmarkChunks(world, requiredChunks);
            if (retainedChunks != requiredChunks.columns().size()) {
                ctx.sender().sendMessage(Message.raw("Impulse auto benchmark warning: retained "
                    + retainedChunks
                    + " of "
                    + requiredChunks.columns().size()
                    + " required entity chunk column(s)."));
            }
            scheduleEntityChunkKeepAlive(world, request.sampleTicks() + request.warmupTicks());
        }
        configureMissingSectionDiagnostics(worldCollisionProfiling, requiredChunks);

        int prewarmedSections = prewarmBenchmarkWorldCollision(store,
            world,
            physics,
            space,
            settings,
            request);
        if (StressAutoBenchmarkSupport.shouldAddFallbackPlane(request.worldCollision())) {
            PhysicsWorkerAccess.run(store, "add auto benchmark fallback plane", () -> {
                PhysicsBody ground = space.createStaticPlane(GROUND_Y);
                space.addBody(ground);
            });
        }

        PhysicsEntityDiagnostics.Snapshot baselineEntityDiagnostics =
            PhysicsEntityDiagnostics.collect(store);

        runtimeProfiling.reset();
        worldCollisionProfiling.reset();
        runtimeProfiling.setEnabled(true);
        worldCollisionProfiling.setEnabled(true);

        int spawned = spawnBenchmark(store, physics, space, request);
        PhysicsEntityDiagnostics.Snapshot entityDiagnostics = PhysicsEntityDiagnostics.collect(store);
        ctx.sender().sendMessage(Message.raw("Impulse auto benchmark started: mode="
            + request.mode().serialized()
            + (rampState != null
                ? " stage=" + rampState.stageNumber() + "/" + rampState.stageTotal()
                : "")
            + " count=" + spawned
            + " livePhysicsEntities=" + entityDiagnostics.physicsBodyEntities()
            + " sampleTicks=" + request.sampleTicks()
            + " warmupTicks=" + request.warmupTicks()
            + " backend=" + space.getBackendId().value()
            + " space=" + space.getId().value()
            + " worldCollision=" + request.worldCollision().serialized()
            + " collisions=" + request.collisionPolicy().serialized()
            + " stepMode=" + request.stepMode().getSerializedName()
            + " simulationSteps=" + request.simulationSteps()
            + " prewarmedSections=" + prewarmedSections
            + " streamingFallMinY=" + format(STREAMING_FALL_ENVELOPE_MIN_Y)
            + " streamingHorizontalHalo=" + format(STREAMING_HORIZONTAL_DRIFT_HALO_BLOCKS)
            + " solverIterations=" + request.solverIterations()
            + " pgsIterations=" + request.pgsIterations()
            + " stabilizationIterations=" + request.stabilizationIterations()
            + " minIslandSize=" + request.minIslandSize()
            + " sleepLinearThreshold=" + request.sleepLinearThreshold()
            + " sleepAngularThreshold=" + request.sleepAngularThreshold()
            + " sleepTime=" + request.sleepTime()
            + " removedEntities=" + removedEntities
            + " baselineHytaleEntities=(" + baselineEntityDiagnostics.hytaleSummary() + ")"
            + (request.mode().simulatesViewer() ? " simulatedViewer=true" : "")
            + (request.mode() == BenchmarkMode.DETACHED_VIEW
                ? " syntheticChunkRetention=false" : "")
            + (request.mode() == BenchmarkMode.DETACHED_VIEW_CHUNKS
                ? " syntheticChunkRetention=true" : "")
            + (request.requiresBenchmarkChunks() ? " retainedColumns=" + retainedChunks : "")
            + ". Profiling starts after warmup; report will print after about "
            + request.totalSeconds()
            + "s."));
        if (request.mode() == BenchmarkMode.ENTITY) {
            scheduleEntityLifecycleProbe(ctx, world, store, "after 1 tick");
        }

        scheduleSampleStart(ctx, world, store, space.getId(), request, rampState);
    }

    private boolean confirmDestructiveRun(@Nonnull CommandContext ctx) {
        if (confirmArg.provided(ctx) && "true".equalsIgnoreCase(confirmArg.get(ctx).trim())) {
            return true;
        }

        ctx.sender().sendMessage(Message.raw("Impulse auto benchmark clears existing Impulse "
            + "body entities, visual proxies, runtime spaces, body ownership, and control sessions, "
            + "then creates a fresh benchmark space and enables profiling. Re-run with "
            + "--confirm=true to continue."));
        return false;
    }

    private static void scheduleEntityLifecycleProbe(@Nonnull CommandContext ctx,
        @Nonnull World world,
        @Nonnull Store<EntityStore> store,
        @Nonnull String label) {
        REPORT_EXECUTOR.schedule(() -> {
            try {
                world.execute(() -> ctx.sender().sendMessage(Message.raw(
                    "Impulse auto benchmark entity probe "
                        + label
                        + ": livePhysicsEntities="
                        + PhysicsEntityDiagnostics.collect(store).physicsBodyEntities()
                        + " "
                        + BenchmarkEntityRemovalDiagnosticsSystem.snapshot())));
            } catch (Exception e) {
                ctx.sender().sendMessage(Message.raw("Impulse auto benchmark entity probe failed: "
                    + e.getMessage()));
            }
        }, MILLIS_PER_TICK, TimeUnit.MILLISECONDS);
    }

    private void scheduleEntityBenchmarkStart(@Nonnull CommandContext ctx,
        @Nonnull World world,
        @Nonnull BenchmarkRequest request,
        @Nonnull BenchmarkChunks requiredChunks,
        int attempt) {
        REPORT_EXECUTOR.schedule(() -> {
            try {
                world.execute(() -> retryEntityBenchmarkStart(ctx, world, request, requiredChunks, attempt));
            } catch (Exception e) {
                ctx.sender().sendMessage(Message.raw("Impulse auto benchmark chunk preflight failed: "
                    + e.getMessage()));
            }
        }, CHUNK_PREFLIGHT_DELAY_MILLIS, TimeUnit.MILLISECONDS);
    }

    private void retryEntityBenchmarkStart(@Nonnull CommandContext ctx,
        @Nonnull World world,
        @Nonnull BenchmarkRequest request,
        @Nonnull BenchmarkChunks requiredChunks,
        int attempt) {
        if (areEntityBenchmarkChunksTicking(world, requiredChunks)) {
            Store<EntityStore> store = world.getEntityStore().getStore();
            startBenchmark(ctx, world, store, request, null);
            return;
        }

        int requested = requestEntityBenchmarkChunks(world, requiredChunks, false);
        if (attempt >= MAX_CHUNK_PREFLIGHT_ATTEMPTS) {
            ctx.sender().sendMessage(Message.raw("Impulse auto benchmark aborted: "
                + requiredChunks.size()
                + " entity chunk ref(s) did not become ticking after "
                + attempt
                + " attempts; requested="
                + requested
                + "."));
            return;
        }

        scheduleEntityBenchmarkStart(ctx, world, request, requiredChunks, attempt + 1);
    }

    private void scheduleRampStageStart(@Nonnull CommandContext ctx,
        @Nonnull World world,
        @Nonnull BenchmarkRequest request,
        @Nonnull List<Integer> stageCounts,
        int stageIndex,
        int attempt) {
        REPORT_EXECUTOR.schedule(() -> {
            try {
                world.execute(() -> retryRampStageStart(ctx, world, request, stageCounts, stageIndex, attempt));
            } catch (Exception e) {
                ctx.sender().sendMessage(Message.raw("Impulse auto benchmark ramp chunk preflight failed: "
                    + e.getMessage()));
            }
        }, CHUNK_PREFLIGHT_DELAY_MILLIS, TimeUnit.MILLISECONDS);
    }

    private void retryRampStageStart(@Nonnull CommandContext ctx,
        @Nonnull World world,
        @Nonnull BenchmarkRequest request,
        @Nonnull List<Integer> stageCounts,
        int stageIndex,
        int attempt) {
        BenchmarkRequest stageRequest = request.withStageCount(stageCounts.get(stageIndex));
        BenchmarkChunks requiredChunks = benchmarkChunks(stageRequest);
        if (areEntityBenchmarkChunksTicking(world, requiredChunks)) {
            startBenchmark(ctx,
                world,
                world.getEntityStore().getStore(),
                stageRequest,
                new RampState(request, stageCounts, stageIndex));
            return;
        }

        int requested = requestEntityBenchmarkChunks(world, requiredChunks, false);
        if (attempt >= MAX_CHUNK_PREFLIGHT_ATTEMPTS) {
            ctx.sender().sendMessage(Message.raw("Impulse auto benchmark ramp aborted before stage "
                + (stageIndex + 1)
                + "/"
                + stageCounts.size()
                + ": "
                + requiredChunks.size()
                + " chunk ref(s) did not become ticking after "
                + attempt
                + " attempts; requested="
                + requested
                + "."));
            return;
        }

        scheduleRampStageStart(ctx, world, request, stageCounts, stageIndex, attempt + 1);
    }

    private void scheduleEntityChunkKeepAlive(@Nonnull World world,
        int remainingTicks) {
        if (remainingTicks <= 0) {
            return;
        }

        REPORT_EXECUTOR.schedule(() -> {
            try {
                world.execute(() -> {
                    refreshRetainedEntityBenchmarkChunks();
                    int elapsedTicks = Math.max(1, (int) (CHUNK_KEEP_ALIVE_MILLIS / MILLIS_PER_TICK));
                    scheduleEntityChunkKeepAlive(world, remainingTicks - elapsedTicks);
                });
            } catch (Exception ignored) {
            }
        }, CHUNK_KEEP_ALIVE_MILLIS, TimeUnit.MILLISECONDS);
    }

    private static int prewarmBenchmarkWorldCollision(@Nonnull Store<EntityStore> store,
        @Nonnull World world,
        @Nonnull PhysicsWorldResource physics,
        @Nonnull PhysicsSpace space,
        @Nonnull PhysicsSpaceSettings settings,
        @Nonnull BenchmarkRequest request) {
        if (request.worldCollision() != BenchmarkWorldCollision.STREAMING
            || settings.getWorldCollisionMode() != WorldCollisionMode.STREAMING) {
            return 0;
        }

        return PhysicsWorkerAccess.call(store, "prewarm benchmark world collision", () -> {
            LongSet visitedSections = new LongOpenHashSet();
            Set<StreamingPrewarmTarget> visitedTargets = new ObjectOpenHashSet<>();
            BenchmarkLayout layout = layoutFor(request);
            for (int i = 0; i < request.count(); i++) {
                Vector3d position = layout.position(i);
                prewarmStreamingCollisionEnvelope(world,
                    physics,
                    space,
                    settings.getWorldCollisionBodyRadius(),
                    position,
                    visitedSections,
                    visitedTargets);
            }
            return visitedSections.size();
        });
    }

    private static void prewarmStreamingCollisionEnvelope(@Nonnull World world,
        @Nonnull PhysicsWorldResource physics,
        @Nonnull PhysicsSpace space,
        int radius,
        @Nonnull Vector3d position,
        @Nonnull LongSet visitedSections,
        @Nonnull Set<StreamingPrewarmTarget> visitedTargets) {
        for (int offsetX = -1; offsetX <= 1; offsetX++) {
            for (int offsetZ = -1; offsetZ <= 1; offsetZ++) {
                Vector3d shifted = new Vector3d(position).add(
                    offsetX * STREAMING_HORIZONTAL_DRIFT_HALO_BLOCKS,
                    0.0,
                    offsetZ * STREAMING_HORIZONTAL_DRIFT_HALO_BLOCKS);
                prewarmStreamingCollisionColumn(world,
                    physics,
                    space,
                    radius,
                    shifted,
                    visitedSections,
                    visitedTargets);
            }
        }
    }

    private static void prewarmStreamingCollisionColumn(@Nonnull World world,
        @Nonnull PhysicsWorldResource physics,
        @Nonnull PhysicsSpace space,
        int radius,
        @Nonnull Vector3d position,
        @Nonnull LongSet visitedSections,
        @Nonnull Set<StreamingPrewarmTarget> visitedTargets) {
        double step = Math.max(1.0, radius * 2.0);
        double minCenterY = Math.min(position.y, STREAMING_FALL_ENVELOPE_MIN_Y + radius);
        Vector3d center = new Vector3d(position);
        double lastY = Double.NaN;
        for (double y = position.y; y >= minCenterY; y -= step) {
            center.y = y;
            prewarmStreamingCollisionTarget(world,
                physics,
                space,
                radius,
                center,
                visitedSections,
                visitedTargets);
            lastY = y;
        }
        if (Double.isNaN(lastY) || lastY > minCenterY) {
            center.y = minCenterY;
            prewarmStreamingCollisionTarget(world,
                physics,
                space,
                radius,
                center,
                visitedSections,
                visitedTargets);
        }
    }

    private static void prewarmStreamingCollisionTarget(@Nonnull World world,
        @Nonnull PhysicsWorldResource physics,
        @Nonnull PhysicsSpace space,
        int radius,
        @Nonnull Vector3d center,
        @Nonnull LongSet visitedSections,
        @Nonnull Set<StreamingPrewarmTarget> visitedTargets) {
        StreamingPrewarmTarget target = streamingPrewarmTarget(center, radius);
        if (!visitedTargets.add(target)) {
            return;
        }
        physics.getWorldVoxelCollisionCache().ensureAround(world,
            space,
            center,
            radius,
            0L,
            null,
            visitedSections);
    }

    @Nonnull
    private static StreamingPrewarmTarget streamingPrewarmTarget(@Nonnull Vector3d center, int radius) {
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

    private static void configureMissingSectionDiagnostics(
        @Nonnull WorldCollisionProfilingResource profiling,
        @Nullable BenchmarkChunks chunks) {
        if (chunks == null) {
            profiling.clearDiagnosticRetainedSections();
            return;
        }

        LongSet sectionKeys = new LongOpenHashSet();
        for (ChunkSection section : chunks.sections()) {
            sectionKeys.add(WorldCollisionProfilingResource.packDiagnosticSectionKey(
                section.x(),
                section.y(),
                section.z()));
        }
        profiling.setDiagnosticRetainedSections(sectionKeys);
    }

    private void scheduleSampleStart(@Nonnull CommandContext ctx,
        @Nonnull World world,
        @Nonnull Store<EntityStore> store,
        @Nonnull SpaceId spaceId,
        @Nonnull BenchmarkRequest request,
        @Nullable RampState rampState) {
        long delayMillis = Math.max(0L, request.warmupTicks()) * MILLIS_PER_TICK;
        REPORT_EXECUTOR.schedule(() -> {
            try {
                world.execute(() -> startSample(ctx, world, store, spaceId, request, rampState));
            } catch (Exception e) {
                ctx.sender().sendMessage(Message.raw("Impulse auto benchmark sample start failed: "
                    + e.getMessage()));
            }
        }, delayMillis, TimeUnit.MILLISECONDS);
    }

    private void startSample(@Nonnull CommandContext ctx,
        @Nonnull World world,
        @Nonnull Store<EntityStore> store,
        @Nonnull SpaceId spaceId,
        @Nonnull BenchmarkRequest request,
        @Nullable RampState rampState) {
        PhysicsWorldResource physics = store.getResource(PhysicsWorldResource.getResourceType());
        if (physics.getSpace(spaceId) == null) {
            ctx.sender().sendMessage(Message.raw("Impulse auto benchmark sample aborted: space "
                + spaceId.value()
                + " no longer exists."));
            WorldCollisionProfilingResource worldCollisionProfiling = store.getResource(
                WorldCollisionProfilingResource.getResourceType());
            worldCollisionProfiling.clearDiagnosticRetainedSections();
            return;
        }

        PhysicsRuntimeProfilingResource runtimeProfiling = store.getResource(
            PhysicsRuntimeProfilingResource.getResourceType());
        WorldCollisionProfilingResource worldCollisionProfiling = store.getResource(
            WorldCollisionProfilingResource.getResourceType());
        runtimeProfiling.reset();
        worldCollisionProfiling.reset();
        runtimeProfiling.setEnabled(true);
        worldCollisionProfiling.setEnabled(true);
        long startedNanos = System.nanoTime();
        scheduleReport(ctx, world, store, spaceId, request, startedNanos, rampState);
    }

    private static int retainEntityBenchmarkChunks(@Nonnull World world,
        @Nonnull BenchmarkChunks chunks) {
        int retained = 0;
        ChunkStore chunkStore = world.getChunkStore();
        Store<ChunkStore> chunkComponentStore = chunkStore.getStore();
        for (long chunkIndex : chunks.columns()) {
            Ref<ChunkStore> chunkRef = chunkStore.getChunkReference(chunkIndex);
            retained += retainChunkRef(chunkComponentStore, chunkRef);
        }
        return retained;
    }

    private static int retainChunkRef(@Nonnull Store<ChunkStore> chunkComponentStore,
        @Nullable Ref<ChunkStore> chunkRef) {
        if (chunkRef == null || !chunkRef.isValid()) {
            return 0;
        }

        WorldChunk worldChunk = chunkComponentStore.getComponent(chunkRef, WorldChunk.getComponentType());
        if (worldChunk == null || !worldChunk.is(ChunkFlag.TICKING)) {
            return 0;
        }

        worldChunk.addKeepLoaded();
        worldChunk.resetKeepAlive();
        worldChunk.resetActiveTimer();
        RETAINED_ENTITY_CHUNKS.add(worldChunk);
        return 1;
    }

    private static void refreshRetainedEntityBenchmarkChunks() {
        for (WorldChunk worldChunk : RETAINED_ENTITY_CHUNKS) {
            worldChunk.resetKeepAlive();
            worldChunk.resetActiveTimer();
        }
    }

    private static void releaseRetainedEntityBenchmarkChunks() {
        for (WorldChunk worldChunk : RETAINED_ENTITY_CHUNKS) {
            worldChunk.removeKeepLoaded();
        }
        RETAINED_ENTITY_CHUNKS.clear();
    }

    @Nonnull
    private static BenchmarkChunks benchmarkChunks(@Nonnull BenchmarkRequest request) {
        LongSet columns = new LongOpenHashSet();
        Set<ChunkSection> sections = new ObjectOpenHashSet<>();
        BenchmarkLayout layout = layoutFor(request);
        for (int i = 0; i < request.count(); i++) {
            Vector3d position = layout.position(i);
            if (request.worldCollision() == BenchmarkWorldCollision.STREAMING) {
                addStreamingCollisionChunks(columns, sections, position);
            } else {
                int chunkX = ChunkUtil.chunkCoordinate(position.x);
                int chunkY = ChunkUtil.chunkCoordinate(position.y);
                int chunkZ = ChunkUtil.chunkCoordinate(position.z);
                columns.add(ChunkUtil.indexChunk(chunkX, chunkZ));
                sections.add(new ChunkSection(chunkX, chunkY, chunkZ));
            }
        }
        if (request.mode() == BenchmarkMode.DETACHED_VIEW_CHUNKS) {
            addDetachedViewChunks(columns, sections, layout);
        }
        return new BenchmarkChunks(columns, sections);
    }

    private static void addStreamingCollisionChunks(@Nonnull LongSet columns,
        @Nonnull Set<ChunkSection> sections,
        @Nonnull Vector3d position) {
        int centerBlockX = (int) Math.floor(position.x);
        int centerBlockZ = (int) Math.floor(position.z);
        int horizontalRadius = STRESS_BODY_WORLD_COLLISION_RADIUS
            + (int) Math.ceil(STREAMING_HORIZONTAL_DRIFT_HALO_BLOCKS);
        int minChunkX = ChunkUtil.chunkCoordinate(centerBlockX - horizontalRadius);
        int maxChunkX = ChunkUtil.chunkCoordinate(centerBlockX + horizontalRadius);
        int minBlockY = Math.max(0, (int) Math.floor(Math.min(
            position.y - STRESS_BODY_WORLD_COLLISION_RADIUS,
            STREAMING_FALL_ENVELOPE_MIN_Y)));
        int maxBlockY = Math.min(ChunkUtil.HEIGHT_MINUS_1,
            (int) Math.floor(position.y + STRESS_BODY_WORLD_COLLISION_RADIUS));
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

    private static void addDetachedViewChunks(@Nonnull LongSet columns,
        @Nonnull Set<ChunkSection> sections,
        @Nonnull BenchmarkLayout layout) {
        double radius = DETACHED_VIEW_DEMATERIALIZATION_RADIUS + DETACHED_VIEW_CHUNK_HALO_BLOCKS;
        int minChunkX = ChunkUtil.chunkCoordinate(DETACHED_VIEW_X - radius);
        int maxChunkX = ChunkUtil.chunkCoordinate(DETACHED_VIEW_X + radius);
        int minChunkZ = ChunkUtil.chunkCoordinate(DETACHED_VIEW_Z - radius);
        int maxChunkZ = ChunkUtil.chunkCoordinate(DETACHED_VIEW_Z + radius);
        double minY = Math.min(GROUND_Y, layout.origin().y) - DETACHED_VIEW_VERTICAL_HALO_BLOCKS;
        double maxY = layout.maxY() + DETACHED_VIEW_VERTICAL_HALO_BLOCKS;
        int minChunkY = ChunkUtil.chunkCoordinate(minY);
        int maxChunkY = ChunkUtil.chunkCoordinate(maxY);

        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                columns.add(ChunkUtil.indexChunk(chunkX, chunkZ));
                for (int chunkY = minChunkY; chunkY <= maxChunkY; chunkY++) {
                    sections.add(new ChunkSection(chunkX, chunkY, chunkZ));
                }
            }
        }
    }

    private static boolean areEntityBenchmarkChunksTicking(@Nonnull World world,
        @Nonnull BenchmarkChunks chunks) {
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

    private static int requestEntityBenchmarkChunks(@Nonnull World world,
        @Nonnull BenchmarkChunks chunks,
        boolean includeTickingChunks) {
        int requested = 0;
        ChunkStore chunkStore = world.getChunkStore();
        Store<ChunkStore> chunkComponentStore = chunkStore.getStore();
        for (long chunkIndex : chunks.columns()) {
            if (includeTickingChunks || !isChunkTicking(chunkStore, chunkComponentStore, chunkIndex)) {
                chunkStore.getChunkReferenceAsync(chunkIndex, TICKING_CHUNK_REQUEST_FLAGS);
                requested++;
            }
        }
        for (ChunkSection section : chunks.sections()) {
            if (includeTickingChunks || !isChunkSectionReady(chunkStore, chunkComponentStore, section)) {
                chunkStore.getChunkSectionReferenceAsync(section.x(),
                    section.y(),
                    section.z(),
                    TICKING_CHUNK_REQUEST_FLAGS);
                requested++;
            }
        }
        return requested;
    }

    private static boolean isChunkTicking(@Nonnull ChunkStore chunkStore,
        @Nonnull Store<ChunkStore> chunkComponentStore,
        long chunkIndex) {
        Ref<ChunkStore> chunkRef = chunkStore.getChunkReference(chunkIndex);
        if (chunkRef == null || !chunkRef.isValid()) {
            return false;
        }

        WorldChunk worldChunk = chunkComponentStore.getComponent(chunkRef, WorldChunk.getComponentType());
        return worldChunk != null && worldChunk.is(ChunkFlag.TICKING);
    }

    private static boolean isChunkSectionReady(@Nonnull ChunkStore chunkStore,
        @Nonnull Store<ChunkStore> chunkComponentStore,
        @Nonnull ChunkSection section) {
        Ref<ChunkStore> chunkRef = chunkStore.getChunkSectionReference(section.x(), section.y(), section.z());
        if (chunkRef == null || !chunkRef.isValid()) {
            return false;
        }

        Archetype<ChunkStore> archetype = chunkComponentStore.getArchetype(chunkRef);
        return archetype != null
            && !archetype.contains(ChunkStore.REGISTRY.getNonTickingComponentType());
    }

    private static int clearBenchmarkState(@Nonnull Store<EntityStore> store,
        @Nonnull PhysicsWorldResource physics,
        @Nonnull World world) {
        AtomicInteger removedEntities = new AtomicInteger();
        store.forEachEntityParallel(PhysicsBodyAttachmentComponent.getComponentType(),
            (index, archetypeChunk, commandBuffer) -> {
                removedEntities.incrementAndGet();
                commandBuffer.removeEntity(archetypeChunk.getReferenceTo(index), RemoveReason.REMOVE);
            });

        store.forEachEntityParallel(PhysicsControlSessionComponent.getComponentType(),
            (index, archetypeChunk, commandBuffer) -> commandBuffer.removeComponent(
                archetypeChunk.getReferenceTo(index),
                PhysicsControlSessionComponent.getComponentType()));

        physics.clearBodies();
        physics.clearAllSpaces(world.getName());
        return removedEntities.get();
    }

    private static int countPhysicsBodyEntities(@Nonnull Store<EntityStore> store) {
        AtomicInteger entities = new AtomicInteger();
        store.forEachEntityParallel(PhysicsBodyAttachmentComponent.getComponentType(),
            (index, archetypeChunk, commandBuffer) -> entities.incrementAndGet());
        return entities.get();
    }

    private static int spawnBenchmark(@Nonnull Store<EntityStore> store,
        @Nonnull PhysicsWorldResource physics,
        @Nonnull PhysicsSpace space,
        @Nonnull BenchmarkRequest request) {
        BenchmarkLayout layout = layoutFor(request);
        return switch (request.mode()) {
            case RAW -> spawnRaw(store, space, layout, request.count(), request.collisionPolicy());
            case DETACHED, DETACHED_VIEW, DETACHED_VIEW_CHUNKS ->
                spawnDetached(store, physics, space, layout, request.count(), request.collisionPolicy());
            case ENTITY -> spawnEntities(store, physics, space, layout, request.count(), request.collisionPolicy());
        };
    }

    @Nonnull
    private static BenchmarkLayout layoutFor(@Nonnull BenchmarkRequest request) {
        if (request.mode().usesDetachedBodies()) {
            return BenchmarkLayout.flatGrid(request.count());
        }
        if (request.mode() == BenchmarkMode.ENTITY) {
            return BenchmarkLayout.sparseAround(request.count());
        }
        return BenchmarkLayout.denseAround(request.count());
    }

    private static int spawnRaw(@Nonnull Store<EntityStore> store,
        @Nonnull PhysicsSpace space,
        @Nonnull BenchmarkLayout layout,
        int count,
        @Nonnull BenchmarkCollisionPolicy collisionPolicy) {
        PhysicsWorkerAccess.run(store, "spawn raw benchmark bodies", () -> {
            for (int i = 0; i < count; i++) {
                PhysicsBody body = createBenchmarkBody(space, collisionPolicy);
                Vector3d position = layout.position(i);
                body.setPosition((float) position.x, (float) position.y, (float) position.z);
                space.addBody(body);
            }
        });
        return count;
    }

    private static int spawnDetached(@Nonnull Store<EntityStore> store,
        @Nonnull PhysicsWorldResource physics,
        @Nonnull PhysicsSpace space,
        @Nonnull BenchmarkLayout layout,
        int count,
        @Nonnull BenchmarkCollisionPolicy collisionPolicy) {
        PhysicsWorkerAccess.run(store, "spawn detached benchmark bodies", () -> {
            for (int i = 0; i < count; i++) {
                PhysicsBody body = createBenchmarkBody(space, collisionPolicy);
                Vector3d position = layout.position(i);
                body.setPosition((float) position.x, (float) position.y, (float) position.z);
                physics.addBody(space.getId(),
                    body,
                    PhysicsBodyKind.BODY,
                    PhysicsBodyPersistenceMode.RUNTIME_ONLY);
            }
        });
        return count;
    }

    private static int spawnEntities(@Nonnull Store<EntityStore> store,
        @Nonnull PhysicsWorldResource physics,
        @Nonnull PhysicsSpace space,
        @Nonnull BenchmarkLayout layout,
        int count,
        @Nonnull BenchmarkCollisionPolicy collisionPolicy) {
        TimeResource time = store.getResource(TimeResource.getResourceType());
        for (int i = 0; i < count; i++) {
            PhysicsBody body = PhysicsWorkerAccess.call(store,
                "create entity auto benchmark body",
                () -> createBenchmarkBody(space, collisionPolicy));
            Vector3d position = layout.position(i);
            ExamplePhysicsUtils.spawnBlockBody(store,
                time,
                physics,
                space.getId(),
                space,
                body,
                position);
        }
        return count;
    }

    @Nonnull
    private static PhysicsBody createBenchmarkBody(@Nonnull PhysicsSpace space,
        @Nonnull BenchmarkCollisionPolicy collisionPolicy) {
        PhysicsBody body = space.createBox(0.48f, 0.48f, 0.48f, 1.0f);
        body.setFriction(0.45f);
        body.setRestitution(0.0f);
        body.setDamping(0.02f, 0.25f);
        if (collisionPolicy.appliesFilter()) {
            body.setCollisionFilter(collisionPolicy.group(), collisionPolicy.mask());
        }
        return body;
    }

    private void scheduleReport(@Nonnull CommandContext ctx,
        @Nonnull World world,
        @Nonnull Store<EntityStore> store,
        @Nonnull SpaceId spaceId,
        @Nonnull BenchmarkRequest request,
        long startedNanos,
        @Nullable RampState rampState) {
        long delayMillis = Math.max(1L, request.sampleTicks()) * MILLIS_PER_TICK;
        REPORT_EXECUTOR.schedule(() -> {
            try {
                world.execute(() -> sendReport(ctx, world, store, spaceId, request, startedNanos, rampState));
            } catch (Exception e) {
                ctx.sender().sendMessage(Message.raw("Impulse auto benchmark report failed: "
                    + e.getMessage()));
            }
        }, delayMillis, TimeUnit.MILLISECONDS);
    }

    private void sendReport(@Nonnull CommandContext ctx,
        @Nonnull World world,
        @Nonnull Store<EntityStore> store,
        @Nonnull SpaceId spaceId,
        @Nonnull BenchmarkRequest request,
        long startedNanos,
        @Nullable RampState rampState) {
        PhysicsWorldResource physics = store.getResource(PhysicsWorldResource.getResourceType());
        PhysicsRuntimeProfilingResource runtimeProfiling = store.getResource(
            PhysicsRuntimeProfilingResource.getResourceType());
        WorldCollisionProfilingResource worldCollisionProfiling = store.getResource(
            WorldCollisionProfilingResource.getResourceType());
        PhysicsSpace space = physics.getSpace(spaceId);
        if (space == null) {
            ctx.sender().sendMessage(Message.raw("Impulse auto benchmark report: space "
                + spaceId.value() + " no longer exists."));
            if (request.requiresBenchmarkChunks()) {
                releaseRetainedEntityBenchmarkChunks();
            }
            worldCollisionProfiling.clearDiagnosticRetainedSections();
            return;
        }

        StepSnapshot step = runtimeProfiling.getCumulativeStep();
        SyncSnapshot sync = runtimeProfiling.getCumulativeSync();
        Snapshot worldCollision = worldCollisionProfiling.getCumulativeSnapshot();
        SpaceStats stats = SpaceStats.collect(store, physics, space);
        PhysicsRuntimeStats runtimeStats = PhysicsWorkerAccess.call(store,
            "collect auto benchmark runtime stats",
            space::getRuntimeStats);
        PhysicsEntityDiagnostics.Snapshot entityDiagnostics = PhysicsEntityDiagnostics.collect(store);
        double elapsedSeconds = Math.max(0.001, (System.nanoTime() - startedNanos) / 1_000_000_000.0);
        double observedTickRate = step.getTickSamples() / elapsedSeconds;
        double avgStepMs = averageMillis(step.getTickNanos(), step.getTickSamples());
        double avgSnapshotMs = averageMillis(step.getSnapshotNanos(), step.getTickSamples());
        double avgSyncMs = averageMillis(sync.getTickNanos(), sync.getTickSamples());
        double avgWorldMs = averageMillis(worldCollision.getTickNanos(), worldCollision.getTickSamples());
        double totalProfiledMs = StressAutoBenchmarkSupport.totalProfiledMillis(avgStepMs,
            avgSnapshotMs,
            avgSyncMs,
            avgWorldMs);
        StageHealth health = StressAutoBenchmarkSupport.assessStage(request.worldCollision(),
            new StageMetrics(request.count(),
                observedTickRate,
                stats.worldCollisionBodies,
                stats.belowPlaneBodies,
                stats.belowTerrainBodies,
                stats.belowWorldMinBodies,
                stats.belowVoidBodies,
                worldCollision.getMissingChunks()));

        ctx.sender().sendMessage(Message.raw("Impulse auto benchmark report: mode="
            + request.mode().serialized()
            + (rampState != null
                ? " stage=" + rampState.stageNumber() + "/" + rampState.stageTotal()
                : "")
            + " count=" + request.count()
            + " worldCollision=" + request.worldCollision().serialized()
            + " collisions=" + request.collisionPolicy().serialized()
            + " solverIterations=" + request.solverIterations()
            + " pgsIterations=" + request.pgsIterations()
            + " stabilizationIterations=" + request.stabilizationIterations()
            + " minIslandSize=" + request.minIslandSize()
            + " elapsed=" + format(elapsedSeconds)
            + "s observedTickSamples=" + step.getTickSamples()
            + " observedTickRate=" + format(observedTickRate)
            + " livePhysicsEntities=" + entityDiagnostics.physicsBodyEntities()));
        ctx.sender().sendMessage(Message.raw("Profiled avg ms/tick: step="
            + format(avgStepMs)
            + " snapshot=" + format(avgSnapshotMs)
            + " sync=" + format(avgSyncMs)
            + " worldCollision=" + format(avgWorldMs)
            + " total=" + format(totalProfiledMs)));
        ctx.sender().sendMessage(Message.raw("Bodies: total=" + stats.bodies
            + " dynamic=" + stats.dynamicBodies
            + " awake=" + stats.awakeDynamicBodies
            + " sleeping=" + stats.sleepingDynamicBodies
            + " owned=" + stats.entityOwnedBodies
            + " detached=" + stats.detachedBodies
            + " raw=" + stats.rawBodies
            + " worldCollision=" + stats.worldCollisionBodies));
        if (runtimeStats.available()) {
            ctx.sender().sendMessage(Message.raw("Backend runtime stats: "
                + runtimeStatsSummary(runtimeStats)));
        }
        ctx.sender().sendMessage(Message.raw("Body bounds: yMin=" + format(stats.minDynamicBodyY())
            + " yMax=" + format(stats.maxDynamicBodyY())
            + " belowPlane=" + stats.belowPlaneBodies
            + " belowTerrain=" + stats.belowTerrainBodies
            + " belowWorldMin=" + stats.belowWorldMinBodies
            + " belowVoid=" + stats.belowVoidBodies));
        ctx.sender().sendMessage(Message.raw("Terrain baseline: samples=" + stats.terrainBaselineBodies
            + " missing=" + stats.missingTerrainBaselineBodies
            + " topYMin=" + formatOptional(stats.minTerrainTopY())
            + " topYMax=" + formatOptional(stats.maxTerrainTopY())
            + " minBottomClearance=" + formatOptional(stats.minTerrainBottomClearance())));
        ctx.sender().sendMessage(Message.raw("Stage health: " + health.status()
            + " reason=" + health.reason()));
        ctx.sender().sendMessage(Message.raw("Hytale entity diagnostics: "
            + entityDiagnostics.hytaleSummary()));
        ctx.sender().sendMessage(Message.raw("Impulse entity diagnostics: "
            + entityDiagnostics.impulseSummary()));
        ctx.sender().sendMessage(Message.raw("Sync avg: inspected="
            + average(sync.getBodiesInspected(), sync.getTickSamples())
            + " synced=" + average(sync.getBodiesSynced(), sync.getTickSamples())
            + " skipSleeping=" + average(sync.getSkippedSleeping(), sync.getTickSamples())
            + " skipThreshold=" + average(sync.getSkippedThreshold(), sync.getTickSamples())
            + " skipVisualDeadzone=" + average(sync.getSkippedVisualDeadzone(), sync.getTickSamples())
            + " skipVisualRange=" + average(sync.getSkippedVisualRange(), sync.getTickSamples())));
        ctx.sender().sendMessage(Message.raw("World collision: samples="
            + worldCollision.getTickSamples()
            + " ensureCalls=" + worldCollision.getEnsureCalls()
            + " sectionsBuilt=" + worldCollision.getSectionsBuilt()
            + " missingChunks=" + worldCollision.getMissingChunks()
            + " missing blockChunk/blockSection/unique/outsideRetained="
            + worldCollision.getMissingBlockChunks()
            + "/" + worldCollision.getMissingBlockSections()
            + "/" + worldCollision.getUniqueMissingSections()
            + "/" + worldCollision.getMissingOutsideRetainedEnvelope()
            + " bodyTargets=" + worldCollision.getBodyStreamingTargets()
            + " targetCache hit/first/bounds/refresh/skip/pruned="
            + worldCollision.getBodyTargetCacheHits()
            + "/" + worldCollision.getBodyTargetFirstSeen()
            + "/" + worldCollision.getBodyTargetBoundsChanged()
            + "/" + (worldCollision.getBodyTargetActiveRefreshes()
            + worldCollision.getBodyTargetSleepingRefreshes())
            + "/" + (worldCollision.getBodyTargetActiveStableSkips()
            + worldCollision.getBodyTargetSleepingStableSkips())
            + "/" + worldCollision.getBodyTargetsPruned()
            + " missingBackoff=" + worldCollision.getMissingBackoffSkips()));
        if (!worldCollision.getMissingSectionSamples().isEmpty()) {
            ctx.sender().sendMessage(Message.raw("Missing section samples: "
                + formatMissingSectionSamples(worldCollision)));
        }
        ctx.sender().sendMessage(Message.raw("Entity removal diagnostics: "
            + BenchmarkEntityRemovalDiagnosticsSystem.snapshot()));
        if (request.requiresBenchmarkChunks()) {
            releaseRetainedEntityBenchmarkChunks();
        }
        worldCollisionProfiling.clearDiagnosticRetainedSections();
        if (rampState != null) {
            continueRamp(ctx, world, health, rampState);
        }
    }

    private void continueRamp(@Nonnull CommandContext ctx,
        @Nonnull World world,
        @Nonnull StageHealth health,
        @Nonnull RampState rampState) {
        if (health.status() == StageStatus.STOP) {
            ctx.sender().sendMessage(Message.raw("Impulse auto benchmark ramp stopped after stage "
                + rampState.stageNumber()
                + "/"
                + rampState.stageTotal()
                + ": "
                + health.reason()));
            return;
        }
        int nextStageIndex = rampState.stageIndex() + 1;
        if (nextStageIndex >= rampState.stageCounts().size()) {
            ctx.sender().sendMessage(Message.raw("Impulse auto benchmark ramp complete: finalStatus="
                + health.status()
                + " finalReason="
                + health.reason()));
            return;
        }

        REPORT_EXECUTOR.schedule(() -> {
            try {
                world.execute(() -> startRampStage(ctx,
                    world,
                    rampState.rootRequest(),
                    rampState.stageCounts(),
                    nextStageIndex));
            } catch (Exception e) {
                ctx.sender().sendMessage(Message.raw("Impulse auto benchmark ramp failed before next stage: "
                    + e.getMessage()));
            }
        }, MILLIS_PER_TICK, TimeUnit.MILLISECONDS);
    }

    @Nullable
    private BenchmarkRequest parseRequest(@Nonnull CommandContext ctx) {
        BenchmarkMode mode = BenchmarkMode.DETACHED_VIEW;
        if (modeArg.provided(ctx)) {
            String rawMode = modeArg.get(ctx).toLowerCase(Locale.ROOT);
            BenchmarkMode parsed = BenchmarkMode.from(rawMode);
            if (parsed == null) {
                ctx.sender().sendMessage(Message.raw("Unknown benchmark mode '" + rawMode
                    + "'. Expected raw, detached, detached-view, detached-view-chunks, entity, entities, hytale, or entity-backed."));
                return null;
            }
            mode = parsed;
        }

        int count = optionalInt(ctx, countArg, DEFAULT_COUNT, 1, MAX_COUNT);
        int sampleTicks = optionalInt(ctx, sampleTicksArg, DEFAULT_SAMPLE_TICKS,
            MIN_SAMPLE_TICKS, MAX_SAMPLE_TICKS);
        PhysicsStepMode stepModeOverride = null;
        if (stepModeArg.provided(ctx)) {
            try {
                stepModeOverride = PhysicsStepMode.parse(stepModeArg.get(ctx));
            } catch (IllegalArgumentException e) {
                ctx.sender().sendMessage(Message.raw("Unknown benchmark stepMode '"
                    + stepModeArg.get(ctx)
                    + "'. Expected progressive_refinement, adaptive, fixed, or ccd."));
                return null;
            }
        }
        int simulationSteps = optionalInt(ctx,
            simulationStepsArg,
            PhysicsWorldResource.MIN_SIMULATION_STEPS,
            PhysicsWorldResource.MIN_SIMULATION_STEPS,
            PhysicsWorldResource.MAX_SIMULATION_STEPS);
        int solverIterations = optionalInt(ctx,
            solverIterationsArg,
            PhysicsSpaceSettings.DEFAULT_SOLVER_ITERATIONS,
            1,
            32);
        int pgsIterations = optionalInt(ctx,
            pgsIterationsArg,
            PhysicsSpaceSettings.DEFAULT_INTERNAL_PGS_ITERATIONS,
            1,
            8);
        int stabilizationIterations = optionalInt(ctx,
            stabilizationIterationsArg,
            PhysicsSpaceSettings.DEFAULT_STABILIZATION_ITERATIONS,
            0,
            8);
        int minIslandSize = optionalInt(ctx,
            minIslandSizeArg,
            PhysicsSpaceSettings.DEFAULT_MIN_ISLAND_SIZE,
            1,
            8192);
        float sleepLinearThreshold = optionalFloat(ctx,
            sleepLinearThresholdArg,
            PhysicsSpaceSettings.DEFAULT_DYNAMIC_SLEEP_LINEAR_THRESHOLD,
            0.0f,
            64.0f);
        float sleepAngularThreshold = optionalFloat(ctx,
            sleepAngularThresholdArg,
            PhysicsSpaceSettings.DEFAULT_DYNAMIC_SLEEP_ANGULAR_THRESHOLD,
            0.0f,
            64.0f);
        float sleepTime = optionalFloat(ctx,
            sleepTimeArg,
            PhysicsSpaceSettings.DEFAULT_DYNAMIC_SLEEP_TIME_UNTIL_SLEEP,
            0.0f,
            60.0f);
        BenchmarkWorldCollision worldCollision = BenchmarkWorldCollision.NONE;
        if (worldCollisionArg.provided(ctx)) {
            String rawWorldCollision = worldCollisionArg.get(ctx);
            BenchmarkWorldCollision parsed = BenchmarkWorldCollision.from(rawWorldCollision);
            if (parsed == null) {
                ctx.sender().sendMessage(Message.raw("Unknown benchmark worldCollision '"
                    + rawWorldCollision
                    + "'. Expected none or streaming."));
                return null;
            }
            worldCollision = parsed;
        }
        BenchmarkCollisionPolicy collisionPolicy = BenchmarkCollisionPolicy.DEFAULT;
        if (collisionsArg.provided(ctx)) {
            String rawCollisions = collisionsArg.get(ctx);
            BenchmarkCollisionPolicy parsed = BenchmarkCollisionPolicy.from(rawCollisions);
            if (parsed == null) {
                ctx.sender().sendMessage(Message.raw("Unknown benchmark collisions '"
                    + rawCollisions
                    + "'. Expected world or full."));
                return null;
            }
            collisionPolicy = parsed;
        }
        Boolean ramp = optionalBoolean(ctx, rampArg, false);
        if (ramp == null) {
            ctx.sender().sendMessage(Message.raw("Unknown benchmark ramp value '"
                + rampArg.get(ctx)
                + "'. Expected true or false."));
            return null;
        }
        int startCount = optionalInt(ctx,
            startCountArg,
            StressAutoBenchmarkSupport.DEFAULT_RAMP_START_COUNT,
            1,
            count);
        int stageTicks = optionalInt(ctx,
            stageTicksArg,
            StressAutoBenchmarkSupport.DEFAULT_STAGE_TICKS,
            MIN_SAMPLE_TICKS,
            MAX_SAMPLE_TICKS);
        int warmupTicks = optionalInt(ctx,
            warmupTicksArg,
            StressAutoBenchmarkSupport.DEFAULT_WARMUP_TICKS,
            0,
            MAX_SAMPLE_TICKS);
        return new BenchmarkRequest(mode,
            count,
            sampleTicks,
            warmupTicks,
            stageTicks,
            stepModeOverride,
            simulationSteps,
            solverIterations,
            pgsIterations,
            stabilizationIterations,
            minIslandSize,
            sleepLinearThreshold,
            sleepAngularThreshold,
            sleepTime,
            worldCollision,
            collisionPolicy,
            ramp,
            startCount);
    }

    private static int optionalInt(@Nonnull CommandContext ctx,
        @Nonnull OptionalArg<Integer> arg,
        int defaultValue,
        int min,
        int max) {
        int value = arg.provided(ctx) ? arg.get(ctx) : defaultValue;
        if (value < min) {
            return min;
        }
        return Math.min(value, max);
    }

    private static float optionalFloat(@Nonnull CommandContext ctx,
        @Nonnull OptionalArg<Float> arg,
        float defaultValue,
        float min,
        float max) {
        float value = arg.provided(ctx) ? arg.get(ctx) : defaultValue;
        if (!Float.isFinite(value)) {
            return defaultValue;
        }
        if (value < min) {
            return min;
        }
        return Math.min(value, max);
    }

    @Nullable
    private static Boolean optionalBoolean(@Nonnull CommandContext ctx,
        @Nonnull OptionalArg<String> arg,
        boolean defaultValue) {
        if (!arg.provided(ctx)) {
            return defaultValue;
        }
        return switch (arg.get(ctx).trim().toLowerCase(Locale.ROOT)) {
            case "true", "yes", "y", "1", "on" -> true;
            case "false", "no", "n", "0", "off" -> false;
            default -> null;
        };
    }

    private static double averageMillis(long nanos, int samples) {
        if (samples <= 0) {
            return 0.0;
        }
        return nanos / 1_000_000.0 / samples;
    }

    @Nonnull
    private static String average(int value, int samples) {
        if (samples <= 0) {
            return "0.000";
        }
        return format((double) value / samples);
    }

    @Nonnull
    private static String format(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }

    @Nonnull
    private static String formatOptional(double value) {
        return Double.isFinite(value) ? format(value) : "n/a";
    }

    @Nonnull
    private static String runtimeStatsSummary(@Nonnull PhysicsRuntimeStats stats) {
        return "bodies=" + stats.bodyCount()
            + " colliders=" + stats.colliderCount()
            + " activeBodies=" + stats.activeBodyCount()
            + " activeIslands=" + stats.activeIslandCount()
            + " contactPairs=" + stats.contactPairCount()
            + " contactManifolds=" + stats.contactManifoldCount()
            + " contactPoints=" + stats.contactPointCount()
            + " dynamicDynamicPairs=" + stats.dynamicDynamicContactPairCount()
            + " terrainPairs=" + stats.terrainContactPairCount()
            + " joints=" + stats.jointCount();
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

    private enum BenchmarkMode {
        RAW("raw"),
        DETACHED("detached"),
        DETACHED_VIEW("detached-view"),
        DETACHED_VIEW_CHUNKS("detached-view-chunks"),
        ENTITY("entity");

        private final String serialized;

        BenchmarkMode(@Nonnull String serialized) {
            this.serialized = serialized;
        }

        @Nonnull
        private String serialized() {
            return serialized;
        }

        @Nullable
        private static BenchmarkMode from(@Nonnull String value) {
            return switch (value) {
                case "raw" -> RAW;
                case "detached", "managed", "physics-only", "physics_only" -> DETACHED;
                case "detached-view", "detached-viewer", "view", "viewed" -> DETACHED_VIEW;
                case "detached-view-chunks", "view-chunks", "viewed-chunks",
                    "detached-view-diagnostic", "view-diagnostic" -> DETACHED_VIEW_CHUNKS;
                case "entity", "entities", "hytale", "hytale-entities", "entity-backed" -> ENTITY;
                default -> null;
            };
        }

        private boolean usesDetachedBodies() {
            return this == DETACHED || this == DETACHED_VIEW || this == DETACHED_VIEW_CHUNKS;
        }

        private boolean simulatesViewer() {
            return this == DETACHED_VIEW || this == DETACHED_VIEW_CHUNKS;
        }

        private boolean requiresBenchmarkChunks() {
            return this == ENTITY || this == DETACHED_VIEW_CHUNKS;
        }
    }

    private record BenchmarkRequest(@Nonnull BenchmarkMode mode,
        int count,
        int sampleTicks,
        int warmupTicks,
        int stageTicks,
        @Nullable PhysicsStepMode stepModeOverride,
        int simulationSteps,
        int solverIterations,
        int pgsIterations,
        int stabilizationIterations,
        int minIslandSize,
        float sleepLinearThreshold,
        float sleepAngularThreshold,
        float sleepTime,
        @Nonnull BenchmarkWorldCollision worldCollision,
        @Nonnull BenchmarkCollisionPolicy collisionPolicy,
        boolean ramp,
        int startCount) {

        private int totalSeconds() {
            return Math.max(1, (int) Math.ceil((warmupTicks + sampleTicks) * MILLIS_PER_TICK / 1000.0));
        }

        private boolean requiresBenchmarkChunks() {
            return mode.requiresBenchmarkChunks() || worldCollision == BenchmarkWorldCollision.STREAMING;
        }

        @Nonnull
        private PhysicsStepMode stepMode() {
            if (stepModeOverride != null) {
                return stepModeOverride;
            }
            if (worldCollision == BenchmarkWorldCollision.STREAMING
                && collisionPolicy == BenchmarkCollisionPolicy.FULL) {
                return PhysicsStepMode.PROGRESSIVE_REFINEMENT;
            }
            return PhysicsStepMode.FIXED;
        }

        @Nonnull
        private List<Integer> rampCounts() {
            return StressAutoBenchmarkSupport.rampCounts(startCount, count);
        }

        @Nonnull
        private BenchmarkRequest withStageCount(int stageCount) {
            return new BenchmarkRequest(mode,
                stageCount,
                stageTicks,
                warmupTicks,
                stageTicks,
                stepModeOverride,
                simulationSteps,
                solverIterations,
                pgsIterations,
                stabilizationIterations,
                minIslandSize,
                sleepLinearThreshold,
                sleepAngularThreshold,
                sleepTime,
                worldCollision,
                collisionPolicy,
                false,
                startCount);
        }
    }

    private record RampState(@Nonnull BenchmarkRequest rootRequest,
        @Nonnull List<Integer> stageCounts,
        int stageIndex) {

        private int stageNumber() {
            return stageIndex + 1;
        }

        private int stageTotal() {
            return stageCounts.size();
        }
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

    private record BenchmarkLayout(Vector3d origin,
        int side,
        double spacing,
        int heightSteps,
        boolean flat) {

        @Nonnull
        private static BenchmarkLayout denseAround(int count) {
            return around(count, SPACING);
        }

        @Nonnull
        private static BenchmarkLayout sparseAround(int count) {
            return around(count, ENTITY_SPACING);
        }

        @Nonnull
        private static BenchmarkLayout flatGrid(int count) {
            int side = (int) Math.ceil(Math.sqrt(count));
            double half = (side - 1) * DETACHED_SPACING * 0.5;
            return new BenchmarkLayout(new Vector3d(
                ORIGIN.x - half,
                ORIGIN.y,
                ORIGIN.z - half), side, DETACHED_SPACING, 1, true);
        }

        @Nonnull
        private static BenchmarkLayout around(int count, double spacing) {
            int side = (int) Math.ceil(Math.cbrt(count));
            double half = (side - 1) * spacing * 0.5;
            return new BenchmarkLayout(new Vector3d(
                ORIGIN.x - half,
                ORIGIN.y,
                ORIGIN.z - half), side, spacing, side, false);
        }

        @Nonnull
        private Vector3d position(int index) {
            int x = index % side;
            int z = flat ? index / side : (index / side) % side;
            int y = flat ? 0 : index / (side * side);
            return new Vector3d(origin).add(x * spacing, y * spacing, z * spacing);
        }

        private double maxY() {
            return origin.y + (heightSteps - 1) * spacing;
        }
    }

    private static final class SpaceStats {

        private int bodies;
        private int dynamicBodies;
        private int awakeDynamicBodies;
        private int sleepingDynamicBodies;
        private int entityOwnedBodies;
        private int detachedBodies;
        private int worldCollisionBodies;
        private int rawBodies;
        private double minDynamicBodyY = Double.POSITIVE_INFINITY;
        private double maxDynamicBodyY = Double.NEGATIVE_INFINITY;
        private int belowPlaneBodies;
        private int belowTerrainBodies;
        private int belowWorldMinBodies;
        private int belowVoidBodies;
        private int terrainBaselineBodies;
        private int missingTerrainBaselineBodies;
        private double minTerrainTopY = Double.POSITIVE_INFINITY;
        private double maxTerrainTopY = Double.NEGATIVE_INFINITY;
        private double minTerrainBottomClearance = Double.POSITIVE_INFINITY;

        @Nonnull
        private static SpaceStats collect(@Nonnull Store<EntityStore> store,
            @Nonnull PhysicsWorldResource physics,
            @Nonnull PhysicsSpace space) {
            return PhysicsWorkerAccess.call(store, "collect auto benchmark space stats", () -> {
                SpaceStats stats = new SpaceStats();
                WorldVoxelCollisionCache cache = physics.getWorldVoxelCollisionCache();
                List<PhysicsBody> bodies = new ArrayList<>(space.getBodies());
                for (PhysicsBody body : bodies) {
                    stats.classify(physics, cache, space, body);
                }
                return stats;
            });
        }

        private void classify(@Nonnull PhysicsWorldResource physics,
            @Nonnull WorldVoxelCollisionCache cache,
            @Nonnull PhysicsSpace space,
            @Nonnull PhysicsBody body) {
            bodies++;
            if (body.isDynamic()) {
                dynamicBodies++;
                Vector3f position = body.getPosition();
                minDynamicBodyY = Math.min(minDynamicBodyY, position.y);
                maxDynamicBodyY = Math.max(maxDynamicBodyY, position.y);
                if (position.y < GROUND_Y - BELOW_PLANE_TOLERANCE) {
                    belowPlaneBodies++;
                }
                WorldVoxelCollisionCache.GroundProbe ground = cache.probeGround(space.getId(),
                    position.x,
                    position.z,
                    horizontalHalfExtent(body));
                if (ground.found()) {
                    terrainBaselineBodies++;
                    minTerrainTopY = Math.min(minTerrainTopY, ground.topY());
                    maxTerrainTopY = Math.max(maxTerrainTopY, ground.topY());
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
                if (body.isSleeping()) {
                    sleepingDynamicBodies++;
                } else {
                    awakeDynamicBodies++;
                }
            }

            PhysicsWorldResource.BodyRegistration registration = physics.getBodyRegistration(body);
            if (registration != null && registration.kind() == PhysicsBodyKind.BODY) {
                if (physics.getBodyAttachments(registration.id()).isEmpty()) {
                    detachedBodies++;
                } else {
                    entityOwnedBodies++;
                }
                return;
            }
            if (body.getShapeType() == ShapeType.PLANE) {
                return;
            }
            if (cache.containsBody(space.getId(), body)) {
                worldCollisionBodies++;
                return;
            }
            rawBodies++;
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

        private double minDynamicBodyY() {
            return Double.isFinite(minDynamicBodyY) ? minDynamicBodyY : 0.0;
        }

        private double maxDynamicBodyY() {
            return Double.isFinite(maxDynamicBodyY) ? maxDynamicBodyY : 0.0;
        }

        private double minTerrainTopY() {
            return terrainBaselineBodies > 0 ? minTerrainTopY : Double.NaN;
        }

        private double maxTerrainTopY() {
            return terrainBaselineBodies > 0 ? maxTerrainTopY : Double.NaN;
        }

        private double minTerrainBottomClearance() {
            return terrainBaselineBodies > 0 ? minTerrainBottomClearance : Double.NaN;
        }
    }
}
