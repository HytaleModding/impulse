package dev.hytalemodding.impulse.core.internal.resources;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.api.BackendId;
import dev.hytalemodding.impulse.api.PhysicsBodySnapshot;
import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.PhysicsCollisionFilters;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.api.runtime.BackendRuntimeCodes;
import dev.hytalemodding.impulse.core.internal.modules.control.ControlLifecycle;
import dev.hytalemodding.impulse.core.internal.modules.control.PhysicsControlRuntimeState;
import dev.hytalemodding.impulse.core.internal.resources.body.PhysicsBodyRegistry;
import dev.hytalemodding.impulse.core.internal.resources.body.PhysicsBodyRuntimeState.BodySyncState;
import dev.hytalemodding.impulse.core.internal.resources.body.PhysicsBodyRuntime;
import dev.hytalemodding.impulse.core.internal.resources.body.PhysicsBodyRuntimeState;
import dev.hytalemodding.impulse.core.internal.resources.body.PhysicsBodySnapshotVisitor;
import dev.hytalemodding.impulse.core.internal.modules.worldcollision.PhysicsChunkBoundaryRuntime;
import dev.hytalemodding.impulse.core.internal.modules.worldcollision.PhysicsChunkBoundaryRuntime.ChunkBoundaryPauseState;
import dev.hytalemodding.impulse.core.internal.modules.worldcollision.PhysicsChunkBoundaryRuntime.ChunkBoundarySafeState;
import dev.hytalemodding.impulse.core.internal.resources.joint.PhysicsJointRegistration;
import dev.hytalemodding.impulse.core.internal.resources.joint.PhysicsJointRegistry;
import dev.hytalemodding.impulse.core.internal.resources.owner.PhysicsOwnerCallable;
import dev.hytalemodding.impulse.core.internal.resources.owner.PhysicsOwnerGateway;
import dev.hytalemodding.impulse.core.internal.resources.owner.PhysicsOwnerHandle;
import dev.hytalemodding.impulse.core.internal.resources.owner.PhysicsOwnerMutation;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsVisualRuntime.BodyVisualInterestState;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsVisualRuntime.VisualInterest;
import dev.hytalemodding.impulse.core.internal.simulation.recorder.MutablePhysicsCommandContext;
import dev.hytalemodding.impulse.core.internal.simulation.query.PhysicsInternalQuery;
import dev.hytalemodding.impulse.core.internal.simulation.PhysicsSimulationExecutor;
import dev.hytalemodding.impulse.core.internal.simulation.batch.RecordedPhysicsCommandBatch;
import dev.hytalemodding.impulse.core.internal.modules.worldcollision.WorldCollisionBuildOptions;
import dev.hytalemodding.impulse.core.internal.modules.worldcollision.PhysicsWorldCollisionRuntime;
import dev.hytalemodding.impulse.core.internal.modules.worldcollision.WorldCollisionLifecycle;
import dev.hytalemodding.impulse.core.internal.modules.worldcollision.WorldVoxelCollisionCache;
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyKind;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyPersistenceMode;
import dev.hytalemodding.impulse.core.internal.resources.body.PhysicsBodyRegistration;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyRegistrationView;
import dev.hytalemodding.impulse.core.plugin.modules.worldcollision.WorldCollisionBuildStats;
import dev.hytalemodding.impulse.core.plugin.modules.worldcollision.WorldCollisionMode;
import dev.hytalemodding.impulse.core.plugin.modules.worldcollision.WorldCollisionPrewarmStats;
import dev.hytalemodding.impulse.core.plugin.modules.worldcollision.WorldCollisionStats;
import dev.hytalemodding.impulse.core.plugin.events.PhysicsEventFrame;
import dev.hytalemodding.impulse.core.plugin.events.PhysicsFrameEvent;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsMutationHandle;
import dev.hytalemodding.impulse.core.plugin.joint.JointKey;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsWorldResource;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsSpaceSettings;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsStepMode;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsWorldCollisionSettings;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsWorldSettings;
import dev.hytalemodding.impulse.core.plugin.simulation.JointType;
import dev.hytalemodding.impulse.core.plugin.simulation.PhysicsCommandCompletion;
import dev.hytalemodding.impulse.core.plugin.simulation.PhysicsCommandHandle;
import dev.hytalemodding.impulse.core.plugin.simulation.PhysicsCommandRecipe;
import dev.hytalemodding.impulse.core.plugin.simulation.query.PhysicsQuery;
import dev.hytalemodding.impulse.core.plugin.simulation.query.PhysicsQueryHandle;
import dev.hytalemodding.impulse.core.plugin.snapshot.PhysicsBodySnapshotEntry;
import dev.hytalemodding.impulse.core.plugin.snapshot.PublishedPhysicsSnapshotFrame;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.logging.Level;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;

/**
 * Internal ECS resource implementation behind {@link PhysicsWorldResource}.
 */
public class PhysicsWorldRuntimeResource extends PhysicsWorldResource {

    private static final HytaleLogger LOGGER = HytaleLogger.get("Impulse");

    private final PhysicsSpaceRuntime spaceRuntime = new PhysicsSpaceRuntime();

    private final PhysicsBodyRegistry bodyRegistry = new PhysicsBodyRegistry();

    private final PhysicsWorldCollisionRuntime collisionRuntime =
        new PhysicsWorldCollisionRuntime();

    @Nonnull
    private final PhysicsSimulationRuntime simulationRuntime = new PhysicsSimulationRuntime();

    private final PhysicsBodyRuntimeState runtimeState = new PhysicsBodyRuntimeState();
    private final PhysicsControlRuntimeState controlRuntime = new PhysicsControlRuntimeState();
    private final PhysicsJointRegistry jointRegistry = new PhysicsJointRegistry();
    private final PhysicsChunkBoundaryRuntime chunkRuntime = new PhysicsChunkBoundaryRuntime();
    private final PhysicsVisualRuntime visualRuntime = new PhysicsVisualRuntime(this::clearBodySyncState);
    private final PhysicsWorldLifecycleState lifecycleState = new PhysicsWorldLifecycleState();
    private final PhysicsBodyRuntime bodyRuntime = new PhysicsBodyRuntime(spaceRuntime,
        bodyRegistry,
        runtimeState,
        controlRuntime,
        jointRegistry,
        chunkRuntime,
        visualRuntime,
        lifecycleState,
        this::markWorldChanged);

    private final AtomicLong visualInterestTick = new AtomicLong();
    private final PhysicsOwnerGateway ownerGateway = new PhysicsOwnerGateway();
    private final PhysicsSimulationExecutor simulationExecutor = new PhysicsSimulationExecutor(this);

    public PhysicsWorldRuntimeResource() {
        ControlLifecycle.registerResource(this);
        WorldCollisionLifecycle.registerResource(this);
    }

    @Nonnull
    public static PhysicsWorldRuntimeResource require(@Nonnull Store<EntityStore> store) {
        return require(store.getResource(PhysicsWorldResource.getResourceType()));
    }

    @Nonnull
    public static PhysicsWorldRuntimeResource require(@Nonnull PhysicsWorldResource resource) {
        if (resource instanceof PhysicsWorldRuntimeResource runtime) {
            return runtime;
        }
        throw new IllegalStateException(
            "Physics world resource is not the Impulse runtime implementation");
    }

    public void attachOwnerExecutor(@Nonnull PhysicsOwnerHandle ownerExecutor) {
        ownerGateway.attachOwnerExecutor(ownerExecutor);
    }

    public void detachOwnerExecutor(@Nonnull PhysicsOwnerHandle ownerExecutor) {
        ownerGateway.detachOwnerExecutor(ownerExecutor);
        if (!ownerGateway.hasOwnerExecutor()) {
            lifecycleState.publishDetachedOwnerRegistrationViews(bodyRegistry);
        }
    }

    public boolean canAccessLiveBackendDirectly() {
        return ownerGateway.canAccessLiveBackendDirectly();
    }

    public long worldEpoch() {
        return lifecycleState.worldEpoch();
    }

    public long commandWorldEpoch() {
        return lifecycleState.commandWorldEpoch();
    }

    @Nonnull
    public MutablePhysicsCommandContext createMutableCommandContext(long submittedServerTick) {
        return new MutablePhysicsCommandContext(submittedServerTick,
            lifecycleState.commandWorldEpoch());
    }

    @Nonnull
    public MutablePhysicsCommandContext createMutableCommandContext(long submittedServerTick,
        int expectedOperations) {
        return new MutablePhysicsCommandContext(submittedServerTick,
            lifecycleState.commandWorldEpoch(),
            expectedOperations);
    }

    @Nonnull
    @Override
    public PhysicsCommandHandle submitCommands(long submittedServerTick,
        @Nonnull PhysicsCommandRecipe recipe) {
        MutablePhysicsCommandContext context = createMutableCommandContext(submittedServerTick);
        context.compose(recipe);
        return submitRecordedCommands(context);
    }

    @Nonnull
    @Override
    public PhysicsCommandHandle submitCommands(long submittedServerTick,
        int expectedOperations,
        @Nonnull PhysicsCommandRecipe recipe) {
        MutablePhysicsCommandContext context =
            createMutableCommandContext(submittedServerTick, expectedOperations);
        context.compose(recipe);
        return submitRecordedCommands(context);
    }

    @Nonnull
    public PhysicsCommandHandle submitRecordedCommands(@Nonnull MutablePhysicsCommandContext context) {
        Objects.requireNonNull(context, "context");
        RecordedPhysicsCommandBatch batch =
            context.freezeInternal(lifecycleState.nextCommandBatchSequence());
        /*
         * Body creation through commands publishes registration views with the next snapshot frame.
         * Until that frame is applied, sync/materialization must treat creation as pending even
         * though the owner may already have completed the command batch.
         */
        boolean trackBodyCreationPublication = trackCommandBodyCreationPublication(batch);
        CompletableFuture<PhysicsCommandCompletion> completion =
            ownerGateway.enqueueCall("execute physics command batch", () -> executeCommandBatch(batch));
        if (trackBodyCreationPublication) {
            completion.whenComplete((ignored, failure) -> {
                if (failure != null) {
                    clearCommandBodyCreationPublication(batch);
                }
            });
        }
        return PhysicsCommandHandle.fromCompletionSummary(batch.publicBatch(), completion);
    }

    @Nonnull
    @Override
    public <R> PhysicsQueryHandle<R> query(@Nonnull PhysicsQuery<R> query) {
        Objects.requireNonNull(query, "query");
        CompletableFuture<R> completion =
            ownerGateway.enqueueCall("execute physics query", () -> simulationExecutor.query(query));
        return PhysicsQueryHandle.fromCompletion(query, completion);
    }

    @Nonnull
    public <R> CompletionStage<R> queryInternal(@Nonnull PhysicsInternalQuery<R> query) {
        Objects.requireNonNull(query, "query");
        CompletableFuture<R> completion =
            ownerGateway.enqueueCall("execute internal physics query",
                () -> simulationExecutor.queryInternal(query));
        return completion.minimalCompletionStage();
    }

    @Nonnull
    @Override
    public PhysicsEventFrame getLatestEventFrame() {
        return lifecycleState.latestEventFrame();
    }

    public void assertCanAccessLiveBackendDirectly(@Nonnull String operation) {
        ownerGateway.assertCanAccessLiveBackendDirectly(operation);
    }

    public void runOwnerMutation(@Nonnull String operation,
        @Nonnull PhysicsOwnerMutation mutation) {
        ownerGateway.run(operation, mutation);
    }

    @Nonnull
    public PhysicsMutationHandle<Void> enqueueOwnerMutation(@Nonnull String operation,
        @Nonnull PhysicsOwnerMutation mutation) {
        return enqueueOwnerMutation(operation, null, mutation);
    }


    @Nonnull
    public <T> PhysicsMutationHandle<T> enqueueOwnerMutation(@Nonnull String operation,
        @Nullable T value,
        @Nonnull PhysicsOwnerMutation mutation) {
        return ownerGateway.enqueue(operation, value, mutation);
    }


    @Nonnull
    public <T> T callOwner(@Nonnull String operation,
        @Nonnull PhysicsOwnerCallable<T> callable) {
        return ownerGateway.call(operation, callable);
    }


    @Nonnull
    public PhysicsWorldSettings getWorldSettings() {
        return simulationRuntime.getWorldSettings();
    }

    public void setWorldSettings(@Nonnull PhysicsWorldSettings settings) {
        PhysicsWorldSettings requested = new PhysicsWorldSettings(settings);
        runOwnerMutation("set physics world settings", () -> setWorldSettingsDirect(requested));
    }

    @Nonnull
    public PhysicsMutationHandle<Void> setWorldSettingsAsync(
        @Nonnull PhysicsWorldSettings settings) {
        PhysicsWorldSettings requested = new PhysicsWorldSettings(settings);
        return enqueueOwnerMutation("set physics world settings",
            () -> setWorldSettingsDirect(requested));
    }

    private void setWorldSettingsDirect(@Nonnull PhysicsWorldSettings settings) {
        validateStepModeSupported(settings.getStepMode());
        simulationRuntime.setWorldSettings(settings);
    }

    @Nonnull
    public SpaceId createSpace(@Nonnull BackendId backendId) {
        return createSpace(backendId, "<unknown>", PhysicsSpaceSettings.defaults());
    }

    @Nonnull
    public SpaceId createSpace(@Nonnull BackendId backendId, @Nonnull String worldName) {
        return createSpace(backendId, worldName, PhysicsSpaceSettings.defaults());
    }

    @Nonnull
    public SpaceId createSpace(@Nonnull BackendId backendId,
        @Nonnull String worldName,
        @Nonnull PhysicsSpaceSettings settings) {
        return createSpace(backendId, SpaceId.next(), worldName, settings);
    }

    @Nonnull
    public SpaceId createSpace(@Nonnull BackendId backendId,
        @Nonnull SpaceId spaceId,
        @Nonnull String worldName,
        @Nonnull PhysicsSpaceSettings settings) {
        callOwner("create physics space",
            () -> createSpaceDirect(backendId, spaceId, worldName, settings));
        return spaceId;
    }

    @Nonnull
    public PhysicsMutationHandle<SpaceId> createSpaceAsync(@Nonnull BackendId backendId,
        @Nonnull String worldName,
        @Nonnull PhysicsSpaceSettings settings) {
        SpaceId spaceId = SpaceId.next();
        return createSpaceAsync(backendId, spaceId, worldName, settings);
    }

    @Nonnull
    public PhysicsMutationHandle<SpaceId> createSpaceAsync(@Nonnull BackendId backendId,
        @Nonnull SpaceId spaceId,
        @Nonnull String worldName,
        @Nonnull PhysicsSpaceSettings settings) {
        return enqueueOwnerMutation("create physics space",
            spaceId,
            () -> createSpaceDirect(backendId, spaceId, worldName, settings));
    }

    @Nonnull
    private PhysicsSpaceBinding createSpaceDirect(@Nonnull BackendId backendId,
        @Nonnull SpaceId spaceId,
        @Nonnull String worldName,
        @Nonnull PhysicsSpaceSettings settings) {
        PhysicsSpaceBinding binding = spaceRuntime.createSpace(backendId,
            spaceId,
            worldName,
            settings,
            simulationRuntime.getWorldSettings().getStepMode());
        collisionRuntime.registerSpace(spaceId);
        markWorldChanged();
        return binding;
    }

    @Nullable
    public PhysicsSpaceBinding getSpaceBinding(@Nonnull SpaceId spaceId) {
        return spaceRuntime.getBinding(spaceId);
    }

    public boolean hasSpace(@Nonnull SpaceId spaceId) {
        return spaceRuntime.getBinding(spaceId) != null;
    }

    @Nonnull
    public PhysicsSpaceBinding requireSpaceBinding(@Nonnull SpaceId spaceId) {
        return spaceRuntime.requireBinding(spaceId);
    }

    @Nonnull
    public Collection<PhysicsSpaceBinding> getSpaceBindings() {
        return spaceRuntime.getBindings();
    }

    @Nonnull
    public Collection<SpaceId> getSpaceIds() {
        return spaceRuntime.getSpaceIds();
    }

    public int getSpaceCount() {
        return spaceRuntime.getSpaceCount();
    }

    /**
     * Iterate spaces without allocating a snapshot collection.
     * Use this from tick systems that do not mutate the space map while iterating.
     */
    @Nonnull
    public Iterable<PhysicsSpaceBinding> iterateSpaceBindings() {
        return spaceRuntime.iterateBindings();
    }

    public int refreshBodySnapshots() {
        return callOwner("refresh physics body snapshots", () -> {
            PublishedPhysicsSnapshotFrame frame = capturePublishedSnapshotFrameDirect(0L,
                0L,
                PublishedPhysicsSnapshotFrame.Status.COMPLETE,
                0L,
                false);
            return applyPublishedSnapshotFrame(frame);
        });
    }

    @Nonnull
    public PhysicsBodySnapshot getBodySnapshot(@Nonnull RigidBodyKey bodyKey) {
        PhysicsBodySnapshot snapshot = lifecycleState.getBodySnapshot(bodyKey);
        if (snapshot != null) {
            return snapshot;
        }
        return callOwner("refresh missing physics body snapshot",
            () -> getBodySnapshotDirect(bodyKey));
    }

    @Nullable
    public PhysicsBodySnapshot getBodySnapshotIfRegistered(@Nonnull RigidBodyKey bodyKey) {
        PhysicsBodySnapshot snapshot = lifecycleState.getBodySnapshot(bodyKey);
        if (snapshot != null) {
            return snapshot;
        }
        return callOwner("refresh optional physics body snapshot",
            () -> getBodySnapshotIfRegisteredDirect(bodyKey));
    }

    @Nonnull
    private PhysicsBodySnapshot getBodySnapshotDirect(@Nonnull RigidBodyKey bodyKey) {
        PhysicsBodySnapshot snapshot = lifecycleState.getBodySnapshot(bodyKey);
        if (snapshot != null) {
            return snapshot;
        }
        PhysicsBodyRegistration registration = requireBodyRegistration(bodyKey);
        return lifecycleState.captureBodySnapshot(registration);
    }

    @Nullable
    private PhysicsBodySnapshot getBodySnapshotIfRegisteredDirect(@Nonnull RigidBodyKey bodyKey) {
        PhysicsBodySnapshot snapshot = lifecycleState.getBodySnapshot(bodyKey);
        if (snapshot != null) {
            return snapshot;
        }
        PhysicsBodyRegistration registration = bodyRegistry.getRegistration(bodyKey);
        return registration != null ? lifecycleState.captureBodySnapshot(registration) : null;
    }

    /**
     * Captures an immutable snapshot frame on the physics owner lane.
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
        return capturePublishedSnapshotFrame(stepSequence,
            serverTick,
            status,
            stepNanos,
            profilingEnabled,
            List.of(),
            0);
    }

    @Nonnull
    public PublishedPhysicsSnapshotFrame capturePublishedSnapshotFrame(long stepSequence,
        long serverTick,
        @Nonnull PublishedPhysicsSnapshotFrame.Status status,
        long stepNanos,
        boolean profilingEnabled,
        @Nonnull List<PhysicsFrameEvent> physicsEvents,
        int droppedBackendEventCount) {
        return callOwner("capture published physics snapshot frame",
            () -> capturePublishedSnapshotFrameDirect(stepSequence,
                serverTick,
                status,
                stepNanos,
                profilingEnabled,
                physicsEvents,
                droppedBackendEventCount));
    }

    @Nonnull
    private PublishedPhysicsSnapshotFrame capturePublishedSnapshotFrameDirect(long stepSequence,
        long serverTick,
        @Nonnull PublishedPhysicsSnapshotFrame.Status status,
        long stepNanos,
        boolean profilingEnabled) {
        return capturePublishedSnapshotFrameDirect(stepSequence,
            serverTick,
            status,
            stepNanos,
            profilingEnabled,
            List.of(),
            0);
    }

    @Nonnull
    private PublishedPhysicsSnapshotFrame capturePublishedSnapshotFrameDirect(long stepSequence,
        long serverTick,
        @Nonnull PublishedPhysicsSnapshotFrame.Status status,
        long stepNanos,
        boolean profilingEnabled,
        @Nonnull List<PhysicsFrameEvent> physicsEvents,
        int droppedBackendEventCount) {

        assertCanAccessLiveBackendDirectly("capture published physics snapshot frame");
        return lifecycleState.capturePublishedSnapshotFrame(spaceRuntime.getBindings(),
            bodyRegistry,
            stepSequence,
            serverTick,
            status,
            stepNanos,
            profilingEnabled,
            physicsEvents,
            droppedBackendEventCount);
    }

    public int applyPublishedSnapshotFrame(@Nonnull PublishedPhysicsSnapshotFrame frame) {
        return applyPublishedSnapshotFrame(frame, 0L);
    }

    public int applyPublishedSnapshotFrame(@Nonnull PublishedPhysicsSnapshotFrame frame,
        long publicationServerTick) {
        return lifecycleState.applyPublishedSnapshotFrame(frame, bodyRegistry, publicationServerTick);
    }

    @Nonnull
    public PublishedPhysicsSnapshotFrame getLatestPublishedFrame() {
        return lifecycleState.latestPublishedFrame();
    }

    public long getLatestSnapshotAppliedNanos() {
        return lifecycleState.latestSnapshotAppliedNanos();
    }

    public int getBodySnapshotCount() {
        return lifecycleState.bodySnapshotCount();
    }

    public int getBodySnapshotCount(@Nonnull SpaceId spaceId) {
        return lifecycleState.bodySnapshotCount(spaceId);
    }

    public int getBodySnapshotCellCount() {
        return lifecycleState.bodySnapshotCellCount();
    }

    @Nonnull
    public WorldVoxelCollisionCache worldCollisionCache() {
        return collisionRuntime.worldVoxelCollisionCache();
    }

    @Nonnull
    public WorldCollisionBuildStats rebuildWorldCollisionAround(@Nonnull World world,
        @Nonnull SpaceId spaceId,
        @Nonnull Vector3d center,
        int radius) {
        requireWorldCollisionLifecycleEnabled();
        return callOwner("rebuild world collision", () -> {
            PhysicsSpaceBinding space = requireSpaceBinding(spaceId);
            requireWorldCollisionSpaceEnabled(spaceId);
            WorldCollisionBuildOptions buildOptions =
                WorldCollisionBuildOptions.fromSettings(getLiveSpaceSettings(spaceId)
                    .getWorldCollisionSettings());
            return collisionRuntime.rebuildAround(world,
                space,
                center,
                radius,
                buildOptions);
        });
    }

    @Nonnull
    public WorldCollisionBuildStats refreshWorldCollisionAround(@Nonnull World world,
        @Nonnull SpaceId spaceId,
        @Nonnull Vector3d center,
        int radius) {
        requireWorldCollisionLifecycleEnabled();
        return callOwner("refresh world collision", () -> {
            PhysicsSpaceBinding space = requireSpaceBinding(spaceId);
            requireWorldCollisionSpaceEnabled(spaceId);
            WorldCollisionBuildOptions buildOptions =
                WorldCollisionBuildOptions.fromSettings(getLiveSpaceSettings(spaceId)
                    .getWorldCollisionSettings());
            return collisionRuntime.refreshAround(world,
                space,
                center,
                radius,
                buildOptions);
        });
    }

    @Nonnull
    public WorldCollisionPrewarmStats ensureWorldCollisionAround(@Nonnull World world,
        @Nonnull SpaceId spaceId,
        @Nonnull Iterable<Vector3d> centers,
        int radius,
        long tick) {
        Objects.requireNonNull(centers, "centers");
        requireWorldCollisionLifecycleEnabled();
        return callOwner("ensure world collision", () -> {
            PhysicsSpaceBinding space = requireSpaceBinding(spaceId);
            requireWorldCollisionSpaceEnabled(spaceId);
            WorldCollisionBuildOptions buildOptions =
                WorldCollisionBuildOptions.fromSettings(getLiveSpaceSettings(spaceId)
                    .getWorldCollisionSettings());
            return collisionRuntime.ensureAround(world,
                space,
                centers,
                radius,
                tick,
                buildOptions);
        });
    }

    public int clearWorldCollision(@Nonnull SpaceId spaceId) {
        return callOwner("clear world collision", () -> {
            PhysicsSpaceBinding space = requireSpaceBinding(spaceId);
            return collisionRuntime.clear(space);
        });
    }

    public long worldCollisionStreamingRevision(@Nonnull SpaceId spaceId) {
        return collisionRuntime.streamingRevision(spaceId);
    }

    @Nonnull
    public WorldCollisionStats getWorldCollisionStats() {
        return callOwner("read world collision stats", collisionRuntime::getStats);
    }

    public void disableWorldCollisionLifecycle() {
        try {
            runOwnerMutation("disable world collision lifecycle", this::disableWorldCollisionLifecycleDirect);
        } catch (RejectedExecutionException ignored) {
            // The server can unload the subplugin after a world owner lane has already closed.
        } catch (RuntimeException exception) {
            LOGGER.at(Level.WARNING).log("Failed to disable world collision lifecycle: %s",
                exception.getMessage());
        }
    }

    private void disableWorldCollisionLifecycleDirect() {
        collisionRuntime.clearRetainedTerrain(spaceRuntime.getBindings());
        restoreCollisionLodFiltersDirect();
        restoreChunkBoundaryPausedBodiesDirect();
        chunkRuntime.clearChunkBoundaryStates();
    }

    private void restoreCollisionLodFiltersDirect() {
        int fullDynamicMask = PhysicsCollisionFilters.TERRAIN
            | PhysicsCollisionFilters.DYNAMIC_BODY;
        for (PhysicsBodyRegistration registration : bodyRegistry.getRegistrations(PhysicsBodyKind.BODY)) {
            PhysicsSpaceBinding space = getSpaceBinding(registration.spaceId());
            if (space == null) {
                continue;
            }
            space.runtime().setBodyCollisionFilter(space.backendSpaceHandle().value(),
                registration.backendBodyHandle().value(),
                PhysicsCollisionFilters.DYNAMIC_BODY,
                fullDynamicMask);
            space.runtime().activateBody(space.backendSpaceHandle().value(),
                registration.backendBodyHandle().value());
        }
    }

    private void restoreChunkBoundaryPausedBodiesDirect() {
        for (RigidBodyKey bodyKey : chunkRuntime.getChunkBoundaryPausedBodyKeys()) {
            ChunkBoundaryPauseState pauseState = chunkRuntime.getChunkBoundaryPauseState(bodyKey);
            PhysicsBodyRegistration registration = getRegistration(bodyKey);
            if (pauseState == null || registration == null) {
                chunkRuntime.clearChunkBoundaryPauseState(bodyKey);
                continue;
            }
            PhysicsSpaceBinding space = getSpaceBinding(registration.spaceId());
            if (space == null) {
                chunkRuntime.clearChunkBoundaryPauseState(bodyKey);
                continue;
            }
            space.runtime().setBodyType(space.backendSpaceHandle().value(),
                registration.backendBodyHandle().value(),
                BackendRuntimeCodes.bodyTypeCode(pauseState.getOriginalBodyType()));
            space.runtime().setBodyVelocity(space.backendSpaceHandle().value(),
                registration.backendBodyHandle().value(),
                pauseState.getLinearVelocity().x,
                pauseState.getLinearVelocity().y,
                pauseState.getLinearVelocity().z,
                pauseState.getAngularVelocity().x,
                pauseState.getAngularVelocity().y,
                pauseState.getAngularVelocity().z);
            space.runtime().activateBody(space.backendSpaceHandle().value(),
                registration.backendBodyHandle().value());
            chunkRuntime.clearChunkBoundaryPauseState(bodyKey);
        }
    }

    private static void requireWorldCollisionLifecycleEnabled() {
        if (!WorldCollisionLifecycle.isEnabled()) {
            throw new IllegalStateException("Impulse world collision subplugin is disabled");
        }
    }

    private void requireWorldCollisionSpaceEnabled(@Nonnull SpaceId spaceId) {
        if (getLiveSpaceSettings(spaceId).getWorldCollisionSettings().getWorldCollisionMode()
            == WorldCollisionMode.NONE) {
            throw new IllegalStateException("World collision is disabled for space " + spaceId);
        }
    }

    public void forEachBodySnapshot(@Nonnull SpaceId spaceId,
        @Nonnull Consumer<PhysicsBodySnapshotEntry> consumer) {
        lifecycleState.forEachBodySnapshot(spaceId, consumer);
    }

    public void forEachIndexedBodySnapshot(@Nonnull SpaceId spaceId,
        @Nonnull PhysicsBodySnapshotVisitor visitor) {
        lifecycleState.forEachIndexedBodySnapshot(spaceId, visitor);
    }

    public int forEachBodySnapshotNear(@Nonnull SpaceId spaceId,
        @Nonnull Vector3f center,
        float radius,
        @Nonnull Consumer<PhysicsBodySnapshotEntry> consumer) {
        return lifecycleState.forEachBodySnapshotNear(spaceId, center, radius, consumer);
    }

    public int forEachIndexedBodySnapshotNear(@Nonnull SpaceId spaceId,
        @Nonnull Vector3f center,
        float radius,
        @Nonnull PhysicsBodySnapshotVisitor visitor) {
        return lifecycleState.forEachIndexedBodySnapshotNear(spaceId, center, radius, visitor);
    }

    public void removeSpace(@Nonnull SpaceId spaceId) {
        removeSpace(spaceId, "<unknown>");
    }

    public void removeSpace(@Nonnull SpaceId spaceId, @Nonnull String worldName) {
        runOwnerMutation("remove physics space", () -> removeSpaceDirect(spaceId, worldName));
    }

    @Nonnull
    public PhysicsMutationHandle<SpaceId> removeSpaceAsync(@Nonnull SpaceId spaceId,
        @Nonnull String worldName) {
        return enqueueOwnerMutation("remove physics space",
            spaceId,
            () -> removeSpaceDirect(spaceId, worldName));
    }

    private void removeSpaceDirect(@Nonnull SpaceId spaceId, @Nonnull String worldName) {
        PhysicsSpaceBinding removed = spaceRuntime.removeSpace(spaceId);
        collisionRuntime.clear(spaceId, removed);
        if (removed != null) {
            jointRegistry.unregisterSpace(spaceId);
            for (PhysicsBodyRegistration registration : bodyRegistry.getRegistrations()) {
                if (registration.spaceId().equals(spaceId)) {
                    destroyBody(registration.bodyKey(), false);
                }
            }
            LOGGER.at(Level.FINE).log(
                "World %s removed physics space id=%s backend=%s",
                worldName,
                removed.spaceId(),
                removed.backendId());
            PhysicsSpaceRuntime.closeBindingSilently(removed, worldName, "removed physics space");
            markWorldChanged();
        }
    }

    public void clearAllSpaces(@Nonnull String worldName) {
        runOwnerMutation("clear physics spaces", () -> clearAllSpacesDirect(worldName));
    }

    @Nonnull
    public PhysicsMutationHandle<Void> clearAllSpacesAsync(@Nonnull String worldName) {
        return enqueueOwnerMutation("clear physics spaces",
            () -> clearAllSpacesDirect(worldName));
    }

    private void clearAllSpacesDirect(@Nonnull String worldName) {
        for (SpaceId spaceId : spaceRuntime.getSpaceIds()) {
            removeSpaceDirect(spaceId, worldName);
        }
    }

    /**
     * Clears runtime physics state by replacing each native backend space with an empty
     * space that keeps the same logical id, backend, settings, and gravity.
     */
    @Nonnull
    public PhysicsRuntimeResetResult resetRuntimeStateKeepingSpaces(@Nonnull String worldName) {
        return callOwner("reset physics runtime state",
            () -> resetRuntimeStateKeepingSpacesDirect(worldName));
    }

    @Nonnull
    private PhysicsRuntimeResetResult resetRuntimeStateKeepingSpacesDirect(@Nonnull String worldName) {
        PhysicsRuntimeResetResult reset = spaceRuntime.resetKeepingSpaces(worldName,
            simulationRuntime.getWorldSettings().getStepMode());
        collisionRuntime.clearAll();
        clearRuntimeTopologyDirect(false);
        markWorldChanged();
        return reset;
    }

    @Nonnull
    public PhysicsSpaceSettings getSpaceSettings(@Nonnull SpaceId spaceId) {
        return spaceRuntime.getSpaceSettings(spaceId);
    }

    @Nonnull
    public PhysicsSpaceSettings getLiveSpaceSettings(@Nonnull SpaceId spaceId) {
        return spaceRuntime.getLiveSpaceSettings(spaceId);
    }

    public void setSpaceSettings(@Nonnull SpaceId spaceId, @Nonnull PhysicsSpaceSettings settings) {
        PhysicsSpaceSettings requested = new PhysicsSpaceSettings(settings);
        runOwnerMutation("set physics space settings", () -> setSpaceSettingsDirect(spaceId, requested));
    }

    @Nonnull
    public PhysicsMutationHandle<SpaceId> setSpaceSettingsAsync(@Nonnull SpaceId spaceId,
        @Nonnull PhysicsSpaceSettings settings) {
        PhysicsSpaceSettings requested = new PhysicsSpaceSettings(settings);
        return enqueueOwnerMutation("set physics space settings",
            spaceId,
            () -> setSpaceSettingsDirect(spaceId, requested));
    }

    private void setSpaceSettingsDirect(@Nonnull SpaceId spaceId,
        @Nonnull PhysicsSpaceSettings settings) {
        PhysicsWorldCollisionSettings previousCollisionSettings =
            spaceRuntime.getLiveSpaceSettings(spaceId).getWorldCollisionSettings();
        boolean worldCollisionSettingsChanged =
            worldCollisionStreamingSettingsChanged(previousCollisionSettings,
                settings.getWorldCollisionSettings());
        boolean terrainRepresentationChanged =
            previousCollisionSettings.isNativeVoxelTerrainEnabled()
                != settings.getWorldCollisionSettings().isNativeVoxelTerrainEnabled();
        boolean worldCollisionDisabled =
            settings.getWorldCollisionSettings().getWorldCollisionMode() == WorldCollisionMode.NONE
                && previousCollisionSettings.getWorldCollisionMode() != WorldCollisionMode.NONE;
        spaceRuntime.setSpaceSettings(spaceId, settings);
        if (worldCollisionDisabled || terrainRepresentationChanged) {
            collisionRuntime.clear(requireSpaceBinding(spaceId));
        } else if (worldCollisionSettingsChanged) {
            collisionRuntime.incrementStreamingRevision(spaceId);
        }
    }

    private static boolean worldCollisionStreamingSettingsChanged(
        @Nonnull PhysicsWorldCollisionSettings previous,
        @Nonnull PhysicsWorldCollisionSettings next) {
        return previous.getWorldCollisionMode() != next.getWorldCollisionMode()
            || previous.getWorldCollisionRadius() != next.getWorldCollisionRadius()
            || previous.getWorldCollisionBodyRadius() != next.getWorldCollisionBodyRadius()
            || previous.getWorldCollisionTtlTicks() != next.getWorldCollisionTtlTicks()
            || previous.isNativeVoxelTerrainEnabled() != next.isNativeVoxelTerrainEnabled();
    }

    private void validateStepModeSupported(@Nonnull PhysicsStepMode stepMode) {
        spaceRuntime.validateStepModeSupported(stepMode);
    }

    @Nonnull
    public RigidBodyKey addBodyOnOwner(@Nonnull RigidBodyKey bodyKey,
        @Nonnull SpaceId spaceId,
        @Nonnull BackendBodyHandle backendBodyHandle,
        @Nonnull PhysicsBodyKind kind,
        @Nonnull PhysicsBodyPersistenceMode persistenceMode) {
        assertCanAccessLiveBackendDirectly("add physics body");
        return addBodyDirect(bodyKey, spaceId, backendBodyHandle, kind, persistenceMode);
    }

    @Nonnull
    private RigidBodyKey addBodyDirect(@Nonnull RigidBodyKey bodyKey,
        @Nonnull SpaceId spaceId,
        @Nonnull BackendBodyHandle backendBodyHandle,
        @Nonnull PhysicsBodyKind kind,
        @Nonnull PhysicsBodyPersistenceMode persistenceMode) {
        return bodyRuntime.addBody(bodyKey, spaceId, backendBodyHandle, kind, persistenceMode);
    }

    public void destroyBody(@Nonnull RigidBodyKey bodyKey) {
        destroyBody(bodyKey, true);
    }

    @Nonnull
    public PhysicsMutationHandle<RigidBodyKey> destroyBodyAsync(@Nonnull RigidBodyKey bodyKey) {
        return destroyBodyAsync(bodyKey, true);
    }

    public void destroyBody(@Nonnull RigidBodyKey bodyKey, boolean removeFromSpace) {
        runOwnerMutation("destroy physics body", () -> destroyBodyDirect(bodyKey, removeFromSpace));
    }

    @Nonnull
    public PhysicsMutationHandle<RigidBodyKey> destroyBodyAsync(@Nonnull RigidBodyKey bodyKey,
        boolean removeFromSpace) {
        return enqueueOwnerMutation("destroy physics body",
            bodyKey,
            () -> destroyBodyDirect(bodyKey, removeFromSpace));
    }

    private void destroyBodyDirect(@Nonnull RigidBodyKey bodyKey, boolean removeFromSpace) {
        bodyRuntime.destroyBody(bodyKey, removeFromSpace);
    }

    @Nullable
    public RigidBodyKey getBodyKey(@Nonnull SpaceId spaceId, long backendBodyId) {
        return bodyRegistry.getBodyKey(spaceId, backendBodyId);
    }

    @Nullable
    public PhysicsBodyRegistration getBodyRegistration(@Nonnull SpaceId spaceId, long backendBodyId) {
        assertCanAccessLiveBackendDirectly("resolve physics body registration");
        RigidBodyKey bodyKey = bodyRegistry.getBodyKey(spaceId, backendBodyId);
        return bodyKey != null ? bodyRegistry.getRegistration(bodyKey) : null;
    }

    @Nullable
    public PhysicsBodyRegistration getRegistration(@Nonnull RigidBodyKey bodyKey) {
        assertCanAccessLiveBackendDirectly("resolve physics body registration");
        return bodyRegistry.getRegistration(bodyKey);
    }

    @Nullable
    public PhysicsBodyRegistrationView getBodyRegistrationView(@Nonnull RigidBodyKey bodyKey) {
        return bodyRegistry.getPublishedRegistrationView(bodyKey);
    }

    @Nonnull
    public JointKey addJointOnOwner(@Nonnull JointKey jointKey,
        @Nonnull SpaceId spaceId,
        @Nonnull BackendJointHandle backendJointHandle,
        @Nonnull RigidBodyKey bodyA,
        @Nonnull RigidBodyKey bodyB,
        @Nonnull JointType type,
        float anchorAX,
        float anchorAY,
        float anchorAZ,
        float anchorBX,
        float anchorBY,
        float anchorBZ,
        float axisX,
        float axisY,
        float axisZ,
        float restLength,
        float stiffness,
        float damping,
        float lowerLimit,
        float upperLimit,
        boolean motorEnabled,
        float motorTargetVelocity,
        float motorMaxForce) {
        assertCanAccessLiveBackendDirectly("add physics joint");
        return addJointDirect(jointKey,
            spaceId,
            backendJointHandle,
            bodyA,
            bodyB,
            type,
            anchorAX,
            anchorAY,
            anchorAZ,
            anchorBX,
            anchorBY,
            anchorBZ,
            axisX,
            axisY,
            axisZ,
            restLength,
            stiffness,
            damping,
            lowerLimit,
            upperLimit,
            motorEnabled,
            motorTargetVelocity,
            motorMaxForce);
    }

    @Nonnull
    private JointKey addJointDirect(@Nonnull JointKey jointKey,
        @Nonnull SpaceId spaceId,
        @Nonnull BackendJointHandle backendJointHandle,
        @Nonnull RigidBodyKey bodyA,
        @Nonnull RigidBodyKey bodyB,
        @Nonnull JointType type,
        float anchorAX,
        float anchorAY,
        float anchorAZ,
        float anchorBX,
        float anchorBY,
        float anchorBZ,
        float axisX,
        float axisY,
        float axisZ,
        float restLength,
        float stiffness,
        float damping,
        float lowerLimit,
        float upperLimit,
        boolean motorEnabled,
        float motorTargetVelocity,
        float motorMaxForce) {
        if (spaceRuntime.getBinding(spaceId) == null) {
            throw new IllegalArgumentException("Physics space id=" + spaceId + " is not registered");
        }
        jointRegistry.registerJoint(jointKey,
            spaceId,
            backendJointHandle,
            bodyA,
            bodyB,
            type,
            anchorAX,
            anchorAY,
            anchorAZ,
            anchorBX,
            anchorBY,
            anchorBZ,
            axisX,
            axisY,
            axisZ,
            restLength,
            stiffness,
            damping,
            lowerLimit,
            upperLimit,
            motorEnabled,
            motorTargetVelocity,
            motorMaxForce);
        markWorldChanged();
        return jointKey;
    }

    public boolean removeJoint(@Nonnull JointKey jointKey) {
        return callOwner("remove physics joint", () -> removeJointDirect(jointKey));
    }

    private boolean removeJointDirect(@Nonnull JointKey jointKey) {
        PhysicsJointRegistration registration = jointRegistry.getRegistration(jointKey);
        if (registration == null) {
            return false;
        }

        PhysicsSpaceBinding binding = spaceRuntime.getBinding(registration.spaceId());
        if (binding != null) {
            binding.runtime().removeJoint(binding.backendSpaceHandle().value(), registration.backendJointHandle().value());
        }
        jointRegistry.unregisterJoint(jointKey);
        markWorldChanged();
        return true;
    }

    @Nullable
    public JointKey getJointKey(@Nonnull SpaceId spaceId, long backendJointId) {
        assertCanAccessLiveBackendDirectly("resolve physics joint key");
        return jointRegistry.getJointKey(spaceId, backendJointId);
    }

    @Nullable
    public PhysicsJointRegistration getJointRegistration(@Nonnull JointKey jointKey) {
        assertCanAccessLiveBackendDirectly("resolve physics joint registration");
        return jointRegistry.getRegistration(jointKey);
    }

    @Nonnull
    public Collection<PhysicsJointRegistration> getJointRegistrations() {
        assertCanAccessLiveBackendDirectly("list physics joint registrations");
        return jointRegistry.getRegistrations();
    }

    @Nullable
    public PhysicsJointRegistration findJointBetween(@Nonnull SpaceId spaceId,
        @Nonnull RigidBodyKey bodyA,
        @Nonnull RigidBodyKey bodyB) {
        assertCanAccessLiveBackendDirectly("resolve physics joint registration");
        return jointRegistry.findJointBetween(spaceId, bodyA, bodyB);
    }

    public boolean isBodyCreationPending(@Nonnull RigidBodyKey bodyKey) {
        return lifecycleState.isBodyCreationPending(bodyKey,
            bodyRuntime.isBodyCreationPending(bodyKey),
            ownerGateway.hasOwnerExecutor());
    }

    public boolean hasPublishedOrPendingBodyRegistration(@Nonnull RigidBodyKey bodyKey) {
        return getBodyRegistrationView(bodyKey) != null
            || isBodyCreationPending(bodyKey);
    }

    @Nonnull
    public PhysicsBodyRegistration requireBodyRegistration(@Nonnull RigidBodyKey bodyKey) {
        PhysicsBodyRegistration registration = getRegistration(bodyKey);
        if (registration == null) {
            throw new IllegalArgumentException("Physics body key=" + bodyKey + " is not registered");
        }
        return registration;
    }

    @Nonnull
    public Collection<PhysicsBodyRegistration> getBodyRegistrations() {
        assertCanAccessLiveBackendDirectly("list physics body registrations");
        return bodyRegistry.getRegistrations();
    }

    @Nonnull
    public Collection<PhysicsBodyRegistrationView> getBodyRegistrationViews() {
        return bodyRegistry.getPublishedRegistrationViews();
    }

    public int getBodyRegistrationCount() {
        return bodyRegistry.getPublishedRegistrationCount();
    }

    public int getBodyRegistrationCount(@Nonnull PhysicsBodyPersistenceMode persistenceMode) {
        return bodyRegistry.getPublishedRegistrationCount(persistenceMode);
    }

    @Nonnull
    public Collection<PhysicsBodyRegistration> getBodyRegistrations(@Nonnull PhysicsBodyKind kind) {
        assertCanAccessLiveBackendDirectly("list physics body registrations");
        return bodyRegistry.getRegistrations(kind);
    }

    @Nonnull
    public Collection<PhysicsBodyRegistrationView> getBodyRegistrationViews(@Nonnull PhysicsBodyKind kind) {
        return bodyRegistry.getPublishedRegistrationViews(kind);
    }

    @Nonnull
    public Collection<Ref<EntityStore>> getBodyAttachments(@Nonnull RigidBodyKey bodyKey) {
        return visualRuntime.getAttachments(bodyKey);
    }

    public boolean hasBodyAttachments(@Nonnull RigidBodyKey bodyKey) {
        return visualRuntime.hasAttachments(bodyKey);
    }

    public void registerBodyAttachment(@Nonnull RigidBodyKey bodyKey, @Nonnull Ref<EntityStore> attachment) {
        visualRuntime.registerAttachment(bodyKey, attachment);
    }

    public void unregisterBodyAttachment(@Nonnull RigidBodyKey bodyKey, @Nonnull Ref<EntityStore> attachment) {
        visualRuntime.unregisterAttachment(bodyKey, attachment);
    }

    @Nullable
    public Ref<EntityStore> getGeneratedVisualProxy(@Nonnull RigidBodyKey bodyKey) {
        return visualRuntime.getGeneratedVisualProxy(bodyKey);
    }

    @Nonnull
    public Collection<RigidBodyKey> getGeneratedVisualProxyBodyKeys() {
        return visualRuntime.getGeneratedVisualProxyBodyKeys();
    }

    public int getGeneratedVisualProxyCount() {
        return visualRuntime.generatedVisualProxyCount();
    }

    public void setGeneratedVisualProxy(@Nonnull RigidBodyKey bodyKey, @Nonnull Ref<EntityStore> proxy) {
        visualRuntime.setGeneratedVisualProxy(bodyKey, proxy);
    }

    public void clearGeneratedVisualProxy(@Nonnull RigidBodyKey bodyKey) {
        visualRuntime.clearGeneratedVisualProxy(bodyKey);
    }

    public boolean clearGeneratedVisualProxy(@Nonnull RigidBodyKey bodyKey,
        @Nonnull Ref<EntityStore> expectedProxy) {
        return visualRuntime.clearGeneratedVisualProxy(bodyKey, expectedProxy);
    }

    public boolean isGeneratedVisualProxy(@Nonnull RigidBodyKey bodyKey,
        @Nonnull Ref<EntityStore> proxy) {
        return visualRuntime.isGeneratedVisualProxy(bodyKey, proxy);
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
        runOwnerMutation("clear physics bodies", this::destroyRegisteredBodiesDirect);
    }

    @Nonnull
    public PhysicsMutationHandle<Void> clearBodiesAsync() {
        return enqueueOwnerMutation("clear physics bodies", this::destroyRegisteredBodiesDirect);
    }

    private void destroyRegisteredBodiesDirect() {
        bodyRuntime.destroyRegisteredBodies();
    }

    private void clearBodyStateDirect() {
        clearRuntimeTopologyDirect(false);
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
    public BodyVisualInterestState getOrCreateBodyVisualInterestState(@Nonnull RigidBodyKey bodyKey) {
        BodyVisualInterestState state = visualRuntime.getOrCreateBodyVisualInterestState(bodyKey);
        state.advanceVisualInterestTick(visualInterestTick.get());
        return state;
    }

    @Nullable
    public BodyVisualInterestState getBodyVisualInterestState(@Nonnull RigidBodyKey bodyKey) {
        BodyVisualInterestState state = visualRuntime.getBodyVisualInterestState(bodyKey);
        if (state != null) {
            state.advanceVisualInterestTick(visualInterestTick.get());
        }
        return state;
    }

    public long advanceVisualInterestTick() {
        return visualInterestTick.incrementAndGet();
    }

    public void markBodyControlled(@Nonnull RigidBodyKey bodyKey) {
        controlRuntime.markBodyControlled(bodyKey);
    }

    public void clearControlledBody(@Nonnull RigidBodyKey bodyKey) {
        controlRuntime.clearControlledBody(bodyKey);
    }

    public boolean isBodyControlled(@Nonnull RigidBodyKey bodyKey) {
        return controlRuntime.isBodyControlled(bodyKey);
    }

    public void disableControlLifecycle() {
        controlRuntime.clear();
    }

    public void updateChunkBoundarySafeState(@Nonnull RigidBodyKey bodyKey,
        @Nonnull Vector3f position,
        @Nonnull Quaternionf rotation) {
        chunkRuntime.updateChunkBoundarySafeState(bodyKey, position, rotation);
    }

    public void updateChunkBoundarySafeState(@Nonnull RigidBodyKey bodyKey,
        @Nonnull PhysicsBodySnapshot snapshot) {
        chunkRuntime.updateChunkBoundarySafeState(bodyKey, snapshot);
    }

    @Nullable
    public ChunkBoundarySafeState getChunkBoundarySafeState(@Nonnull RigidBodyKey bodyKey) {
        return chunkRuntime.getChunkBoundarySafeState(bodyKey);
    }

    public void pauseChunkBoundaryBody(@Nonnull RigidBodyKey bodyKey,
        long targetChunkIndex,
        @Nonnull PhysicsBodyType originalBodyType,
        @Nonnull Vector3f linearVelocity,
        @Nonnull Vector3f angularVelocity) {
        chunkRuntime.pauseChunkBoundaryBody(bodyKey,
            targetChunkIndex,
            originalBodyType,
            linearVelocity,
            angularVelocity);
    }

    public void pauseChunkBoundaryBody(@Nonnull RigidBodyKey bodyKey,
        long targetChunkIndex,
        @Nonnull PhysicsBodySnapshot snapshot) {
        chunkRuntime.pauseChunkBoundaryBody(bodyKey, targetChunkIndex, snapshot);
    }

    public void pauseChunkBoundaryBody(@Nonnull RigidBodyKey bodyKey,
        long targetChunkIndex,
        @Nonnull long[] targetChunkIndices,
        @Nonnull PhysicsBodySnapshot snapshot) {
        chunkRuntime.pauseChunkBoundaryBody(bodyKey, targetChunkIndex, targetChunkIndices, snapshot);
    }

    @Nullable
    public ChunkBoundaryPauseState getChunkBoundaryPauseState(@Nonnull RigidBodyKey bodyKey) {
        return chunkRuntime.getChunkBoundaryPauseState(bodyKey);
    }

    public void clearChunkBoundaryPauseState(@Nonnull RigidBodyKey bodyKey) {
        chunkRuntime.clearChunkBoundaryPauseState(bodyKey);
    }

    public void clearBodyRuntimeState(@Nonnull RigidBodyKey bodyKey) {
        runOwnerMutation("clear physics body runtime state", () -> clearBodyRuntimeStateDirect(bodyKey));
    }

    @Nonnull
    public PhysicsMutationHandle<RigidBodyKey> clearBodyRuntimeStateAsync(
        @Nonnull RigidBodyKey bodyKey) {
        return enqueueOwnerMutation("clear physics body runtime state",
            bodyKey,
            () -> clearBodyRuntimeStateDirect(bodyKey));
    }

    private void clearBodyRuntimeStateDirect(@Nonnull RigidBodyKey bodyKey) {
        bodyRuntime.clearBodyRuntimeState(bodyKey);
    }

    public void markContinuousCollisionForced(@Nonnull RigidBodyKey bodyKey) {
        chunkRuntime.markContinuousCollisionForced(bodyKey);
    }

    @Nonnull
    public Collection<RigidBodyKey> getForcedContinuousCollisionBodyKeys() {
        return chunkRuntime.getForcedContinuousCollisionBodyKeys();
    }

    public boolean hasForcedContinuousCollisionBodies() {
        return chunkRuntime.hasForcedContinuousCollisionBodies();
    }

    public void forEachForcedContinuousCollisionBody(@Nonnull Consumer<RigidBodyKey> consumer) {
        chunkRuntime.forEachForcedContinuousCollisionBody(consumer);
    }

    public void clearForcedContinuousCollisionBodies() {
        chunkRuntime.clearForcedContinuousCollisionBodies();
    }

    public void copyFrom(@Nonnull PhysicsWorldResource other) {
        runOwnerMutation("copy physics world resource", () -> copyFromDirect(other));
    }

    @Nonnull
    public PhysicsMutationHandle<Void> copyFromAsync(@Nonnull PhysicsWorldResource other) {
        return enqueueOwnerMutation("copy physics world resource", () -> copyFromDirect(other));
    }

    private void copyFromDirect(@Nonnull PhysicsWorldResource other) {
        if (this == other) {
            return;
        }
        PhysicsWorldRuntimeResource otherRuntime = require(other);
        spaceRuntime.clearLiveTopology("<copy>");
        clearRuntimeTopologyDirect(true);
        simulationRuntime.copyFrom(otherRuntime.simulationRuntime);
        markWorldChanged();
    }

    private void clearRuntimeTopologyDirect(boolean clearCollision) {
        bodyRuntime.clearBodyStateWithoutMarkingWorldChanged();
        if (clearCollision) {
            collisionRuntime.clearAllAndUnregisterSpaces();
        }
    }

    private void markBodyCreationPending(@Nonnull RigidBodyKey bodyKey) {
        bodyRuntime.markBodyCreationPending(bodyKey);
    }

    private void clearBodyCreationPending(@Nonnull RigidBodyKey bodyKey) {
        bodyRuntime.clearBodyCreationPending(bodyKey);
    }

    private boolean trackCommandBodyCreationPublication(@Nonnull RecordedPhysicsCommandBatch batch) {
        return lifecycleState.trackBodyCreationPublication(batch, ownerGateway.hasOwnerExecutor());
    }

    private void clearCommandBodyCreationPublication(@Nonnull RecordedPhysicsCommandBatch batch) {
        lifecycleState.clearBodyCreationPublication(batch);
    }

    private void markWorldChanged() {
        lifecycleState.markWorldChanged(bodyRegistry, ownerGateway.hasOwnerExecutor());
    }

    @Nonnull
    private PhysicsCommandCompletion executeCommandBatch(@Nonnull RecordedPhysicsCommandBatch batch) {
        return lifecycleState.executeCommandBatch(batch, simulationExecutor::execute);
    }

    @Nonnull
    @Override
    public PhysicsWorldResource clone() {
        PhysicsWorldRuntimeResource copy = new PhysicsWorldRuntimeResource();
        copy.copyFrom(this);
        return copy;
    }
}
