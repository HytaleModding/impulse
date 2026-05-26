package dev.hytalemodding.impulse.core.plugin.resources;

import com.hypixel.hytale.component.Resource;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.api.BackendId;
import dev.hytalemodding.impulse.api.PhysicsBody;
import dev.hytalemodding.impulse.api.PhysicsBodySnapshot;
import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.PhysicsJoint;
import dev.hytalemodding.impulse.api.PhysicsSpace;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.ImpulsePlugin;
import dev.hytalemodding.impulse.core.internal.resources.body.PhysicsBodyRuntimeState.BodySyncState;
import dev.hytalemodding.impulse.core.internal.resources.chunk.PhysicsChunkBoundaryRuntime.ChunkBoundaryPauseState;
import dev.hytalemodding.impulse.core.internal.resources.chunk.PhysicsChunkBoundaryRuntime.ChunkBoundarySafeState;
import dev.hytalemodding.impulse.core.internal.resources.visual.PhysicsVisualRuntime.BodyVisualInterestState;
import dev.hytalemodding.impulse.core.internal.resources.visual.PhysicsVisualRuntime.VisualInterest;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyId;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyKind;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyPersistenceMode;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyRegistration;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyRegistrationView;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodies;
import dev.hytalemodding.impulse.core.plugin.collision.WorldCollisionBuildStats;
import dev.hytalemodding.impulse.core.plugin.collision.WorldCollisionPrewarmStats;
import dev.hytalemodding.impulse.core.plugin.collision.WorldCollisionStats;
import dev.hytalemodding.impulse.core.plugin.execution.PhysicsMutationHandle;
import dev.hytalemodding.impulse.core.plugin.execution.PhysicsOwnerCallable;
import dev.hytalemodding.impulse.core.plugin.execution.PhysicsOwnerHandle;
import dev.hytalemodding.impulse.core.plugin.execution.PhysicsOwnerMutation;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsSpaceSettings;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsStepMode;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsWorldSettings;
import dev.hytalemodding.impulse.core.plugin.snapshot.PhysicsBodySnapshotEntry;
import dev.hytalemodding.impulse.core.plugin.snapshot.PublishedPhysicsSnapshotFrame;
import dev.hytalemodding.impulse.core.internal.resources.body.PhysicsBodyRegistry;
import dev.hytalemodding.impulse.core.internal.resources.body.PhysicsBodyRuntime;
import dev.hytalemodding.impulse.core.internal.resources.body.PhysicsBodyRuntimeState;
import dev.hytalemodding.impulse.core.internal.resources.chunk.PhysicsChunkBoundaryRuntime;
import dev.hytalemodding.impulse.core.internal.resources.collision.PhysicsWorldCollisionRuntime;
import dev.hytalemodding.impulse.core.internal.resources.owner.PhysicsOwnerGateway;
import dev.hytalemodding.impulse.core.internal.resources.simulation.PhysicsSimulationRuntime;
import dev.hytalemodding.impulse.core.internal.resources.space.PhysicsSpaceRuntime;
import dev.hytalemodding.impulse.core.internal.resources.snapshot.PhysicsWorldSnapshotState;
import dev.hytalemodding.impulse.core.internal.resources.visual.PhysicsVisualRuntime;
import dev.hytalemodding.impulse.core.internal.voxel.WorldCollisionCacheAccess;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.logging.Level;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
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

    private final PhysicsSpaceRuntime spaceRuntime = new PhysicsSpaceRuntime();

    private final PhysicsBodyRegistry bodyRegistry = new PhysicsBodyRegistry();

    private final PhysicsWorldCollisionRuntime collisionRuntime =
        new PhysicsWorldCollisionRuntime();

    @Nonnull
    private final PhysicsSimulationRuntime simulationRuntime = new PhysicsSimulationRuntime();

    private final PhysicsBodyRuntimeState runtimeState = new PhysicsBodyRuntimeState();
    private final PhysicsChunkBoundaryRuntime chunkRuntime = new PhysicsChunkBoundaryRuntime();
    private final PhysicsVisualRuntime visualRuntime = new PhysicsVisualRuntime(this::clearBodySyncState);
    private final PhysicsWorldSnapshotState snapshotState = new PhysicsWorldSnapshotState();
    private final PhysicsBodyRuntime bodyRuntime = new PhysicsBodyRuntime(spaceRuntime,
        bodyRegistry,
        runtimeState,
        chunkRuntime,
        visualRuntime,
        snapshotState,
        this::markWorldChanged);

    private final AtomicLong visualInterestTick = new AtomicLong();
    private final PhysicsOwnerGateway ownerGateway = new PhysicsOwnerGateway();

    public PhysicsWorldResource() {
    }

    public void attachWorkerResource(@Nonnull PhysicsOwnerHandle workerResource) {
        ownerGateway.attachWorkerResource(workerResource);
    }

    public void detachWorkerResource(@Nonnull PhysicsOwnerHandle workerResource) {
        ownerGateway.detachWorkerResource(workerResource);
    }

    /**
     * Returns whether the current thread may touch live backend objects without routing.
     *
     * <p>This is only an owner-thread check for code that already has a safe direct path. Gameplay,
     * diagnostics, and commands should prefer {@link #runOnPhysicsOwner} or
     * {@link #callOnPhysicsOwner} instead of skipping behavior when this is false.</p>
     */
    public boolean canAccessLiveBackendDirectly() {
        return ownerGateway.canAccessLiveBackendDirectly();
    }

    /**
     * Fails when the current thread is not allowed to touch live backend objects directly.
     *
     * <p>Use this at lower-level integration points that intentionally accept a live
     * {@link PhysicsSpace}, {@link PhysicsBody}, or {@link PhysicsJoint}. Most gameplay and command
     * code should route through {@link #runOnPhysicsOwner}, {@link #callOnPhysicsOwner}, or
     * {@link #enqueuePhysicsMutation} instead.</p>
     */
    public void assertCanAccessLiveBackendDirectly(@Nonnull String operation) {
        ownerGateway.assertCanAccessLiveBackendDirectly(operation);
    }

    /**
     * Run a live-backend mutation on the current physics owner.
     *
     * <p>When a world worker is attached, this submits to the physics worker and blocks until the
     * mutation completes. If no worker is attached, or the caller is already on the worker thread,
     * this runs immediately. The callback may access live {@link PhysicsSpace} and
     * {@link PhysicsBody} objects, but must not access Hytale ECS store state unless the caller owns
     * that access.</p>
     */
    public void runOnPhysicsOwner(@Nonnull String operation,
        @Nonnull PhysicsOwnerMutation mutation) {
        ownerGateway.run(operation, mutation);
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
        return ownerGateway.enqueue(operation, value, mutation);
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
        return ownerGateway.call(operation, callable);
    }

    @Nullable
    public SpaceId getDefaultSpaceId() {
        return spaceRuntime.getDefaultSpaceId();
    }

    @Nonnull
    public SpaceId requireDefaultSpaceId() {
        return spaceRuntime.requireDefaultSpaceId();
    }

    @Nullable
    public PhysicsSpace getDefaultSpace() {
        return spaceRuntime.getDefaultSpace();
    }

    @Nonnull
    public PhysicsSpace requireDefaultSpace() {
        return spaceRuntime.requireDefaultSpace();
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
        spaceRuntime.setDefaultSpaceId(defaultSpaceId);
    }

    /**
     * Returns a defensive copy of the world-level simulation settings.
     *
     * <p>The live settings instance is owner-thread state. Change the returned copy, then apply it
     * with {@link #setWorldSettings} or {@link #setWorldSettingsAsync}; direct mutation of the copy
     * has no effect on the world.</p>
     */
    @Nonnull
    public PhysicsWorldSettings getWorldSettings() {
        return simulationRuntime.getWorldSettings();
    }

    /**
     * Applies world-level simulation settings on the physics owner thread.
     *
     * <p>This is the only public write path for world step settings. It preserves worker routing
     * and validates CCD compatibility before replacing the owned settings.</p>
     */
    public void setWorldSettings(@Nonnull PhysicsWorldSettings settings) {
        PhysicsWorldSettings requested = new PhysicsWorldSettings(settings);
        runOnPhysicsOwner("set physics world settings", () -> setWorldSettingsDirect(requested));
    }

    @Nonnull
    public PhysicsMutationHandle<Void> setWorldSettingsAsync(
        @Nonnull PhysicsWorldSettings settings) {
        PhysicsWorldSettings requested = new PhysicsWorldSettings(settings);
        return enqueuePhysicsMutation("set physics world settings",
            () -> setWorldSettingsDirect(requested));
    }

    private void setWorldSettingsDirect(@Nonnull PhysicsWorldSettings settings) {
        validateStepModeSupported(settings.getStepMode());
        simulationRuntime.setWorldSettings(settings);
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
        PhysicsSpace space = spaceRuntime.createSpace(backendId,
            spaceId,
            worldName,
            settings,
            makeDefault,
            simulationRuntime.getWorldSettings().getStepMode());
        markWorldChanged();
        return space;
    }

    @Nullable
    public PhysicsSpace getSpace(@Nonnull SpaceId spaceId) {
        return spaceRuntime.getSpace(spaceId);
    }

    @Nonnull
    private PhysicsSpace requireSpace(@Nonnull SpaceId spaceId) {
        return spaceRuntime.requireSpace(spaceId);
    }

    @Nonnull
    public Collection<PhysicsSpace> getSpaces() {
        return spaceRuntime.getSpaces();
    }

    public int getSpaceCount() {
        return spaceRuntime.getSpaceCount();
    }

    /**
     * Iterate spaces without allocating a snapshot collection.
     * Use this from tick systems that do not mutate the space map while iterating.
     */
    @Nonnull
    public Iterable<PhysicsSpace> iterateSpaces() {
        return spaceRuntime.iterateSpaces();
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
        PhysicsBodySnapshot snapshot = snapshotState.getBodySnapshot(bodyId);
        if (snapshot != null) {
            return snapshot;
        }
        return callOnPhysicsOwner("refresh missing physics body snapshot",
            () -> getBodySnapshotDirect(bodyId));
    }

    @Nonnull
    private PhysicsBodySnapshot getBodySnapshotDirect(@Nonnull PhysicsBodyId bodyId) {
        PhysicsBodySnapshot snapshot = snapshotState.getBodySnapshot(bodyId);
        if (snapshot != null) {
            return snapshot;
        }
        PhysicsBodyRegistration registration = requireBodyRegistration(bodyId);
        return snapshotState.captureBodySnapshot(registration);
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

        assertCanAccessLiveBackendDirectly("capture published physics snapshot frame");
        return snapshotState.capturePublishedSnapshotFrame(spaceRuntime.liveSpaces(),
            bodyRegistry,
            stepSequence,
            serverTick,
            status,
            stepNanos,
            profilingEnabled);
    }

    public int applyPublishedSnapshotFrame(@Nonnull PublishedPhysicsSnapshotFrame frame) {
        return snapshotState.applyPublishedSnapshotFrame(frame, bodyRegistry);
    }

    @Nonnull
    public PublishedPhysicsSnapshotFrame getLatestPublishedFrame() {
        return snapshotState.getLatestPublishedFrame();
    }

    public long getLatestSnapshotAppliedNanos() {
        return snapshotState.getLatestSnapshotAppliedNanos();
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
        return snapshotState.getBodySnapshotCount();
    }

    public int getBodySnapshotCount(@Nonnull SpaceId spaceId) {
        return snapshotState.getBodySnapshotCount(spaceId);
    }

    public int getBodySnapshotCellCount() {
        return snapshotState.getBodySnapshotCellCount();
    }

    /**
     * Internal bridge for Impulse systems that own streamed terrain cache behavior.
     */
    @Nonnull
    public Object internalWorldCollisionState(
        @Nonnull WorldCollisionCacheAccess access) {
        return collisionRuntime.internalState(access);
    }

    @Nonnull
    public WorldCollisionBuildStats rebuildWorldCollisionAround(@Nonnull World world,
        @Nonnull SpaceId spaceId,
        @Nonnull Vector3d center,
        int radius) {
        return callOnPhysicsOwner("rebuild world collision", () -> {
            PhysicsSpace space = requireSpace(spaceId);
            return collisionRuntime.rebuildAround(world, space, center, radius);
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
            return collisionRuntime.ensureAround(world, space, centers, radius, tick);
        });
    }

    public int clearWorldCollision(@Nonnull SpaceId spaceId) {
        return callOnPhysicsOwner("clear world collision", () -> {
            PhysicsSpace space = requireSpace(spaceId);
            return collisionRuntime.clear(space);
        });
    }

    @Nonnull
    public WorldCollisionStats getWorldCollisionStats() {
        return callOnPhysicsOwner("read world collision stats", collisionRuntime::getStats);
    }

    public void forEachBodySnapshot(@Nonnull SpaceId spaceId,
        @Nonnull Consumer<PhysicsBodySnapshotEntry> consumer) {
        snapshotState.forEachBodySnapshot(spaceId, consumer);
    }

    public int forEachBodySnapshotNear(@Nonnull SpaceId spaceId,
        @Nonnull Vector3f center,
        float radius,
        @Nonnull Consumer<PhysicsBodySnapshotEntry> consumer) {
        return snapshotState.forEachBodySnapshotNear(spaceId, center, radius, consumer);
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
        PhysicsSpace removed = spaceRuntime.removeSpace(spaceId);
        collisionRuntime.clear(spaceId, removed);
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
        for (SpaceId spaceId : spaceRuntime.getSpaceIds()) {
            removeSpaceDirect(spaceId, worldName);
        }
    }

    /**
     * Clears runtime physics state by replacing each native backend space with an empty
     * space that keeps the same logical id, backend, settings, gravity, and default-space mapping.
     */
    @Nonnull
    public PhysicsRuntimeResetResult resetRuntimeStateKeepingSpaces(@Nonnull String worldName) {
        return callOnPhysicsOwner("reset physics runtime state",
            () -> resetRuntimeStateKeepingSpacesDirect(worldName));
    }

    @Nonnull
    private PhysicsRuntimeResetResult resetRuntimeStateKeepingSpacesDirect(@Nonnull String worldName) {
        PhysicsRuntimeResetResult reset = spaceRuntime.resetKeepingSpaces(worldName,
            simulationRuntime.getWorldSettings().getStepMode(),
            collisionRuntime::clear,
            this::markWorldChanged);
        clearBodyStateDirect();
        return reset;
    }

    @Nonnull
    public PhysicsSpaceSettings getSpaceSettings(@Nonnull SpaceId spaceId) {
        return spaceRuntime.getSpaceSettings(spaceId);
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
        spaceRuntime.setSpaceSettings(spaceId, settings);
    }

    private void validateStepModeSupported(@Nonnull PhysicsStepMode stepMode) {
        spaceRuntime.validateStepModeSupported(stepMode);
    }

    /**
     * Registers an already-created live backend body with Impulse and returns its generated id.
     *
     * <p>This is the low-level path for code that already runs on the physics owner or received a
     * body from another owner callback. Ordinary plugin body creation should prefer
     * {@link PhysicsBodies} so creation, configuration, registration, and id return stay together.</p>
     */
    @Nonnull
    public PhysicsBodyId addBody(@Nonnull SpaceId spaceId,
        @Nonnull PhysicsBody body,
        @Nonnull PhysicsBodyKind kind,
        @Nonnull PhysicsBodyPersistenceMode persistenceMode) {
        return addBody(PhysicsBodyId.random(), spaceId, body, kind, persistenceMode);
    }

    /**
     * Registers an already-created live backend body with Impulse under an explicit stable id.
     *
     * <p>Use this for restore, temporary helpers, and internal body flows that must supply the id
     * directly. It is not the preferred public spawn path for ordinary gameplay bodies.</p>
     */
    @Nonnull
    public PhysicsBodyId addBody(@Nonnull PhysicsBodyId bodyId,
        @Nonnull SpaceId spaceId,
        @Nonnull PhysicsBody body,
        @Nonnull PhysicsBodyKind kind,
        @Nonnull PhysicsBodyPersistenceMode persistenceMode) {
        return callOnPhysicsOwner("add physics body",
            () -> addBodyDirect(bodyId, spaceId, body, kind, persistenceMode));
    }

    /**
     * Queues low-level registration for an already-created body.
     *
     * <p>The body must still have been created safely for the target backend owner. This method is
     * not a factory-based spawn helper.</p>
     */
    @Nonnull
    public PhysicsMutationHandle<PhysicsBodyId> addBodyAsync(@Nonnull SpaceId spaceId,
        @Nonnull PhysicsBody body,
        @Nonnull PhysicsBodyKind kind,
        @Nonnull PhysicsBodyPersistenceMode persistenceMode) {
        PhysicsBodyId bodyId = PhysicsBodyId.random();
        return addBodyAsync(bodyId, spaceId, body, kind, persistenceMode);
    }

    /**
     * Queues low-level registration for an already-created body under an explicit stable id.
     */
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
        handle.completion().whenComplete((ignored, _) -> clearPending.run());
        return handle;
    }

    @Nonnull
    private PhysicsBodyId addBodyDirect(@Nonnull PhysicsBodyId bodyId,
        @Nonnull SpaceId spaceId,
        @Nonnull PhysicsBody body,
        @Nonnull PhysicsBodyKind kind,
        @Nonnull PhysicsBodyPersistenceMode persistenceMode) {
        return bodyRuntime.addBody(bodyId, spaceId, body, kind, persistenceMode);
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
        bodyRuntime.destroyBody(bodyId, removeFromSpace);
    }

    public void destroyBody(@Nonnull PhysicsBody body) {
        runOnPhysicsOwner("destroy physics body", () -> destroyBodyDirect(body));
    }

    @Nonnull
    public PhysicsMutationHandle<Void> destroyBodyAsync(@Nonnull PhysicsBody body) {
        return enqueuePhysicsMutation("destroy physics body", () -> destroyBodyDirect(body));
    }

    private void destroyBodyDirect(@Nonnull PhysicsBody body) {
        bodyRuntime.destroyBody(body);
    }

    @Nullable
    public PhysicsBody getBody(@Nonnull PhysicsBodyId bodyId) {
        assertCanAccessLiveBackendDirectly("resolve live physics body");
        PhysicsBodyRegistration registration = bodyRegistry.getRegistration(bodyId);
        return registration != null ? registration.body() : null;
    }

    @Nullable
    public PhysicsBodyId getBodyId(@Nonnull PhysicsBody body) {
        return bodyRegistry.getBodyId(body);
    }

    @Nullable
    public PhysicsBodyRegistration getBodyRegistration(@Nonnull PhysicsBody body) {
        assertCanAccessLiveBackendDirectly("resolve live physics body registration");
        return bodyRegistry.getRegistration(body);
    }

    @Nullable
    public PhysicsBodyRegistration getRegistration(@Nonnull PhysicsBodyId bodyId) {
        assertCanAccessLiveBackendDirectly("resolve live physics body registration");
        return bodyRegistry.getRegistration(bodyId);
    }

    @Nullable
    public PhysicsBodyRegistrationView getBodyRegistrationView(@Nonnull PhysicsBodyId bodyId) {
        return bodyRegistry.getRegistrationView(bodyId);
    }

    public boolean isBodyCreationPending(@Nonnull PhysicsBodyId bodyId) {
        return bodyRuntime.isBodyCreationPending(bodyId);
    }

    @Nonnull
    public PhysicsBodyRegistration requireBodyRegistration(@Nonnull PhysicsBodyId bodyId) {
        PhysicsBodyRegistration registration = getRegistration(bodyId);
        if (registration == null) {
            throw new IllegalArgumentException("Physics body id=" + bodyId + " is not registered");
        }
        return registration;
    }

    @Nonnull
    public Collection<PhysicsBodyRegistration> getBodyRegistrations() {
        assertCanAccessLiveBackendDirectly("list live physics body registrations");
        return bodyRegistry.getRegistrations();
    }

    @Nonnull
    public Collection<PhysicsBodyRegistrationView> getBodyRegistrationViews() {
        return bodyRegistry.getRegistrationViews();
    }

    public int getBodyRegistrationCount() {
        return bodyRegistry.getRegistrationCount();
    }

    public int getBodyRegistrationCount(@Nonnull PhysicsBodyPersistenceMode persistenceMode) {
        return bodyRegistry.getRegistrationCount(persistenceMode);
    }

    @Nonnull
    public Collection<PhysicsBodyRegistration> getBodyRegistrations(@Nonnull PhysicsBodyKind kind) {
        assertCanAccessLiveBackendDirectly("list live physics body registrations");
        return bodyRegistry.getRegistrations(kind);
    }

    @Nonnull
    public Collection<PhysicsBodyRegistrationView> getBodyRegistrationViews(@Nonnull PhysicsBodyKind kind) {
        return bodyRegistry.getRegistrationViews(kind);
    }

    @Nonnull
    public Collection<Ref<EntityStore>> getBodyAttachments(@Nonnull PhysicsBodyId bodyId) {
        return visualRuntime.getAttachments(bodyId);
    }

    public void registerBodyAttachment(@Nonnull PhysicsBodyId bodyId, @Nonnull Ref<EntityStore> attachment) {
        visualRuntime.registerAttachment(bodyId, attachment);
    }

    public void unregisterBodyAttachment(@Nonnull PhysicsBodyId bodyId, @Nonnull Ref<EntityStore> attachment) {
        visualRuntime.unregisterAttachment(bodyId, attachment);
    }

    @Nullable
    public Ref<EntityStore> getGeneratedVisualProxy(@Nonnull PhysicsBodyId bodyId) {
        return visualRuntime.getGeneratedVisualProxy(bodyId);
    }

    @Nonnull
    public Collection<PhysicsBodyId> getGeneratedVisualProxyBodyIds() {
        return visualRuntime.getGeneratedVisualProxyBodyIds();
    }

    public void setGeneratedVisualProxy(@Nonnull PhysicsBodyId bodyId, @Nonnull Ref<EntityStore> proxy) {
        visualRuntime.setGeneratedVisualProxy(bodyId, proxy);
    }

    public void clearGeneratedVisualProxy(@Nonnull PhysicsBodyId bodyId) {
        visualRuntime.clearGeneratedVisualProxy(bodyId);
    }

    public boolean clearGeneratedVisualProxy(@Nonnull PhysicsBodyId bodyId,
        @Nonnull Ref<EntityStore> expectedProxy) {
        return visualRuntime.clearGeneratedVisualProxy(bodyId, expectedProxy);
    }

    public boolean isGeneratedVisualProxy(@Nonnull PhysicsBodyId bodyId,
        @Nonnull Ref<EntityStore> proxy) {
        return visualRuntime.isGeneratedVisualProxy(bodyId, proxy);
    }

    public void setSyntheticVisualInterests(@Nonnull Collection<VisualInterest> interests) {
        visualRuntime.setSyntheticVisualInterests(interests);
    }

    @Nonnull
    public List<VisualInterest> getSyntheticVisualInterests() {
        return visualRuntime.getSyntheticVisualInterests();
    }

    public void clearSyntheticVisualInterests() {
        visualRuntime.clearSyntheticVisualInterests();
    }

    public void clearBodies() {
        runOnPhysicsOwner("clear physics bodies", this::destroyRegisteredBodiesDirect);
    }

    @Nonnull
    public PhysicsMutationHandle<Void> clearBodiesAsync() {
        return enqueuePhysicsMutation("clear physics bodies", this::destroyRegisteredBodiesDirect);
    }

    private void destroyRegisteredBodiesDirect() {
        bodyRuntime.destroyRegisteredBodies();
    }

    private void clearBodyStateDirect() {
        bodyRuntime.clearBodyState();
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
        BodyVisualInterestState state = visualRuntime.getOrCreateBodyVisualInterestState(bodyId);
        state.advanceVisualInterestTick(visualInterestTick.get());
        return state;
    }

    @Nullable
    public BodyVisualInterestState getBodyVisualInterestState(@Nonnull PhysicsBodyId bodyId) {
        BodyVisualInterestState state = visualRuntime.getBodyVisualInterestState(bodyId);
        if (state != null) {
            state.advanceVisualInterestTick(visualInterestTick.get());
        }
        return state;
    }

    public long advanceVisualInterestTick() {
        return visualInterestTick.incrementAndGet();
    }

    public void markBodyControlled(@Nonnull PhysicsBodyId bodyId) {
        chunkRuntime.markBodyControlled(bodyId);
    }

    public void clearControlledBody(@Nonnull PhysicsBodyId bodyId) {
        chunkRuntime.clearControlledBody(bodyId);
    }

    public boolean isBodyControlled(@Nonnull PhysicsBodyId bodyId) {
        return chunkRuntime.isBodyControlled(bodyId);
    }

    public void updateChunkBoundarySafeState(@Nonnull PhysicsBodyId bodyId,
        @Nonnull Vector3f position,
        @Nonnull Quaternionf rotation) {
        chunkRuntime.updateChunkBoundarySafeState(bodyId, position, rotation);
    }

    @Nullable
    public ChunkBoundarySafeState getChunkBoundarySafeState(@Nonnull PhysicsBodyId bodyId) {
        return chunkRuntime.getChunkBoundarySafeState(bodyId);
    }

    public void pauseChunkBoundaryBody(@Nonnull PhysicsBodyId bodyId,
        long targetChunkIndex,
        @Nonnull PhysicsBodyType originalBodyType,
        @Nonnull Vector3f linearVelocity,
        @Nonnull Vector3f angularVelocity) {
        chunkRuntime.pauseChunkBoundaryBody(bodyId,
            targetChunkIndex,
            originalBodyType,
            linearVelocity,
            angularVelocity);
    }

    @Nullable
    public ChunkBoundaryPauseState getChunkBoundaryPauseState(@Nonnull PhysicsBodyId bodyId) {
        return chunkRuntime.getChunkBoundaryPauseState(bodyId);
    }

    public void clearChunkBoundaryPauseState(@Nonnull PhysicsBodyId bodyId) {
        chunkRuntime.clearChunkBoundaryPauseState(bodyId);
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
        bodyRuntime.clearBodyRuntimeState(bodyId);
    }

    public void markContinuousCollisionForced(@Nonnull PhysicsBody body) {
        chunkRuntime.markContinuousCollisionForced(body);
    }

    @Nonnull
    public Collection<PhysicsBody> getForcedContinuousCollisionBodies() {
        return chunkRuntime.getForcedContinuousCollisionBodies();
    }

    public void clearForcedContinuousCollisionBodies() {
        chunkRuntime.clearForcedContinuousCollisionBodies();
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
        spaceRuntime.copySettingsFrom(other.spaceRuntime);
        bodyRegistry.clear();
        runtimeState.clear();
        chunkRuntime.clear();
        visualRuntime.clear();
        snapshotState.clearBodySnapshots();
        collisionRuntime.clearAll();
        simulationRuntime.copyFrom(other.simulationRuntime);
        markWorldChanged();
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
        return spaceRuntime.replaceSpace(spaceId,
            replacement,
            worldName,
            simulationRuntime.getWorldSettings().getStepMode(),
            collisionRuntime::clear,
            this::markWorldChanged);
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

    private void markBodyCreationPending(@Nonnull PhysicsBodyId bodyId) {
        bodyRuntime.markBodyCreationPending(bodyId);
    }

    private void clearBodyCreationPending(@Nonnull PhysicsBodyId bodyId) {
        bodyRuntime.clearBodyCreationPending(bodyId);
    }

    private void markWorldChanged() {
        snapshotState.markWorldChanged();
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
