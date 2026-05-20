package dev.hytalemodding.impulse.examples.commands;

import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.util.MathUtil;
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
import dev.hytalemodding.impulse.api.PhysicsSpace;
import dev.hytalemodding.impulse.api.ShapeType;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.ImpulsePlugin;
import dev.hytalemodding.impulse.core.components.PhysicsBodyAttachmentComponent;
import dev.hytalemodding.impulse.core.components.PhysicsControlSessionComponent;
import dev.hytalemodding.impulse.core.diagnostics.PhysicsEntityDiagnostics;
import dev.hytalemodding.impulse.core.resources.PhysicsBodyKind;
import dev.hytalemodding.impulse.core.resources.PhysicsBodyPersistenceMode;
import dev.hytalemodding.impulse.core.resources.PhysicsRuntimeProfilingResource;
import dev.hytalemodding.impulse.core.resources.PhysicsRuntimeProfilingResource.StepSnapshot;
import dev.hytalemodding.impulse.core.resources.PhysicsRuntimeProfilingResource.SyncSnapshot;
import dev.hytalemodding.impulse.core.resources.PhysicsSpaceSettings;
import dev.hytalemodding.impulse.core.resources.PhysicsStepMode;
import dev.hytalemodding.impulse.core.resources.PhysicsWorldResource;
import dev.hytalemodding.impulse.core.resources.WorldCollisionProfilingResource;
import dev.hytalemodding.impulse.core.resources.WorldCollisionProfilingResource.Snapshot;
import dev.hytalemodding.impulse.core.voxel.WorldVoxelCollisionCache;
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

        if (request.mode().requiresBenchmarkChunks()) {
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

        startBenchmark(ctx, world, store, request);
    }

    private void startBenchmark(@Nonnull CommandContext ctx,
        @Nonnull World world,
        @Nonnull Store<EntityStore> store,
        @Nonnull BenchmarkRequest request) {
        PhysicsWorldResource physics = store.getResource(PhysicsWorldResource.getResourceType());
        PhysicsRuntimeProfilingResource runtimeProfiling = store.getResource(
            PhysicsRuntimeProfilingResource.getResourceType());
        WorldCollisionProfilingResource worldCollisionProfiling = store.getResource(
            WorldCollisionProfilingResource.getResourceType());

        releaseRetainedEntityBenchmarkChunks();
        int removedEntities = clearBenchmarkState(store, physics, world);
        BenchmarkEntityRemovalDiagnosticsSystem.reset();
        physics.setStepMode(PhysicsStepMode.FIXED);
        physics.setSimulationSteps(1);
        physics.setMaxStepDt(TARGET_MAX_STEP_DT);
        PhysicsSpaceSettings settings = PhysicsSpaceSettings.defaults();
        settings.setSolverIterations(request.solverIterations());
        settings.setInternalPgsIterations(request.pgsIterations());
        settings.setStabilizationIterations(request.stabilizationIterations());
        settings.setMinIslandSize(request.minIslandSize());
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
        PhysicsBody ground = space.createStaticPlane(GROUND_Y);
        space.addBody(ground);

        int retainedChunks = 0;
        if (request.mode().requiresBenchmarkChunks()) {
            BenchmarkChunks requiredChunks = benchmarkChunks(request);
            retainedChunks = retainEntityBenchmarkChunks(world, requiredChunks);
            if (retainedChunks != requiredChunks.columns().size()) {
                ctx.sender().sendMessage(Message.raw("Impulse auto benchmark warning: retained "
                    + retainedChunks
                    + " of "
                    + requiredChunks.columns().size()
                    + " required entity chunk column(s)."));
            }
            scheduleEntityChunkKeepAlive(world, request.sampleTicks());
        }

        PhysicsEntityDiagnostics.Snapshot baselineEntityDiagnostics =
            PhysicsEntityDiagnostics.collect(store);

        runtimeProfiling.reset();
        worldCollisionProfiling.reset();
        runtimeProfiling.setEnabled(true);
        worldCollisionProfiling.setEnabled(true);

        int spawned = spawnBenchmark(store, physics, space, request);
        PhysicsEntityDiagnostics.Snapshot entityDiagnostics = PhysicsEntityDiagnostics.collect(store);
        long startedNanos = System.nanoTime();
        ctx.sender().sendMessage(Message.raw("Impulse auto benchmark started: mode="
            + request.mode().serialized()
            + " count=" + spawned
            + " livePhysicsEntities=" + entityDiagnostics.physicsBodyEntities()
            + " sampleTicks=" + request.sampleTicks()
            + " backend=" + space.getBackendId().value()
            + " space=" + space.getId().value()
            + " worldCollision=none"
            + " solverIterations=" + request.solverIterations()
            + " pgsIterations=" + request.pgsIterations()
            + " stabilizationIterations=" + request.stabilizationIterations()
            + " minIslandSize=" + request.minIslandSize()
            + " removedEntities=" + removedEntities
            + " baselineHytaleEntities=(" + baselineEntityDiagnostics.hytaleSummary() + ")"
            + (request.mode().simulatesViewer() ? " simulatedViewer=true" : "")
            + (request.mode() == BenchmarkMode.DETACHED_VIEW
                ? " syntheticChunkRetention=false" : "")
            + (request.mode() == BenchmarkMode.DETACHED_VIEW_CHUNKS
                ? " syntheticChunkRetention=true" : "")
            + (request.mode().requiresBenchmarkChunks() ? " retainedColumns=" + retainedChunks : "")
            + ". Report will print after about " + request.sampleSeconds() + "s."));
        if (request.mode() == BenchmarkMode.ENTITY) {
            scheduleEntityLifecycleProbe(ctx, world, store, "after 1 tick");
        }

        scheduleReport(ctx, world, store, space.getId(), request, startedNanos);
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
            startBenchmark(ctx, world, store, request);
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
            int chunkX = ChunkUtil.chunkCoordinate(position.x);
            int chunkY = ChunkUtil.chunkCoordinate(position.y);
            int chunkZ = ChunkUtil.chunkCoordinate(position.z);
            columns.add(ChunkUtil.indexChunk(chunkX, chunkZ));
            sections.add(new ChunkSection(chunkX, chunkY, chunkZ));
        }
        if (request.mode() == BenchmarkMode.DETACHED_VIEW_CHUNKS) {
            addDetachedViewChunks(columns, sections, layout);
        }
        return new BenchmarkChunks(columns, sections);
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
            case RAW -> spawnRaw(space, layout, request.count());
            case DETACHED, DETACHED_VIEW, DETACHED_VIEW_CHUNKS ->
                spawnDetached(physics, space, layout, request.count());
            case ENTITY -> spawnEntities(store, physics, space, layout, request.count());
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

    private static int spawnRaw(@Nonnull PhysicsSpace space,
        @Nonnull BenchmarkLayout layout,
        int count) {
        for (int i = 0; i < count; i++) {
            PhysicsBody body = createBenchmarkBody(space);
            Vector3d position = layout.position(i);
            body.setPosition((float) position.x, (float) position.y, (float) position.z);
            space.addBody(body);
        }
        return count;
    }

    private static int spawnDetached(@Nonnull PhysicsWorldResource physics,
        @Nonnull PhysicsSpace space,
        @Nonnull BenchmarkLayout layout,
        int count) {
        for (int i = 0; i < count; i++) {
            PhysicsBody body = createBenchmarkBody(space);
            Vector3d position = layout.position(i);
            body.setPosition((float) position.x, (float) position.y, (float) position.z);
            physics.addBody(space.getId(),
                body,
                PhysicsBodyKind.BODY,
                PhysicsBodyPersistenceMode.RUNTIME_ONLY);
        }
        return count;
    }

    private static int spawnEntities(@Nonnull Store<EntityStore> store,
        @Nonnull PhysicsWorldResource physics,
        @Nonnull PhysicsSpace space,
        @Nonnull BenchmarkLayout layout,
        int count) {
        TimeResource time = store.getResource(TimeResource.getResourceType());
        for (int i = 0; i < count; i++) {
            PhysicsBody body = createBenchmarkBody(space);
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
    private static PhysicsBody createBenchmarkBody(@Nonnull PhysicsSpace space) {
        PhysicsBody body = space.createBox(0.48f, 0.48f, 0.48f, 1.0f);
        body.setFriction(0.45f);
        body.setRestitution(0.0f);
        body.setDamping(0.02f, 0.25f);
        return body;
    }

    private static void scheduleReport(@Nonnull CommandContext ctx,
        @Nonnull World world,
        @Nonnull Store<EntityStore> store,
        @Nonnull SpaceId spaceId,
        @Nonnull BenchmarkRequest request,
        long startedNanos) {
        long delayMillis = Math.max(1L, request.sampleTicks()) * MILLIS_PER_TICK;
        REPORT_EXECUTOR.schedule(() -> {
            try {
                world.execute(() -> sendReport(ctx, store, spaceId, request, startedNanos));
            } catch (Exception e) {
                ctx.sender().sendMessage(Message.raw("Impulse auto benchmark report failed: "
                    + e.getMessage()));
            }
        }, delayMillis, TimeUnit.MILLISECONDS);
    }

    private static void sendReport(@Nonnull CommandContext ctx,
        @Nonnull Store<EntityStore> store,
        @Nonnull SpaceId spaceId,
        @Nonnull BenchmarkRequest request,
        long startedNanos) {
        PhysicsWorldResource physics = store.getResource(PhysicsWorldResource.getResourceType());
        PhysicsRuntimeProfilingResource runtimeProfiling = store.getResource(
            PhysicsRuntimeProfilingResource.getResourceType());
        WorldCollisionProfilingResource worldCollisionProfiling = store.getResource(
            WorldCollisionProfilingResource.getResourceType());
        PhysicsSpace space = physics.getSpace(spaceId);
        if (space == null) {
            ctx.sender().sendMessage(Message.raw("Impulse auto benchmark report: space "
                + spaceId.value() + " no longer exists."));
            if (request.mode().requiresBenchmarkChunks()) {
                releaseRetainedEntityBenchmarkChunks();
            }
            return;
        }

        StepSnapshot step = runtimeProfiling.getCumulativeStep();
        SyncSnapshot sync = runtimeProfiling.getCumulativeSync();
        Snapshot worldCollision = worldCollisionProfiling.getCumulativeSnapshot();
        SpaceStats stats = SpaceStats.collect(physics, space);
        PhysicsEntityDiagnostics.Snapshot entityDiagnostics = PhysicsEntityDiagnostics.collect(store);
        double elapsedSeconds = Math.max(0.001, (System.nanoTime() - startedNanos) / 1_000_000_000.0);
        double observedTickRate = step.getTickSamples() / elapsedSeconds;
        double avgStepMs = averageMillis(step.getTickNanos(), step.getTickSamples());
        double avgSyncMs = averageMillis(sync.getTickNanos(), sync.getTickSamples());
        double avgWorldMs = averageMillis(worldCollision.getTickNanos(), worldCollision.getTickSamples());
        double totalProfiledMs = avgStepMs + avgSyncMs + avgWorldMs;

        ctx.sender().sendMessage(Message.raw("Impulse auto benchmark report: mode="
            + request.mode().serialized()
            + " count=" + request.count()
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
            + " worldCollision=" + stats.worldCollisionBodies
            + " contacts=" + stats.contacts));
        ctx.sender().sendMessage(Message.raw("Body bounds: yMin=" + format(stats.minDynamicBodyY())
            + " yMax=" + format(stats.maxDynamicBodyY())
            + " belowPlane=" + stats.belowPlaneBodies
            + " belowWorldMin=" + stats.belowWorldMinBodies
            + " belowVoid=" + stats.belowVoidBodies));
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
            + " bodyTargets=" + worldCollision.getBodyStreamingTargets()));
        ctx.sender().sendMessage(Message.raw("Entity removal diagnostics: "
            + BenchmarkEntityRemovalDiagnosticsSystem.snapshot()));
        if (request.mode().requiresBenchmarkChunks()) {
            releaseRetainedEntityBenchmarkChunks();
        }
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
        return new BenchmarkRequest(mode,
            count,
            sampleTicks,
            solverIterations,
            pgsIterations,
            stabilizationIterations,
            minIslandSize);
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

    private record BenchmarkRequest(BenchmarkMode mode,
        int count,
        int sampleTicks,
        int solverIterations,
        int pgsIterations,
        int stabilizationIterations,
        int minIslandSize) {

        private int sampleSeconds() {
            return Math.max(1, (int) Math.ceil(sampleTicks * MILLIS_PER_TICK / 1000.0));
        }
    }

    private record BenchmarkChunks(@Nonnull LongSet columns, @Nonnull Set<ChunkSection> sections) {

        private int size() {
            return columns.size() + sections.size();
        }
    }

    private record ChunkSection(int x, int y, int z) {
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
        private int contacts;
        private double minDynamicBodyY = Double.POSITIVE_INFINITY;
        private double maxDynamicBodyY = Double.NEGATIVE_INFINITY;
        private int belowPlaneBodies;
        private int belowWorldMinBodies;
        private int belowVoidBodies;

        @Nonnull
        private static SpaceStats collect(@Nonnull PhysicsWorldResource physics,
            @Nonnull PhysicsSpace space) {
            SpaceStats stats = new SpaceStats();
            WorldVoxelCollisionCache cache = physics.getWorldVoxelCollisionCache();
            List<PhysicsBody> bodies = new ArrayList<>(space.getBodies());
            for (PhysicsBody body : bodies) {
                stats.classify(physics, cache, space, body);
            }
            stats.contacts = space.getContacts().size();
            return stats;
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

        private double minDynamicBodyY() {
            return Double.isFinite(minDynamicBodyY) ? minDynamicBodyY : 0.0;
        }

        private double maxDynamicBodyY() {
            return Double.isFinite(maxDynamicBodyY) ? maxDynamicBodyY : 0.0;
        }
    }
}
