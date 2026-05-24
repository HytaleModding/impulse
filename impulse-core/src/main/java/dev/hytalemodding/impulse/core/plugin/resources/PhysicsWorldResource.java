package dev.hytalemodding.impulse.core.plugin.resources;

import com.hypixel.hytale.component.Resource;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.api.BackendId;
import dev.hytalemodding.impulse.api.Impulse;
import dev.hytalemodding.impulse.api.PhysicsActivationTuning;
import dev.hytalemodding.impulse.api.PhysicsBody;
import dev.hytalemodding.impulse.api.PhysicsBodySnapshot;
import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.PhysicsJoint;
import dev.hytalemodding.impulse.api.PhysicsSpace;
import dev.hytalemodding.impulse.api.PhysicsSolverTuning;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.ImpulsePlugin;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyId;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyKind;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyPersistenceMode;
import dev.hytalemodding.impulse.core.plugin.collision.WorldCollisionBuildStats;
import dev.hytalemodding.impulse.core.plugin.collision.WorldCollisionPrewarmStats;
import dev.hytalemodding.impulse.core.plugin.collision.WorldCollisionStats;
import dev.hytalemodding.impulse.core.plugin.execution.PhysicsMutationHandle;
import dev.hytalemodding.impulse.core.plugin.execution.PhysicsOwnerCallable;
import dev.hytalemodding.impulse.core.plugin.execution.PhysicsOwnerHandle;
import dev.hytalemodding.impulse.core.plugin.execution.PhysicsOwnerMutation;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsSpaceSettings;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsStepMode;
import dev.hytalemodding.impulse.core.plugin.snapshot.PublishedPhysicsBodySnapshot;
import dev.hytalemodding.impulse.core.plugin.snapshot.PublishedPhysicsSnapshotFrame;
import dev.hytalemodding.impulse.core.plugin.snapshot.PublishedPhysicsSpaceFrame;
import dev.hytalemodding.impulse.core.internal.resources.body.PhysicsBodyRegistry;
import dev.hytalemodding.impulse.core.internal.resources.body.PhysicsBodyRuntimeState;
import dev.hytalemodding.impulse.core.internal.resources.body.PhysicsBodySnapshotStore;
import dev.hytalemodding.impulse.core.internal.resources.worker.PhysicsWorldWorkerResource;
import dev.hytalemodding.impulse.core.internal.voxel.WorldCollisionCacheAccess;
import dev.hytalemodding.impulse.core.internal.voxel.WorldVoxelCollisionCache;
import dev.hytalemodding.impulse.core.internal.worker.PhysicsWorkerAccess;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.logging.Level;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import lombok.Getter;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;

/**
 * ECS resource that holds the physics spaces for a world.
 *
 * <p>Spaces are created explicitly by the consumer via {@link #createSpace}.
 * No space is created implicitly; the default space is opt-in and set by
 * the consumer at creation time or via {@link #setDefaultSpaceId}.</p>
 */
public class PhysicsWorldResource implements Resource<EntityStore> {

    private static final HytaleLogger LOGGER = HytaleLogger.get("Impulse");
    public static final int MIN_SIMULATION_STEPS = 1;
    public static final int MAX_SIMULATION_STEPS = 16;
    public static final float DEFAULT_MAX_STEP_DT = 1f / 30f;

    private final Int2ObjectMap<PhysicsSpace> spaces = new Int2ObjectOpenHashMap<>();

    /**
     * Per-space settings (world collision mode, radius, TTL, etc.). Keyed by space id value.
     */
    private final Int2ObjectMap<PhysicsSpaceSettings> spaceSettings = new Int2ObjectOpenHashMap<>();

    private final PhysicsBodyRegistry bodyRegistry = new PhysicsBodyRegistry(this::clearBodySyncState);
    private final Object2IntOpenHashMap<PhysicsBodyId> pendingBodyCreations =
        new Object2IntOpenHashMap<>();

    private final WorldVoxelCollisionCache worldVoxelCollisionCache = new WorldVoxelCollisionCache();

    /**
     * The default space for this world, if one has been designated.
     * This is an optional convenience -- integrators can manage multiple spaces
     * without designating a default.
     */
    @Nullable
    private SpaceId defaultSpaceId;
    @Getter
    private int simulationSteps = MIN_SIMULATION_STEPS;

    @Nonnull
    private PhysicsStepMode stepMode = PhysicsStepMode.PROGRESSIVE_REFINEMENT;

    @Getter
    private float maxStepDt = DEFAULT_MAX_STEP_DT;

    private final PhysicsBodyRuntimeState runtimeState = new PhysicsBodyRuntimeState();
    private final PhysicsBodySnapshotStore bodySnapshots = new PhysicsBodySnapshotStore();
    private final PhysicsBodySnapshotStore workerBodySnapshots = new PhysicsBodySnapshotStore();
    private final AtomicLong worldEpoch = new AtomicLong();
    private final AtomicLong snapshotFrameEpoch = new AtomicLong();
    private final AtomicLong visualInterestTick = new AtomicLong();
    @Nonnull
    private final AtomicReference<PublishedPhysicsSnapshotFrame> latestPublishedFrame =
        new AtomicReference<>(PublishedPhysicsSnapshotFrame.empty(0L, 0L));
    @Getter
    private volatile long latestSnapshotAppliedNanos;
    private final AtomicReference<PhysicsWorldWorkerResource> workerResource = new AtomicReference<>();

    public PhysicsWorldResource() {
    }

    public void attachWorkerResource(@Nonnull PhysicsOwnerHandle workerResource) {
        this.workerResource.set(requireWorkerResource(workerResource));
    }

    public void detachWorkerResource(@Nonnull PhysicsOwnerHandle workerResource) {
        PhysicsWorldWorkerResource worker = requireWorkerResource(workerResource);
        this.workerResource.compareAndSet(worker, null);
    }

    @Nonnull
    private static PhysicsWorldWorkerResource requireWorkerResource(
        @Nonnull PhysicsOwnerHandle workerResource) {
        Objects.requireNonNull(workerResource, "workerResource");
        if (workerResource instanceof PhysicsWorldWorkerResource worker) {
            return worker;
        }
        throw new IllegalArgumentException("Unsupported physics owner handle "
            + workerResource.getClass().getName());
    }

    public boolean canAccessLiveBackendDirectly() {
        PhysicsWorldWorkerResource worker = workerResource.get();
        return worker == null || worker.isWorkerThread();
    }

    /**
     * Run a live-backend mutation on the current physics owner.
     *
     * <p>In inline execution this runs immediately on the caller thread. In worker execution this
     * submits to the world's physics worker and blocks until the mutation completes. The callback
     * may access live {@link PhysicsSpace} and {@link PhysicsBody} objects, but must not access
     * Hytale ECS store state unless the caller owns that access.</p>
     */
    public void runOnPhysicsOwner(@Nonnull String operation,
        @Nonnull PhysicsOwnerMutation mutation) {
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(mutation, "mutation");
        PhysicsWorldWorkerResource worker = workerResource.get();
        if (worker == null || worker.isWorkerThread()) {
            runDirect(operation, mutation);
            return;
        }
        PhysicsWorkerAccess.run(worker, operation, mutation::run);
    }

    @Nonnull
    public PhysicsMutationHandle<Void> enqueuePhysicsMutation(@Nonnull String operation,
        @Nonnull PhysicsOwnerMutation mutation) {
        return enqueuePhysicsMutation(operation, null, mutation);
    }

    /**
     * Queue a live-backend mutation on the current physics owner without blocking the caller.
     *
     * <p>The returned handle completes when the mutation finishes or fails. The {@code value}
     * parameter lets callers reserve and expose a logical id before the queued mutation runs.</p>
     */
    @Nonnull
    public <T> PhysicsMutationHandle<T> enqueuePhysicsMutation(@Nonnull String operation,
        @Nullable T value,
        @Nonnull PhysicsOwnerMutation mutation) {
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(mutation, "mutation");
        PhysicsWorldWorkerResource worker = workerResource.get();
        if (worker == null || worker.isWorkerThread()) {
            return runDirectAsync(operation, value, mutation);
        }
        return PhysicsWorkerAccess.runAsync(worker, operation, value, mutation::run);
    }

    /**
     * Run a live-backend read on the current physics owner and return its value.
     *
     * <p>Prefer published snapshots for ordinary gameplay reads. Use this for explicit live-backend
     * operations that cannot be expressed through snapshots or the higher-level resource methods.</p>
     */
    @Nonnull
    public <T> T callOnPhysicsOwner(@Nonnull String operation,
        @Nonnull PhysicsOwnerCallable<T> callable) {
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(callable, "callable");
        PhysicsWorldWorkerResource worker = workerResource.get();
        if (worker == null || worker.isWorkerThread()) {
            try {
                return callable.call();
            } catch (RuntimeException exception) {
                throw exception;
            } catch (Exception exception) {
                throw new IllegalStateException("Physics operation " + operation + " failed",
                    exception);
            }
        }
        return PhysicsWorkerAccess.call(worker, operation, callable::call);
    }

    private void runDirect(@Nonnull String operation,
        @Nonnull PhysicsOwnerMutation mutation) {
        try {
            mutation.run();
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Physics operation " + operation + " failed",
                exception);
        }
    }

    @Nonnull
    private <T> PhysicsMutationHandle<T> runDirectAsync(@Nonnull String operation,
        @Nullable T value,
        @Nonnull PhysicsOwnerMutation mutation) {
        try {
            mutation.run();
            return PhysicsMutationHandle.completed(operation, value);
        } catch (Throwable throwable) {
            return PhysicsMutationHandle.failed(operation, value, throwable);
        }
    }

    @Nullable
    public SpaceId getDefaultSpaceId() {
        return defaultSpaceId;
    }

    @Nonnull
    public SpaceId requireDefaultSpaceId() {
        if (defaultSpaceId == null) {
            throw new IllegalStateException("No default physics space is configured");
        }
        return defaultSpaceId;
    }

    @Nullable
    public PhysicsSpace getDefaultSpace() {
        if (defaultSpaceId == null) {
            return null;
        }
        return getSpace(defaultSpaceId);
    }

    @Nonnull
    public PhysicsSpace requireDefaultSpace() {
        SpaceId spaceId = requireDefaultSpaceId();
        PhysicsSpace space = getSpace(spaceId);
        if (space == null) {
            throw new IllegalStateException("Default physics space id=" + spaceId
                + " is not registered");
        }
        return space;
    }

    public void setDefaultSpaceId(@Nullable SpaceId defaultSpaceId) {
        runOnPhysicsOwner("set default physics space", () -> setDefaultSpaceIdDirect(defaultSpaceId));
    }

    @Nonnull
    public PhysicsMutationHandle<SpaceId> setDefaultSpaceIdAsync(@Nullable SpaceId defaultSpaceId) {
        return enqueuePhysicsMutation("set default physics space",
            defaultSpaceId,
            () -> setDefaultSpaceIdDirect(defaultSpaceId));
    }

    private void setDefaultSpaceIdDirect(@Nullable SpaceId defaultSpaceId) {
        if (defaultSpaceId != null && !spaces.containsKey(defaultSpaceId.value())) {
            throw new IllegalArgumentException("Physics space id=" + defaultSpaceId
                + " is not registered");
        }
        this.defaultSpaceId = defaultSpaceId;
    }

    @Nonnull
    public PhysicsStepMode getStepMode() {
        return stepMode;
    }

    public void setStepMode(@Nonnull PhysicsStepMode stepMode) {
        runOnPhysicsOwner("set physics step mode", () -> setStepModeDirect(stepMode));
    }

    @Nonnull
    public PhysicsMutationHandle<Void> setStepModeAsync(@Nonnull PhysicsStepMode stepMode) {
        return enqueuePhysicsMutation("set physics step mode", () -> setStepModeDirect(stepMode));
    }

    private void setStepModeDirect(@Nonnull PhysicsStepMode stepMode) {
        validateStepModeSupported(stepMode);
        this.stepMode = stepMode;
    }

    public void setMaxStepDt(float maxStepDt) {
        runOnPhysicsOwner("set max physics step dt", () -> this.maxStepDt = maxStepDt);
    }

    @Nonnull
    public PhysicsMutationHandle<Void> setMaxStepDtAsync(float maxStepDt) {
        return enqueuePhysicsMutation("set max physics step dt", () -> this.maxStepDt = maxStepDt);
    }

    @Nonnull
    public PhysicsSpace createSpace(@Nonnull BackendId backendId) {
        return createSpace(backendId, "<unknown>", PhysicsSpaceSettings.defaults(), false);
    }

    @Nonnull
    public PhysicsSpace createSpace(@Nonnull BackendId backendId, @Nonnull String worldName) {
        return createSpace(backendId, worldName, PhysicsSpaceSettings.defaults(), false);
    }

    /**
     * Creates a new physics space with the given backend and settings.
     *
     * @param makeDefault if true, this space becomes the default space
     */
    @Nonnull
    public PhysicsSpace createSpace(@Nonnull BackendId backendId,
        @Nonnull String worldName,
        @Nonnull PhysicsSpaceSettings settings,
        boolean makeDefault) {
        return createSpace(backendId, SpaceId.next(), worldName, settings, makeDefault);
    }

    /**
     * Creates a new physics space with the given backend, explicit logical id, and settings.
     *
     * @param makeDefault if true, this space becomes the default space
     */
    @Nonnull
    public PhysicsSpace createSpace(@Nonnull BackendId backendId,
        @Nonnull SpaceId spaceId,
        @Nonnull String worldName,
        @Nonnull PhysicsSpaceSettings settings,
        boolean makeDefault) {
        return callOnPhysicsOwner("create physics space",
            () -> createSpaceDirect(backendId, spaceId, worldName, settings, makeDefault));
    }

    @Nonnull
    public PhysicsMutationHandle<SpaceId> createSpaceAsync(@Nonnull BackendId backendId,
        @Nonnull String worldName,
        @Nonnull PhysicsSpaceSettings settings,
        boolean makeDefault) {
        SpaceId spaceId = SpaceId.next();
        return createSpaceAsync(backendId, spaceId, worldName, settings, makeDefault);
    }

    @Nonnull
    public PhysicsMutationHandle<SpaceId> createSpaceAsync(@Nonnull BackendId backendId,
        @Nonnull SpaceId spaceId,
        @Nonnull String worldName,
        @Nonnull PhysicsSpaceSettings settings,
        boolean makeDefault) {
        return enqueuePhysicsMutation("create physics space",
            spaceId,
            () -> createSpaceDirect(backendId, spaceId, worldName, settings, makeDefault));
    }

    @Nonnull
    private PhysicsSpace createSpaceDirect(@Nonnull BackendId backendId,
        @Nonnull SpaceId spaceId,
        @Nonnull String worldName,
        @Nonnull PhysicsSpaceSettings settings,
        boolean makeDefault) {
        if (spaces.containsKey(spaceId.value())) {
            throw new IllegalArgumentException("Physics space id=" + spaceId + " is already registered");
        }
        SpaceId.reserveAtLeast(spaceId.value());

        LOGGER.at(Level.FINE).log(
            "World %s creating physics space using backend %s collision=%s",
            worldName,
            backendId,
            settings.getWorldCollisionMode());

        PhysicsSpace space = Impulse.createSpace(backendId, spaceId);
        try {
            validateSpaceCompatibleWithStepMode(space, stepMode);
            applySolverTuning(space, settings);
        } catch (RuntimeException exception) {
            closeSpaceQuietly(space, worldName, "discarding failed physics space");
            throw exception;
        }
        spaces.put(space.getId().value(), space);
        spaceSettings.put(space.getId().value(), new PhysicsSpaceSettings(settings));
        if (makeDefault) {
            defaultSpaceId = space.getId();
        }
        markWorldChanged();

        LOGGER.at(Level.FINE).log(
            "World %s created physics space id=%s backend=%s collision=%s",
            worldName,
            space.getId(),
            space.getBackendId(),
            settings.getWorldCollisionMode());
        return space;
    }

    @Nullable
    public PhysicsSpace getSpace(@Nonnull SpaceId spaceId) {
        return spaces.get(spaceId.value());
    }

    @Nonnull
    private PhysicsSpace requireSpace(@Nonnull SpaceId spaceId) {
        PhysicsSpace space = getSpace(spaceId);
        if (space == null) {
            throw new IllegalArgumentException("Physics space id=" + spaceId + " is not registered");
        }
        return space;
    }

    @Nonnull
    public Collection<PhysicsSpace> getSpaces() {
        return new ArrayList<>(spaces.values());
    }

    public int getSpaceCount() {
        return spaces.size();
    }

    /**
     * Iterate spaces without allocating a snapshot collection.
     * Use this from tick systems that do not mutate the space map while iterating.
     */
    @Nonnull
    public Iterable<PhysicsSpace> iterateSpaces() {
        return spaces.values();
    }

    public int refreshBodySnapshots() {
        return callOnPhysicsOwner("refresh physics body snapshots", () -> {
            PublishedPhysicsSnapshotFrame frame = capturePublishedSnapshotFrameDirect(0L,
                0L,
                PublishedPhysicsSnapshotFrame.Status.COMPLETE,
                0L,
                false);
            return applyPublishedSnapshotFrame(frame);
        });
    }

    @Nonnull
    public PhysicsBodySnapshot getBodySnapshot(@Nonnull PhysicsBodyId bodyId) {
        PhysicsBodySnapshot snapshot = bodySnapshots.get(bodyId);
        if (snapshot != null) {
            return snapshot;
        }
        return callOnPhysicsOwner("refresh missing physics body snapshot",
            () -> getBodySnapshotDirect(bodyId));
    }

    @Nonnull
    private PhysicsBodySnapshot getBodySnapshotDirect(@Nonnull PhysicsBodyId bodyId) {
        PhysicsBodySnapshot snapshot = bodySnapshots.get(bodyId);
        if (snapshot != null) {
            return snapshot;
        }
        BodyRegistration registration = requireBodyRegistration(bodyId);
        PhysicsBody body = registration.body();
        snapshot = PhysicsBodySnapshot.from(body);
        bodySnapshots.put(bodyId, snapshot, registration.spaceId(), registration);
        workerBodySnapshots.put(bodyId, snapshot, registration.spaceId(), registration);
        return snapshot;
    }

    /**
     * Captures an immutable snapshot frame on the physics owner thread.
     *
     * <p>The generated {@code frameEpoch} and current {@code worldEpoch} govern
     * publication ordering and stale-frame rejection. {@code stepSequence} and
     * {@code serverTick} are copied through as external correlation metadata.</p>
     */
    @Nonnull
    public PublishedPhysicsSnapshotFrame capturePublishedSnapshotFrame(long stepSequence,
        long serverTick,
        @Nonnull PublishedPhysicsSnapshotFrame.Status status,
        long stepNanos,
        boolean profilingEnabled) {
        return callOnPhysicsOwner("capture published physics snapshot frame",
            () -> capturePublishedSnapshotFrameDirect(stepSequence,
                serverTick,
                status,
                stepNanos,
                profilingEnabled));
    }

    @Nonnull
    private PublishedPhysicsSnapshotFrame capturePublishedSnapshotFrameDirect(long stepSequence,
        long serverTick,
        @Nonnull PublishedPhysicsSnapshotFrame.Status status,
        long stepNanos,
        boolean profilingEnabled) {

        long frameEpoch = snapshotFrameEpoch.incrementAndGet();
        long frameWorldEpoch = worldEpoch.get();
        long snapshotStartNanos = profilingEnabled ? System.nanoTime() : 0L;
        workerBodySnapshots.refresh(spaces.values(), bodyRegistry);
        int spatialIndexCellCount = workerBodySnapshots.cellCount();

        List<PublishedPhysicsSpaceFrame> spaceFrames = new ArrayList<>(spaces.size());

        for (PhysicsSpace space : spaces.values()) {
            SpaceId spaceId = space.getId();
            List<PublishedPhysicsBodySnapshot> bodyFrames = new ArrayList<>(
                workerBodySnapshots.bodyCount(spaceId));
            workerBodySnapshots.forEach(spaceId, entry -> bodyFrames.add(
                PublishedPhysicsBodySnapshot.from(entry.bodyId(),
                    entry.spaceId(),
                    frameEpoch,
                    frameWorldEpoch,
                    frameWorldEpoch,
                    frameWorldEpoch,
                    entry.registration().kind(),
                    entry.registration().persistenceMode(),
                    entry.snapshot())));
            spaceFrames.add(new PublishedPhysicsSpaceFrame(spaceId,
                frameEpoch,
                frameWorldEpoch,
                frameWorldEpoch,
                bodyFrames));
        }
        long snapshotNanos = profilingEnabled ? System.nanoTime() - snapshotStartNanos : 0L;
        PublishedPhysicsSnapshotFrame frame = new PublishedPhysicsSnapshotFrame(frameEpoch,
            frameWorldEpoch,
            stepSequence,
            serverTick,
            status,
            spatialIndexCellCount,
            stepNanos,
            snapshotNanos,
            spaceFrames);
        publishLatestFrameIfWorldCurrent(frame);
        return frame;
    }

    public int applyPublishedSnapshotFrame(@Nonnull PublishedPhysicsSnapshotFrame frame) {
        Objects.requireNonNull(frame, "frame");
        if (frame.worldEpoch() != worldEpoch.get()) {
            return 0;
        }

        bodySnapshots.clear();
        int applied = 0;
        for (PublishedPhysicsSpaceFrame spaceFrame : frame.spaces()) {
            for (PublishedPhysicsBodySnapshot bodyFrame : spaceFrame.bodies()) {
                BodyRegistration registration = bodyRegistry.getRegistration(bodyFrame.bodyId());
                if (registration == null || !registration.spaceId().equals(bodyFrame.spaceId())) {
                    continue;
                }
                PhysicsBodySnapshot snapshot = new PhysicsBodySnapshot(registration.body(),
                    bodyFrame.position(),
                    bodyFrame.rotation(),
                    bodyFrame.linearVelocity(),
                    bodyFrame.angularVelocity(),
                    bodyFrame.bodyType(),
                    bodyFrame.sleeping(),
                    bodyFrame.sensor(),
                    bodyFrame.centerOfMassOffsetY());
                bodySnapshots.put(bodyFrame.bodyId(), snapshot, bodyFrame.spaceId(), registration);
                applied++;
            }
        }
        latestSnapshotAppliedNanos = System.nanoTime();
        return applied;
    }

    @Nonnull
    public PublishedPhysicsSnapshotFrame getLatestPublishedFrame() {
        return latestPublishedFrame.get();
    }

    @Nonnull
    public PhysicsBodySnapshot getBodySnapshot(@Nonnull PhysicsBody body) {
        PhysicsBodyId bodyId = getBodyId(body);
        if (bodyId == null) {
            return callOnPhysicsOwner("read unregistered physics body snapshot",
                () -> PhysicsBodySnapshot.from(body));
        }
        return getBodySnapshot(bodyId);
    }

    public int getBodySnapshotCount() {
        return bodySnapshots.bodyCount();
    }

    public int getBodySnapshotCount(@Nonnull SpaceId spaceId) {
        return bodySnapshots.bodyCount(spaceId);
    }

    public int getBodySnapshotCellCount() {
        return bodySnapshots.cellCount();
    }

    /**
     * Internal bridge for Impulse systems that own streamed terrain cache behavior.
     */
    @Nonnull
    public Object internalWorldCollisionState(
        @Nonnull WorldCollisionCacheAccess access) {
        Objects.requireNonNull(access, "access");
        return worldVoxelCollisionCache;
    }

    @Nonnull
    public WorldCollisionBuildStats rebuildWorldCollisionAround(@Nonnull World world,
        @Nonnull SpaceId spaceId,
        @Nonnull Vector3d center,
        int radius) {
        return callOnPhysicsOwner("rebuild world collision", () -> {
            PhysicsSpace space = requireSpace(spaceId);
            return worldCollisionStats(worldVoxelCollisionCache.rebuildAround(world,
                space,
                center,
                radius));
        });
    }

    @Nonnull
    public WorldCollisionPrewarmStats ensureWorldCollisionAround(@Nonnull World world,
        @Nonnull SpaceId spaceId,
        @Nonnull Iterable<Vector3d> centers,
        int radius,
        long tick) {
        Objects.requireNonNull(centers, "centers");
        return callOnPhysicsOwner("ensure world collision", () -> {
            PhysicsSpace space = requireSpace(spaceId);
            LongSet visitedSections = new LongOpenHashSet();
            WorldVoxelCollisionCache.BuildStats total = WorldVoxelCollisionCache.BuildStats.empty();
            for (Vector3d center : centers) {
                total = total.plus(worldVoxelCollisionCache.ensureAround(world,
                    space,
                    center,
                    radius,
                    tick,
                    null,
                    visitedSections));
            }
            return new WorldCollisionPrewarmStats(visitedSections.size(),
                worldCollisionStats(total));
        });
    }

    public int clearWorldCollision(@Nonnull SpaceId spaceId) {
        return callOnPhysicsOwner("clear world collision", () -> {
            PhysicsSpace space = requireSpace(spaceId);
            return worldVoxelCollisionCache.clear(space);
        });
    }

    @Nonnull
    public WorldCollisionStats getWorldCollisionStats() {
        return callOnPhysicsOwner("read world collision stats", () -> new WorldCollisionStats(
            worldVoxelCollisionCache.spaceCount(),
            worldVoxelCollisionCache.sectionCount(),
            worldVoxelCollisionCache.bodyCount(),
            worldVoxelCollisionCache.shapeTemplateCount()));
    }

    @Nonnull
    private static WorldCollisionBuildStats worldCollisionStats(
        @Nonnull WorldVoxelCollisionCache.BuildStats stats) {
        return new WorldCollisionBuildStats(stats.scannedBlocks(),
            stats.solidBlocks(),
            stats.culledInteriorBlocks(),
            stats.fullCubeRuns(),
            stats.detailBoxes(),
            stats.colliderBodies(),
            stats.removedBodies(),
            stats.sectionsBuilt(),
            stats.sectionsRebuilt(),
            stats.voxelBodies());
    }

    public void forEachBodySnapshot(@Nonnull SpaceId spaceId,
        @Nonnull Consumer<BodySnapshotEntry> consumer) {
        bodySnapshots.forEach(spaceId, consumer);
    }

    public int forEachBodySnapshotNear(@Nonnull SpaceId spaceId,
        @Nonnull Vector3f center,
        float radius,
        @Nonnull Consumer<BodySnapshotEntry> consumer) {
        return bodySnapshots.forEachNear(spaceId, center, radius, consumer);
    }

    public void removeSpace(@Nonnull SpaceId spaceId) {
        removeSpace(spaceId, "<unknown>");
    }

    public void removeSpace(@Nonnull SpaceId spaceId, @Nonnull String worldName) {
        runOnPhysicsOwner("remove physics space", () -> removeSpaceDirect(spaceId, worldName));
    }

    @Nonnull
    public PhysicsMutationHandle<SpaceId> removeSpaceAsync(@Nonnull SpaceId spaceId,
        @Nonnull String worldName) {
        return enqueuePhysicsMutation("remove physics space",
            spaceId,
            () -> removeSpaceDirect(spaceId, worldName));
    }

    private void removeSpaceDirect(@Nonnull SpaceId spaceId, @Nonnull String worldName) {
        PhysicsSpace removed = spaces.remove(spaceId.value());
        spaceSettings.remove(spaceId.value());
        worldVoxelCollisionCache.clear(spaceId, removed);
        if (defaultSpaceId != null && defaultSpaceId.equals(spaceId)) {
            defaultSpaceId = null;
        }
        if (removed != null) {
            for (PhysicsBody body : new ArrayList<>(removed.getBodies())) {
                PhysicsBodyId bodyId = getBodyId(body);
                if (bodyId != null) {
                    destroyBody(bodyId, false);
                }
            }
            LOGGER.at(Level.FINE).log(
                "World %s removed physics space id=%s backend=%s",
                worldName,
                removed.getId(),
                removed.getBackendId());
            closeSpaceQuietly(removed, worldName, "removed physics space");
            markWorldChanged();
        }
    }

    public void clearAllSpaces(@Nonnull String worldName) {
        runOnPhysicsOwner("clear physics spaces", () -> clearAllSpacesDirect(worldName));
    }

    @Nonnull
    public PhysicsMutationHandle<Void> clearAllSpacesAsync(@Nonnull String worldName) {
        return enqueuePhysicsMutation("clear physics spaces",
            () -> clearAllSpacesDirect(worldName));
    }

    private void clearAllSpacesDirect(@Nonnull String worldName) {
        List<SpaceId> ids = new ArrayList<>(spaces.size());
        for (PhysicsSpace space : spaces.values()) {
            ids.add(space.getId());
        }
        for (SpaceId spaceId : ids) {
            removeSpaceDirect(spaceId, worldName);
        }
    }

    /**
     * Clears runtime physics state by replacing each native backend space with an empty
     * space that keeps the same logical id, backend, settings, gravity, and default-space mapping.
     */
    @Nonnull
    public RuntimeResetResult resetRuntimeStateKeepingSpaces(@Nonnull String worldName) {
        return callOnPhysicsOwner("reset physics runtime state",
            () -> resetRuntimeStateKeepingSpacesDirect(worldName));
    }

    @Nonnull
    private RuntimeResetResult resetRuntimeStateKeepingSpacesDirect(@Nonnull String worldName) {
        List<SpaceReset> replacements = new ArrayList<>();
        for (PhysicsSpace previous : spaces.values()) {
            Vector3f gravity = previous.getGravity();
            PhysicsSpace replacement = Impulse.createSpace(previous.getBackendId(), previous.getId());
            try {
                validateSpaceCompatibleWithStepMode(replacement, stepMode);
                replacement.setGravity(gravity.x, gravity.y, gravity.z);
                replacements.add(new SpaceReset(previous.getId(), previous, replacement));
            } catch (RuntimeException exception) {
                closeSpaceQuietly(replacement, worldName, "discarding failed clean replacement");
                throw exception;
            }
        }

        int removedBodies = 0;
        int removedJoints = 0;
        int keptSpaces = 0;
        for (SpaceReset reset : replacements) {
            removedBodies += reset.previous().bodyCount();
            removedJoints += reset.previous().jointCount();
            worldVoxelCollisionCache.clear(reset.spaceId(), null);
            PhysicsSpace previous = replaceSpace(reset.spaceId(), reset.replacement(), worldName);
            closeSpaceQuietly(previous, worldName, "cleaned physics space");
            keptSpaces++;
        }
        clearBodyStateDirect();
        return new RuntimeResetResult(removedBodies, removedJoints, keptSpaces);
    }

    @Nonnull
    public PhysicsSpaceSettings getSpaceSettings(@Nonnull SpaceId spaceId) {
        PhysicsSpaceSettings settings = spaceSettings.get(spaceId.value());
        if (settings == null) {
            throw new IllegalStateException("Physics space settings are missing for id=" + spaceId);
        }
        return settings;
    }

    public void setSpaceSettings(@Nonnull SpaceId spaceId, @Nonnull PhysicsSpaceSettings settings) {
        runOnPhysicsOwner("set physics space settings", () -> setSpaceSettingsDirect(spaceId, settings));
    }

    @Nonnull
    public PhysicsMutationHandle<SpaceId> setSpaceSettingsAsync(@Nonnull SpaceId spaceId,
        @Nonnull PhysicsSpaceSettings settings) {
        return enqueuePhysicsMutation("set physics space settings",
            spaceId,
            () -> setSpaceSettingsDirect(spaceId, settings));
    }

    private void setSpaceSettingsDirect(@Nonnull SpaceId spaceId,
        @Nonnull PhysicsSpaceSettings settings) {
        PhysicsSpace space = spaces.get(spaceId.value());
        if (space == null) {
            throw new IllegalArgumentException("Physics space id=" + spaceId
                + " is not registered");
        }
        applySolverTuning(space, settings);
        spaceSettings.put(spaceId.value(), new PhysicsSpaceSettings(settings));
    }

    private void validateStepModeSupported(@Nonnull PhysicsStepMode stepMode) {
        if (stepMode != PhysicsStepMode.CCD) {
            return;
        }

        List<String> unsupportedSpaces = new ArrayList<>();
        for (PhysicsSpace space : spaces.values()) {
            if (!space.supportsContinuousCollision()) {
                unsupportedSpaces.add(formatSpace(space));
            }
        }
        if (!unsupportedSpaces.isEmpty()) {
            throw new IllegalArgumentException("CCD mode is not available for: "
                + String.join(", ", unsupportedSpaces));
        }
    }

    private static void validateSpaceCompatibleWithStepMode(@Nonnull PhysicsSpace space,
        @Nonnull PhysicsStepMode stepMode) {
        if (stepMode == PhysicsStepMode.CCD && !space.supportsContinuousCollision()) {
            throw new IllegalArgumentException("CCD mode is not available for "
                + formatSpace(space));
        }
    }

    @Nonnull
    private static String formatSpace(@Nonnull PhysicsSpace space) {
        return "space " + space.getId().value() + " (" + space.getBackendId().value() + ")";
    }

    private static void applySolverTuning(@Nonnull PhysicsSpace space,
        @Nonnull PhysicsSpaceSettings settings) {
        if (!(space instanceof PhysicsSolverTuning tuning)) {
            applyActivationTuning(space, settings);
            return;
        }
        tuning.setSolverTuning(settings.getSolverIterations(),
            settings.getInternalPgsIterations(),
            settings.getStabilizationIterations(),
            settings.getMinIslandSize());
        applyActivationTuning(space, settings);
    }

    private static void applyActivationTuning(@Nonnull PhysicsSpace space,
        @Nonnull PhysicsSpaceSettings settings) {
        if (!(space instanceof PhysicsActivationTuning tuning)) {
            return;
        }
        tuning.setDynamicSleepTuning(settings.getDynamicSleepLinearThreshold(),
            settings.getDynamicSleepAngularThreshold(),
            settings.getDynamicSleepTimeUntilSleep());
    }

    @Nonnull
    public PhysicsBodyId addBody(@Nonnull SpaceId spaceId,
        @Nonnull PhysicsBody body,
        @Nonnull PhysicsBodyKind kind,
        @Nonnull PhysicsBodyPersistenceMode persistenceMode) {
        return addBody(PhysicsBodyId.random(), spaceId, body, kind, persistenceMode);
    }

    @Nonnull
    public PhysicsBodyId addBody(@Nonnull PhysicsBodyId bodyId,
        @Nonnull SpaceId spaceId,
        @Nonnull PhysicsBody body,
        @Nonnull PhysicsBodyKind kind,
        @Nonnull PhysicsBodyPersistenceMode persistenceMode) {
        return callOnPhysicsOwner("add physics body",
            () -> addBodyDirect(bodyId, spaceId, body, kind, persistenceMode));
    }

    @Nonnull
    public PhysicsMutationHandle<PhysicsBodyId> addBodyAsync(@Nonnull SpaceId spaceId,
        @Nonnull PhysicsBody body,
        @Nonnull PhysicsBodyKind kind,
        @Nonnull PhysicsBodyPersistenceMode persistenceMode) {
        PhysicsBodyId bodyId = PhysicsBodyId.random();
        return addBodyAsync(bodyId, spaceId, body, kind, persistenceMode);
    }

    @Nonnull
    public PhysicsMutationHandle<PhysicsBodyId> addBodyAsync(@Nonnull PhysicsBodyId bodyId,
        @Nonnull SpaceId spaceId,
        @Nonnull PhysicsBody body,
        @Nonnull PhysicsBodyKind kind,
        @Nonnull PhysicsBodyPersistenceMode persistenceMode) {
        markBodyCreationPending(bodyId);
        AtomicBoolean pendingCleared = new AtomicBoolean();
        Runnable clearPending = () -> {
            if (pendingCleared.compareAndSet(false, true)) {
                clearBodyCreationPending(bodyId);
            }
        };
        PhysicsMutationHandle<PhysicsBodyId> handle = enqueuePhysicsMutation("add physics body",
            bodyId,
            () -> {
                try {
                    addBodyDirect(bodyId, spaceId, body, kind, persistenceMode);
                } finally {
                    clearPending.run();
                }
            });
        handle.completion().whenComplete((ignored, failure) -> clearPending.run());
        return handle;
    }

    @Nonnull
    private PhysicsBodyId addBodyDirect(@Nonnull PhysicsBodyId bodyId,
        @Nonnull SpaceId spaceId,
        @Nonnull PhysicsBody body,
        @Nonnull PhysicsBodyKind kind,
        @Nonnull PhysicsBodyPersistenceMode persistenceMode) {
        PhysicsSpace space = getSpace(spaceId);
        if (space == null) {
            throw new IllegalArgumentException("Physics space id=" + spaceId + " is not registered");
        }
        if (!containsBody(space, body)) {
            space.addBody(body);
        }
        BodyRegistration registration = bodyRegistry.registerBody(bodyId, body, spaceId, kind, persistenceMode);
        PhysicsBodySnapshot snapshot = PhysicsBodySnapshot.from(body);
        bodySnapshots.put(bodyId, snapshot, spaceId, registration);
        workerBodySnapshots.put(bodyId, snapshot, spaceId, registration);
        markWorldChanged();
        return bodyId;
    }

    public void destroyBody(@Nonnull PhysicsBodyId bodyId) {
        destroyBody(bodyId, true);
    }

    @Nonnull
    public PhysicsMutationHandle<PhysicsBodyId> destroyBodyAsync(@Nonnull PhysicsBodyId bodyId) {
        return destroyBodyAsync(bodyId, true);
    }

    public void destroyBody(@Nonnull PhysicsBodyId bodyId, boolean removeFromSpace) {
        runOnPhysicsOwner("destroy physics body", () -> destroyBodyDirect(bodyId, removeFromSpace));
    }

    @Nonnull
    public PhysicsMutationHandle<PhysicsBodyId> destroyBodyAsync(@Nonnull PhysicsBodyId bodyId,
        boolean removeFromSpace) {
        return enqueuePhysicsMutation("destroy physics body",
            bodyId,
            () -> destroyBodyDirect(bodyId, removeFromSpace));
    }

    private void destroyBodyDirect(@Nonnull PhysicsBodyId bodyId, boolean removeFromSpace) {
        BodyRegistration registration = bodyRegistry.getRegistration(bodyId);
        if (removeFromSpace && registration != null) {
            removeBodyFromSpace(registration.body(), registration);
        }
        if (registration != null) {
            bodyRegistry.unregisterBody(bodyId);
            clearBodyRuntimeState(registration.body(), bodyId);
        } else {
            clearBodyRuntimeState(bodyId);
        }
        markWorldChanged();
    }

    public void destroyBody(@Nonnull PhysicsBody body) {
        runOnPhysicsOwner("destroy physics body", () -> destroyBodyDirect(body));
    }

    @Nonnull
    public PhysicsMutationHandle<Void> destroyBodyAsync(@Nonnull PhysicsBody body) {
        return enqueuePhysicsMutation("destroy physics body", () -> destroyBodyDirect(body));
    }

    private void destroyBodyDirect(@Nonnull PhysicsBody body) {
        PhysicsBodyId bodyId = getBodyId(body);
        if (bodyId != null) {
            destroyBodyDirect(bodyId, true);
        } else {
            runtimeState.clearBody(body);
            removeBodyFromSpace(body, null);
            markWorldChanged();
        }
    }

    @Nullable
    public PhysicsBody getBody(@Nonnull PhysicsBodyId bodyId) {
        BodyRegistration registration = bodyRegistry.getRegistration(bodyId);
        return registration != null ? registration.body() : null;
    }

    @Nullable
    public PhysicsBodyId getBodyId(@Nonnull PhysicsBody body) {
        return bodyRegistry.getBodyId(body);
    }

    @Nullable
    public BodyRegistration getBodyRegistration(@Nonnull PhysicsBody body) {
        return bodyRegistry.getRegistration(body);
    }

    @Nullable
    public BodyRegistration getRegistration(@Nonnull PhysicsBodyId bodyId) {
        return bodyRegistry.getRegistration(bodyId);
    }

    public boolean isBodyCreationPending(@Nonnull PhysicsBodyId bodyId) {
        synchronized (pendingBodyCreations) {
            return pendingBodyCreations.containsKey(bodyId);
        }
    }

    @Nonnull
    public BodyRegistration requireBodyRegistration(@Nonnull PhysicsBodyId bodyId) {
        BodyRegistration registration = getRegistration(bodyId);
        if (registration == null) {
            throw new IllegalArgumentException("Physics body id=" + bodyId + " is not registered");
        }
        return registration;
    }

    @Nonnull
    public Collection<BodyRegistration> getBodyRegistrations() {
        return bodyRegistry.getRegistrations();
    }

    public int getBodyRegistrationCount() {
        return bodyRegistry.getRegistrationCount();
    }

    public int getBodyRegistrationCount(@Nonnull PhysicsBodyPersistenceMode persistenceMode) {
        return bodyRegistry.getRegistrationCount(persistenceMode);
    }

    @Nonnull
    public Collection<BodyRegistration> getBodyRegistrations(@Nonnull PhysicsBodyKind kind) {
        return bodyRegistry.getRegistrations(kind);
    }

    @Nonnull
    public Collection<Ref<EntityStore>> getBodyAttachments(@Nonnull PhysicsBodyId bodyId) {
        return bodyRegistry.getAttachments(bodyId);
    }

    public void registerBodyAttachment(@Nonnull PhysicsBodyId bodyId, @Nonnull Ref<EntityStore> attachment) {
        bodyRegistry.registerAttachment(bodyId, attachment);
    }

    public void unregisterBodyAttachment(@Nonnull PhysicsBodyId bodyId, @Nonnull Ref<EntityStore> attachment) {
        bodyRegistry.unregisterAttachment(bodyId, attachment);
    }

    @Nullable
    public Ref<EntityStore> getGeneratedVisualProxy(@Nonnull PhysicsBodyId bodyId) {
        return bodyRegistry.getGeneratedVisualProxy(bodyId);
    }

    @Nonnull
    public Collection<PhysicsBodyId> getGeneratedVisualProxyBodyIds() {
        return bodyRegistry.getGeneratedVisualProxyBodyIds();
    }

    public void setGeneratedVisualProxy(@Nonnull PhysicsBodyId bodyId, @Nonnull Ref<EntityStore> proxy) {
        bodyRegistry.setGeneratedVisualProxy(bodyId, proxy);
    }

    public void clearGeneratedVisualProxy(@Nonnull PhysicsBodyId bodyId) {
        bodyRegistry.clearGeneratedVisualProxy(bodyId);
    }

    public boolean clearGeneratedVisualProxy(@Nonnull PhysicsBodyId bodyId,
        @Nonnull Ref<EntityStore> expectedProxy) {
        return bodyRegistry.clearGeneratedVisualProxy(bodyId, expectedProxy);
    }

    public boolean isGeneratedVisualProxy(@Nonnull PhysicsBodyId bodyId,
        @Nonnull Ref<EntityStore> proxy) {
        return bodyRegistry.isGeneratedVisualProxy(bodyId, proxy);
    }

    public void setSyntheticVisualInterests(@Nonnull Collection<VisualInterest> interests) {
        bodyRegistry.setSyntheticVisualInterests(interests);
    }

    @Nonnull
    public List<VisualInterest> getSyntheticVisualInterests() {
        return bodyRegistry.getSyntheticVisualInterests();
    }

    public void clearSyntheticVisualInterests() {
        bodyRegistry.clearSyntheticVisualInterests();
    }

    public void clearBodies() {
        runOnPhysicsOwner("clear physics bodies", this::destroyRegisteredBodiesDirect);
    }

    @Nonnull
    public PhysicsMutationHandle<Void> clearBodiesAsync() {
        return enqueuePhysicsMutation("clear physics bodies", this::destroyRegisteredBodiesDirect);
    }

    private void destroyRegisteredBodiesDirect() {
        RuntimeException failure = null;
        boolean bodyFailure = false;
        for (PhysicsSpace space : spaces.values()) {
            for (PhysicsJoint joint : new ArrayList<>(space.getJoints())) {
                try {
                    space.removeJoint(joint);
                } catch (RuntimeException exception) {
                    failure = collectFailure(failure, exception);
                }
            }
        }
        List<BodyRegistration> registrations = new ArrayList<>(bodyRegistry.getRegistrations());
        for (BodyRegistration registration : registrations) {
            try {
                destroyBodyDirect(registration.id(), true);
            } catch (RuntimeException exception) {
                bodyFailure = true;
                failure = collectFailure(failure, exception);
            }
        }
        if (bodyFailure) {
            throw failure;
        }
        clearBodyStateDirect();
        if (failure != null) {
            throw failure;
        }
    }

    private void clearBodyStateDirect() {
        bodyRegistry.clear();
        runtimeState.clear();
        bodySnapshots.clear();
        workerBodySnapshots.clear();
        markWorldChanged();
    }

    @Nonnull
    public BodySyncState getOrCreateBodySyncState(@Nonnull Ref<EntityStore> entityRef) {
        return runtimeState.getOrCreateBodySyncState(entityRef);
    }

    @Nullable
    public BodySyncState getBodySyncState(@Nonnull Ref<EntityStore> entityRef) {
        return runtimeState.getBodySyncState(entityRef);
    }

    public void clearBodySyncState(@Nonnull Ref<EntityStore> entityRef) {
        runtimeState.clearBodySyncState(entityRef);
    }

    @Nonnull
    public BodyVisualInterestState getOrCreateBodyVisualInterestState(@Nonnull PhysicsBodyId bodyId) {
        BodyVisualInterestState state = bodyRegistry.getOrCreateBodyVisualInterestState(bodyId);
        state.advanceVisualInterestTick(visualInterestTick.get());
        return state;
    }

    @Nullable
    public BodyVisualInterestState getBodyVisualInterestState(@Nonnull PhysicsBodyId bodyId) {
        BodyVisualInterestState state = bodyRegistry.getBodyVisualInterestState(bodyId);
        if (state != null) {
            state.advanceVisualInterestTick(visualInterestTick.get());
        }
        return state;
    }

    public long advanceVisualInterestTick() {
        return visualInterestTick.incrementAndGet();
    }

    public void markBodyControlled(@Nonnull PhysicsBodyId bodyId) {
        runtimeState.markBodyControlled(bodyId);
    }

    public void clearControlledBody(@Nonnull PhysicsBodyId bodyId) {
        runtimeState.clearControlledBody(bodyId);
    }

    public boolean isBodyControlled(@Nonnull PhysicsBodyId bodyId) {
        return runtimeState.isBodyControlled(bodyId);
    }

    public void updateChunkBoundarySafeState(@Nonnull PhysicsBodyId bodyId,
        @Nonnull Vector3f position,
        @Nonnull Quaternionf rotation) {
        runtimeState.updateChunkBoundarySafeState(bodyId, position, rotation);
    }

    @Nullable
    public ChunkBoundarySafeState getChunkBoundarySafeState(@Nonnull PhysicsBodyId bodyId) {
        return runtimeState.getChunkBoundarySafeState(bodyId);
    }

    public void pauseChunkBoundaryBody(@Nonnull PhysicsBodyId bodyId,
        long targetChunkIndex,
        @Nonnull PhysicsBodyType originalBodyType,
        @Nonnull Vector3f linearVelocity,
        @Nonnull Vector3f angularVelocity) {
        runtimeState.pauseChunkBoundaryBody(bodyId,
            targetChunkIndex,
            originalBodyType,
            linearVelocity,
            angularVelocity);
    }

    @Nullable
    public ChunkBoundaryPauseState getChunkBoundaryPauseState(@Nonnull PhysicsBodyId bodyId) {
        return runtimeState.getChunkBoundaryPauseState(bodyId);
    }

    public void clearChunkBoundaryPauseState(@Nonnull PhysicsBodyId bodyId) {
        runtimeState.clearChunkBoundaryPauseState(bodyId);
    }

    public void clearBodyRuntimeState(@Nonnull PhysicsBodyId bodyId) {
        runOnPhysicsOwner("clear physics body runtime state", () -> clearBodyRuntimeStateDirect(bodyId));
    }

    @Nonnull
    public PhysicsMutationHandle<PhysicsBodyId> clearBodyRuntimeStateAsync(
        @Nonnull PhysicsBodyId bodyId) {
        return enqueuePhysicsMutation("clear physics body runtime state",
            bodyId,
            () -> clearBodyRuntimeStateDirect(bodyId));
    }

    private void clearBodyRuntimeStateDirect(@Nonnull PhysicsBodyId bodyId) {
        bodyRegistry.clearBodyRuntimeState(bodyId);
        runtimeState.clearBody(bodyId);
        bodySnapshots.remove(bodyId);
        workerBodySnapshots.remove(bodyId);
    }

    private void clearBodyRuntimeState(@Nonnull PhysicsBody body, @Nonnull PhysicsBodyId bodyId) {
        bodyRegistry.clearBodyRuntimeState(bodyId);
        runtimeState.clearBody(body, bodyId);
        bodySnapshots.remove(bodyId);
        workerBodySnapshots.remove(bodyId);
    }

    public void markContinuousCollisionForced(@Nonnull PhysicsBody body) {
        runtimeState.markContinuousCollisionForced(body);
    }

    @Nonnull
    public Collection<PhysicsBody> getForcedContinuousCollisionBodies() {
        return runtimeState.getForcedContinuousCollisionBodies();
    }

    public void clearForcedContinuousCollisionBodies() {
        runtimeState.clearForcedContinuousCollisionBodies();
    }

    public void copyFrom(@Nonnull PhysicsWorldResource other) {
        runOnPhysicsOwner("copy physics world resource", () -> copyFromDirect(other));
    }

    @Nonnull
    public PhysicsMutationHandle<Void> copyFromAsync(@Nonnull PhysicsWorldResource other) {
        return enqueuePhysicsMutation("copy physics world resource", () -> copyFromDirect(other));
    }

    private void copyFromDirect(@Nonnull PhysicsWorldResource other) {
        if (this == other) {
            return;
        }
        spaces.clear();
        spaceSettings.clear();
        bodyRegistry.clear();
        runtimeState.clear();
        bodySnapshots.clear();
        workerBodySnapshots.clear();
        worldVoxelCollisionCache.copyFrom(new WorldVoxelCollisionCache());
        defaultSpaceId = other.defaultSpaceId;
        simulationSteps = other.simulationSteps;
        stepMode = other.stepMode;
        maxStepDt = other.maxStepDt;
        for (var entry : other.spaceSettings.int2ObjectEntrySet()) {
            spaceSettings.put(entry.getIntKey(), new PhysicsSpaceSettings(entry.getValue()));
        }
        markWorldChanged();
    }

    /**
     * Set how many fixed substeps are run for each server tick.
     * Higher values can improve stability at the cost of backend step time.
     */
    public void setSimulationSteps(int simulationSteps) {
        runOnPhysicsOwner("set simulation steps", () -> setSimulationStepsDirect(simulationSteps));
    }

    @Nonnull
    public PhysicsMutationHandle<Void> setSimulationStepsAsync(int simulationSteps) {
        return enqueuePhysicsMutation("set simulation steps",
            () -> setSimulationStepsDirect(simulationSteps));
    }

    private void setSimulationStepsDirect(int simulationSteps) {
        if (simulationSteps < MIN_SIMULATION_STEPS || simulationSteps > MAX_SIMULATION_STEPS) {
            throw new IllegalArgumentException("Simulation steps must be between "
                + MIN_SIMULATION_STEPS + " and " + MAX_SIMULATION_STEPS);
        }
        this.simulationSteps = simulationSteps;
    }

    @Nonnull
    public PhysicsSpace replaceSpace(@Nonnull SpaceId spaceId,
        @Nonnull PhysicsSpace replacement,
        @Nonnull String worldName) {
        return callOnPhysicsOwner("replace physics space",
            () -> replaceSpaceDirect(spaceId, replacement, worldName));
    }

    @Nonnull
    public PhysicsMutationHandle<SpaceId> replaceSpaceAsync(@Nonnull SpaceId spaceId,
        @Nonnull PhysicsSpace replacement,
        @Nonnull String worldName) {
        return enqueuePhysicsMutation("replace physics space",
            spaceId,
            () -> replaceSpaceDirect(spaceId, replacement, worldName));
    }

    @Nonnull
    private PhysicsSpace replaceSpaceDirect(@Nonnull SpaceId spaceId,
        @Nonnull PhysicsSpace replacement,
        @Nonnull String worldName) {
        if (!spaceId.equals(replacement.getId())) {
            throw new IllegalArgumentException("Replacement space id " + replacement.getId()
                + " does not match target id " + spaceId);
        }

        PhysicsSpace previous = spaces.get(spaceId.value());
        if (previous == null) {
            throw new IllegalStateException("Cannot replace missing physics space id=" + spaceId);
        }
        validateSpaceCompatibleWithStepMode(replacement, stepMode);
        worldVoxelCollisionCache.clear(spaceId, previous);

        PhysicsSpaceSettings settings = spaceSettings.get(spaceId.value());
        if (settings == null) {
            settings = PhysicsSpaceSettings.defaults();
        }
        applySolverTuning(replacement, settings);
        spaces.put(spaceId.value(), replacement);
        spaceSettings.put(spaceId.value(), new PhysicsSpaceSettings(settings));
        markWorldChanged();
        LOGGER.at(Level.INFO).log(
            "World %s replaced physics space id=%s backend=%s -> backend=%s",
            worldName,
            spaceId,
            previous.getBackendId(),
            replacement.getBackendId());
        return previous;
    }

    private static void closeSpaceQuietly(@Nonnull PhysicsSpace space,
        @Nonnull String worldName,
        @Nonnull String action) {
        try {
            space.close();
        } catch (RuntimeException exception) {
            LOGGER.at(Level.WARNING).log(
                "World %s failed to close %s id=%s backend=%s: %s",
                worldName,
                action,
                space.getId(),
                space.getBackendId(),
                exception.getMessage());
        }
    }

    @Nonnull
    private static RuntimeException collectFailure(@Nullable RuntimeException failure,
        @Nonnull RuntimeException exception) {
        if (failure == null) {
            return exception;
        }
        failure.addSuppressed(exception);
        return failure;
    }

    private void removeBodyFromSpace(@Nonnull PhysicsBody body, @Nullable BodyRegistration registration) {
        if (registration != null) {
            PhysicsSpace space = getSpace(registration.spaceId());
            if (space != null) {
                space.removeBody(body);
                return;
            }
        }

        for (PhysicsSpace space : spaces.values()) {
            space.removeBody(body);
        }
    }

    private static boolean containsBody(@Nonnull PhysicsSpace space, @Nonnull PhysicsBody body) {
        for (PhysicsBody existing : space.getBodies()) {
            if (existing == body) {
                return true;
            }
        }
        return false;
    }

    private void markBodyCreationPending(@Nonnull PhysicsBodyId bodyId) {
        synchronized (pendingBodyCreations) {
            pendingBodyCreations.addTo(bodyId, 1);
        }
    }

    private void clearBodyCreationPending(@Nonnull PhysicsBodyId bodyId) {
        synchronized (pendingBodyCreations) {
            int count = pendingBodyCreations.getInt(bodyId);
            if (count <= 1) {
                pendingBodyCreations.removeInt(bodyId);
            } else {
                pendingBodyCreations.put(bodyId, count - 1);
            }
        }
    }

    private void markWorldChanged() {
        long newWorldEpoch = worldEpoch.incrementAndGet();
        publishLatestFrame(PublishedPhysicsSnapshotFrame.empty(snapshotFrameEpoch.get(), newWorldEpoch));
    }

    private void publishLatestFrameIfWorldCurrent(@Nonnull PublishedPhysicsSnapshotFrame frame) {
        publishLatestFrame(frame, true);
    }

    private void publishLatestFrame(@Nonnull PublishedPhysicsSnapshotFrame frame) {
        publishLatestFrame(frame, false);
    }

    private void publishLatestFrame(@Nonnull PublishedPhysicsSnapshotFrame frame,
        boolean requireCurrentWorldEpoch) {
        latestPublishedFrame.updateAndGet(current -> {
            if (requireCurrentWorldEpoch && frame.worldEpoch() != worldEpoch.get()) {
                return current;
            }
            return isNewerFrame(frame, current) ? frame : current;
        });
    }

    private static boolean isNewerFrame(@Nonnull PublishedPhysicsSnapshotFrame candidate,
        @Nonnull PublishedPhysicsSnapshotFrame current) {
        if (candidate.worldEpoch() != current.worldEpoch()) {
            return candidate.worldEpoch() > current.worldEpoch();
        }
        return candidate.frameEpoch() >= current.frameEpoch();
    }

    public record BodyRegistration(@Nonnull PhysicsBodyId id,
        @Nonnull PhysicsBody body,
        @Nonnull SpaceId spaceId,
        @Nonnull PhysicsBodyKind kind,
        @Nonnull PhysicsBodyPersistenceMode persistenceMode) {
    }

    public record RuntimeResetResult(int removedBodies, int removedJoints, int keptSpaces) {
    }

    private record SpaceReset(@Nonnull SpaceId spaceId,
        @Nonnull PhysicsSpace previous,
        @Nonnull PhysicsSpace replacement) {
    }

    public record BodySnapshotEntry(@Nonnull PhysicsBodyId bodyId,
        @Nonnull PhysicsBodySnapshot snapshot,
        @Nonnull SpaceId spaceId,
        @Nonnull BodyRegistration registration) {
    }

    public record VisualInterest(@Nonnull Vector3f position, @Nullable Vector3f direction) {
    }

    /**
     * Per-body visual-interest cache produced by detached visual materialization.
     *
     * <p>Physics sync can reuse fresh raycast results from this state when
     * {@code VisualOcclusionMode.CULL} is enabled, so materialization and sync
     * share one occlusion decision window instead of spending duplicate raycasts.</p>
     */
    public static final class BodyVisualInterestState {

        @Getter
        private float nearestDistanceSquared = Float.POSITIVE_INFINITY;
        @Getter
        private boolean inRange;
        @Getter
        private boolean likelyVisible;
        @Getter
        private boolean raycastVisible;
        private long currentTick;
        private long lastRaycastTick = Long.MIN_VALUE;

        public void recordInterest(float nearestDistanceSquared,
            boolean likelyVisible,
            boolean raycastVisible,
            boolean raycastEvaluated) {
            recordInterest(nearestDistanceSquared,
                likelyVisible,
                raycastVisible,
                raycastEvaluated,
                currentTick);
        }

        public void recordInterest(float nearestDistanceSquared,
            boolean likelyVisible,
            boolean raycastVisible,
            boolean raycastEvaluated,
            long currentTick) {
            long resolvedTick = advanceVisualInterestTick(currentTick);
            this.nearestDistanceSquared = nearestDistanceSquared;
            inRange = nearestDistanceSquared != Float.POSITIVE_INFINITY;
            this.likelyVisible = likelyVisible;
            if (raycastEvaluated) {
                this.raycastVisible = raycastVisible;
                lastRaycastTick = resolvedTick;
            }
        }

        public boolean hasFreshRaycast(int cacheTicks) {
            return hasFreshRaycast(cacheTicks, currentTick);
        }

        public boolean hasFreshRaycast(int cacheTicks, long currentTick) {
            long resolvedTick = advanceVisualInterestTick(currentTick);
            return lastRaycastTick != Long.MIN_VALUE
                && resolvedTick - lastRaycastTick <= cacheTicks;
        }

        public long advanceVisualInterestTick(long currentTick) {
            this.currentTick = Math.max(this.currentTick, currentTick);
            return this.currentTick;
        }

    }

    public static final class ChunkBoundarySafeState {

        @Nonnull
        private final Vector3f position = new Vector3f();
        @Nonnull
        private final Quaternionf rotation = new Quaternionf();

        public void set(@Nonnull Vector3f position, @Nonnull Quaternionf rotation) {
            this.position.set(position);
            this.rotation.set(rotation);
        }

        @Nonnull
        public Vector3f getPosition() {
            return position;
        }

        @Nonnull
        public Quaternionf getRotation() {
            return rotation;
        }
    }

    public static final class BodySyncState {

        @Nonnull
        private final Vector3f lastSyncedPosition = new Vector3f();
        @Nonnull
        private final Quaternionf lastSyncedRotation = new Quaternionf();
        @Getter
        private boolean initialized;
        @Getter
        private boolean sleeping;
        @Getter
        private float secondsSinceSync;

        public void recordSync(@Nonnull Vector3f position,
            @Nonnull Quaternionf rotation,
            boolean sleeping) {
            lastSyncedPosition.set(position);
            lastSyncedRotation.set(rotation);
            initialized = true;
            this.sleeping = sleeping;
            secondsSinceSync = 0.0f;
        }

        public void recordSkip(float dt) {
            secondsSinceSync += Math.max(dt, 0.0f);
        }

        @Nonnull
        public Vector3f getLastSyncedPosition() {
            return lastSyncedPosition;
        }

        @Nonnull
        public Quaternionf getLastSyncedRotation() {
            return lastSyncedRotation;
        }

    }

    public static final class ChunkBoundaryPauseState {

        @Getter
        private long targetChunkIndex;
        @Nonnull
        private PhysicsBodyType originalBodyType = PhysicsBodyType.DYNAMIC;
        @Nonnull
        private final Vector3f linearVelocity = new Vector3f();
        @Nonnull
        private final Vector3f angularVelocity = new Vector3f();

        public void set(long targetChunkIndex,
            @Nonnull PhysicsBodyType originalBodyType,
            @Nonnull Vector3f linearVelocity,
            @Nonnull Vector3f angularVelocity) {
            this.targetChunkIndex = targetChunkIndex;
            this.originalBodyType = originalBodyType;
            this.linearVelocity.set(linearVelocity);
            this.angularVelocity.set(angularVelocity);
        }

        @Nonnull
        public PhysicsBodyType getOriginalBodyType() {
            return originalBodyType;
        }

        @Nonnull
        public Vector3f getLinearVelocity() {
            return linearVelocity;
        }

        @Nonnull
        public Vector3f getAngularVelocity() {
            return angularVelocity;
        }
    }

    public static ResourceType<EntityStore, PhysicsWorldResource> getResourceType()
    {
        return ImpulsePlugin.get().getPhysicsWorldResourceType();
    }

    @Nonnull
    @Override
    public PhysicsWorldResource clone() {
        PhysicsWorldResource copy = new PhysicsWorldResource();
        copy.copyFrom(this);
        return copy;
    }
}
