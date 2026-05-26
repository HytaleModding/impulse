package dev.hytalemodding.impulse.core.internal.crucible;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.api.BackendId;
import dev.hytalemodding.impulse.api.Impulse;
import dev.hytalemodding.impulse.api.PhysicsBody;
import dev.hytalemodding.impulse.api.PhysicsCollisionFilters;
import dev.hytalemodding.impulse.api.PhysicsSpace;
import dev.hytalemodding.impulse.api.ShapeType;
import dev.hytalemodding.impulse.core.plugin.components.PhysicsControlSessionComponent;
import dev.hytalemodding.impulse.core.internal.resources.profiling.PhysicsRuntimeProfilingResource;
import dev.hytalemodding.impulse.core.internal.resources.profiling.PhysicsRuntimeProfilingResource.StepSnapshot;
import dev.hytalemodding.impulse.core.internal.resources.profiling.PhysicsRuntimeProfilingResource.SyncSnapshot;
import dev.hytalemodding.impulse.core.internal.resources.profiling.WorldCollisionProfilingResource;
import dev.hytalemodding.impulse.core.internal.resources.profiling.WorldCollisionProfilingResource.Snapshot;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsWorldRuntimeResource;
import dev.hytalemodding.impulse.core.internal.voxel.WorldVoxelCollisionCache;
import dev.hytalemodding.impulse.core.plugin.components.PhysicsBodyAttachmentComponent;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyKind;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyPersistenceMode;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyRegistration;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsSpaceSettings;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsSolverSettings;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsStepMode;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsStepSchedulingMode;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsWorldSettings;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsWorldResource;
import dev.hytalemodding.impulse.core.plugin.collision.WorldCollisionMode;
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
 * Benchmark-oriented Crucible scenario for Rapier detached body-only fixed substeps.
 */
final class ImpulseRapierBodyBenchmarkCrucibleTests {

    private static final HytaleLogger LOGGER = HytaleLogger.get("Impulse");
    private static final BackendId RAPIER_BACKEND_ID = new BackendId("impulse:rapier");
    private static final String COUNT_PROPERTY = "impulse.crucible.rapierBodyMatrix.count";
    private static final String SUBSTEPS_PROPERTY = "impulse.crucible.rapierBodyMatrix.substeps";
    private static final String WARMUP_TICKS_PROPERTY =
        "impulse.crucible.rapierBodyMatrix.warmupTicks";
    private static final String SAMPLE_TICKS_PROPERTY =
        "impulse.crucible.rapierBodyMatrix.sampleTicks";
    private static final String MIN_TPS_PROPERTY = "impulse.crucible.rapierBodyMatrix.minTps";
    private static final String WARN_TPS_PROPERTY = "impulse.crucible.rapierBodyMatrix.warnTps";

    private static final int DEFAULT_COUNT = 5_000;
    private static final int DEFAULT_WARMUP_TICKS = 60;
    private static final int DEFAULT_SAMPLE_TICKS = 200;
    private static final int MIN_COUNT = 1;
    private static final int MAX_COUNT = 10_000;
    private static final int MIN_WARMUP_TICKS = 1;
    private static final int MAX_WARMUP_TICKS = 1_200;
    private static final int MIN_SAMPLE_TICKS = 20;
    private static final int MAX_SAMPLE_TICKS = 7_200;
    private static final float TARGET_MAX_STEP_DT = 1.0f / 30.0f;
    private static final float GROUND_Y = 122.0f;
    private static final float BELOW_PLANE_TOLERANCE = 1.0f;
    private static final float BODY_WORLD_MIN_Y = -32.0f;
    private static final float BODY_VOID_Y = -128.0f;
    private static final double DETACHED_SPACING = 1.5;
    private static final Vector3d ORIGIN = new Vector3d(0.0, 128.0, 0.0);
    private static final ComponentType<EntityStore, PhysicsBodyAttachmentComponent> ATTACHMENT_TYPE =
        PhysicsBodyAttachmentComponent.getComponentType();
    private static final ComponentType<EntityStore, PhysicsControlSessionComponent> CONTROL_SESSION_TYPE =
        PhysicsControlSessionComponent.getComponentType();

    private ImpulseRapierBodyBenchmarkCrucibleTests() {
    }

    static void register(CrucibleBridge bridge, ClassLoader loader)
        throws ReflectiveOperationException {

        bridge.registerSuite(loader, benchmarkSuite());
    }

    private static CrucibleSuite benchmarkSuite() {
        return new CrucibleSuite(
            "impulse:rapier_body_fixed_substep_benchmark",
            "Impulse Rapier Body Fixed-Substep Benchmark",
            "Runs the 5000 detached body-only Rapier benchmark for fixed substep counts",
            Set.of("benchmark", "rapier", "body", "fixed"),
            List.of(CrucibleTestCase.asyncResult("5000 body-only fixed substep matrix",
                ImpulseRapierBodyBenchmarkCrucibleTests::rapierBodyMatrix,
                "Rapier body fixed-substep benchmark breached a health gate")));
    }

    private static CompletionStage<CrucibleTestCase.TestOutcome> rapierBodyMatrix(
        CrucibleContext context) {
        if (!rapierBackendAvailable()) {
            return CompletableFuture.completedFuture(CrucibleTestCase.TestOutcome.fail(
                "Rapier backend impulse:rapier is not registered; available="
                    + availableBackendIds()));
        }
        try {
            MatrixPlan plan = MatrixPlan.fromSystemProperties();
            MatrixRunner runner = new MatrixRunner(context, plan);
            return runner.run();
        } catch (ReflectiveOperationException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    private static boolean rapierBackendAvailable() {
        return Impulse.getBackends().stream()
            .anyMatch(backend -> RAPIER_BACKEND_ID.equals(backend.getId()));
    }

    @Nonnull
    private static List<String> availableBackendIds() {
        return Impulse.getBackends().stream()
            .map(backend -> backend.getId().value())
            .sorted()
            .toList();
    }

    private static final class MatrixRunner {

        private final CrucibleContext context;
        private final MatrixPlan plan;
        private final World world;
        private final Store<EntityStore> store;
        private final PhysicsWorldRuntimeResource physics;
        private final PhysicsRuntimeProfilingResource runtimeProfiling;
        private final WorldCollisionProfilingResource worldCollisionProfiling;
        private final PhysicsWorldSettings previousWorldSettings;
        private final boolean previousRuntimeProfilingEnabled;
        private final boolean previousWorldCollisionProfilingEnabled;

        private MatrixRunner(@Nonnull CrucibleContext context, @Nonnull MatrixPlan plan)
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
            this.previousRuntimeProfilingEnabled = runtimeProfiling.isEnabled();
            this.previousWorldCollisionProfilingEnabled = worldCollisionProfiling.isEnabled();
        }

        private CompletionStage<CrucibleTestCase.TestOutcome> run() {
            return runCase(0, new ArrayList<>()).handle((outcome, failure) -> {
                clearCaseState();
                restoreSettings();
                if (failure != null) {
                    if (failure instanceof CompletionException completionException) {
                        throw completionException;
                    }
                    throw new CompletionException(failure);
                }
                return outcome;
            });
        }

        private CompletionStage<CrucibleTestCase.TestOutcome> runCase(int index,
            @Nonnull List<MatrixReport> reports) {
            if (index >= plan.substeps().size()) {
                logComparison(reports);
                return CompletableFuture.completedFuture(outcome(reports));
            }

            MatrixCase matrixCase = new MatrixCase(plan.count(), plan.substeps().get(index));
            return startCase(matrixCase)
                .thenCompose(started -> contextWait(plan.warmupTicks()).thenCompose(ignored -> {
                    runtimeProfiling.reset();
                    worldCollisionProfiling.reset();
                    runtimeProfiling.setEnabled(true);
                    worldCollisionProfiling.setEnabled(true);
                    long startedNanos = System.nanoTime();
                    return contextWait(plan.sampleTicks()).thenApply(
                        unused -> finishCase(matrixCase, started, startedNanos));
                }))
                .thenCompose(report -> {
                    reports.add(report);
                    LOGGER.at(Level.INFO).log("Crucible Rapier body matrix case: %s",
                        report.summary());
                    clearCaseState();
                    if (report.health().status() == MatrixStatus.STOP) {
                        return CompletableFuture.completedFuture(outcome(reports));
                    }
                    return runCase(index + 1, reports);
                });
        }

        private CompletionStage<StartedCase> startCase(@Nonnull MatrixCase matrixCase) {
            clearCaseState();
            PhysicsWorldSettings worldSettings = physics.getWorldSettings();
            worldSettings.setStepMode(PhysicsStepMode.FIXED);
            worldSettings.setStepSchedulingMode(PhysicsStepSchedulingMode.DROP_PENDING_DT);
            worldSettings.setSimulationSteps(matrixCase.fixedSubsteps());
            worldSettings.setMaxStepDt(TARGET_MAX_STEP_DT);
            physics.setWorldSettings(worldSettings);
            physics.clearSyntheticVisualInterests();

            PhysicsSpaceSettings settings = PhysicsSpaceSettings.defaults();
            settings.getWorldCollisionSettings().setWorldCollisionMode(WorldCollisionMode.NONE);
            settings.getSolverSettings().setSolverIterations(PhysicsSolverSettings.DEFAULT_SOLVER_ITERATIONS);
            settings.getSolverSettings().setInternalPgsIterations(
                PhysicsSolverSettings.DEFAULT_INTERNAL_PGS_ITERATIONS);
            settings.getSolverSettings().setStabilizationIterations(
                PhysicsSolverSettings.DEFAULT_STABILIZATION_ITERATIONS);
            settings.getSolverSettings().setMinIslandSize(PhysicsSolverSettings.DEFAULT_MIN_ISLAND_SIZE);
            try {
                PhysicsSpace space = physics.createSpace(RAPIER_BACKEND_ID,
                    world.getName(),
                    settings,
                    true);
                physics.runOnPhysicsOwner("prepare rapier body benchmark space", () -> {
                    PhysicsBody ground = space.createStaticPlane(GROUND_Y);
                    ground.setCollisionFilter(PhysicsCollisionFilters.TERRAIN,
                        PhysicsCollisionFilters.ALL);
                    space.addBody(ground);
                    spawnDetachedBodies(space, matrixCase.count());
                });
                return CompletableFuture.completedFuture(StartedCase.started(space));
            } catch (RuntimeException exception) {
                return CompletableFuture.completedFuture(
                    StartedCase.failed(exception.getMessage()));
            }
        }

        private MatrixReport finishCase(@Nonnull MatrixCase matrixCase,
            @Nonnull StartedCase started,
            long startedNanos) {
            if (!started.started()) {
                return MatrixReport.failedPreflight(matrixCase, started.failureMessage());
            }
            PhysicsSpace space = started.space();
            if (space == null || physics.getSpace(space.getId()) == null) {
                return MatrixReport.failedPreflight(matrixCase,
                    "space disappeared during benchmark");
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
            MatrixHealth health = assessHealth(matrixCase,
                observedTickRate,
                step,
                worldCollision,
                stats);

            return new MatrixReport(matrixCase,
                observedTickRate,
                avgStepMs,
                avgSnapshotMs,
                avgSyncMs,
                avgWorldMs,
                totalMs,
                step.getTickSamples(),
                step.getSubsteps(),
                step.getBodySnapshots(),
                step.getSpatialIndexCells(),
                sync.getTickSamples(),
                sync.getBodiesInspected(),
                sync.getBodiesSynced(),
                worldCollision.getTickSamples(),
                worldCollision.getStreamingSpaces(),
                worldCollision.getEnsureCalls(),
                worldCollision.getSectionRequests(),
                worldCollision.getSectionsBuilt(),
                worldCollision.getBodyStreamingTargets(),
                stats.bodies,
                stats.dynamicBodies,
                stats.detachedBodies,
                stats.rawBodies,
                stats.worldCollisionBodies,
                stats.awakeDynamicBodies,
                stats.sleepingDynamicBodies,
                stats.belowPlaneBodies,
                stats.belowWorldMinBodies,
                stats.belowVoidBodies,
                stats.minDynamicBodyY(),
                stats.maxDynamicBodyY(),
                health);
        }

        private CompletionStage<Void> contextWait(int ticks) {
            try {
                return context.waitApproxTicksOnWorld(ticks);
            } catch (ReflectiveOperationException e) {
                return CompletableFuture.failedFuture(e);
            }
        }

        private void clearCaseState() {
            removeBenchmarkEntities();
            physics.clearSyntheticVisualInterests();
            physics.clearBodies();
            physics.clearAllSpaces(world.getName());
            runtimeProfiling.reset();
            worldCollisionProfiling.reset();
            worldCollisionProfiling.clearDiagnosticRetainedSections();
        }

        private void restoreSettings() {
            physics.setWorldSettings(previousWorldSettings);
            runtimeProfiling.setEnabled(previousRuntimeProfilingEnabled);
            worldCollisionProfiling.setEnabled(previousWorldCollisionProfilingEnabled);
        }

        private void removeBenchmarkEntities() {
            store.forEachEntityParallel(ATTACHMENT_TYPE,
                (index, archetypeChunk, commandBuffer) -> commandBuffer.removeEntity(
                    archetypeChunk.getReferenceTo(index),
                    RemoveReason.REMOVE));
            store.forEachEntityParallel(CONTROL_SESSION_TYPE,
                (index, archetypeChunk, commandBuffer) -> commandBuffer.removeComponent(
                    archetypeChunk.getReferenceTo(index),
                    CONTROL_SESSION_TYPE));
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
                physics.addBody(space.getId(),
                    body,
                    PhysicsBodyKind.BODY,
                    PhysicsBodyPersistenceMode.RUNTIME_ONLY);
            }
        }
    }

    private static CrucibleTestCase.TestOutcome outcome(@Nonnull List<MatrixReport> reports) {
        List<String> failed = reports.stream()
            .filter(report -> report.health().status() == MatrixStatus.STOP)
            .map(MatrixReport::summary)
            .toList();
        if (!failed.isEmpty()) {
            return CrucibleTestCase.TestOutcome.fail(String.join(" | ", failed));
        }
        return CrucibleTestCase.TestOutcome.pass();
    }

    private static MatrixHealth assessHealth(@Nonnull MatrixCase matrixCase,
        double observedTickRate,
        @Nonnull StepSnapshot step,
        @Nonnull Snapshot worldCollision,
        @Nonnull SpaceStats stats) {
        List<String> stops = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        if (step.getTickSamples() <= 0) {
            stops.add("stepSamples=0");
        }
        if (worldCollision.getTickSamples() <= 0) {
            stops.add("worldCollisionSamples=0");
        }
        if (stats.dynamicBodies != matrixCase.count()) {
            stops.add("dynamicBodies=" + stats.dynamicBodies + "!=" + matrixCase.count());
        }
        if (stats.detachedBodies != matrixCase.count()) {
            stops.add("detachedBodies=" + stats.detachedBodies + "!=" + matrixCase.count());
        }
        int expectedSubsteps = step.getTickSamples() * matrixCase.fixedSubsteps();
        if (step.getTickSamples() > 0 && step.getSubsteps() != expectedSubsteps) {
            stops.add("substeps=" + step.getSubsteps() + "!=" + expectedSubsteps);
        }
        int expectedSnapshots = step.getTickSamples() * matrixCase.count();
        if (step.getTickSamples() > 0 && step.getBodySnapshots() != expectedSnapshots) {
            stops.add("bodySnapshots=" + step.getBodySnapshots() + "!=" + expectedSnapshots);
        }
        if (worldCollision.getStreamingSpaces() > 0) {
            stops.add("worldStreamingSpaces=" + worldCollision.getStreamingSpaces());
        }
        if (worldCollision.getEnsureCalls() > 0) {
            stops.add("worldEnsureCalls=" + worldCollision.getEnsureCalls());
        }
        if (worldCollision.getSectionsBuilt() > 0) {
            stops.add("worldSectionsBuilt=" + worldCollision.getSectionsBuilt());
        }
        if (worldCollision.getBodyStreamingTargets() > 0) {
            stops.add("worldBodyTargets=" + worldCollision.getBodyStreamingTargets());
        }
        if (stats.worldCollisionBodies > 0) {
            stops.add("worldCollisionBodies=" + stats.worldCollisionBodies);
        }
        if (stats.belowWorldMinBodies > 0) {
            stops.add("belowWorldMinBodies=" + stats.belowWorldMinBodies);
        }
        if (stats.belowVoidBodies > 0) {
            stops.add("belowVoidBodies=" + stats.belowVoidBodies);
        }
        int maxBelowGround = Math.max(5, (int) Math.ceil(matrixCase.count() * 0.01));
        if (stats.belowPlaneBodies > maxBelowGround) {
            stops.add("belowPlaneBodies=" + stats.belowPlaneBodies + ">" + maxBelowGround);
        }
        double minTps = configuredDouble(MIN_TPS_PROPERTY, 8.0);
        if (observedTickRate < minTps) {
            stops.add("observedTPS=" + format(observedTickRate) + "<" + format(minTps));
        }
        if (!stops.isEmpty()) {
            return new MatrixHealth(MatrixStatus.STOP, String.join("; ", stops));
        }
        double warnTps = configuredDouble(WARN_TPS_PROPERTY, 15.0);
        if (observedTickRate < warnTps) {
            warnings.add("observedTPS=" + format(observedTickRate) + "<" + format(warnTps));
        }
        if (!warnings.isEmpty()) {
            return new MatrixHealth(MatrixStatus.WARN, String.join("; ", warnings));
        }
        return new MatrixHealth(MatrixStatus.PASS, "within gates");
    }

    private static void logComparison(@Nonnull List<MatrixReport> reports) {
        if (reports.size() < 2) {
            return;
        }
        MatrixReport first = reports.get(0);
        MatrixReport second = reports.get(1);
        LOGGER.at(Level.INFO).log("Crucible Rapier body matrix comparison: %sx=%sms "
                + "%sx=%sms stepRatio=%s snapshotRatio=%s totalRatio=%s worldCounters=%s/%s",
            first.matrixCase().fixedSubsteps(),
            format(first.avgStepMs()),
            second.matrixCase().fixedSubsteps(),
            format(second.avgStepMs()),
            format(ratio(second.avgStepMs(), first.avgStepMs())),
            format(ratio(second.avgSnapshotMs(), first.avgSnapshotMs())),
            format(ratio(second.totalMs(), first.totalMs())),
            first.worldCounterSummary(),
            second.worldCounterSummary());
    }

    private static double ratio(double numerator, double denominator) {
        return denominator > 0.0 ? numerator / denominator : Double.NaN;
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

    private record MatrixPlan(int count,
                              @Nonnull List<Integer> substeps,
                              int warmupTicks,
                              int sampleTicks) {

        private static MatrixPlan fromSystemProperties() {
            return new MatrixPlan(configuredInt(COUNT_PROPERTY, DEFAULT_COUNT, MIN_COUNT, MAX_COUNT),
                configuredSubsteps(),
                configuredInt(WARMUP_TICKS_PROPERTY,
                    DEFAULT_WARMUP_TICKS,
                    MIN_WARMUP_TICKS,
                    MAX_WARMUP_TICKS),
                configuredInt(SAMPLE_TICKS_PROPERTY,
                    DEFAULT_SAMPLE_TICKS,
                    MIN_SAMPLE_TICKS,
                    MAX_SAMPLE_TICKS));
        }

        @Nonnull
        private static List<Integer> configuredSubsteps() {
            String raw = System.getProperty(SUBSTEPS_PROPERTY);
            if (raw == null || raw.isBlank()) {
                return List.of(1, 2);
            }
            List<Integer> substeps = new ArrayList<>();
            for (String token : raw.split(",")) {
                try {
                    int value = Math.clamp(Integer.parseInt(token.trim()),
                        PhysicsWorldSettings.MIN_SIMULATION_STEPS,
                        PhysicsWorldSettings.MAX_SIMULATION_STEPS);
                    if (!substeps.contains(value)) {
                        substeps.add(value);
                    }
                } catch (NumberFormatException ignored) {
                }
            }
            return substeps.isEmpty() ? List.of(1, 2) : List.copyOf(substeps);
        }
    }

    private record MatrixCase(int count, int fixedSubsteps) {
    }

    private record StartedCase(boolean started,
                               @Nullable PhysicsSpace space,
                               @Nonnull String failureMessage) {

        private static StartedCase started(@Nonnull PhysicsSpace space) {
            return new StartedCase(true, space, "");
        }

        private static StartedCase failed(@Nullable String failureMessage) {
            return new StartedCase(false,
                null,
                failureMessage != null ? failureMessage : "unknown startup failure");
        }
    }

    private record MatrixReport(@Nonnull MatrixCase matrixCase,
                                double observedTickRate,
                                double avgStepMs,
                                double avgSnapshotMs,
                                double avgSyncMs,
                                double avgWorldMs,
                                double totalMs,
                                int stepSamples,
                                int substeps,
                                int bodySnapshots,
                                int spatialIndexCells,
                                int syncSamples,
                                int syncInspected,
                                int syncSynced,
                                int worldSamples,
                                int worldStreamingSpaces,
                                int worldEnsureCalls,
                                int worldSectionRequests,
                                int worldSectionsBuilt,
                                int worldBodyTargets,
                                int bodies,
                                int dynamicBodies,
                                int detachedBodies,
                                int rawBodies,
                                int worldCollisionBodies,
                                int awakeDynamicBodies,
                                int sleepingDynamicBodies,
                                int belowPlaneBodies,
                                int belowWorldMinBodies,
                                int belowVoidBodies,
                                double minDynamicBodyY,
                                double maxDynamicBodyY,
                                @Nonnull MatrixHealth health) {

        private static MatrixReport failedPreflight(@Nonnull MatrixCase matrixCase,
            @Nonnull String reason) {
            MatrixHealth health = new MatrixHealth(MatrixStatus.STOP, reason);
            return new MatrixReport(matrixCase,
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
                Double.NaN,
                health);
        }

        private String summary() {
            return "count=" + matrixCase.count()
                + " fixedSubsteps=" + matrixCase.fixedSubsteps()
                + " health=" + health.status()
                + " reason=" + health.reason()
                + " tps=" + format(observedTickRate)
                + " totalMs=" + format(totalMs)
                + " step/snapshot/sync/worldMs=" + format(avgStepMs)
                + "/" + format(avgSnapshotMs)
                + "/" + format(avgSyncMs)
                + "/" + format(avgWorldMs)
                + " step samples/substeps/bodySnapshots/spatialCells=" + stepSamples
                + "/" + substeps
                + "/" + bodySnapshots
                + "/" + spatialIndexCells
                + " sync samples/inspected/synced=" + syncSamples
                + "/" + syncInspected
                + "/" + syncSynced
                + " world samples/streaming/ensure/req/build/bodyTargets="
                + worldSamples
                + "/" + worldStreamingSpaces
                + "/" + worldEnsureCalls
                + "/" + worldSectionRequests
                + "/" + worldSectionsBuilt
                + "/" + worldBodyTargets
                + " bodies total/dynamic/detached/raw/worldCollision=" + bodies
                + "/" + dynamicBodies
                + "/" + detachedBodies
                + "/" + rawBodies
                + "/" + worldCollisionBodies
                + " awake/sleeping=" + awakeDynamicBodies
                + "/" + sleepingDynamicBodies
                + " belowPlane/worldMin/void=" + belowPlaneBodies
                + "/" + belowWorldMinBodies
                + "/" + belowVoidBodies
                + " yMin/yMax=" + formatOptional(minDynamicBodyY)
                + "/" + formatOptional(maxDynamicBodyY);
        }

        private String worldCounterSummary() {
            return worldSamples
                + "/" + worldStreamingSpaces
                + "/" + worldEnsureCalls
                + "/" + worldSectionsBuilt
                + "/" + worldBodyTargets;
        }
    }

    private record MatrixHealth(@Nonnull MatrixStatus status, @Nonnull String reason) {
    }

    private enum MatrixStatus {
        PASS,
        WARN,
        STOP
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
        private int awakeDynamicBodies;
        private int sleepingDynamicBodies;
        private int detachedBodies;
        private int rawBodies;
        private int worldCollisionBodies;
        private int belowPlaneBodies;
        private int belowWorldMinBodies;
        private int belowVoidBodies;
        private double minDynamicBodyY = Double.POSITIVE_INFINITY;
        private double maxDynamicBodyY = Double.NEGATIVE_INFINITY;

        private static SpaceStats collect(@Nonnull PhysicsWorldResource physics,
            @Nonnull PhysicsSpace space) {
            return physics.callOnPhysicsOwner("collect rapier body benchmark space stats",
                () -> collectOnOwner(physics, space));
        }

        private static SpaceStats collectOnOwner(@Nonnull PhysicsWorldResource physics,
            @Nonnull PhysicsSpace space) {
            SpaceStats stats = new SpaceStats();
            PhysicsWorldRuntimeResource runtime = PhysicsWorldRuntimeResource.require(physics);
            WorldVoxelCollisionCache cache = runtime.worldCollisionCache();
            for (PhysicsBody body : space.getBodies()) {
                stats.classify(runtime, cache, space, body);
            }
            return stats;
        }

        private void classify(@Nonnull PhysicsWorldRuntimeResource physics,
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
            PhysicsBodyRegistration registration = physics.getBodyRegistration(body);
            if (registration != null && registration.kind() == PhysicsBodyKind.BODY) {
                if (physics.getBodyAttachments(registration.id()).isEmpty()) {
                    detachedBodies++;
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

        private double minDynamicBodyY() {
            return Double.isFinite(minDynamicBodyY) ? minDynamicBodyY : Double.NaN;
        }

        private double maxDynamicBodyY() {
            return Double.isFinite(maxDynamicBodyY) ? maxDynamicBodyY : Double.NaN;
        }
    }
}
